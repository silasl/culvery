package uk.co.siland.culvery.core.setup

import androidx.compose.ui.test.assertIsNotSelected
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.assertTextContains
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.google.common.truth.Truth.assertThat
import java.io.IOException
import java.util.concurrent.CopyOnWriteArrayList
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.awaitCancellation
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import uk.co.siland.culvery.core.household.HomeLocation
import uk.co.siland.culvery.core.ui.CulveryTheme

@RunWith(AndroidJUnit4::class)
class LocationPaneTest {
    @get:Rule(order = 0) val touchMode = TouchModeRule()
    @get:Rule(order = 1) val compose = createComposeRule()
    private val canterbury = PlaceMatch("Canterbury", "England", "United Kingdom", 51.27904, 1.07992, "Europe/London")
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
        search.answer = { q -> if (q == "Ca") awaitCancellation() else listOf(canterbury) }
        show()
        type("Ca")
        advance(500)
        type("n")
        advance(500)
        assertThat(search.queries).containsExactly("Ca", "Can").inOrder()
        assertThat(search.cancelled).isEqualTo(1)
        compose.onNodeWithTag("place_0").assertTextContains("Canterbury, England, United Kingdom")
    }

    @Test
    fun choosingATownSavesIt() {
        search.answer = { listOf(canterbury) }
        show()
        type("Can")
        advance(500)
        compose.onNodeWithTag("place_0").assertIsNotSelected().performClick()
        advance(100)
        assertThat(saved).containsExactly(canterbury)
    }

    /** Picking a town is the one choice: the field shows its name and the suggestions close, without a new search. */
    @Test
    fun choosingATownFillsTheFieldWithItsNameAndClosesTheResults() {
        search.answer = { listOf(canterbury) }
        show()
        type("Can")
        advance(500)
        compose.onNodeWithTag("place_0").performClick()
        advance(1_000)
        assertThat(saved).containsExactly(canterbury)
        compose.onNodeWithTag("place_0").assertDoesNotExist()
        compose.onNodeWithTag("location_query").assertTextContains("Canterbury, England, United Kingdom")
        assertThat(search.queries).containsExactly("Can")
    }

    @Test
    fun editingTheNameAfterChoosingSearchesAgain() {
        search.answer = { listOf(canterbury) }
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
        search.answer = { listOf(canterbury) }
        show(current = canterbury.toHome())
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
        compose.onNodeWithText("Couldn't search for towns — check the tablet's Wi-Fi and try again.").assertExists()
        // Only the fixed words, never the exception's own.
        compose.onNodeWithText("offline", substring = true).assertDoesNotExist()
    }

    @Test
    fun noMatchNamesTheQuery() {
        show()
        type("Xqz")
        advance(500)
        compose.onNodeWithText("No towns match \"Xqz\".").assertExists()
    }

    @Test
    fun settingsShowsTheSavedHomeAboveTheField() {
        show(current = canterbury.toHome(), showCurrent = true)
        advance(100)
        compose.onNodeWithTag("location_current").assertTextContains("Canterbury, England, United Kingdom")
    }

    @Test
    fun aFailedSaveSaysSoAndKeepsTheResults() {
        search.answer = { listOf(canterbury) }
        saveFails = true
        show()
        type("Can")
        advance(500)
        compose.onNodeWithTag("place_0").performClick()
        advance(100)
        compose.onNodeWithText("Couldn't save — try again.").assertExists()
        compose.onNodeWithTag("place_0").assertTextContains("Canterbury, England, United Kingdom")
        assertNoSecretsLogged("LocationPane", listOf("Canterbury", "disk full"))
        // Trying again clears it.
        saveFails = false
        compose.onNodeWithTag("place_0").performClick()
        advance(100)
        assertThat(saved).containsExactly(canterbury)
        compose.onNodeWithText("Couldn't save — try again.").assertDoesNotExist()
    }
}
