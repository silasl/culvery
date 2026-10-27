package uk.co.siland.culvery.capability.hoover.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertContentDescriptionEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.CompletableDeferred
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import uk.co.siland.culvery.capability.hoover.FakeHooverSystem
import uk.co.siland.culvery.capability.hoover.HooverActions
import uk.co.siland.culvery.capability.hoover.HooverActivity
import uk.co.siland.culvery.capability.hoover.HooverResult
import uk.co.siland.culvery.capability.hoover.HooverState
import uk.co.siland.culvery.capability.hoover.TestAccess
import uk.co.siland.culvery.core.household.Role
import uk.co.siland.culvery.core.ui.Culvery
import uk.co.siland.culvery.core.ui.CulveryTheme
import uk.co.siland.culvery.core.ui.HhTrayColumn
import uk.co.siland.culvery.core.ui.test.assertClickablesHaveRoleAndLabel
import uk.co.siland.culvery.core.ui.test.assertNothingReadTwice

private const val TOKEN_LINE = "Home Assistant no longer accepts Culvery’s token. Add a new one in Settings › Other integrations."
private const val NOT_STARTED_LINE = "The hoover didn’t start. Check the Eufy app."

/** 11 §4.4, §4.6: the Hoover row in the One-tap tray, its Start behind a fresh PIN. */
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = "w1280dp-h800dp-land-hdpi")
class HooverRowTest {
    @get:Rule val compose = createComposeRule()
    private val system = FakeHooverSystem()
    private val access = TestAccess(Role.ADULT)
    private var shown by mutableStateOf(true)

    private fun show() {
        compose.setContent {
            CulveryTheme(dark = true) {
                Box(Modifier.background(Culvery.colors.bg)) {
                    if (shown) {
                        HhTrayColumn("One-tap", listOf("hoover" to { HooverRowHost(system, HooverActions(access, setOf(system))) }), Modifier.width(333.dp))
                    }
                }
            }
        }
    }

    private fun state(activity: HooverActivity?, battery: Int? = 60, problem: String? = null) = HooverState(activity, battery, problem)

    private fun spoken(text: String) = compose.onNode(hasContentDescription(text)).assertExists()

    private fun status() = compose.onNodeWithTag("hoover_status", useUnmergedTree = true)

    private fun noButton() {
        compose.onNodeWithTag("hoover_start").assertDoesNotExist()
        compose.onAllNodes(hasClickAction()).assertCountEquals(0)
    }

    private fun statusLines(): Int {
        val results = mutableListOf<TextLayoutResult>()
        status().fetchSemanticsNode().config[SemanticsActions.GetTextLayoutResult].action!!(results)
        return results.single().lineCount
    }

    /** Fails if the words, the button or what TalkBack hears differ from the spec. */
    @Test
    fun dockedOffersStart() {
        show()
        compose.onNodeWithTag("hoover_row").assertIsDisplayed()
        status().assertTextEquals("Docked · 86%")
        compose.onNodeWithTag("hoover_start").assertContentDescriptionEquals("Start cleaning")
        compose.onNodeWithText("Start", useUnmergedTree = true).assertExists()
        spoken("Hoover, docked, battery 86 percent.")
        compose.assertNothingReadTwice()
        compose.assertClickablesHaveRoleAndLabel()
    }

    /** Fails if Cleaning gets a button, or the pill is read out. */
    @Test
    fun cleaningHasNoButton() {
        system.current.value = state(HooverActivity.CLEANING)
        show()
        status().assertTextEquals("Cleaning · 60%")
        compose.onNodeWithTag("hoover_inactive").assertTextEquals("Cleaning")
        assertThat(compose.onNodeWithTag("hoover_inactive").fetchSemanticsNode().config.contains(SemanticsProperties.HideFromAccessibility)).isTrue()
        noButton()
        spoken("Hoover, cleaning, battery 60 percent.")
        compose.assertClickablesHaveRoleAndLabel()
    }

