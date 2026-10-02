package uk.co.siland.culvery.provider.calendar_google

import androidx.test.ext.junit.runners.AndroidJUnit4
import com.google.common.truth.Truth.assertThat
import com.google.common.truth.Truth.assertWithMessage
import java.time.Duration
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.runInterruptible
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import okhttp3.HttpUrl
import okhttp3.OkHttpClient
import okhttp3.Response
import okhttp3.ResponseBody
import okhttp3.ResponseBody.Companion.asResponseBody
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.SocketPolicy
import okio.Buffer
import okio.ForwardingSource
import okio.buffer
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.shadows.ShadowLog
import uk.co.siland.culvery.capability.calendar.NeedsSignInException
import uk.co.siland.culvery.capability.calendar.UnreachableException
import uk.co.siland.culvery.capability.calendar.WriteRejectedException

private const val ACCOUNT = "family@example.com"

// Robolectric for android.util.Log; the server is a real MockWebServer on localhost.
@RunWith(AndroidJUnit4::class)
class GoogleApiTest {
    private val google = FakeGoogleServer()
    private val tokens = FakeTokenSource()
    private val client = OkHttpClient.Builder().readTimeout(Duration.ofSeconds(1)).build()
    private lateinit var baseUrl: HttpUrl
    private lateinit var api: GoogleApi

    @Before
    fun setUp() {
        baseUrl = google.start()
        api = GoogleApi(baseUrl, tokens, client)
        google.addCalendar(ACCOUNT, "Family", primary = true)
    }

    @After
    fun tearDown() {
        google.shutdown()
        // P8, for every test: nothing logged names the account, any email, or a token.
        ShadowLog.getLogs().forEach { log ->
            val text = "${log.msg} ${log.throwable}"
            assertWithMessage(text).that(text).doesNotContain(ACCOUNT)
            assertWithMessage(text).that(text).doesNotContain("@")
            assertWithMessage(text).that(text).doesNotContain("token-")
        }
    }

    private val calendarList get() = api.url("users", "me", "calendarList")

    @Test
    fun everyRequestCarriesTheAccountsToken() = runTest {
        assertThat(api.send(ACCOUNT, "GET", calendarList).code).isEqualTo(200)
        assertThat(tokens.asked).containsExactly(ACCOUNT)
        assertThat(google.requests.single().getHeader("Authorization")).isEqualTo("Bearer token-1")
    }

    @Test
    fun a401ClearsTheTokenAndTriesOnceMoreWithAFreshOne() = runTest {
        google.failNext(401, "authError")
        assertThat(api.send(ACCOUNT, "GET", calendarList).code).isEqualTo(200)
        assertThat(tokens.invalidated).containsExactly("token-1")
        assertThat(google.requests.map { it.getHeader("Authorization") }).containsExactly("Bearer token-1", "Bearer token-2").inOrder()
    }

    @Test
    fun a401TwiceMeansTheAccountNeedsSigningIn() = runTest {
        google.failNext(401, "authError")
        google.failNext(401, "authError")
        assertThat(failureOf { api.send(ACCOUNT, "GET", calendarList) }).isInstanceOf(NeedsSignInException::class.java)
    }

    @Test
    fun tryLaterAnswersAreUnreachable() = runTest {
        listOf(
            429 to "rateLimitExceeded", 403 to "rateLimitExceeded", 403 to "userRateLimitExceeded",
            // Google's quotas: try later, never a refusal that drops the change or a calendar that has gone.
            403 to "quotaExceeded", 403 to "dailyLimitExceeded", 403 to "calendarUsageLimitsExceeded",
            500 to "backendError", 503 to "backendError",
        ).forEach { (status, reason) ->
            google.failNext(status, reason)
            assertWithMessage("$status $reason").that(failureOf { api.send(ACCOUNT, "GET", calendarList) })
                .isInstanceOf(UnreachableException::class.java)
        }
    }

    @Test
    fun a403ForAMissingScopeMeansTheAccountNeedsSigningIn() = runTest {
        google.failNext(403, "insufficientPermissions")
        assertThat(failureOf { api.send(ACCOUNT, "GET", calendarList) }).isInstanceOf(NeedsSignInException::class.java)
        // Google's newer error form names it in details[] only.
        google.failNextWith(
            MockResponse().setResponseCode(403).setBody(
                """{"error":{"code":403,"message":"Request had insufficient authentication scopes.","errors":[{"reason":"forbidden"}],""" +
                    """"details":[{"@type":"type.googleapis.com/google.rpc.ErrorInfo","reason":"ACCESS_TOKEN_SCOPE_INSUFFICIENT"}]}}""",
            ),
        )
        assertThat(failureOf { api.send(ACCOUNT, "GET", calendarList) }).isInstanceOf(NeedsSignInException::class.java)
    }

