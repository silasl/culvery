package uk.co.siland.culvery.provider.calendar_midsussex

import com.google.common.truth.Truth.assertThat
import java.net.ConnectException
import java.net.InetAddress
import java.net.UnknownHostException
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.runTest
import okhttp3.Dns
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.Interceptor
import okhttp3.OkHttpClient
import okhttp3.mockwebserver.Dispatcher
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import okhttp3.mockwebserver.QueueDispatcher
import okhttp3.mockwebserver.RecordedRequest
import org.junit.After
import org.junit.Assert.assertThrows
import org.junit.Test
import uk.co.siland.culvery.capability.calendar.UnreachableException
import uk.co.siland.culvery.core.plugin.OfflineException

class CouncilClientTest {
    private val council = FakeCouncil()
    private val base = council.start()
    private val subject = testCouncil(base)

    @After
    fun tearDown() = council.shutdown()

    private fun failure(block: suspend () -> Unit): Throwable =
        assertThrows(Throwable::class.java) { runBlocking { block() } }

    @Test
    fun aReadWalksTheThreePagesInOneSession() = runTest {
        val days = subject.read(HOUSE, POSTCODE, LINE)

        assertThat(days).hasSize(3)
        val start = council.takeRequest()
        assertThat(start.method).isEqualTo("GET")
        assertThat(start.getHeader("User-Agent")).isEqualTo("Mozilla/5.0 (Linux; Android) Culvery/test")
        val search = council.takeRequest()
        assertThat(search.method).isEqualTo("POST")
        assertThat(search.requestUrl?.queryParameter("seq")).isEqualTo("2")
        assertThat(search.body.readUtf8()).isEqualTo("address_name_number=1%20Example%20Road&address_postcode=ZZ99%209ZZ")
        assertThat(search.getHeader("Cookie")).isEqualTo("session=1")
        val house = council.takeRequest()
        assertThat(house.requestUrl?.queryParameter("pIndex")).isEqualTo("1")
        assertThat(house.getHeader("Cookie")).isEqualTo("session=1")
    }

    @Test
    fun eachReadStartsAFreshSession() = runTest {
        subject.read(HOUSE, POSTCODE, LINE)
        subject.read(HOUSE, POSTCODE, LINE)

        repeat(3) { council.takeRequest() }
        assertThat(council.takeRequest().getHeader("Cookie")).isNull()
        assertThat(council.takeRequest().getHeader("Cookie")).isEqualTo("session=2")
    }

    @Test
    fun searchListsTheAddresses() = runTest {
        assertThat(subject.search(HOUSE, POSTCODE).addresses.map { it.line }).containsExactly(
            LINE,
            "2 EXAMPLE ROAD, SAMPLETOWN, WEST SUSSEX",
        ).inOrder()
        assertThat(council.requests).isEqualTo(2)
    }

    @Test
    fun anAddressFromASearchIsReadInItsSession() = runTest {
        val found = subject.search(HOUSE, POSTCODE)
        assertThat(subject.readAddress(found, found.addresses.last())).hasSize(3)

        assertThat(council.requests).isEqualTo(3)
        repeat(2) { council.takeRequest() }
        val house = council.takeRequest()
        assertThat(house.requestUrl?.queryParameter("pIndex")).isEqualTo("2")
        assertThat(house.getHeader("Cookie")).isEqualTo("session=1")
    }

    @Test
    fun noDnsMeansOffline() {
        val noDns = object : Dns {
            override fun lookup(hostname: String): List<InetAddress> = throw UnknownHostException(hostname)
        }
        val offline = CouncilClient("http://council.invalid/".toHttpUrl(), lazy { OkHttpClient.Builder().dns(noDns).build() })
        assertThat(failure { offline.search(HOUSE, POSTCODE) }).isInstanceOf(OfflineException::class.java)
    }

    @Test
    fun aBusyOrDownSiteIsUnreachable() {
        council.status = 503
        assertThat(failure { subject.read(HOUSE, POSTCODE, LINE) }).isInstanceOf(UnreachableException::class.java)
        council.status = 429
        assertThat(failure { subject.read(HOUSE, POSTCODE, LINE) }).isInstanceOf(UnreachableException::class.java)
    }

    @Test
    fun aRefusedPageIsTheStepsFailure() {
        council.status = 404
        val e = failure { subject.read(HOUSE, POSTCODE, LINE) }
        assertThat((e as BinsReadFailed).kind).isEqualTo(BinsFailure.NO_START_LINK)
    }

    @Test
    fun aGoneAddressFailsTheRead() {
        val e = failure { subject.read(HOUSE, POSTCODE, "3 EXAMPLE ROAD, SAMPLETOWN, WEST SUSSEX") }
        assertThat((e as BinsReadFailed).kind).isEqualTo(BinsFailure.ADDRESS_GONE)
    }

    @Test
    fun noServerIsUnreachable() {
        council.shutdown()
        assertThat(failure { subject.read(HOUSE, POSTCODE, LINE) }).isInstanceOf(UnreachableException::class.java)
    }

    @Test
    fun aClosedPortIsUnanswered() {
        council.shutdown()
        assertThat(failure { subject.read(HOUSE, POSTCODE, LINE) }).isInstanceOf(CouncilUnanswered::class.java)
    }

