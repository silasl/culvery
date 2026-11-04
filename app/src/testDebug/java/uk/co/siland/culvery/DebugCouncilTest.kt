package uk.co.siland.culvery

import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.google.common.truth.Truth.assertThat
import java.time.LocalDate
import java.time.ZoneId
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.runTest
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import okhttp3.Protocol
import okhttp3.Request
import okhttp3.Response
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.Assert.assertThrows
import org.junit.Test
import org.junit.runner.RunWith
import uk.co.siland.culvery.capability.calendar.UnreachableException
import uk.co.siland.culvery.core.plugin.WallClock
import uk.co.siland.culvery.provider.calendar_midsussex.BinsFailure
import uk.co.siland.culvery.provider.calendar_midsussex.BinsReadFailed
import uk.co.siland.culvery.provider.calendar_midsussex.COUNCIL_BASE_URL
import uk.co.siland.culvery.provider.calendar_midsussex.CouncilClient

private const val LINE_ONE = "1 EXAMPLE ROAD, SAMPLETOWN, WEST SUSSEX"
private const val LINE_TWO = "2 EXAMPLE ROAD, SAMPLETOWN, WEST SUSSEX"

@RunWith(AndroidJUnit4::class)
class DebugCouncilTest {
    private val zone = ZoneId.of("Europe/London")
    private val today = LocalDate.of(2026, 11, 4)
    private val clock = WallClock { today.atTime(9, 0).atZone(zone).toInstant().toEpochMilli() }
    private val assets = ApplicationProvider.getApplicationContext<android.content.Context>().assets
    private val council = DebugCouncil(
        { name -> assets.open("council/$name").use { it.readBytes().decodeToString() } },
        clock,
        zone,
    )
    private val wentOn = mutableListOf<Request>()

    // Stands in for the network: anything the sample council lets through lands here as a busy site.
    private val client = OkHttpClient.Builder()
        .addInterceptor(council)
        .addInterceptor { chain ->
            wentOn += chain.request()
            Response.Builder().request(chain.request()).protocol(Protocol.HTTP_1_1).code(503).message("Busy")
                .body("".toResponseBody()).build()
        }
        .build()
    private val subject = CouncilClient(COUNCIL_BASE_URL.toHttpUrl(), lazy { client })

    @Test
    fun servesThePagesForTheSamplePostcodeOnly() = runTest {
        assertThat(subject.search("1 Example Road", "ZZ99 9ZZ").map { it.line }).containsExactly(LINE_ONE, LINE_TWO).inOrder()
        assertThat(subject.search("1 Example Road", "zz999zz")).hasSize(2)

        val good = subject.read("2 Example Road", "ZZ99 9ZZ", LINE_TWO)
        assertThat(good.map { it.date }).isInStrictOrder()
        assertThat(good.first().date).isEqualTo(today.plusDays(1))
        assertThat(good.all { it.date > today }).isTrue()
        assertThat(good.map { it.kinds }).contains(listOf("food", "garden", "recycling", "waste"))
        assertThat(wentOn).isEmpty()

        assertThrows(UnreachableException::class.java) { runBlocking { subject.search("1 High Street", "AB1 2CD") } }
        assertThat(wentOn.map { it.method }).containsExactly("POST")
    }

    @Test
    fun aRealTrackGoesOnToTheCouncil() {
        val house = "${COUNCIL_BASE_URL}mop.php?Track=2026/11/04/ABCDEF123&serviceID=A&seq=3&pIndex=1".toHttpUrl()
        client.newCall(Request.Builder().url(house).build()).execute().close()
        assertThat(wentOn.map { it.url }).containsExactly(house)
    }

    @Test
    fun theBroadcastPageSwitchesTheCollections() = runTest {
        council.page = CouncilPage.MISSING
        val failure = assertThrows(BinsReadFailed::class.java) { runBlocking { subject.read("1", "ZZ99 9ZZ", LINE_ONE) } }
        assertThat(failure.kind).isEqualTo(BinsFailure.NO_COLLECTIONS)

        council.page = CouncilPage.UNKNOWN
        val kinds = subject.read("1", "ZZ99 9ZZ", LINE_ONE).flatMap { it.kinds }
        assertThat(kinds).contains("Domestic Glass Collection Service")
        assertThat(kinds.filter { it !in setOf("food", "garden", "recycling", "waste") }).hasSize(1)

        council.page = CouncilPage.GOOD
        assertThat(subject.read("1", "ZZ99 9ZZ", LINE_ONE).flatMap { it.kinds }).doesNotContain("Domestic Glass Collection Service")
    }

    @Test
    fun theDatesFollowTheClock() = runTest {
        val later = DebugCouncil({ name -> assets.open("council/$name").use { it.readBytes().decodeToString() } }, { clock.nowMillis() + 30 * 86_400_000L }, zone)
        val second = CouncilClient(COUNCIL_BASE_URL.toHttpUrl(), lazy { OkHttpClient.Builder().addInterceptor(later).build() })
        assertThat(second.read("1", "ZZ99 9ZZ", LINE_ONE).first().date).isEqualTo(today.plusDays(31))
    }

    @Test
    fun aMistypedPageNameIsNoPage() {
        assertThat(CouncilPage.of("missing")).isEqualTo(CouncilPage.MISSING)
        assertThat(CouncilPage.of("mising")).isNull()
        assertThat(CouncilPage.of(null)).isNull()
    }
}