    @Test
    fun idleAndPausedOfferStart() {
        system.current.value = state(HooverActivity.IDLE, 55)
        show()
        status().assertTextEquals("Idle · 55%")
        compose.onNodeWithTag("hoover_start").assertIsDisplayed()
        system.current.value = state(HooverActivity.PAUSED, 55)
        compose.waitForIdle()
        status().assertTextEquals("Paused · 55%")
        compose.onNodeWithTag("hoover_start").assertIsDisplayed()
        compose.assertClickablesHaveRoleAndLabel()
    }

    @Test
    fun goingHomeHasNoButton() {
        system.current.value = state(HooverActivity.RETURNING, 40)
        show()
        status().assertTextEquals("Going home · 40%")
        compose.onNodeWithTag("hoover_inactive").assertTextEquals("Cleaning")
        noButton()
        compose.assertClickablesHaveRoleAndLabel()
    }

    /** An unknown activity, a stuck hoover and a problem offer nothing: no button and no pill. */
    @Test
    fun unknownOrAProblemHasNoButton() {
        system.current.value = state(null, null)
        show()
        status().assertTextEquals("Checking…")
        noButton()
        compose.onNodeWithTag("hoover_inactive").assertDoesNotExist()

        system.current.value = state(HooverActivity.ERROR)
        compose.waitForIdle()
        status().assertTextEquals("Stuck. Check the Eufy app.")
        noButton()
        compose.onNodeWithTag("hoover_inactive").assertDoesNotExist()

        system.current.value = state(HooverActivity.DOCKED, 86, "Can’t reach Home Assistant. Check the NAS is on.")
        compose.waitForIdle()
        status().assertTextEquals("Can’t reach Home Assistant. Check the NAS is on.")
        spoken("Hoover. Can’t reach Home Assistant. Check the NAS is on.")
        noButton()
        compose.onNodeWithTag("hoover_inactive").assertDoesNotExist()
        assertThat(statusLines()).isAtMost(2)
        compose.assertClickablesHaveRoleAndLabel()
    }

    /** Review Focus 3. Fails if a second tap while the PIN pad or the check is out asks again or starts again. */
    @Test
    fun aDoubleTapAsksOnce() {
        val gate = CompletableDeferred<Unit>()
        system.gate = gate
        show()
        compose.onNodeWithTag("hoover_start").performClick()
        compose.waitUntil(5_000) { system.starts == 1 }
        compose.onNodeWithTag("hoover_start").performClick()
        compose.waitForIdle()
        assertThat(access.asked).hasSize(1)
        assertThat(system.starts).isEqualTo(1)
        compose.onNodeWithText("Starting…", useUnmergedTree = true).assertExists()
        compose.assertClickablesHaveRoleAndLabel()
        gate.complete(Unit)
        compose.waitUntil(5_000) { compose.onAllNodes(hasContentDescription("Start cleaning")).fetchSemanticsNodes().isNotEmpty() }
        assertThat(system.starts).isEqualTo(1)
    }

    /** A Child's PIN: nothing starts, and the button is back for the next try. */
    @Test
    fun aChildsPinLeavesTheButton() {
        access.role = Role.CHILD
        show()
        compose.onNodeWithTag("hoover_start").performClick()
        compose.waitUntil(5_000) { access.asked.size == 1 }
        compose.waitForIdle()
        assertThat(system.starts).isEqualTo(0)
        compose.onNodeWithTag("hoover_start").assertContentDescriptionEquals("Start cleaning")
    }

    /** 11 §4.4: the line stays until the hoover moves or START_LINE_MS pass. Fails if it goes 1 ms early or stays past START_LINE_MS. */
    @Test
    fun aFailedStartShowsItsLineUntilTheHooverMoves() {
        system.next = HooverResult.NotStarted
        show()
        compose.mainClock.autoAdvance = false
        val clock = compose.mainClock
        compose.onNodeWithTag("hoover_start").performClick()
        // The line is set at the tap's time; the frame after it only draws it.
        val shownAt = clock.currentTime
        clock.advanceTimeByFrame()
        status().assertTextEquals(NOT_STARTED_LINE)
        clock.advanceTimeBy(shownAt + START_LINE_MS - 1 - clock.currentTime, ignoreFrameDuration = true)
        status().assertTextEquals(NOT_STARTED_LINE)
        clock.advanceTimeBy(1, ignoreFrameDuration = true)
        repeat(2) { clock.advanceTimeByFrame() }
        status().assertTextEquals("Docked · 86%")
    }

