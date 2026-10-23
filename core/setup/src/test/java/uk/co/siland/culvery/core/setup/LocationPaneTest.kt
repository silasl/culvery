package uk.co.siland.culvery.core.setup

import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertIsFocused
import androidx.compose.ui.test.assertIsNotFocused
import androidx.compose.ui.test.assertIsNotSelected
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.assertTextContains
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.height
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.google.common.truth.Truth.assertThat
import java.io.IOException
import java.util.concurrent.CopyOnWriteArrayList
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.awaitCancellation
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.GraphicsMode
import uk.co.siland.culvery.core.household.HomeLocation
import uk.co.siland.culvery.core.ui.CulveryTheme
import uk.co.siland.culvery.core.ui.DarkColors
import uk.co.siland.culvery.core.ui.test.TouchModeRule
import uk.co.siland.culvery.core.ui.test.assertClickablesHaveRoleAndLabel
import uk.co.siland.culvery.core.ui.test.pixelAt

@RunWith(AndroidJUnit4::class)
class LocationPaneTest {
    @get:Rule(order = 0) val touchMode = TouchModeRule()
    @get:Rule(order = 1) val compose = createComposeRule()
    private val brighton = PlaceMatch("Brighton", "England", "United Kingdom", 50.82838, -0.13947, "Europe/London")
    private val saved = CopyOnWriteArrayList<PlaceMatch>()
    private var saveFails = false

    private class FakeSearch : LocationSearch {
        val queries: MutableList<String> = CopyOnWriteArrayList()
        var cancelled = 0
        var answer: suspend (String) -> List<PlaceMatch> = { emptyList() }

        override suspend fun search(query: String): List<PlaceMatch> {
            queries += query
            try {
                return answer(query)
            } catch (e: CancellationException) {
                cancelled++
                throw e
            }
        }
    }

    private val search = FakeSearch()

    private fun show(current: HomeLocation? = null, showCurrent: Boolean = false) {
        compose.mainClock.autoAdvance = false
        compose.setContent {
            CulveryTheme(dark = true) {
                LocationPane(search, current, showCurrent) { place ->
                    if (saveFails) throw IOException("disk full saving ${place.label}")
                    saved += place
                    true
                }
            }
        }
    }

    private fun type(text: String) {
        compose.onNodeWithTag("location_query").performTextInput(text)
    }

    private fun advance(millis: Long) = compose.mainClock.advanceTimeBy(millis)

    @Test
    fun itSearchesOnlyAfterTwoLettersAndAPause() {
        show()
        type("C")
        advance(1_000)
        assertThat(search.queries).isEmpty()
        type("a")
        advance(SEARCH_PAUSE_MS - 50)
        assertThat(search.queries).isEmpty()
        advance(100)
        assertThat(search.queries).containsExactly("Ca")
    }

    @Test
    fun aNewQueryCancelsTheSearchBeforeIt() {
        search.answer = { q -> if (q == "Ca") awaitCancellation() else listOf(brighton) }
        show()
        type("Ca")
        advance(500)
        type("n")
        advance(500)
        assertThat(search.queries).containsExactly("Ca", "Can").inOrder()
        assertThat(search.cancelled).isEqualTo(1)
        compose.onNodeWithTag("place_0").assertTextContains("Brighton, England, United Kingdom")
    }

    @Test
    fun choosingATownSavesIt() {
        search.answer = { listOf(brighton) }
        show()
        type("Can")
        advance(500)
        compose.onNodeWithTag("place_0").assertIsNotSelected().performClick()
        advance(100)
        assertThat(saved).containsExactly(brighton)
    }

    /** Picking a town is the one choice: the field shows its name and the suggestions close, without a new search. */
    @Test
    fun choosingATownFillsTheFieldWithItsNameAndClosesTheResults() {
        search.answer = { listOf(brighton) }
        show()
        type("Can")
        advance(500)
        compose.onNodeWithTag("place_0").performClick()
        advance(1_000)
        assertThat(saved).containsExactly(brighton)
        compose.onNodeWithTag("place_0").assertDoesNotExist()
        compose.onNodeWithTag("location_query").assertTextContains("Brighton, England, United Kingdom")
        assertThat(search.queries).containsExactly("Can")
    }

    /** 4e: on the SM-T510 the keyboard stayed up over Settings after a town was chosen. */
    @Test
    fun choosingATownClosesTheKeyboard() {
        search.answer = { listOf(brighton) }
        show()
        type("Can")
        advance(500)
        compose.onNodeWithTag("location_query").assertIsFocused()
        compose.onNodeWithTag("place_0").performClick()
        advance(1_000)
        compose.onNodeWithTag("location_query").assertIsNotFocused()
    }

