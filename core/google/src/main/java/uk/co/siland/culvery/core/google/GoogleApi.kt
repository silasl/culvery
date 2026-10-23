package uk.co.siland.culvery.core.google

import android.util.Log
import java.io.IOException
import java.net.UnknownHostException
import java.time.Duration
import kotlinx.serialization.DeserializationStrategy
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import okhttp3.HttpUrl
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import uk.co.siland.culvery.core.plugin.NeedsSignInException
import uk.co.siland.culvery.core.plugin.OfflineException
import uk.co.siland.culvery.core.plugin.UnreachableException

/** The one JSON setup: unknown keys ignored; a null field is left out unless a PATCH builds its body by hand. */
val GoogleJson = Json {
    ignoreUnknownKeys = true
    explicitNulls = false
}

/** A Google API a provider talks to: its log [tag] and the [displayName] its log lines and exceptions use. */
class GoogleService(val tag: String, val displayName: String)

/** Which call a log line is about: a fixed name, never an account, a list, a calendar or an item. */
interface GoogleCallLabel {
    val label: String
}

@Serializable
private class ErrorBody(val error: ErrorDetail? = null)

/** [errors] is Google's classic list of reasons; [details] its newer one (e.g. ACCESS_TOKEN_SCOPE_INSUFFICIENT). */
@Serializable
private class ErrorDetail(val errors: List<ErrorItem> = emptyList(), val details: List<ErrorItem> = emptyList())

@Serializable
private class ErrorItem(val reason: String? = null)

/** 403 reasons that mean "try later": Google's rate limits and its quotas. */
val GOOGLE_RATE_LIMITS = setOf("rateLimitExceeded", "userRateLimitExceeded", "quotaExceeded", "dailyLimitExceeded")

/** 403 reasons that mean the grant lacks a scope: the account must approve it again. */
val GOOGLE_SCOPE_MISSING = setOf("insufficientPermissions", "ACCESS_TOKEN_SCOPE_INSUFFICIENT")

/** What Google answered: the status, the body, and Google's error reasons. Its error message is never read: it can echo emails and item text. */
class GoogleAnswer(val code: Int, val body: String) {
    val isSuccessful: Boolean get() = code in 200..299

    private val error: ErrorDetail? by lazy {
        runCatching { GoogleJson.decodeFromString(ErrorBody.serializer(), body).error }.getOrNull()
    }

    /** Every reason Google gave: its classic errors[].reason, then its newer details[].reason. */
    val reasons: List<String> by lazy { error?.let { e -> (e.errors + e.details).mapNotNull { it.reason } }.orEmpty() }
    val reason: String? get() = reasons.firstOrNull()
}

/** An answer every call treats alike. */
enum class GoogleFault { SCOPE_MISSING, TRY_LATER }

/** 429, 5xx and a 403 for a rate limit ([extraRateLimits] adds to Google's) mean try later; a 403 for a missing scope means sign in again. */
fun GoogleAnswer.fault(extraRateLimits: Set<String> = emptySet()): GoogleFault? = when {
    code == 403 && reasons.any { it in GOOGLE_SCOPE_MISSING } -> GoogleFault.SCOPE_MISSING
    code == 429 || code >= 500 || (code == 403 && reasons.any { it in GOOGLE_RATE_LIMITS || it in extraRateLimits }) -> GoogleFault.TRY_LATER
    else -> null
}

/**
 * Sends with the account's token and, on a 401, clears it and sends once more with a fresh one. A second 401 calls
 * [refusedTwice], which throws.
 */
suspend fun TokenSource.sendRetrying401(
    account: String,
    scopes: List<String>,
    refusedTwice: () -> Nothing,
    send: suspend (token: String) -> GoogleAnswer,
): GoogleAnswer {
    val first = token(account, scopes)
    val answer = send(first)
    if (answer.code != 401) return answer
    invalidate(first)
    val second = send(token(account, scopes))
    if (second.code == 401) refusedTwice()
    return second
}

/** A failed connection as "try later"; no network at all is [OfflineException] when [offlineOnUnknownHost]. */
fun IOException.asUnreachable(service: GoogleService, offlineOnUnknownHost: Boolean): UnreachableException =
    if (offlineOnUnknownHost && this is UnknownHostException) {
        OfflineException("No network to ${service.displayName}", this)
    } else {
        UnreachableException("Couldn't reach ${service.displayName}", this)
    }

private val JSON_TYPE = "application/json; charset=utf-8".toMediaType()

fun googleRequest(url: HttpUrl, token: String, method: String, body: JsonElement?): Request = Request.Builder()
    .url(url)
    .header("Authorization", "Bearer $token")
    .method(method, body?.toString()?.toRequestBody(JSON_TYPE))
    .build()

/** [segments] below this URL, each encoded (a calendar id holds '@' and '#'), with the non-null [query] values. */
fun HttpUrl.child(vararg segments: String, query: Map<String, String?> = emptyMap()): HttpUrl {
    val builder = newBuilder()
    segments.forEach { builder.addPathSegment(it) }
    query.forEach { (key, value) -> if (value != null) builder.addQueryParameter(key, value) }
    return builder.build()
}

/**
 * A successful answer as [deserializer] reads it; a body that doesn't parse means try later. [what] is logged, so it
 * names the request and never a calendar, an email or an id.
 */
fun <T> GoogleAnswer.decode(deserializer: DeserializationStrategy<T>, service: GoogleService, what: GoogleCallLabel): T =
    try {
        GoogleJson.decodeFromString(deserializer, body)
    } catch (e: IllegalArgumentException) {
        // Not the exception itself: kotlinx.serialization quotes the body, which can hold emails and item text.
        Log.w(service.tag, "${what.label}: ${service.displayName} sent a body the tablet can't read (${e::class.simpleName})")
        throw UnreachableException("${service.displayName} sent an answer the tablet can't read")
    }

/** A read's answer: success, or "try later" for any status the caller didn't handle, logged with Google's reason. */
fun GoogleAnswer.readOrUnreachable(service: GoogleService, what: GoogleCallLabel): GoogleAnswer {
    if (isSuccessful) return this
    Log.w(service.tag, "${what.label}: ${service.displayName} answered ${this.code} (${this.reason})")
    throw UnreachableException("${service.displayName} answered $code")
}

private val CONNECT_TIMEOUT: Duration = Duration.ofSeconds(15)
private val READ_TIMEOUT: Duration = Duration.ofSeconds(30)

// The engine gives a call 60 s too; this ends a body that drips in.
private val CALL_TIMEOUT: Duration = Duration.ofSeconds(60)

/** Google compresses only for a User-Agent that contains "gzip"; OkHttp sends Accept-Encoding itself. */
fun userAgent(version: String): String = "Culvery/$version (gzip)"

/** OkHttp's own timeouts sit inside the engines' (10 s editor, 60 s drain and sync), so the engines decide. */
fun googleHttpClient(version: String): OkHttpClient = OkHttpClient.Builder()
    .connectTimeout(CONNECT_TIMEOUT)
    .readTimeout(READ_TIMEOUT)
    .callTimeout(CALL_TIMEOUT)
    .addInterceptor { chain -> chain.proceed(chain.request().newBuilder().header("User-Agent", userAgent(version)).build()) }
    .build()
