package uk.co.siland.culvery.provider.calendar_google

import android.util.Log
import java.io.IOException
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.serialization.DeserializationStrategy
import kotlinx.serialization.json.JsonElement
import okhttp3.Call
import okhttp3.Callback
import okhttp3.HttpUrl
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.Response
import uk.co.siland.culvery.capability.calendar.NeedsSignInException
import uk.co.siland.culvery.capability.calendar.UnreachableException
import uk.co.siland.culvery.capability.calendar.WriteRejectedException

const val GOOGLE_CALENDAR_BASE_URL = "https://www.googleapis.com/calendar/v3/"

/** Follow-up R8's fixed wording. Google's own text is neither shown nor logged: it can echo calendar ids (emails). */
internal const val REFUSED = "the change was refused"
internal const val READ_ONLY_HERE = "this calendar can't be changed from the tablet"

/** The module's one log tag. */
internal const val TAG = "GoogleCalendar"

/** Which call a log line is about: a fixed name such as "events.list", never a calendar, an email or an id. */
@JvmInline
internal value class GoogleCall(val label: String)

private val JSON_TYPE = "application/json; charset=utf-8".toMediaType()

/** 403 reasons that mean "try later": Google's rate limits and its quotas (3a design §3.7). */
private val RATE_LIMITS = setOf("rateLimitExceeded", "userRateLimitExceeded", "quotaExceeded", "dailyLimitExceeded", "calendarUsageLimitsExceeded")

/** 403 reasons that mean the grant lacks a calendar scope: the account must approve it again (3a design §3.7). */
private val SCOPE_MISSING = setOf("insufficientPermissions", "ACCESS_TOKEN_SCOPE_INSUFFICIENT")

/** Path segments that are Google's words rather than ids, and the ones after which an id always follows. */
private val PATH_WORDS = setOf("calendar", "v3", "users", "me", "calendarList", "calendars", "events")
private val ID_FOLLOWS = setOf("calendars", "events", "calendarList")

/**
 * [url]'s path with every id replaced by "{id}", for the log (P8): a calendar id is often the account's email.
 * A segment is kept only when it is one of Google's words in a place no id can be.
 */
internal fun pathTemplate(url: HttpUrl): String {
    val out = mutableListOf<String>()
    for (segment in url.pathSegments.filter { it.isNotEmpty() }) {
        out += if (segment in PATH_WORDS && out.lastOrNull() !in ID_FOLLOWS) segment else "{id}"
    }
    return out.joinToString("/").removePrefix("calendar/v3/")
}

/**
 * Enqueues the call and suspends until its whole body is in (follow-up R9). The body is read on OkHttp's own thread,
 * never the caller's, and inside the cancellable region: cancelling the coroutine cancels the call, which ends a
 * stalled body read at once, and the caller sees the cancellation straight away.
 */
internal suspend fun Call.await(): GoogleResponse = suspendCancellableCoroutine { cont ->
    cont.invokeOnCancellation { cancel() }
    enqueue(
        object : Callback {
            override fun onFailure(call: Call, e: IOException) {
                cont.resumeWithException(e)
            }

            override fun onResponse(call: Call, response: Response) {
                val answer = try {
                    response.use { GoogleResponse(it.code, it.body?.string().orEmpty()) }
                } catch (e: Throwable) {
                    // After a cancel, this is the closed socket; the continuation has already been cancelled. Anything
                    // else goes to the caller too: OkHttp would rethrow it on its own thread and the caller would hang.
                    cont.resumeWithException(e)
                    return
                }
                cont.resume(answer)
            }
        },
    )
}

/** What Google answered: the status, the body, and Google's error reasons (for the log) and message (never logged). */
internal class GoogleResponse(val code: Int, val body: String) {
    val isSuccessful: Boolean get() = code in 200..299

    /** 404 or 410: the event (or calendar) isn't there. */
    val isGoneStatus: Boolean get() = code == 404 || code == 410

    private val error: ErrorDetail? by lazy {
        runCatching { GoogleJson.decodeFromString(ErrorBody.serializer(), body).error }.getOrNull()
    }

    /** Every reason Google gave: its classic errors[].reason, then its newer details[].reason. */
    val reasons: List<String> by lazy { error?.let { e -> (e.errors + e.details).mapNotNull { it.reason } }.orEmpty() }
    val reason: String? get() = reasons.firstOrNull()
    val message: String? get() = error?.message
}

/**
 * A successful answer as [deserializer] reads it; a body that doesn't parse means try later (3a design §3.7).
 * [what] is logged, so it names the request and never a calendar, an email or an id.
 */
