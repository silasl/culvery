package uk.co.siland.culvery.provider.shopping_google_tasks

import android.app.Activity
import android.util.Log
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import java.time.Instant
import java.time.temporal.ChronoUnit
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import okhttp3.HttpUrl
import uk.co.siland.culvery.capability.shopping.AccountGoneException
import uk.co.siland.culvery.capability.shopping.ItemGoneException
import uk.co.siland.culvery.capability.shopping.ListGoneException
import uk.co.siland.culvery.capability.shopping.ProviderAccounts
import uk.co.siland.culvery.capability.shopping.SHOPPING_CONFIG_ACCOUNT
import uk.co.siland.culvery.capability.shopping.ShoppingItem
import uk.co.siland.culvery.capability.shopping.ShoppingProvider
import uk.co.siland.culvery.capability.shopping.ShoppingRejectedException
import uk.co.siland.culvery.core.google.Authorizer
import uk.co.siland.culvery.core.google.GoogleAccounts
import uk.co.siland.culvery.core.google.PlayServicesCheck
import uk.co.siland.culvery.core.google.rememberGoogleScreens
import uk.co.siland.culvery.core.plugin.Connection
import uk.co.siland.culvery.core.plugin.Feature
import uk.co.siland.culvery.core.plugin.ProviderDescriptor
import uk.co.siland.culvery.core.plugin.Toaster
import uk.co.siland.culvery.core.plugin.UnreachableException
import uk.co.siland.culvery.core.ui.Icons

private const val PAGE_SIZE = "100"
private const val OPEN_FIELDS = "items(id,title,status,updated,parent,position),nextPageToken"
private const val HISTORY_FIELDS = "items(id,title,status,completed),nextPageToken"
private const val NO_ACCOUNTS_LINE = "Connect Google Calendar first. Shopping uses the same account."

/** Tick and untick bodies, by hand: the untick's explicit null is what clears the completed time. */
private val TICK_BODY: JsonObject = buildJsonObject { put("status", "completed") }
private val UNTICK_BODY: JsonObject = buildJsonObject {
    put("status", "needsAction")
    put("completed", JsonNull)
}

/**
 * The shopping list in a Google Tasks list called "Shopping". The connection's config holds the account's email and
 * the task list's id; every call takes a fresh token from Play services. Nothing is ever deleted.
 */
