package uk.co.siland.culvery.provider.weather_openmeteo

import androidx.test.ext.junit.runners.AndroidJUnit4
import com.google.common.truth.Truth.assertThat
import com.google.common.truth.Truth.assertWithMessage
import java.time.Duration
import java.util.concurrent.TimeUnit
import kotlin.coroutines.cancellation.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.runInterruptible
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import kotlinx.coroutines.withTimeoutOrNull
import okhttp3.OkHttpClient
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import okhttp3.mockwebserver.SocketPolicy
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.shadows.ShadowLog
import uk.co.siland.culvery.core.setup.LocationSearchException
import uk.co.siland.culvery.core.setup.PlaceMatch

/** Open-Meteo's answer for "Canterbury", trimmed; the third town has no time zone. */
private const val CANTERBURY = """{"results":[
  {"id":2653877,"name":"Canterbury","latitude":51.27904,"longitude":1.07992,"elevation":19.0,"feature_code":"PPLA2",
   "country_code":"GB","admin1":"England","admin2":"Kent","timezone":"Europe/London","country":"United Kingdom"},
  {"id":2172797,"name":"Canterbury","latitude":-33.91667,"longitude":151.11667,"country_code":"AU",
   "admin1":"New South Wales","timezone":"Australia/Sydney","country":"Australia"},
  {"id":9999999,"name":"Canterbury Siding","latitude":10.5,"longitude":20.5,"country":"Nowhere"}
],"generationtime_ms":0.61}"""

// Robolectric for android.util.Log; the server is a real MockWebServer on localhost.
@RunWith(AndroidJUnit4::class)
class OpenMeteoLocationSearchTest {
    private lateinit var server: MockWebServer
    private lateinit var search: OpenMeteoLocationSearch
    private val client = OkHttpClient.Builder().retryOnConnectionFailure(false).build()

    @Before
    fun setUp() {
        ShadowLog.clear()
        server = MockWebServer()
        server.start()
        search = OpenMeteoLocationSearch(server.url("/v1/search"), client)
    }

    @After
    fun tearDown() = server.shutdown()

    private fun answer(body: String, code: Int = 200) = server.enqueue(MockResponse().setResponseCode(code).setBody(body))

    private suspend fun failure(query: String = "Canterbury"): Throwable? =
        try {
            search.search(query)
            null
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            e
        }

    @Test
    fun itAsksForFiveEnglishResultsAndReadsEachTownWithAZone() = runTest {
        answer(CANTERBURY)
        val found = search.search("Canterbury")
        val url = server.takeRequest().requestUrl!!
        assertThat(url.encodedPath).isEqualTo("/v1/search")
        assertThat(listOf("name", "count", "language", "format").map { url.queryParameter(it) })
            .containsExactly("Canterbury", "5", "en", "json").inOrder()
        assertThat(found).containsExactly(
            PlaceMatch("Canterbury", "England", "United Kingdom", 51.27904, 1.07992, "Europe/London"),
            PlaceMatch("Canterbury", "New South Wales", "Australia", -33.91667, 151.11667, "Australia/Sydney"),
        ).inOrder()
        assertThat(found.first().label).isEqualTo("Canterbury, England, United Kingdom")
    }

    @Test
    fun noMatchIsAnEmptyList() = runTest {
        answer("""{"generationtime_ms":0.3}""")
        assertThat(search.search("Xqzt")).isEmpty()
    }

    @Test
    fun anErrorAnswerIsALocationSearchException() = runTest {
        answer("""{"error":true,"reason":"Parameter count must be between 1 and 100."}""", code = 400)
        assertThat(failure()).isInstanceOf(LocationSearchException::class.java)
    }

    @Test
    fun aDroppedConnectionIsALocationSearchException() = runTest {
        server.enqueue(MockResponse().setSocketPolicy(SocketPolicy.DISCONNECT_AFTER_REQUEST))
        assertThat(failure()).isInstanceOf(LocationSearchException::class.java)
    }

    @Test
    fun anUnreadableAnswerIsALocationSearchException() = runTest {
        answer("<html>Gateway</html>")
        assertThat(failure()).isInstanceOf(LocationSearchException::class.java)
    }

    @Test
    fun cancellingTheSearchCancelsTheCall() = runTest {
        // Its own client with a long read timeout, so only cancelling the call can end it inside the second.
        val patient = OkHttpClient.Builder().readTimeout(Duration.ofSeconds(30)).build()
        val slow = OpenMeteoLocationSearch(server.url("/v1/search"), patient)
        // One byte every 3 s: the headers arrive, the body stalls.
        server.enqueue(MockResponse().setBody(CANTERBURY).throttleBody(1, 3, TimeUnit.SECONDS))
        val returned = withContext(Dispatchers.Default) {
            val call = launch { slow.search("Canterbury") }
            checkNotNull(runInterruptible(Dispatchers.IO) { server.takeRequest(5, TimeUnit.SECONDS) }) { "the search never reached the server" }
            call.cancel()
            withTimeoutOrNull(1_000) { call.join() } != null
        }
        assertThat(returned).isTrue()
        withContext(Dispatchers.Default) { withTimeout(1_000) { while (patient.dispatcher.runningCallsCount() > 0) delay(10) } }
    }

    @Test
    fun nothingLoggedHoldsTheQueryOrAPlace() = runTest {
        answer("{}", code = 500)
        failure()
        server.enqueue(MockResponse().setSocketPolicy(SocketPolicy.DISCONNECT_AFTER_REQUEST))
        failure()
        answer("""{"results":[{"name":"Canterbury","latitude":"fifty-one"}]}""")
        failure()
        assertNoSecretsLogged(TAG, listOf("Canterbury", "canterbury", "51.", "name=", "fifty-one"), minLines = 3)
    }
}

/**
 * Nothing logged under [tag], with its whole chain of causes, holds any of [secrets]; at least [minLines] were logged,
 * so a check that saw no log can't pass by default. (`:core:setup` and the calendar have the same helper in their own
 * test sources; test sources aren't shared between modules.)
 */
private fun assertNoSecretsLogged(tag: String, secrets: List<String>, minLines: Int = 1) {
    val logs = ShadowLog.getLogs().filter { it.tag == tag }
    assertWithMessage("lines logged under $tag").that(logs.size).isAtLeast(minLines)
    logs.forEach { log ->
        val text = "${log.msg} ${generateSequence(log.throwable) { it.cause }.joinToString(" ")}"
        secrets.forEach { assertWithMessage(text).that(text).doesNotContain(it) }
    }
}
