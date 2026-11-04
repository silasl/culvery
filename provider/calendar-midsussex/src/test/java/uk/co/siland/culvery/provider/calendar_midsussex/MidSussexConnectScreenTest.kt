package uk.co.siland.culvery.provider.calendar_midsussex

import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import com.google.common.truth.Truth.assertThat
import java.io.File
import java.time.LocalDate
import java.time.LocalTime
import org.junit.After
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import uk.co.siland.culvery.capability.calendar.UpdateRequests
import uk.co.siland.culvery.core.plugin.Connection
import uk.co.siland.culvery.core.ui.CulveryTheme

@RunWith(RobolectricTestRunner::class)
class MidSussexConnectScreenTest {
    @get:Rule
    val compose = createComposeRule()

    @get:Rule
    val dir = TemporaryFolder(File("C:/t9").apply { mkdirs() })

    private val council = FakeCouncil()
    private val base = council.start()
    private val wall = FakeWall(LocalDate.of(2026, 11, 4), LocalTime.of(8, 0))
    private var connected: Connection? = null
    private var cancelled = false

    @After
    fun tearDown() = council.shutdown()

    private fun show(existing: Connection? = null) {
        val provider = MidSussexBinsProvider(testCouncil(base), testReads(dir.root), wall, UpdateRequests())
        compose.setContent {
            CulveryTheme(dark = true) {
                provider.ConnectScreen(existing, onConnected = { connected = it }, onCancel = { cancelled = true })
            }
        }
    }

    private fun find() {
        compose.onNodeWithTag("bins_house").performTextInput(HOUSE)
        compose.onNodeWithTag("bins_postcode").performTextInput(POSTCODE)
        compose.onNodeWithText("Find the address").performClick()
    }

    private fun waitFor(text: String) =
        compose.waitUntil(10_000) { compose.onAllNodes(hasText(text, substring = true)).fetchSemanticsNodes().isNotEmpty() }

    @Test
    fun oneAddressGoesStraightToTheNextCollection() {
        council.addresses = "addresses_one.html"
        show()
        find()
        waitFor("Next: ")
        compose.onNodeWithText("Next: Monday 9 November — Food").assertExists()
        compose.onNodeWithText("Add calendar").assertExists()
        compose.onNodeWithText("Cancel").assertExists()
    }

    @Test
    fun twoAddressesAskWhichOne() {
        show()
        find()
        waitFor("2 EXAMPLE ROAD")
        compose.onNodeWithText("1 EXAMPLE ROAD, SAMPLETOWN, WEST SUSSEX").assertExists()
        compose.onNodeWithText("2 EXAMPLE ROAD, SAMPLETOWN, WEST SUSSEX").performClick()
        waitFor("Next: ")
        compose.onNodeWithText("Next: Monday 9 November — Food").assertExists()
    }

    @Test
    fun noAddressSaysSo() {
        council.addresses = "addresses_none.html"
        show()
        find()
        waitFor("No address found. Check the house name and postcode.")
        compose.onNodeWithText("Find the address").assertExists()
    }

    @Test
    fun aFailureOffersToTryAgain() {
        council.status = 503
        show()
        find()
        waitFor("Can’t read the council’s site.")
        council.status = null
        council.addresses = "addresses_one.html"
        compose.onNodeWithText("Try again").performClick()
        waitFor("Next: ")
    }

    @Test
    fun addStoresHousePostcodeAndLine() {
        show()
        find()
        waitFor("2 EXAMPLE ROAD")
        compose.onNodeWithText("2 EXAMPLE ROAD, SAMPLETOWN, WEST SUSSEX").performClick()
        waitFor("Next: ")
        compose.onNodeWithText("Add calendar").performClick()

        val made = checkNotNull(connected)
        assertThat(made.providerId).isEqualTo(MIDSUSSEX_PROVIDER_ID)
        assertThat(made.label).isEqualTo("Mid Sussex bins")
        assertThat(made.id).isNotEmpty()
        assertThat(made.config).containsExactly(
            CONFIG_HOUSE, HOUSE,
            CONFIG_POSTCODE, POSTCODE,
            CONFIG_ADDRESS, "2 EXAMPLE ROAD, SAMPLETOWN, WEST SUSSEX",
        )
    }

    @Test
    fun reconnectingKeepsTheConnectionId() {
        council.addresses = "addresses_one.html"
        show(existing = binsConnection(id = "bins9"))
        compose.onNodeWithText("Find the address").performClick()
        waitFor("Next: ")
        compose.onNodeWithText("Add calendar").performClick()
        assertThat(connected?.id).isEqualTo("bins9")
    }

    @Test
    fun cancelReportsCancel() {
        council.addresses = "addresses_one.html"
        show()
        find()
        waitFor("Next: ")
        compose.onNodeWithText("Cancel").performClick()
        assertThat(cancelled).isTrue()
        assertThat(connected).isNull()
    }
}