    @Test
    fun anotherForbiddenIsLeftToTheCallerWithGooglesReason() = runTest {
        google.failNext(403, "forbidden")
        val answer = api.send(ACCOUNT, "GET", calendarList)
        assertThat(answer.code to answer.reason).isEqualTo(403 to "forbidden")
    }

    @Test
    fun aDroppedConnectionIsUnreachable() = runTest {
        // The server reads the request, then drops the connection unanswered (a Dispatcher can't drop it at the
        // start: MockWebServer honours that only through peek()). Its own client, so OkHttp doesn't quietly retry on a
        // fresh connection and get the next, healthy answer.
        val noRetry = GoogleApi(baseUrl, tokens, OkHttpClient.Builder().retryOnConnectionFailure(false).build())
        google.failNextWith(MockResponse().setSocketPolicy(SocketPolicy.DISCONNECT_AFTER_REQUEST))
        assertThat(failureOf { noRetry.send(ACCOUNT, "GET", calendarList) }).isInstanceOf(UnreachableException::class.java)
    }

    @Test
    fun aSlowReplyIsUnreachable() = runTest {
        google.failNextWith(MockResponse().setBody("{}").setHeadersDelay(3, TimeUnit.SECONDS))
        assertThat(failureOf { api.send(ACCOUNT, "GET", calendarList) }).isInstanceOf(UnreachableException::class.java)
    }

    @Test
    fun aBodyThatDoesNotParseIsUnreachable() = runTest {
        google.failNextWith(MockResponse().setBody("<html>Service Unavailable</html>"))
        assertThat(failureOf { api.send(ACCOUNT, "GET", calendarList).decode(CalendarListPage.serializer(), GoogleCall.CALENDAR_LIST) })
            .isInstanceOf(UnreachableException::class.java)
    }

    /** Follow-up R9, proved here rather than in the contract suite (review Simp8). */
    @Test
    fun cancellingTheCallerCancelsTheHttpCall() = runTest {
        // Its own client with a long read timeout, so nothing but cancelling the call can end it inside the second.
        val patient = OkHttpClient.Builder().readTimeout(Duration.ofSeconds(30)).build()
        val held = GoogleApi(baseUrl, tokens, patient)
        google.writeHold = CountDownLatch(1)
        val body = buildJsonObject { put("id", "held0123") }
        val returned = withContext(Dispatchers.Default) {
            val call = launch { held.send(ACCOUNT, "POST", held.url("calendars", ACCOUNT, "events"), body) }
            // Cancelled only once the request is really at the server.
            check(runInterruptible(Dispatchers.IO) { google.writeReached.await(5, TimeUnit.SECONDS) }) { "the write never reached the server" }
            call.cancel()
            withTimeoutOrNull(1_000) { call.join() } != null
        }
        assertThat(returned).isTrue()
        // Nothing left running in OkHttp: the call itself was cancelled, not just abandoned.
        withContext(Dispatchers.Default) { withTimeout(1_000) { while (patient.dispatcher.runningCallsCount() > 0) delay(10) } }
    }

    /** Review fix: headers in, body stalled. Cancelling must end the call at once, as a cancellation, not a failure. */
    @Test
    fun cancellingDuringTheBodyReadCancelsTheCallPromptly() = runTest {
        val reading = CountDownLatch(1)
        val patient = OkHttpClient.Builder()
            .readTimeout(Duration.ofSeconds(30))
            .addInterceptor { chain ->
                val response = chain.proceed(chain.request())
                response.newBuilder().body(response.body!!.onFirstRead { reading.countDown() }).build()
            }.build()
        // One byte every 3 s: only cancelling the call can end the read inside a second.
        google.failNextWith(MockResponse().setBody("{\"items\":[]}").throttleBody(1, 3, TimeUnit.SECONDS))
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
        try {
            val call = scope.async { GoogleApi(baseUrl, tokens, patient).send(ACCOUNT, "GET", calendarList) }
            check(runInterruptible(Dispatchers.IO) { reading.await(5, TimeUnit.SECONDS) }) { "the body was never read" }
            call.cancel()
            val finished = withContext(Dispatchers.Default) { withTimeoutOrNull(1_000) { call.join() } } != null
            assertThat(finished).isTrue()
            assertThat(runCatching { call.await() }.exceptionOrNull()).isInstanceOf(CancellationException::class.java)
            withContext(Dispatchers.Default) { withTimeout(1_000) { while (patient.dispatcher.runningCallsCount() > 0) delay(10) } }
        } finally {
            scope.cancel()
        }
    }

    @Test
    fun aNewTokenRefusedMeansSigningInAgain() = runTest {
        google.failNext(401, "authError")
        assertThat(failureOf { api.sendWithToken("fresh-token", "GET", calendarList) }).isInstanceOf(NeedsSignInException::class.java)
    }

