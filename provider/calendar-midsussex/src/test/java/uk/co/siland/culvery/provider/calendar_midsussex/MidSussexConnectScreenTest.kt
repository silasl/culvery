package uk.co.siland.culvery.provider.calendar_midsussex

import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertIsFocused
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.isHeading
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performImeAction
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.performTextReplacement
import com.google.common.truth.Truth.assertThat
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
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
    val dir = TemporaryFolder()

    private val council = FakeCouncil()
    private val base = council.start()
    private val wall = FakeWall(LocalDate.of(2026, 11, 4), LocalTime.of(8, 0))
    private val connections = mutableListOf<Connection>()
    private val connected: Connection? get() = connections.lastOrNull()

    @After
    fun tearDown() = council.shutdown()

    private fun show(existing: Connection? = null, zone: ZoneId = LONDON) {
        val provider = MidSussexBinsProvider(testCouncil(base), testReads(dir.root), wall, UpdateRequests()) { zone }
        compose.setContent {
            CulveryTheme(dark = true) {
                provider.ConnectScreen(existing, onConnected = { connections += it }, onCancel = {})
            }
        }
    }

    private fun find() {
        compose.onNodeWithTag("bins_house").performTextInput(HOUSE)
        compose.onNodeWithTag("bins_postcode").performTextInput(POSTCODE)
        compose.onNodeWithText("Find the address").performClick()
    }

    /** Two taps before the screen can redraw, as a quick double tap can land. */
    private fun tapTwiceInOneFrame(text: String) {
        val tap = checkNotNull(compose.onNodeWithText(text).fetchSemanticsNode().config[SemanticsActions.OnClick].action)
        compose.runOnIdle {
            tap()
            tap()
        }
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
        assertThat(council.requests).isEqualTo(3)
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
        assertThat(council.requests).isEqualTo(3)
    }

    @Test
    fun tryAgainAfterAFailedPickWalksAgain() {
        show()
        find()
        waitFor("2 EXAMPLE ROAD")
        council.collections = "collections_missing.html"
        compose.onNodeWithText("2 EXAMPLE ROAD, SAMPLETOWN, WEST SUSSEX").performClick()
        waitFor(COUNCIL_UNREADABLE)
        council.collections = "collections.html"
        val before = council.requests

        compose.onNodeWithText("Try again").performClick()
        waitFor("Next: ")
        assertThat(council.requests - before).isEqualTo(3)
    }

    @Test
    fun theHintShowsOnlyWhileSearching() {
        show()
        compose.onNodeWithText(SEARCH_HINT).assertExists()
        find()
        waitFor("2 EXAMPLE ROAD")
        compose.onNodeWithText(SEARCH_HINT).assertDoesNotExist()
        compose.onNodeWithText("Which address?").assert(isHeading())
        compose.onNodeWithText("2 EXAMPLE ROAD, SAMPLETOWN, WEST SUSSEX").performClick()
        waitFor("Next: ")
        compose.onNodeWithText(SEARCH_HINT).assertDoesNotExist()
        compose.onNodeWithText("Which address?").assertDoesNotExist()
    }

    @Test
    fun theHousesNextMovesToThePostcode() {
        show()
        compose.onNodeWithTag("bins_house").performTextInput(HOUSE)
        compose.onNodeWithTag("bins_house").performImeAction()
        compose.onNodeWithTag("bins_postcode").assertIsFocused()
    }

    @Test
    fun thePostcodesSearchFinds() {
        show()
        compose.onNodeWithTag("bins_house").performTextInput(HOUSE)
        compose.onNodeWithTag("bins_postcode").performTextInput(POSTCODE)
        compose.onNodeWithTag("bins_postcode").performImeAction()
        waitFor("2 EXAMPLE ROAD")
    }

    @Test
    fun searchWithoutAHouseFindsNothing() {
        show()
        compose.onNodeWithTag("bins_postcode").performTextInput(POSTCODE)
        compose.onNodeWithTag("bins_postcode").performImeAction()
        compose.waitForIdle()
        assertThat(council.takeRequestOrNull()).isNull()
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
    fun theReadyStepLeavesCancelToTheConnectingCard() {
        council.addresses = "addresses_one.html"
        show()
        find()
        waitFor("Next: ")
        compose.onNodeWithText("Cancel").assertDoesNotExist()
    }

    @Test
    fun aSecondTapOnFindStartsNoSecondSearch() {
        council.addresses = "addresses_one.html"
        council.stalled = true
        show()
        compose.onNodeWithTag("bins_house").performTextInput(HOUSE)
        compose.onNodeWithTag("bins_postcode").performTextInput(POSTCODE)
        tapTwiceInOneFrame("Find the address")
        council.takeRequest()
        compose.waitForIdle()
        assertThat(council.takeRequestOrNull()).isNull()
    }

    @Test
    fun aSecondTapOnAddConnectsOnce() {
        council.addresses = "addresses_one.html"
        show()
        find()
        waitFor("Next: ")
        tapTwiceInOneFrame("Add calendar")
        assertThat(connections).hasSize(1)
    }

    @Test
    fun pickingUsesTheSearchedHouseAndPostcode() {
        show()
        find()
        waitFor("2 EXAMPLE ROAD")
        compose.onNodeWithTag("bins_house").performTextReplacement("9 Other Road")
        compose.onNodeWithTag("bins_postcode").performTextReplacement("ZZ1 1ZZ")
        compose.onNodeWithText("2 EXAMPLE ROAD, SAMPLETOWN, WEST SUSSEX").performClick()
        waitFor("Next: ")
        compose.onNodeWithText("Add calendar").performClick()

        assertThat(connected?.config).containsExactly(
            CONFIG_HOUSE, HOUSE,
            CONFIG_POSTCODE, POSTCODE,
            CONFIG_ADDRESS, "2 EXAMPLE ROAD, SAMPLETOWN, WEST SUSSEX",
        )
    }

    @Test
    fun aChangedPageOffersToTryAgain() {
        council.start = "collections_missing.html"
        show()
        find()
        waitFor("Can’t read the council’s site.")
        compose.onNodeWithText("Try again").assertExists()
    }

    /** 23:30 in London on the 9th is already the 10th in Tokyo, so the household's next collection is the 16th. */
    @Test
    fun theNextCollectionCountsFromTheHouseholdsToday() {
        council.addresses = "addresses_one.html"
        wall.at(LocalDate.of(2026, 11, 9), LocalTime.of(23, 30))
        show(zone = ZoneId.of("Asia/Tokyo"))
        find()
        waitFor("Next: ")
        compose.onNodeWithText("Next: Monday 16 November — Food, Garden, Recycling, Waste").assertExists()
    }
}