internal fun <T> GoogleResponse.decode(deserializer: DeserializationStrategy<T>, what: GoogleCall): T =
    try {
        GoogleJson.decodeFromString(deserializer, body)
    } catch (e: IllegalArgumentException) {
        // Not the exception itself: kotlinx.serialization quotes the body, which can hold emails (P8).
        Log.w(TAG, "${what.label}: Google Calendar sent a body the tablet can't read (${e::class.simpleName})")
        throw UnreachableException("Google Calendar sent an answer the tablet can't read")
    }

/** A read's answer: success, or "try later" for any status the caller didn't handle, logged with Google's reason ([what] as for [decode]). */
internal fun GoogleResponse.readOrUnreachable(what: GoogleCall): GoogleResponse {
    if (isSuccessful) return this
    Log.w(TAG, "${what.label}: Google Calendar answered ${this.code} (${this.reason})")
    throw UnreachableException("Google Calendar answered $code")
}

/** A write's refusal in the tablet's own words (3a design §3.7, R8); Google's reason is logged ([what] as for [decode]). */
internal fun GoogleResponse.refusal(what: GoogleCall): WriteRejectedException {
    Log.w(TAG, "${what.label}: Google Calendar refused it with ${this.code} (${this.reason})")
    return WriteRejectedException(if (code == 403) READ_ONLY_HERE else REFUSED)
}

/**
 * Google Calendar API v3 over OkHttp (3a design §3.1, §3.7). Each request asks [tokens] for the account's token; a
 * 401 clears it and tries once more with a fresh one, and a second 401 means the account needs signing in again, as
 * does a 403 for a missing scope. The answers every call treats alike are "try later" (UnreachableException): 429, a
 * rate-limit or quota 403, 5xx, and a connection that fails or times out. Everything else is returned for the caller
 * to map. Main-safe: the connect screen calls it from the main thread.
 */
class GoogleApi(private val baseUrl: HttpUrl, private val tokens: TokenSource, private val client: OkHttpClient) {
    /** [segments] below the base URL, each encoded (calendar ids hold '@' and '#'), with the non-null [query] values. */
    fun url(vararg segments: String, query: Map<String, String?> = emptyMap()): HttpUrl {
        val builder = baseUrl.newBuilder()
        segments.forEach { builder.addPathSegment(it) }
        query.forEach { (key, value) -> if (value != null) builder.addQueryParameter(key, value) }
        return builder.build()
    }

    internal suspend fun send(account: String, method: String, url: HttpUrl, body: JsonElement? = null): GoogleResponse {
        val first = tokens.token(account)
        val answer = execute(first, method, url, body)
        if (answer.code != 401) return answer
        tokens.invalidate(first)
        val second = execute(tokens.token(account), method, url, body)
        if (second.code == 401) throw NeedsSignInException("Google refused the account's token twice")
        return second
    }

    /** With a token already in hand: the connect flow's, before the account is known. */
    internal suspend fun sendWithToken(token: String, method: String, url: HttpUrl): GoogleResponse {
        val answer = execute(token, method, url, null)
        if (answer.code == 401) throw NeedsSignInException("Google refused the new token")
        return answer
    }

    private suspend fun execute(token: String, method: String, url: HttpUrl, body: JsonElement?): GoogleResponse {
        val request = Request.Builder()
            .url(url)
            .header("Authorization", "Bearer $token")
            .method(method, body?.toString()?.toRequestBody(JSON_TYPE))
            .build()
        val answer = try {
            client.newCall(request).await()
        } catch (e: IOException) {
            // A cancelled caller gets its cancellation, never "unreachable".
            currentCoroutineContext().ensureActive()
            throw UnreachableException("Couldn't reach Google Calendar", e)
        }
        if (answer.code == 403 && answer.reasons.any { it in SCOPE_MISSING }) {
            Log.w(TAG, "${request.method} ${pathTemplate(request.url)}: Google Calendar says the grant lacks a calendar scope (${answer.reason})")
            throw NeedsSignInException("Google Calendar needs the calendar scopes approved again")
        }
        if (answer.code == 429 || answer.code >= 500 || (answer.code == 403 && answer.reasons.any { it in RATE_LIMITS })) {
            Log.w(TAG, "${request.method} ${pathTemplate(request.url)}: Google Calendar said try later: ${answer.code} (${answer.reason})")
            throw UnreachableException("Google Calendar asked to try later (${answer.code})")
        }
        return answer
    }
}