    @Test
    fun editingTheNameAfterChoosingSearchesAgain() {
        search.answer = { listOf(brighton) }
        show()
        type("Can")
        advance(500)
        compose.onNodeWithTag("place_0").performClick()
        advance(1_000)
        type("x")
        advance(500)
        assertThat(search.queries.last()).contains("x")
        compose.onNodeWithTag("place_0").assertExists()
    }

    @Test
    fun theSavedHomeIsTicked() {
        search.answer = { listOf(brighton) }
        show(current = brighton.toHome())
        type("Can")
        advance(500)
        compose.onNodeWithTag("place_0").assertIsSelected()
    }

    @Test
    fun aFailedSearchSaysToCheckTheWiFi() {
        search.answer = { throw LocationSearchException("offline") }
        show()
        type("Can")
        advance(500)
        compose.onNodeWithText("Couldn’t search for towns — check the tablet’s Wi-Fi, then try again.").assertExists()
        // Only the fixed words, never the exception's own.
        compose.onNodeWithText("offline", substring = true).assertDoesNotExist()
    }

    @Test
    fun noMatchNamesTheQuery() {
        show()
        type("Xqz")
        advance(500)
        compose.onNodeWithText("No towns match “Xqz” — check the spelling or try a nearby town.").assertExists()
    }

    @Test
    fun settingsShowsTheSavedHomeAboveTheField() {
        show(current = brighton.toHome(), showCurrent = true)
        advance(100)
        compose.onNodeWithTag("location_current").assertTextContains("Brighton, England, United Kingdom")
    }

    @Test
    fun aFailedSaveSaysSoAndKeepsTheResults() {
        search.answer = { listOf(brighton) }
        saveFails = true
        show()
        type("Can")
        advance(500)
        compose.onNodeWithTag("place_0").performClick()
        advance(100)
        compose.onNodeWithText("Couldn’t save the home location — try again.").assertExists()
        compose.onNodeWithTag("place_0").assertTextContains("Brighton, England, United Kingdom")
        assertNoSecretsLogged("LocationPane", listOf("Brighton", "disk full"))
        // Trying again clears it.
        saveFails = false
        compose.onNodeWithTag("place_0").performClick()
        advance(100)
        assertThat(saved).containsExactly(brighton)
        compose.onNodeWithText("Couldn’t save the home location — try again.").assertDoesNotExist()
    }

    @Test
    fun aTownSaysATapMakesItHome() {
        search.answer = { listOf(brighton) }
        show()
        type("Can")
        advance(500)
        assertThat(compose.onNodeWithTag("place_0").fetchSemanticsNode().config[SemanticsActions.OnClick].label).isEqualTo("use as home")
    }

    private val polite = SemanticsMatcher.expectValue(SemanticsProperties.LiveRegion, LiveRegionMode.Polite)

    @Test
    fun theStatusLineIsThereBeforeAnySearchAndSpeaksTheCountOverAList() {
        search.answer = { listOf(brighton) }
        show()
        compose.onNodeWithTag("location_status").assertTextEquals("").assert(polite)
        type("Can")
        advance(500)
        compose.onNodeWithTag("location_status")
            .assert(SemanticsMatcher.expectValue(SemanticsProperties.ContentDescription, listOf("1 town found")))
            .assert(polite)
        compose.onNodeWithTag("location_query").assert(SemanticsMatcher.expectValue(SemanticsProperties.ContentDescription, listOf("Town or city")))
    }

    /** Compose leaves a zero-size node out of TalkBack's tree, and its count and a later failed save would go unspoken. */
    @Test
    fun theStatusNodeKeepsASizeInTheFoundStateSoTalkBackKeepsIt() {
        search.answer = { listOf(brighton) }
        show()
        type("Can")
        advance(500)
        val status = compose.onNodeWithTag("location_status").assert(polite)
        status.assert(SemanticsMatcher.expectValue(SemanticsProperties.ContentDescription, listOf("1 town found")))
        assertThat(status.getUnclippedBoundsInRoot().height.value).isAtLeast(1f)
    }

    @Test
    fun aSearchSaysSoWhileItRuns() {
        search.answer = { awaitCancellation() }
        show()
        type("Can")
        advance(100)
        compose.onNodeWithTag("location_status").assertTextEquals("Searching…").assert(polite)
        advance(500)
        compose.onNodeWithTag("location_status").assertTextEquals("Searching…")
    }

