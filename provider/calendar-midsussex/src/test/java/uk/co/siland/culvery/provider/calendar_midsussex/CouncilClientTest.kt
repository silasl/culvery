package uk.co.siland.culvery.provider.calendar_midsussex

import com.google.common.truth.Truth.assertThat
import java.net.InetAddress
import java.net.UnknownHostException
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.runTest
import okhttp3.Dns
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import org.junit.After
import org.junit.Assert.assertThrows
import org.junit.Test
import uk.co.siland.culvery.capability.calendar.UnreachableException
import uk.co.siland.culvery.core.plugin.OfflineException

class CouncilClientTest {
    private val council = FakeCouncil()
    private val subject = testCouncil(council.start())

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
}
