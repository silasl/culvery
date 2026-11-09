package uk.co.siland.culvery.provider.calendar_midsussex

import com.google.common.truth.Truth.assertThat
import java.time.LocalDate
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrl
import org.junit.Assert.assertThrows
import org.junit.Test

class CouncilPagesTest {
    private val base = "https://waste.services.midsussex.gov.uk/".toHttpUrl()

    private fun page(name: String): String =
        checkNotNull(javaClass.getResource("/council/$name")) { name }.readText()

    private fun failure(block: () -> Unit): BinsFailure =
        assertThrows(BinsReadFailed::class.java) { block() }.kind

    @Test
    fun searchUrlSwapsSeq() {
        val url: HttpUrl = searchUrl(page("start.html"), base)

        assertThat(url.host).isEqualTo("waste.services.midsussex.gov.uk")
        assertThat(url.queryParameter("seq")).isEqualTo("2")
        assertThat(url.queryParameter("Track")).isEqualTo("2031/01/01/ABCDEF123")
        assertThat(url.queryParameter("serviceID")).isEqualTo("A")
    }

    @Test
    fun addressTextNamesNoAddress() {
        val text = Address("1 Example Road, ZZ1 1ZZ", "https://waste.services.midsussex.gov.uk/mop.php?Track=2031/01/01/ABCDEF123".toHttpUrl()).toString()

        assertThat(text).isEqualTo("Address")
    }

    @Test
    fun noStartLinkFails() {
        assertThat(failure { searchUrl(page("collections_missing.html"), base) })
            .isEqualTo(BinsFailure.NO_START_LINK)
    }

    @Test
    fun twoAddressesAreListed() {
        val list = addresses(page("addresses_two.html"), base)

        assertThat(list.map { it.line }).containsExactly(
            "1 EXAMPLE ROAD, SAMPLETOWN, WEST SUSSEX",
            "2 EXAMPLE ROAD, SAMPLETOWN, WEST SUSSEX",
        ).inOrder()
        assertThat(list[1].url.toString()).isEqualTo(
            "https://waste.services.midsussex.gov.uk/mop.php?Track=2031/01/01/ABCDEF123&serviceID=A&seq=3&pIndex=2",
        )
    }

    @Test
    fun noAddressesIsAnEmptyList() {
        assertThat(addresses(page("addresses_none.html"), base)).isEmpty()
        assertThat(failure { addresses(page("collections.html"), base) })
            .isEqualTo(BinsFailure.NO_ADDRESS_LIST)
    }

    @Test
    fun theStoredAddressIsChosenByItsLine() {
        val list = addresses(page("addresses_two.html"), base)

        assertThat(pick(list, "2 EXAMPLE ROAD, SAMPLETOWN, WEST SUSSEX")).isEqualTo(list[1])
    }

    @Test
    fun aGoneAddressIsAFailure() {
        val list = addresses(page("addresses_two.html"), base)

        assertThat(failure { pick(list, "3 EXAMPLE ROAD, SAMPLETOWN, WEST SUSSEX") })
            .isEqualTo(BinsFailure.ADDRESS_GONE)
    }

    @Test
    fun collectionsGroupByDay() {
        val days = collections(page("collections.html"))

        assertThat(days.map { it.date }).containsExactly(
            LocalDate.of(2031, 1, 2),
            LocalDate.of(2031, 1, 9),
            LocalDate.of(2031, 1, 16),
        ).inOrder()
        assertThat(days[0].kinds).containsExactly("food")
        assertThat(days[1].kinds).containsExactly("food", "garden", "recycling", "waste").inOrder()
    }

    @Test
    fun anUnknownNameKeepsTheCouncilsWords() {
        val days = collections(page("collections.html"))

        assertThat(days.last().kinds).containsExactly("Domestic Clinical Waste Service")
    }

    @Test
    fun emptyMissingBadDateAndHintEachFail() {
        assertThat(failure { collections(page("collections_empty.html")) })
            .isEqualTo(BinsFailure.NO_COLLECTIONS)
        assertThat(failure { collections(page("collections_missing.html")) })
            .isEqualTo(BinsFailure.NO_COLLECTIONS)
        assertThat(failure { collections(page("bad_date.html")) }).isEqualTo(BinsFailure.BAD_DATE)
        assertThat(failure { collections(page("waste_hint.html")) })
            .isEqualTo(BinsFailure.NO_SERVICES)
    }

    @Test
    fun aRowWithNoNameIsSkipped() {
        val html = """
            <section id="scheduled-collections">
            <ul class="displayinlineblock"><li><img/></li><li><p>02/01/2031</p></li><li><p> </p></li></ul>
            <ul class="displayinlineblock"><li><img/></li><li><p>09/01/2031</p></li><li><p>Domestic Food Waste Service</p></li></ul>
            </section>
        """.trimIndent()

        assertThat(collections(html)).containsExactly(BinDay(LocalDate.of(2031, 1, 9), listOf("food")))
    }

    @Test
    fun withoutTheSectionTheHeadingsParentHoldsTheRows() {
        val html = """
            <div>
            <h2 id="upcoming-scheduled-collections">Your upcoming scheduled collections</h2>
            <ul class="displayinlineblock"><li><img/></li><li><p>09/01/2031</p></li><li><p>Domestic Garden Waste Service</p></li></ul>
            </div>
        """.trimIndent()

        assertThat(collections(html)).containsExactly(BinDay(LocalDate.of(2031, 1, 9), listOf("garden")))
    }

    private fun startPage(href: String) = """<a href="$href">View my collections</a>"""

    private fun list(vararg hrefs: String) =
        """<div id="property_list">${hrefs.joinToString("") { """<a href="$it">A house</a>""" }}</div>"""

    @Test
    fun councilBaseUrlIsHttps() {
        assertThat(COUNCIL_BASE_URL.toHttpUrl().scheme).isEqualTo("https")
    }

    @Test
    fun aStartLinkToAnotherSchemeOrHostFails() {
        listOf("http://waste.services.midsussex.gov.uk/mop.php?Track=1", "https://example.org/mop.php?Track=1").forEach {
            assertThat(failure { searchUrl(startPage(it), base) }).isEqualTo(BinsFailure.NO_START_LINK)
        }
    }

    @Test
    fun aStartLinkOnTheBaseHostIsKept() {
        assertThat(searchUrl(startPage("/mop.php?Track=1"), base).host).isEqualTo("waste.services.midsussex.gov.uk")
    }

    @Test
    fun anAddressLinkOffTheBaseIsDropped() {
        val kept = addresses(list("mop.php?pIndex=1", "https://example.org/mop.php?pIndex=2", "http://waste.services.midsussex.gov.uk/mop.php?pIndex=3"), base)

        assertThat(kept.map { it.url.queryParameter("pIndex") }).containsExactly("1")
    }

    @Test
    fun addressLinksAllOffTheBaseFail() {
        val html = list("https://example.org/mop.php?pIndex=1", "http://waste.services.midsussex.gov.uk/mop.php?pIndex=2")

        assertThat(failure { addresses(html, base) }).isEqualTo(BinsFailure.NO_ADDRESS_LIST)
    }
}