    @Test
    fun tryAgainRunsTheSameSearchAgain() {
        var fail = true
        search.answer = { if (fail) throw LocationSearchException("offline") else listOf(brighton) }
        show()
        type("Can")
        advance(500)
        compose.onNodeWithTag("location_status").assertTextEquals("Couldn’t search for towns — check the tablet’s Wi-Fi, then try again.")
        fail = false
        compose.onNodeWithTag("location_retry").performClick()
        advance(50)
        compose.onNodeWithTag("location_status").assertTextEquals("Searching…")
        advance(500)
        assertThat(search.queries).containsExactly("Can", "Can").inOrder()
        compose.onNodeWithTag("place_0").assertExists()
        compose.onNodeWithTag("location_retry").assertDoesNotExist()
    }

    @Test
    fun editingTheQueryDuringARetryShowsOnlyTheNewQuerysTowns() {
        search.answer = { throw LocationSearchException("offline") }
        show()
        type("Can")
        advance(500)
        search.answer = { q -> if (q == "Can") awaitCancellation() else listOf(brighton) }
        compose.onNodeWithTag("location_retry").performClick()
        advance(500)
        type("x")
        advance(500)
        assertThat(search.queries).containsExactly("Can", "Can", "Canx").inOrder()
        assertThat(search.cancelled).isEqualTo(1)
        compose.onNodeWithTag("place_0").assertTextContains("Brighton, England, United Kingdom")
    }

    @Test
    fun noMatchIsSaidOnTheSameLine() {
        show()
        type("Xqz")
        advance(500)
        compose.onNodeWithTag("location_status").assertTextContains("Xqz", substring = true).assert(polite)
    }

    private fun assertDirectlyUnderTheField() {
        val field = compose.onNodeWithTag("location_query").getUnclippedBoundsInRoot()
        val status = compose.onNodeWithTag("location_status").getUnclippedBoundsInRoot()
        assertThat(status.top.value).isAtLeast(field.bottom.value)
        assertThat(status.top.value - field.bottom.value).isLessThan(40f)
    }

    private fun assertAbove(upper: String, lower: String) {
        val a = compose.onNodeWithTag(upper).getUnclippedBoundsInRoot()
        val b = compose.onNodeWithTag(lower).getUnclippedBoundsInRoot()
        assertThat(a.bottom.value).isAtMost(b.top.value)
    }

    /** A failed save over results puts the line above the first town; a failed search then keeps the line, in danger, with Try again. */
    @Test
    fun aFailedSearchSaysSoUnderTheField() {
        search.answer = { q -> if (q == "Can") listOf(brighton) else throw LocationSearchException("offline") }
        saveFails = true
        show()
        type("Can")
        advance(500)
        assertThat(compose.onNodeWithTag("location_status").getUnclippedBoundsInRoot().height.value).isAtMost(2f)
        compose.onNodeWithTag("place_0").performClick()
        advance(100)
        assertDirectlyUnderTheField()
        assertAbove("location_status", "place_0")
        type("x")
        advance(500)
        compose.onNodeWithTag("location_status").assertTextEquals("Couldn’t search for towns — check the tablet’s Wi-Fi, then try again.")
        assertThat(compose.onNodeWithTag("location_status").textLayout().layoutInput.style.color).isEqualTo(DarkColors.danger)
        assertDirectlyUnderTheField()
        assertAbove("location_status", "location_retry")
        compose.onNodeWithTag("location_retry").assertExists()
        compose.assertClickablesHaveRoleAndLabel()
    }

    @Test
    fun aSearchInProgressAndAFailedSaveShowInTheSameSpot() {
        search.answer = { listOf(brighton) }
        saveFails = true
        show()
        type("Can")
        advance(100)
        compose.onNodeWithTag("location_status").assertTextEquals("Searching…")
        assertThat(compose.onNodeWithTag("location_status").textLayout().layoutInput.style.color).isEqualTo(DarkColors.mute)
        assertDirectlyUnderTheField()
        advance(500)
        compose.onNodeWithTag("place_0").performClick()
        advance(100)
        compose.onNodeWithTag("location_status").assertTextEquals("Couldn’t save the home location — try again.")
        assertDirectlyUnderTheField()
        assertAbove("location_status", "place_0")
        compose.assertClickablesHaveRoleAndLabel()
    }

    /** Pin: neither error is the text's fault, so the field keeps no danger ring. */
    @Test
    @GraphicsMode(GraphicsMode.Mode.NATIVE)
    fun theFieldHasNoRingForASearchFailure() {
        search.answer = { throw LocationSearchException("offline") }
        show()
        type("Can")
        advance(500)
        compose.onNodeWithTag("location_status").assertExists()
        assertThat(pixelAt(compose.onNodeWithTag("location_query"), 1.dp, 30.dp)).isNotEqualTo(DarkColors.danger.toArgb())
        compose.assertClickablesHaveRoleAndLabel()
    }
}
