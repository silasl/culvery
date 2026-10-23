package uk.co.siland.culvery.provider.shopping_google_tasks

import android.content.Intent
import android.util.Log
import java.util.UUID
import uk.co.siland.culvery.capability.shopping.SHOPPING_CONFIG_ACCOUNT
import uk.co.siland.culvery.core.google.Authorization
import uk.co.siland.culvery.core.google.Authorizer
import uk.co.siland.culvery.core.google.ConnectStep
import uk.co.siland.culvery.core.google.GoogleConnectSteps
import uk.co.siland.culvery.core.google.GoogleJson
import uk.co.siland.culvery.core.google.PlayServicesCheck
import uk.co.siland.culvery.core.plugin.Connection
import uk.co.siland.culvery.core.plugin.Toaster
import uk.co.siland.culvery.core.plugin.textKey

/** Stored with every Google Tasks connection: never change it. */
const val TASKS_PROVIDER_ID = "shopping.google-tasks"

internal const val TASKS_DISPLAY_NAME = "Google Tasks"

/** The name of the task list the shopping list lives in. */
internal const val SHOPPING_LIST_TITLE = "Shopping"

/** The [Connection.config] key holding the Google task list's id. */
internal const val CONFIG_LIST = "list"

internal const val PAGE_SIZE = "100"

/** The id of the account's list called "Shopping" (any case, spacing or accents), made when it has none. */
internal suspend fun TasksApi.findOrMakeShoppingList(account: String): String {
    var pageToken: String? = null
    do {
        val url = url("users", "@me", "lists", query = mapOf("maxResults" to PAGE_SIZE, "pageToken" to pageToken))
        val page = send(account, "GET", url, TasksCall.TASKLISTS_LIST)
            .readOrUnreachable(TasksCall.TASKLISTS_LIST)
            .decode(TaskListsPage.serializer(), TasksCall.TASKLISTS_LIST)
        page.items.firstOrNull { textKey(it.title.orEmpty()) == textKey(SHOPPING_LIST_TITLE) }?.let { return it.id }
        pageToken = page.nextPageToken
    } while (pageToken != null)
    val body = GoogleJson.encodeToJsonElement(NewTaskList.serializer(), NewTaskList(SHOPPING_LIST_TITLE))
    return send(account, "POST", url("users", "@me", "lists"), TasksCall.TASKLISTS_INSERT, body)
        .readOrUnreachable(TasksCall.TASKLISTS_INSERT)
        .decode(TaskListDto.serializer(), TasksCall.TASKLISTS_INSERT)
        .id
}

/**
 * Connecting Google Tasks: ask Play services for [TASKS_SCOPES] for the chosen account, check the grant holds them, then
 * find the list called "Shopping" or make it. A re-allow of an existing connection (passed as `existing`) checks the
 * grant only and keeps the list it has. Backing out says nothing; any other failure says "Couldn't connect", and
 * nothing is stored.
 */
internal class TasksConnectFlow(
    private val authorizer: Authorizer,
    private val api: TasksApi,
    private val toaster: Toaster,
    playServices: PlayServicesCheck,
) {
    private val steps = GoogleConnectSteps(authorizer, toaster, playServices, TAG, logName = TASKS_DISPLAY_NAME, displayName = TASKS_DISPLAY_NAME)

    /** [chosen] is the account picked on the card; a re-allow uses the connection's own, and neither means nothing to connect. */
    suspend fun start(chosen: String?, existing: Connection?): ConnectStep {
        val account = accountOf(chosen, existing) ?: return ConnectStep.Stopped
        return steps.start(account, TASKS_SCOPES) { finish(account, existing, it) }
    }

    suspend fun afterScreens(chosen: String?, existing: Connection?, data: Intent?): ConnectStep {
        val account = accountOf(chosen, existing) ?: return ConnectStep.Stopped
        return steps.afterScreens(data) { finish(account, existing, it) }
    }

    /** A re-allow is for the connection's own account, whatever was chosen. */
    private fun accountOf(chosen: String?, existing: Connection?): String? = existing?.config?.get(SHOPPING_CONFIG_ACCOUNT) ?: chosen

    private suspend fun finish(account: String, existing: Connection?, grant: Authorization.Granted): ConnectStep {
        if (!grant.scopes.containsAll(TASKS_SCOPES)) {
            // An unticked box on the consent screen.
            Log.w(TAG, "Google granted ${grant.scopes.size} scopes, without the Tasks scope; nothing stored")
            toaster.show("Google Tasks wasn’t allowed. Try again and tick Google Tasks.", urgent = true)
            return ConnectStep.Stopped
        }
        val list = existing?.config?.get(CONFIG_LIST) ?: api.findOrMakeShoppingList(account)
        val id = existing?.id ?: UUID.randomUUID().toString()
        val config = existing?.config.orEmpty() + (SHOPPING_CONFIG_ACCOUNT to account) + (CONFIG_LIST to list)
        return ConnectStep.Done(Connection(id, TASKS_PROVIDER_ID, TASKS_DISPLAY_NAME, config))
    }
}