    /** Fails if a second identical failure inherits the first one's timer instead of getting its own START_LINE_MS. */
    @Test
    fun aRepeatedFailureGetsItsFullTime() {
        system.next = HooverResult.NotStarted
        show()
        compose.mainClock.autoAdvance = false
        val clock = compose.mainClock
        compose.onNodeWithTag("hoover_start").performClick()
        clock.advanceTimeByFrame()
        clock.advanceTimeBy(START_LINE_MS / 2, ignoreFrameDuration = true)
        status().assertTextEquals(NOT_STARTED_LINE)
        compose.onNodeWithTag("hoover_start").performClick()
        // The line is set at the tap's time; the frame after it only draws it.
        val shownAt = clock.currentTime
        clock.advanceTimeByFrame()
        status().assertTextEquals(NOT_STARTED_LINE)
        clock.advanceTimeBy(shownAt + START_LINE_MS - 1 - clock.currentTime, ignoreFrameDuration = true)
        status().assertTextEquals(NOT_STARTED_LINE)
        clock.advanceTimeBy(1, ignoreFrameDuration = true)
        repeat(2) { clock.advanceTimeByFrame() }
        status().assertTextEquals("Docked · 86%")
    }

    @Test
    fun aFailedStartsLineGoesWhenTheHooverStarts() {
        system.next = HooverResult.NotStarted
        show()
        compose.onNodeWithTag("hoover_start").performClick()
        compose.waitUntil(5_000) { status().fetchSemanticsNode().config[SemanticsProperties.Text].joinToString { it.text } == NOT_STARTED_LINE }
        system.current.value = state(HooverActivity.CLEANING)
        compose.waitForIdle()
        status().assertTextEquals("Cleaning · 60%")
    }

    /** Review Focus 3 and the 30-second check: the row leaving Home cancels the start in flight. */
    @Test
    fun leavingHomeCancelsTheStart() {
        system.gate = CompletableDeferred()
        show()
        compose.onNodeWithTag("hoover_start").performClick()
        compose.waitUntil(5_000) { system.starts == 1 }
        shown = false
        compose.waitUntil(5_000) { system.cancelled == 1 }
        assertThat(system.cancelled).isEqualTo(1)
    }

    /** A problem line wraps in full at 333 dp: no line cap, no ellipsis. */
    @Test
    fun aProblemLineWrapsInFull() {
        system.current.value = state(HooverActivity.DOCKED, 86, TOKEN_LINE)
        show()
        status().assertTextEquals(TOKEN_LINE)
        val results = mutableListOf<TextLayoutResult>()
        status().fetchSemanticsNode().config[SemanticsActions.GetTextLayoutResult].action!!(results)
        val layout = results.single()
        assertThat(layout.lineCount).isGreaterThan(2)
        assertThat((0 until layout.lineCount).any { layout.isLineEllipsized(it) }).isFalse()
    }

    /** An ordinary status keeps its two-line cap. */
    @Test
    fun anOrdinaryLineKeepsTheCap() {
        system.current.value = state(HooverActivity.DOCKED, 86)
        show()
        assertThat(statusLines()).isAtMost(2)
    }

    @Test
    fun theRowWatchesWhileShown() {
        show()
        compose.waitForIdle()
        assertThat(system.watchers).isEqualTo(1)
        shown = false
        compose.waitForIdle()
        assertThat(system.watchers).isEqualTo(0)
    }

    /** Pin: with no hoover chosen the row draws nothing. */
    @Test
    fun noStateDrawsNothing() {
        system.current.value = null
        show()
        compose.onNodeWithTag("hoover_row").assertDoesNotExist()
    }
}