@Singleton
class GoogleTasksProvider @Inject constructor(
    private val api: TasksApi,
    private val authorizer: Authorizer,
    private val googleAccounts: GoogleAccounts,
    private val playServices: PlayServicesCheck,
    private val toaster: Toaster,
) : ShoppingProvider {
    override val descriptor = ProviderDescriptor(
        TASKS_PROVIDER_ID,
        TASKS_DISPLAY_NAME,
        Icons.SHOPPING_CART,
        setOf(Feature.READ, Feature.WRITE),
        connectHint = "Allow Google Tasks for the family’s Google account.",
    )

    override val accounts: Flow<ProviderAccounts> = googleAccounts.connected.map { connected ->
        if (connected.isEmpty()) ProviderAccounts.Blocked(NO_ACCOUNTS_LINE) else ProviderAccounts.Ready(connected)
    }

    override val accountGoneLine = "Shopping’s Google account was disconnected. Connect it again in Settings › Calendars."

    @Composable
    override fun ConnectScreen(account: String?, existing: Connection?, onConnected: (Connection) -> Unit, onCancel: () -> Unit) {
        val chosen = account ?: existing?.config?.get(SHOPPING_CONFIG_ACCOUNT)
        val flow = remember { TasksConnectFlow(authorizer, api, toaster, playServices) }
        val scope = rememberCoroutineScope()
        val connected by rememberUpdatedState(onConnected)
        val cancelled by rememberUpdatedState(onCancel)
        val screens = rememberGoogleScreens(
            onAnswer = { resultCode, data ->
                // Backing out of the chooser or the consent screen closes the card with nothing said.
                if (resultCode != Activity.RESULT_OK || chosen == null) {
                    cancelled()
                } else {
                    scope.launch {
                        when (val step = flow.afterScreens(chosen, existing, data)) {
                            is TasksConnectStep.Done -> connected(step.connection)
                            else -> cancelled()
                        }
                    }
                }
            },
            onNotOpened = { cancelled() },
        )
        LaunchedEffect(chosen, existing) {
            if (chosen == null) {
                cancelled()
                return@LaunchedEffect
            }
            when (val step = flow.start(chosen, existing)) {
                is TasksConnectStep.Done -> connected(step.connection)
                is TasksConnectStep.ShowScreens -> screens.show(step.intent)
                TasksConnectStep.Stopped -> cancelled()
            }
        }
    }

    override suspend fun remakeList(conn: Connection): Connection {
        val account = accountOf(conn)
        val list = api.findOrMakeShoppingList(account)
        return conn.copy(config = conn.config + (CONFIG_LIST to list))
    }

    override suspend fun items(conn: Connection): List<ShoppingItem> {
        val account = accountOf(conn)
        val query = mapOf("showCompleted" to "false", "showHidden" to "false", "maxResults" to PAGE_SIZE, "fields" to OPEN_FIELDS)
        val open = readTasks(account, conn, TasksCall.TASKS_LIST, query)
        return inGoogleOrder(open).mapNotNull { task -> task.toItem(task.updated) }
    }

    override suspend fun history(conn: Connection, since: Instant): List<ShoppingItem> {
        val account = accountOf(conn)
        val query = mapOf(
            "showCompleted" to "true",
            "showHidden" to "true",
            "completedMin" to since.truncatedTo(ChronoUnit.SECONDS).toString(),
            "maxResults" to PAGE_SIZE,
            "fields" to HISTORY_FIELDS,
        )
        return readTasks(account, conn, TasksCall.TASKS_HISTORY, query)
            .filter { it.status == "completed" }
            .mapNotNull { task -> task.toItem(task.completed) }
    }

    override suspend fun add(conn: Connection, text: String, clientKey: String): ShoppingItem {
        val account = accountOf(conn)
        val body = TasksJson.encodeToJsonElement(NewTask.serializer(), NewTask(text))
        val answer = api.send(account, "POST", listUrl(conn, "tasks"), TasksCall.TASKS_INSERT, body)
        if (answer.code == 404) {
            logAnswer(TasksCall.TASKS_INSERT, answer)
            throw ListGoneException("The Shopping list is gone")
        }
        if (answer.code in 400..499) {
            logAnswer(TasksCall.TASKS_INSERT, answer)
            throw ShoppingRejectedException("Google Tasks refused the item (${answer.code})")
        }
        val task = answer.readOrUnreachable(TasksCall.TASKS_INSERT).decode(TaskDto.serializer(), TasksCall.TASKS_INSERT)
        return ShoppingItem(task.id, text, instantOf(task.updated))
    }

    override suspend fun tick(conn: Connection, id: String) = patch(conn, id, TICK_BODY)

    override suspend fun untick(conn: Connection, id: String) = patch(conn, id, UNTICK_BODY)

    private suspend fun patch(conn: Connection, id: String, body: JsonObject) {
        val account = accountOf(conn)
        val answer = api.send(account, "PATCH", listUrl(conn, "tasks", id), TasksCall.TASKS_PATCH, body)
        if (answer.isSuccessful) return
        logAnswer(TasksCall.TASKS_PATCH, answer)
        when (answer.code) {
            404, 410 -> throw ItemGoneException("The item is gone")
            in 400..499 -> throw ShoppingRejectedException("Google Tasks refused the change (${answer.code})")
            else -> throw UnreachableException("Google Tasks answered ${answer.code}")
        }
    }

    /** Every page of the list's tasks under [query]; a 404 is the list gone. */
    private suspend fun readTasks(account: String, conn: Connection, what: TasksCall, query: Map<String, String>): List<TaskDto> {
        val tasks = mutableListOf<TaskDto>()
        var pageToken: String? = null
        do {
            val url = listUrl(conn, "tasks", query = query + ("pageToken" to pageToken))
            val answer = api.send(account, "GET", url, what)
            if (answer.code == 404) {
                logAnswer(what, answer)
                throw ListGoneException("The Shopping list is gone")
            }
            val page = answer.readOrUnreachable(what).decode(TasksPage.serializer(), what)
            tasks += page.items
            pageToken = page.nextPageToken
        } while (pageToken != null)
        return tasks
    }

    private fun logAnswer(what: TasksCall, answer: TasksAnswer) {
        Log.w(TAG, "${what.label}: Google Tasks answered ${answer.code} (${answer.reason})")
    }

    /** A URL under the connection's own list, never "@default"; a connection without a list has none to read. */
    private fun listUrl(conn: Connection, vararg rest: String, query: Map<String, String?> = emptyMap()): HttpUrl {
        val list = conn.config[CONFIG_LIST] ?: throw ListGoneException("No Shopping list is stored")
        return api.url("lists", list, *rest, query = query)
    }

    /** The connection's account, if it is still one of the connected ones. */
    private suspend fun accountOf(conn: Connection): String {
        val account = conn.config[SHOPPING_CONFIG_ACCOUNT]
        if (account == null || googleAccounts.connected.first().none { it.equals(account, ignoreCase = true) }) {
            throw AccountGoneException("The Google account is no longer connected")
        }
        return account
    }
}

/** Google's order: top-level tasks by position, each followed by its subtasks by position; an orphaned subtask counts as top-level. */
private fun inGoogleOrder(open: List<TaskDto>): List<TaskDto> {
    val ids = open.map { it.id }.toSet()
    val (subtasks, top) = open.partition { it.parent != null && it.parent in ids }
    val byParent = subtasks.groupBy { it.parent }
    return top.sortedBy { it.position.orEmpty() }.flatMap { parent ->
        listOf(parent) + byParent[parent.id].orEmpty().sortedBy { it.position.orEmpty() }
    }
}

private fun TaskDto.toItem(changedAt: String?): ShoppingItem? {
    val text = title.orEmpty()
    if (text.isBlank() || deleted == true) return null
    return ShoppingItem(id, text, instantOf(changedAt))
}

private fun instantOf(text: String?): Instant = text?.let { runCatching { Instant.parse(it) }.getOrNull() } ?: Instant.EPOCH
