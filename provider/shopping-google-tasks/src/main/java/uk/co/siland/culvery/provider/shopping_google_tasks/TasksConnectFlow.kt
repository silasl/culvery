package uk.co.siland.culvery.provider.shopping_google_tasks

import android.app.PendingIntent
import android.content.Intent
import android.util.Log
import java.util.UUID
import kotlinx.coroutines.CancellationException
import uk.co.siland.culvery.capability.shopping.SHOPPING_CONFIG_ACCOUNT
import uk.co.siland.culvery.core.google.Authorization
import uk.co.siland.culvery.core.google.Authorizer
import uk.co.siland.culvery.core.google.PlayServicesCheck
import uk.co.siland.culvery.core.google.PlayServicesStatusException
import uk.co.siland.culvery.core.google.UPDATE_PLAY_SERVICES
import uk.co.siland.culvery.core.google.isUserCancel
import uk.co.siland.culvery.core.plugin.Connection
import uk.co.siland.culvery.core.plugin.Toaster
import uk.co.siland.culvery.core.plugin.UnreachableException
import uk.co.siland.culvery.core.plugin.couldNotConnect
import uk.co.siland.culvery.core.plugin.textKey

/** Stored with every Google Tasks connection: never change it. */
const val TASKS_PROVIDER_ID = "shopping.google-tasks"

internal const val TASKS_DISPLAY_NAME = "Google Tasks"

/** The name of the task list the shopping list lives in. */
internal const val SHOPPING_LIST_TITLE = "Shopping"

/** The [Connection.config] key holding the Google task list's id. */
internal const val CONFIG_LIST = "list"

internal const val PAGE_SIZE = "100"

/** One step of connecting: done, screens the user must see, or stopped (after saying why, if there is a why). */
internal sealed interface TasksConnectStep {
    data class Done(val connection: Connection) : TasksConnectStep

    class ShowScreens(val intent: PendingIntent) : TasksConnectStep

    data object Stopped : TasksConnectStep
}

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
    val body = TasksJson.encodeToJsonElement(NewTaskList.serializer(), NewTaskList(SHOPPING_LIST_TITLE))
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
    private val playServices: PlayServicesCheck,
) {
    suspend fun start(account: String, existing: Connection?): TasksConnectStep {
        val account = accountOf(account, existing)
        if (!playServices.usable()) {
            Log.w(TAG, "Play services can't run Google's sign-in on this tablet; nothing started")
            toaster.show(UPDATE_PLAY_SERVICES, urgent = true)
            return TasksConnectStep.Stopped
        }
        return step(account, existing, screensAllowed = true) { authorizer.authorize(account, TASKS_SCOPES) }
    }

    suspend fun afterScreens(account: String, existing: Connection?, data: Intent?): TasksConnectStep =
        step(accountOf(account, existing), existing, screensAllowed = false) { authorizer.authorizationFrom(data) }

    /** A re-allow is for the connection's own account, whatever was chosen. */
    private fun accountOf(chosen: String, existing: Connection?): String = existing?.config?.get(SHOPPING_CONFIG_ACCOUNT) ?: chosen

    private suspend fun step(account: String, existing: Connection?, screensAllowed: Boolean, ask: suspend () -> Authorization): TasksConnectStep =
        try {
            when (val answer = ask()) {
                is Authorization.NeedsUser -> if (screensAllowed) {
                    TasksConnectStep.ShowScreens(answer.intent)
                } else {
                    // The screens were shown and still didn't grant: only a cancel is silent.
                    Log.w(TAG, "Play services wants its screens again after they were shown; nothing stored")
                    stopWithToast()
                }
                is Authorization.Granted -> finish(account, existing, answer)
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            if (e.isUserCancel()) {
                Log.i(TAG, "Google sign-in was cancelled")
            } else {
                Log.w(TAG, "Couldn't connect to Google Tasks (${e::class.simpleName})")
                // Play services answering with a status is not the network failing.
                stopWithToast(unreachable = e is UnreachableException && e !is PlayServicesStatusException)
            }
            TasksConnectStep.Stopped
        }

    private suspend fun finish(account: String, existing: Connection?, grant: Authorization.Granted): TasksConnectStep {
        if (!grant.scopes.containsAll(TASKS_SCOPES)) {
            // An unticked box on the consent screen.
            Log.w(TAG, "Google granted ${grant.scopes.size} scopes, without the Tasks scope; nothing stored")
            toaster.show("Google Tasks wasn’t allowed. Try again and tick Google Tasks.", urgent = true)
            return TasksConnectStep.Stopped
        }
        val list = existing?.config?.get(CONFIG_LIST) ?: api.findOrMakeShoppingList(account)
        val id = existing?.id ?: UUID.randomUUID().toString()
        val config = existing?.config.orEmpty() + (SHOPPING_CONFIG_ACCOUNT to account) + (CONFIG_LIST to list)
        return TasksConnectStep.Done(Connection(id, TASKS_PROVIDER_ID, TASKS_DISPLAY_NAME, config))
    }

    private fun stopWithToast(unreachable: Boolean = false): TasksConnectStep {
        toaster.show(couldNotConnect(TASKS_DISPLAY_NAME, unreachable), urgent = true)
        return TasksConnectStep.Stopped
    }
}
