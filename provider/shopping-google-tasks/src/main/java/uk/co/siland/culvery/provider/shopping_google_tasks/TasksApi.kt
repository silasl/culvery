package uk.co.siland.culvery.provider.shopping_google_tasks

import android.util.Log
import java.io.IOException
import java.net.UnknownHostException
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.serialization.DeserializationStrategy
import kotlinx.serialization.json.JsonElement
import okhttp3.HttpUrl
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import uk.co.siland.culvery.core.google.TokenSource
import uk.co.siland.culvery.core.net.await
import uk.co.siland.culvery.core.plugin.NeedsSignInException
import uk.co.siland.culvery.core.plugin.OfflineException
import uk.co.siland.culvery.core.plugin.UnreachableException

const val TASKS_BASE_URL = "https://tasks.googleapis.com/tasks/v1/"

val TASKS_SCOPES = listOf("https://www.googleapis.com/auth/tasks")

internal const val TASKS_MAX_BYTES = 2L * 1024 * 1024

/** The module's one log tag. */
internal const val TAG = "GoogleTasks"

/** Which call a log line is about: a fixed name, never a list, an account or an item. */
@JvmInline
internal value class TasksCall(val label: String) {
    companion object {
        val TASKLISTS_LIST = TasksCall("tasklists.list")
        val TASKLISTS_INSERT = TasksCall("tasklists.insert")
        val TASKS_LIST = TasksCall("tasks.list")
        val TASKS_HISTORY = TasksCall("tasks.list completed")
        val TASKS_INSERT = TasksCall("tasks.insert")
        val TASKS_PATCH = TasksCall("tasks.patch")
    }
}

private val JSON_TYPE = "application/json; charset=utf-8".toMediaType()

/** 403 reasons that mean "try later". */
private val RATE_LIMITS = setOf("rateLimitExceeded", "userRateLimitExceeded", "quotaExceeded", "dailyLimitExceeded")

/** 403 reasons that mean the grant lacks the Tasks scope: the account must allow it again. */
private val SCOPE_MISSING = setOf("insufficientPermissions", "ACCESS_TOKEN_SCOPE_INSUFFICIENT")

/** What Google answered: the status, the body, and Google's error reasons. Its `message` is never read: it can echo item text. */
internal class TasksAnswer(val code: Int, val body: String) {
    val isSuccessful: Boolean get() = code in 200..299

    private val error: ErrorDetail? by lazy {
        runCatching { TasksJson.decodeFromString(ErrorBody.serializer(), body).error }.getOrNull()
    }

    val reasons: List<String> by lazy { error?.let { e -> (e.errors + e.details).mapNotNull { it.reason } }.orEmpty() }
    val reason: String? get() = reasons.firstOrNull()
}

/** A successful answer as [deserializer] reads it; a body that doesn't parse means try later. */
internal fun <T> TasksAnswer.decode(deserializer: DeserializationStrategy<T>, what: TasksCall): T =
    try {
        TasksJson.decodeFromString(deserializer, body)
    } catch (e: IllegalArgumentException) {
        // Not the exception itself: kotlinx.serialization quotes the body, which holds item text.
        Log.w(TAG, "${what.label}: Google Tasks sent a body the tablet can't read (${e::class.simpleName})")
        throw UnreachableException("Google Tasks sent an answer the tablet can't read")
    }

/** An answer a read can use, or "try later" for any status the caller didn't handle. */
internal fun TasksAnswer.readOrUnreachable(what: TasksCall): TasksAnswer {
    if (isSuccessful) return this
    Log.w(TAG, "${what.label}: Google Tasks answered ${this.code} (${this.reason})")
    throw UnreachableException("Google Tasks answered $code")
}

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
    fun url(vararg segments: String, query: Map<String, String?> = emptyMap()): HttpUrl {
        val builder = baseUrl.newBuilder()
        segments.forEach { builder.addPathSegment(it) }
        query.forEach { (key, value) -> if (value != null) builder.addQueryParameter(key, value) }
        return builder.build()
    }

    internal suspend fun send(account: String, method: String, url: HttpUrl, what: TasksCall, body: JsonElement? = null): TasksAnswer {
        val first = tokens.token(account, TASKS_SCOPES)
        val answer = execute(first, method, url, what, body)
        if (answer.code != 401) return answer
        tokens.invalidate(first)
        val second = execute(tokens.token(account, TASKS_SCOPES), method, url, what, body)
        if (second.code == 401) {
            Log.w(TAG, "${what.label}: Google Tasks refused the account's token twice")
            throw NeedsSignInException("Google refused the account's token twice")
        }
        return second
    }

    private suspend fun execute(token: String, method: String, url: HttpUrl, what: TasksCall, body: JsonElement?): TasksAnswer {
        val request = Request.Builder()
            .url(url)
            .header("Authorization", "Bearer $token")
            .method(method, body?.toString()?.toRequestBody(JSON_TYPE))
            .build()
        val answer = try {
            client.newCall(request).await(TASKS_MAX_BYTES).let { TasksAnswer(it.code, it.text) }
        } catch (e: IOException) {
            // A cancelled caller gets its cancellation, never "unreachable".
            currentCoroutineContext().ensureActive()
            Log.w(TAG, "${what.label}: Couldn't reach Google Tasks (${e::class.simpleName})")
            throw if (e is UnknownHostException) OfflineException("No network to Google Tasks", e) else UnreachableException("Couldn't reach Google Tasks", e)
        }
        if (answer.code == 403 && answer.reasons.any { it in SCOPE_MISSING }) {
            Log.w(TAG, "${what.label}: Google Tasks says the grant lacks the Tasks scope (${answer.reason})")
            throw NeedsSignInException("Google Tasks needs the Tasks scope approved again")
        }
        if (answer.code == 429 || answer.code >= 500 || (answer.code == 403 && answer.reasons.any { it in RATE_LIMITS })) {
            Log.w(TAG, "${what.label}: Google Tasks said try later: ${answer.code} (${answer.reason})")
            throw UnreachableException("Google Tasks asked to try later (${answer.code})")
        }
        return answer
    }
}