    @Test
    fun aRefusalIsInTheTabletsOwnWords() {
        val forbidden = GoogleResponse(403, errorBody(403, "forbidden"))
        val invalid = GoogleResponse(400, errorBody(400, "invalid"))
        assertThat(forbidden.refusal(GoogleCall.ADDING)).isInstanceOf(WriteRejectedException::class.java)
        assertThat(forbidden.refusal(GoogleCall.ADDING).message).isEqualTo(READ_ONLY_HERE)
        assertThat(invalid.refusal(GoogleCall.ADDING).message).isEqualTo(REFUSED)
    }

    @Test
    fun aReadThatFailsIsUnreachableAndOneThatWorksIsReturned() = runTest {
        val ok = GoogleResponse(200, "{}")
        assertThat(ok.readOrUnreachable(GoogleCall.CALENDAR_LIST)).isSameInstanceAs(ok)
        assertThat(failureOf { GoogleResponse(404, errorBody(404, "notFound")).readOrUnreachable(GoogleCall.CALENDAR_LIST) })
            .isInstanceOf(UnreachableException::class.java)
    }

    /** Review H2: an answer without items is a bad read, never an account with no calendars. */
    @Test
    fun aCalendarListWithoutItemsIsUnreachable() = runTest {
        assertThat(failureOf { GoogleResponse(200, "{}").decode(CalendarListPage.serializer(), GoogleCall.CALENDAR_LIST) })
            .isInstanceOf(UnreachableException::class.java)
    }

    /** P8: Google's message and the path both hold the account's email; neither reaches the log. */
    @Test
    fun errorsOnTheAccountsCalendarLogNoEmail() = runTest {
        val events = api.url("calendars", ACCOUNT, "events")
        listOf(403 to "insufficientPermissions", 503 to "backendError", 403 to "forbidden").forEach { (status, reason) ->
            google.failNextWith(
                MockResponse().setResponseCode(status).setBody(
                    """{"error":{"code":$status,"message":"Not allowed for $ACCOUNT","errors":[{"reason":"$reason","message":"$ACCOUNT"}]}}""",
                ),
            )
            val answer = runCatching { api.send(ACCOUNT, "GET", events) }.getOrNull()
            answer?.refusal(GoogleCall.CHANGING)
            answer?.let { runCatching { it.readOrUnreachable(GoogleCall.EVENTS_LIST) } }
        }
        google.failNextWith(MockResponse().setBody("""{"items":"$ACCOUNT"}"""))
        runCatching { api.send(ACCOUNT, "GET", events).decode(CalendarListPage.serializer(), GoogleCall.CALENDAR_LIST) }
        assertThat(ShadowLog.getLogsForTag("GoogleCalendar").size).isAtLeast(4)
        // The assertions are tearDown's.
    }

    /** Review M3: the connect screen calls from the main thread, so the body must be read elsewhere. */
    @Test
    fun theBodyIsReadOffTheCallersThread() = runTest {
        val readOn = mutableListOf<Thread>()
        val recording = OkHttpClient.Builder().addInterceptor { chain ->
            val response = chain.proceed(chain.request())
            response.newBuilder().body(response.body!!.recordingReads(readOn)).build()
        }.build()
        val caller = Thread.currentThread()
        assertThat(GoogleApi(baseUrl, tokens, recording).send(ACCOUNT, "GET", calendarList).code).isEqualTo(200)
        assertThat(readOn).isNotEmpty()
        assertThat(readOn).doesNotContain(caller)
    }

    /** Task 8 review: anything the body read throws reaches the caller; OkHttp would rethrow it on its own thread and the caller would hang. */
    @Test
    fun aBodyReadThatThrowsAnythingFailsTheCaller() = runTest {
        val broken = OkHttpClient.Builder().addInterceptor { chain ->
            val response = chain.proceed(chain.request())
            response.newBuilder().body(response.body!!.onEachRead { throw IllegalStateException("broken body") }).build()
        }.build()
        val failure = withContext(Dispatchers.Default) {
            withTimeoutOrNull(5_000) { failureOf { GoogleApi(baseUrl, tokens, broken).send(ACCOUNT, "GET", calendarList) } }
        }
        assertThat(failure).isInstanceOf(IllegalStateException::class.java)
    }
}

private fun errorBody(status: Int, reason: String) =
    """{"error":{"code":$status,"message":"Google's own words","errors":[{"reason":"$reason"}]}}"""

private fun ResponseBody.recordingReads(threads: MutableList<Thread>): ResponseBody =
    onEachRead { synchronized(threads) { threads += Thread.currentThread() } }

private fun ResponseBody.onFirstRead(action: () -> Unit): ResponseBody {
    var first = true
    return onEachRead { if (first) action().also { first = false } }
}

private fun ResponseBody.onEachRead(action: () -> Unit): ResponseBody {
    val source = object : ForwardingSource(source()) {
        override fun read(sink: Buffer, byteCount: Long): Long {
            action()
            return super.read(sink, byteCount)
        }
    }
    return source.buffer().asResponseBody(contentType(), contentLength())
}