    @Test
    fun aDropAfterAnAnswerIsStillAnAnswer() {
        council.dropsAfterStart = true
        val e = failure { subject.read(HOUSE, POSTCODE, LINE) }
        assertThat(e).isInstanceOf(UnreachableException::class.java)
        assertThat(e).isNotInstanceOf(CouncilUnanswered::class.java)
    }

    @Test
    fun aBusyOrDownSiteHasAnswered() {
        council.status = 503
        assertThat(failure { subject.read(HOUSE, POSTCODE, LINE) }).isNotInstanceOf(CouncilUnanswered::class.java)
    }

    @Test
    fun aRefusedConnectionAfterTheStartPageCounts() {
        val refuseLater = Interceptor { chain -> if (chain.request().method == "POST") throw ConnectException("refused") else chain.proceed(chain.request()) }
        val later = CouncilClient(base, lazy { councilHttpClient("test", setOf(refuseLater)) })
        val e = failure { later.read(HOUSE, POSTCODE, LINE) }
        assertThat(e).isInstanceOf(UnreachableException::class.java)
        assertThat(e).isNotInstanceOf(CouncilUnanswered::class.java)
    }

    @Test
    fun aServerThatTookTheConnectionHasAnswered() {
        val twoRoutes = object : Dns {
            override fun lookup(hostname: String): List<InetAddress> =
                listOf(InetAddress.getByName("127.0.0.2"), InetAddress.getByName("127.0.0.1"))
        }
        val url = "http://council.test:${base.port}/".toHttpUrl()
        val routed = CouncilClient(url, lazy { OkHttpClient.Builder().dns(twoRoutes).build() })
        council.dropsEveryConnection = true
        val e = failure { routed.read(HOUSE, POSTCODE, LINE) }
        assertThat(e).isInstanceOf(UnreachableException::class.java)
        assertThat(e).isNotInstanceOf(CouncilUnanswered::class.java)
    }

    private fun MockWebServer.councilBase() = url("/").newBuilder().host("localhost").build()

    private fun startBody(site: MockWebServer) =
        fixture("start.html").replace("https://waste.services.midsussex.gov.uk", site.councilBase().toString().trimEnd('/'))

    private fun councilAt(site: MockWebServer, followSsl: Boolean = false) =
        CouncilClient(site.councilBase(), lazy { councilHttpClient("test", emptySet()).newBuilder().followSslRedirects(followSsl).build() })

    private fun answeringEverything() = MockWebServer().apply {
        dispatcher = object : Dispatcher() {
            override fun dispatch(request: RecordedRequest) = MockResponse().setBody("")
        }
        start()
    }

    /** The search's POST is answered with a redirect to [target]; the read must fail as an unreadable page. */
    private fun assertSearchRedirectStays(code: Int, followSsl: Boolean = false, target: (first: MockWebServer, other: MockWebServer) -> String) {
        val first = answeringEverything()
        val other = answeringEverything()
        try {
            first.dispatcher = QueueDispatcher().apply {
                enqueueResponse(MockResponse().setBody(startBody(first)))
                enqueueResponse(MockResponse().setResponseCode(code).setHeader("Location", target(first, other)))
                setFailFast(MockResponse().setBody(""))
            }
            val e = failure { councilAt(first, followSsl).search(HOUSE, POSTCODE) }

            assertThat((e as BinsReadFailed).kind).isEqualTo(BinsFailure.NO_ADDRESS_LIST)
            assertThat(first.requestCount).isEqualTo(2)
            assertThat(other.requestCount).isEqualTo(0)
        } finally {
            first.shutdown()
            other.shutdown()
        }
    }

    @Test
    fun aSearchRedirectedToAnotherHostIsNotFollowed() {
        listOf(300, 301, 302, 303, 307, 308).forEach { code ->
            assertSearchRedirectStays(code) { first, _ -> first.url("/mop.php").newBuilder().host("127.0.0.1").build().toString() }
        }
    }

    /** The client is told to follow scheme changes, so the council client's own check is the only thing refusing it. */
    @Test
    fun aSearchRedirectedToAnotherSchemeIsNotFollowed() {
        assertSearchRedirectStays(307, followSsl = true) { first, _ -> "https://localhost:${first.port}/mop.php" }
    }

    @Test
    fun aSearchRedirectedToAnotherPortIsNotFollowed() {
        assertSearchRedirectStays(307) { _, other -> other.url("/mop.php").newBuilder().host("localhost").build().toString() }
    }

    /** Regression guard. */
    @Test
    fun aRedirectToTheSameHostIsFollowed() = runTest {
        val site = MockWebServer().apply { start() }
        try {
            site.enqueue(MockResponse().setBody(startBody(site)))
            site.enqueue(MockResponse().setResponseCode(302).setHeader("Location", "/list"))
            site.enqueue(MockResponse().setBody(fixture("addresses_two.html")))

            assertThat(councilAt(site).search(HOUSE, POSTCODE).addresses).hasSize(2)
        } finally {
            site.shutdown()
        }
    }

    @Test
    fun theSearchIsPostedOnlyToTheBasesHost() = runTest {
        subject.search(HOUSE, POSTCODE)

        repeat(2) { assertThat(council.takeRequest().requestUrl?.host).isEqualTo(base.host) }
    }
}
