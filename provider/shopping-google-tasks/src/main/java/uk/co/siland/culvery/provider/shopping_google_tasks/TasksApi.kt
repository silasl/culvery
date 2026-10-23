package uk.co.siland.culvery.provider.shopping_google_tasks

import android.util.Log
import java.io.IOException
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.serialization.DeserializationStrategy
import kotlinx.serialization.json.JsonElement
import okhttp3.HttpUrl
import okhttp3.OkHttpClient
import uk.co.siland.culvery.core.google.GoogleAnswer
import uk.co.siland.culvery.core.google.GoogleCallLabel
import uk.co.siland.culvery.core.google.GoogleFault
import uk.co.siland.culvery.core.google.GoogleService
import uk.co.siland.culvery.core.google.TokenSource
import uk.co.siland.culvery.core.google.asUnreachable
import uk.co.siland.culvery.core.google.child
import uk.co.siland.culvery.core.google.decode
import uk.co.siland.culvery.core.google.fault
import uk.co.siland.culvery.core.google.googleRequest
import uk.co.siland.culvery.core.google.readOrUnreachable
import uk.co.siland.culvery.core.google.sendRetrying401
import uk.co.siland.culvery.core.net.await
import uk.co.siland.culvery.core.plugin.NeedsSignInException
import uk.co.siland.culvery.core.plugin.UnreachableException

const val TASKS_BASE_URL = "https://tasks.googleapis.com/tasks/v1/"

val TASKS_SCOPES = listOf("https://www.googleapis.com/auth/tasks")

internal const val TASKS_MAX_BYTES = 2L * 1024 * 1024

/** The module's one log tag. */
internal const val TAG = "GoogleTasks"

/** Which call a log line is about: a fixed name, never a list, an account or an item. */
@JvmInline
internal value class TasksCall(override val label: String) : GoogleCallLabel {
    companion object {
        val TASKLISTS_LIST = TasksCall("tasklists.list")
        val TASKLISTS_INSERT = TasksCall("tasklists.insert")
        val TASKS_LIST = TasksCall("tasks.list")
        val TASKS_HISTORY = TasksCall("tasks.list completed")
        val TASKS_INSERT = TasksCall("tasks.insert")
        val TASKS_PATCH = TasksCall("tasks.patch")
    }
}

private val SERVICE = GoogleService(TAG, "Google Tasks")

internal fun <T> GoogleAnswer.decode(deserializer: DeserializationStrategy<T>, what: TasksCall): T = decode(deserializer, SERVICE, what)

internal fun GoogleAnswer.readOrUnreachable(what: TasksCall): GoogleAnswer = readOrUnreachable(SERVICE, what)

/**
 * Google Tasks API v1 over OkHttp. Each request asks [tokens] for the account's token with [TASKS_SCOPES]; a 401 clears
 * it and tries once more, and a second 401 means the account needs signing in again, as does a 403 for a missing scope.
 * "Try later" (UnreachableException) is 429, a rate-limit 403, 5xx and a connection that fails; no network at all is
 * OfflineException. Everything else is returned for the caller to map. Item text travels in request bodies only.
 */
class TasksApi(private val baseUrl: HttpUrl, private val tokens: TokenSource, clients: Lazy<OkHttpClient>) {
    /** Tests pass a client they built; the app's is built on its first call. */
    constructor(baseUrl: HttpUrl, tokens: TokenSource, client: OkHttpClient) : this(baseUrl, tokens, lazyOf(client))

    private val client: OkHttpClient by clients

    /** [segments] below the base URL, each encoded, with the non-null [query] values. */
    fun url(vararg segments: String, query: Map<String, String?> = emptyMap()): HttpUrl = baseUrl.child(*segments, query = query)

    internal suspend fun send(account: String, method: String, url: HttpUrl, what: TasksCall, body: JsonElement? = null): GoogleAnswer =
        tokens.sendRetrying401(
            account,
            TASKS_SCOPES,
            refusedTwice = {
                Log.w(TAG, "${what.label}: Google Tasks refused the account's token twice")
                throw NeedsSignInException("Google refused the account's token twice")
            },
        ) { token -> execute(token, method, url, what, body) }

    private suspend fun execute(token: String, method: String, url: HttpUrl, what: TasksCall, body: JsonElement?): GoogleAnswer {
        val answer = try {
            client.newCall(googleRequest(url, token, method, body)).await(TASKS_MAX_BYTES).let { GoogleAnswer(it.code, it.text) }
        } catch (e: IOException) {
            // A cancelled caller gets its cancellation, never "unreachable".
            currentCoroutineContext().ensureActive()
            Log.w(TAG, "${what.label}: Couldn't reach Google Tasks (${e::class.simpleName})")
            throw e.asUnreachable(SERVICE, offlineOnUnknownHost = true)
        }
        when (answer.fault()) {
            GoogleFault.SCOPE_MISSING -> {
                Log.w(TAG, "${what.label}: Google Tasks says the grant lacks the Tasks scope (${answer.reason})")
                throw NeedsSignInException("Google Tasks needs the Tasks scope approved again")
            }
            GoogleFault.TRY_LATER -> {
                Log.w(TAG, "${what.label}: Google Tasks said try later: ${answer.code} (${answer.reason})")
                throw UnreachableException("Google Tasks asked to try later (${answer.code})")
            }
            null -> return answer
        }
    }
}
