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
import okhttp3.OkHttpClient
import okhttp3.Response
import uk.co.siland.culvery.capability.calendar.NeedsSignInException
import uk.co.siland.culvery.capability.calendar.UnreachableException
import uk.co.siland.culvery.capability.calendar.WriteRejectedException
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

const val GOOGLE_CALENDAR_BASE_URL = "https://www.googleapis.com/calendar/v3/"

/** Google's own refusal text is neither shown nor logged: it can echo calendar ids (emails). */
internal const val READ_ONLY_HERE = "this calendar can’t be changed from the tablet"

/** The module's one log tag. */
internal const val TAG = "GoogleCalendar"

/** Which call a log line is about: a fixed name such as "events.list", never a calendar, an email or an id. */
@JvmInline
internal value class GoogleCall private constructor(override val label: String) : GoogleCallLabel {
    companion object {
        val CALENDAR_LIST = GoogleCall("calendarList.list")
        val EVENTS_LIST = GoogleCall("events.list")
        val EVENT_SERIES = GoogleCall("events.get series")
        val EVENT_INSERT = GoogleCall("events.insert")
        val EVENT_PATCH = GoogleCall("events.patch")
        val EVENT_GET = GoogleCall("events.get")
        val PRIMARY_CALENDAR = GoogleCall("calendars.get primary")
        val ADDING = GoogleCall("Adding an event")
        val CHANGING = GoogleCall("Changing an event")
        val DELETING = GoogleCall("Deleting an event")
    }
}

private val SERVICE = GoogleService(TAG, "Google Calendar")

/** Calendar's own 403 quota reason, besides Google's (3a design �3.7). */
private val CALENDAR_RATE_LIMITS = setOf("calendarUsageLimitsExceeded")

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
internal suspend fun Call.await(): GoogleAnswer = suspendCancellableCoroutine { cont ->
    cont.invokeOnCancellation { cancel() }
    enqueue(
        object : Callback {
            override fun onFailure(call: Call, e: IOException) {
                cont.resumeWithException(e)
            }

            override fun onResponse(call: Call, response: Response) {
                val answer = try {
                    response.use { GoogleAnswer(it.code, it.body?.string().orEmpty()) }
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

/** 404 or 410: the event (or calendar) isn't there. */
internal val GoogleAnswer.isGoneStatus: Boolean get() = code == 404 || code == 410

internal fun <T> GoogleAnswer.decode(deserializer: DeserializationStrategy<T>, what: GoogleCall): T = decode(deserializer, SERVICE, what)

internal fun GoogleAnswer.readOrUnreachable(what: GoogleCall): GoogleAnswer = readOrUnreachable(SERVICE, what)

/** A write's refusal in the tablet's own words (3a design §3.7, R8); Google's reason is logged ([what] as for [decode]). */
internal fun GoogleAnswer.refusal(what: GoogleCall): WriteRejectedException {
    Log.w(TAG, "${what.label}: Google Calendar refused it with ${this.code} (${this.reason})")
    return WriteRejectedException(if (code == 403) READ_ONLY_HERE else null)
}

/**
 * Google Calendar API v3 over OkHttp (3a design §3.1, §3.7). Each request asks [tokens] for the account's token; a
 * 401 clears it and tries once more with a fresh one, and a second 401 means the account needs signing in again, as
 * does a 403 for a missing scope. The answers every call treats alike are "try later" (UnreachableException): 429, a
 * rate-limit or quota 403, 5xx, and a connection that fails or times out. Everything else is returned for the caller
 * to map. Main-safe: the connect screen calls it from the main thread.
 */
class GoogleApi(private val baseUrl: HttpUrl, private val tokens: TokenSource, clients: Lazy<OkHttpClient>) {
    /** Tests pass a client they built; the app's is built on its first call rather than at start-up (4c §4.1). */
    constructor(baseUrl: HttpUrl, tokens: TokenSource, client: OkHttpClient) : this(baseUrl, tokens, lazyOf(client))

    private val client: OkHttpClient by clients

    /** [segments] below the base URL, each encoded (calendar ids hold '@' and '#'), with the non-null [query] values. */
    fun url(vararg segments: String, query: Map<String, String?> = emptyMap()): HttpUrl = baseUrl.child(*segments, query = query)

    internal suspend fun send(account: String, method: String, url: HttpUrl, body: JsonElement? = null): GoogleAnswer =
        tokens.sendRetrying401(
            account,
            CALENDAR_SCOPES,
            refusedTwice = { throw NeedsSignInException("Google refused the account's token twice") },
        ) { token -> execute(token, method, url, body) }

    /** With a token already in hand: the connect flow's, before the account is known. */
    internal suspend fun sendWithToken(token: String, method: String, url: HttpUrl): GoogleAnswer {
        val answer = execute(token, method, url, null)
        if (answer.code == 401) throw NeedsSignInException("Google refused the new token")
        return answer
    }

    private suspend fun execute(token: String, method: String, url: HttpUrl, body: JsonElement?): GoogleAnswer {
        val request = googleRequest(url, token, method, body)
        val answer = try {
            client.newCall(request).await()
        } catch (e: IOException) {
            // A cancelled caller gets its cancellation, never "unreachable".
            currentCoroutineContext().ensureActive()
            throw e.asUnreachable(SERVICE, offlineOnUnknownHost = false)
        }
        when (answer.fault(CALENDAR_RATE_LIMITS)) {
            GoogleFault.SCOPE_MISSING -> {
                Log.w(TAG, "${request.method} ${pathTemplate(request.url)}: Google Calendar says the grant lacks a calendar scope (${answer.reason})")
                throw NeedsSignInException("Google Calendar needs the calendar scopes approved again")
            }
            GoogleFault.TRY_LATER -> {
                Log.w(TAG, "${request.method} ${pathTemplate(request.url)}: Google Calendar said try later: ${answer.code} (${answer.reason})")
                throw UnreachableException("Google Calendar asked to try later (${answer.code})")
            }
            null -> return answer
        }
    }
}
