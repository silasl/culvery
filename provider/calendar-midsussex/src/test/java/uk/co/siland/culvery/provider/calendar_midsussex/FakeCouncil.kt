package uk.co.siland.culvery.provider.calendar_midsussex

import java.io.File
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import java.util.concurrent.TimeUnit
import okhttp3.HttpUrl
import okhttp3.mockwebserver.Dispatcher
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import okhttp3.mockwebserver.RecordedRequest
import uk.co.siland.culvery.core.plugin.Connection
import uk.co.siland.culvery.core.plugin.WallClock
import uk.co.siland.culvery.core.plugin.settingsDataStore

internal val LONDON: ZoneId = ZoneId.of("Europe/London")
internal const val HOUSE = "1 Example Road"
internal const val POSTCODE = "ZZ99 9ZZ"
internal const val LINE = "1 EXAMPLE ROAD, SAMPLETOWN, WEST SUSSEX"

internal fun binsConnection(id: String = "bins1", house: String = HOUSE) = Connection(
    id,
    MIDSUSSEX_PROVIDER_ID,
    "Mid Sussex bins",
    mapOf(CONFIG_HOUSE to house, CONFIG_POSTCODE to POSTCODE, CONFIG_ADDRESS to LINE),
)

internal fun fixture(name: String): String =
    checkNotNull(FakeCouncil::class.java.getResource("/council/$name")) { name }.readText()

/** The council's three pages from Task 5's fixtures, with the start page's links pointed at this server. */
internal class FakeCouncil {
    private val server = MockWebServer()

    /** The third page's fixture. */
    @Volatile var collections = "collections.html"

    /** The second page's fixture. */
    @Volatile var addresses = "addresses_two.html"

    /** When set, every page takes two seconds to start answering. */
    @Volatile var slow = false

    /** When set, every page answers this status. */
    @Volatile var status: Int? = null

    val requests: Int get() = server.requestCount

    private var sessions = 0

    fun start(): HttpUrl {
        server.dispatcher = object : Dispatcher() {
            override fun dispatch(request: RecordedRequest): MockResponse {
                val url = checkNotNull(request.requestUrl)
                val failing = status
                val answer = when {
                    failing != null -> MockResponse().setResponseCode(failing)
                    request.method == "POST" -> MockResponse().setBody(fixture(addresses))
                    url.queryParameter("seq") == "3" -> MockResponse().setBody(fixture(collections))
                    else -> MockResponse()
                        .setHeader("Set-Cookie", "session=${++sessions}; Path=/")
                        .setBody(fixture("start.html").replace("https://waste.services.midsussex.gov.uk", base.toString().trimEnd('/')))
                }
                return if (slow) answer.setHeadersDelay(2, TimeUnit.SECONDS) else answer
            }
        }
        server.start()
        return base
    }

    private val base: HttpUrl get() = server.url("/")

    fun takeRequest(): RecordedRequest = checkNotNull(server.takeRequest(5, TimeUnit.SECONDS))

    private var stopped = false

    fun shutdown() {
        if (stopped) return
        stopped = true
        server.shutdown()
    }
}

internal class FakeWall(day: LocalDate, time: LocalTime) : WallClock {
    private var millis = 0L

    init {
        at(day, time)
    }

    override fun nowMillis(): Long = millis

    fun at(day: LocalDate, time: LocalTime) {
        millis = day.atTime(time).atZone(LONDON).toInstant().toEpochMilli()
    }

    fun plusMinutes(minutes: Long) {
        millis += minutes * 60_000L
    }
}

internal fun testReads(dir: File, name: String = "bins_reads.preferences_pb") =
    BinsReads(settingsDataStore { File(dir, name) })

internal fun testCouncil(base: HttpUrl) = CouncilClient(base, lazy { councilHttpClient("test", emptySet()) })
