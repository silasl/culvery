package uk.co.siland.culvery.core.access.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshots.Snapshot
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertContentDescriptionEquals
import androidx.compose.ui.test.assertHeightIsEqualTo
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsFocused
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertIsNotFocused
import androidx.compose.ui.test.assertWidthIsEqualTo
import androidx.compose.ui.test.click
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.isHeading
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.ExperimentalCoroutinesApi
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import uk.co.siland.culvery.core.access.PinError
import uk.co.siland.culvery.core.access.PinPromptController
import uk.co.siland.culvery.core.access.PinReason
import uk.co.siland.culvery.core.access.pinReasonText
import uk.co.siland.culvery.core.plugin.EDIT_HOLD_MS
import uk.co.siland.culvery.core.plugin.test.FakeUserActivity
import uk.co.siland.culvery.core.ui.CulveryTheme
import uk.co.siland.culvery.core.ui.HhTextField
import uk.co.siland.culvery.core.ui.test.TouchModeRule
import uk.co.siland.culvery.core.ui.test.assertAccessible

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(AndroidJUnit4::class)
class PinPadTest {
    /** Focus behaves as on the tablet only in touch mode, set before the activity launches. */
    @get:Rule(order = 0) val touchMode = TouchModeRule()
    @get:Rule(order = 1) val compose = createComposeRule()
    private val controller = PinPromptController()

    private val activity = FakeUserActivity()

    private fun show(overSheet: Boolean = false, overSettings: Boolean = false) =
        compose.setContent { CulveryTheme(dark = true) { PinPadHost(controller, activity, overSheet, overSettings) } }

    private fun tap(vararg keys: String) = keys.forEach { compose.onNodeWithTag("pin_key_$it").performClick() }

    @Test
    fun fourthDigitSubmitsAutomatically() {
        val request = controller.open("Change settings", null, null)
        show()
        tap("1", "2", "3", "4")
        compose.waitForIdle()
        assertThat(request.answer.getCompleted()).isEqualTo("1234")
    }

    /** Disabled keys at the fourth digit made TalkBack read the focused key again, which drowned the refusal. */
    @Test
    fun theKeysStayEnabledAtTheFourthDigitAndAFifthIsIgnored() {
        val submitted = mutableListOf<String>()
        compose.setContent {
            CulveryTheme(dark = true) {
                PinPadFrame("Who’s this?", line = null, message = "", wrongPin = false, enabled = true, onSubmit = { submitted += it }, onCancel = {})
            }
        }
        tap("1", "2", "3", "4")
        compose.onNodeWithTag("pin_key_4").assertIsEnabled()
        // A submitted PIN is being checked: Delete can't reopen the dots for a PIN that would never be checked.
        compose.onNodeWithTag("pin_backspace").assertIsNotEnabled()
        tap("5")
        compose.waitForIdle()
        assertThat(submitted).containsExactly("1234")
    }

    /**
     * A retry keeps the pad's nodes and empties the digits. Rebuilt nodes moved TalkBack's focus to the new key, and its
     * reading of that key flushed "That PIN can’t do this. Ask an Admin." before it was said.
     */
    @Test
    fun aRetryKeepsThePadAndEmptiesTheDigits() {
        controller.open("Change settings", null, null)
        show()
        val key = compose.onNodeWithTag("pin_key_4").fetchSemanticsNode().id
        tap("1", "2", "3")
        controller.open("Change settings", PinError.NotAllowed("That PIN can’t do this. Ask an Admin."), null)
        compose.waitForIdle()
        assertThat(compose.onNodeWithTag("pin_key_4").fetchSemanticsNode().id).isEqualTo(key)
        compose.onNodeWithTag("pin_dots").assertContentDescriptionEquals("0 of 4 digits entered")
    }

    /** 4e session 1: a PIN pad opened from a step with the keyboard up stayed under the keyboard. */
    @Test
    fun theKeyboardClosesWhenAPadOpens() {
        var padUp by mutableStateOf(false)
        compose.setContent {
            CulveryTheme(dark = true) {
                Column {
                    HhTextField("Sam", {}, placeholder = "Name", label = "Name", tag = "field")
                    if (padUp) PinPadFrame("Choose a 4-digit PIN", line = null, message = "", wrongPin = false, enabled = true, onSubmit = {}, onCancel = {})
                }
            }
        }
        compose.onNodeWithTag("field").performClick().assertIsFocused()
        padUp = true
        compose.waitForIdle()
        compose.onNodeWithTag("field").assertIsNotFocused()
    }

    @Test
    fun threeDigitsDoNotSubmit() {
        val request = controller.open("Change settings", null, null)
        show()
        tap("1", "2", "3")
        compose.waitForIdle()
        assertThat(request.answer.isCompleted).isFalse()
    }

    @Test
    fun backspaceRemovesLastDigit() {
        val request = controller.open("Change settings", null, null)
        show()
        tap("1", "2", "3")
        compose.onNodeWithTag("pin_backspace").performClick()
        tap("4", "5")
        compose.waitForIdle()
        assertThat(request.answer.getCompleted()).isEqualTo("1245")
    }

    @Test
    fun cancelKeyAnswersNull() {
        val request = controller.open("Change settings", null, null)
        show()
        compose.onNodeWithTag("pin_cancel").performClick()
        compose.waitForIdle()
        assertThat(request.answer.getCompleted()).isNull()
    }

    @Test
    fun tappingOutsideTheCardCancels() {
        val request = controller.open("Change settings", null, null)
        show()
        compose.onNodeWithTag("pin_scrim").performTouchInput { click(Offset(10f, 10f)) }
        compose.waitForIdle()
        assertThat(request.answer.getCompleted()).isNull()
    }

    @Test
    fun tappingTheCardItselfDoesNotCancel() {
        val request = controller.open("Change settings", null, null)
        show()
        compose.onNodeWithText("Who’s this?").performClick()
        compose.waitForIdle()
        assertThat(request.answer.isCompleted).isFalse()
    }

    @Test
    fun asksWhoIsThisWithTheEventReason() {
        controller.open("Change any event", null, null, PinReason.Delete)
        show()
        compose.onNodeWithText("Who’s this?").assertExists()
        compose.onNodeWithText("Enter your PIN to delete this event. It also records who made the change.").assertExists()
    }

    @Test
    fun genericReasonUsesThePermissionLabel() {
        controller.open("Change settings", null, null)
        show()
        compose.onNodeWithText("Enter your PIN to change settings.").assertExists()
    }

    @Test
    fun wrongPinShowsTheErrorLineWithEmptyDigits() {
        controller.open("Change settings", PinError.WrongPin, null)
        show()
        compose.onNodeWithText("Wrong PIN — try again.").assertExists()
    }

    @Test
    fun notAllowedShowsItsMessage() {
        controller.open("Change settings", PinError.NotAllowed("That PIN can’t do this. Ask an Admin."), null)
        show()
        compose.onNodeWithText("That PIN can’t do this. Ask an Admin.").assertExists()
    }

    /** The pad stays up between tries, so a lock that arrives on a retry must start its own countdown. */
    @Test
    fun aLockThatArrivesOnARetryDisablesTheKeys() {
        compose.mainClock.autoAdvance = false
        controller.open("Change settings", PinError.WrongPin, null)
        show()
        compose.onNodeWithTag("pin_key_1").assertIsEnabled()
        controller.open("Change settings", PinError.WrongPin, System.currentTimeMillis() + 30_000)
        compose.mainClock.advanceTimeByFrame()
        compose.onNodeWithTag("pin_key_1").assertIsNotEnabled()
        compose.onNodeWithText("Too many tries", substring = true).assertExists()
    }

    /** When the wait ends nobody has typed, so the last refusal isn't said again. */
    @Test
    fun theEndOfALockSaysYouCanTryAgainAndClearsTheDotsError() {
        compose.mainClock.autoAdvance = false
        controller.open("Change settings", PinError.WrongPin, System.currentTimeMillis() + 2_000)
        show()
        compose.mainClock.advanceTimeBy(3_000)
        compose.onNodeWithTag("pin_message").assertContentDescriptionEquals("You can try again.")
        compose.onNodeWithTag("pin_dots").assert(SemanticsMatcher.keyNotDefined(SemanticsProperties.Error))
        compose.onNodeWithTag("pin_key_1").assertIsEnabled()
    }

    @Test
    fun keysAreDisabledWhileLocked() {
        // The countdown loops on delay(); stop the test clock racing through it.
        compose.mainClock.autoAdvance = false
        controller.open("Change settings", PinError.WrongPin, System.currentTimeMillis() + 60_000)
        show()
        compose.onNodeWithTag("pin_key_1").assertIsNotEnabled()
        compose.onNodeWithText("Too many tries", substring = true).assertExists()
    }

    @Test
    fun hiddenWhenNoRequest() {
        show()
        compose.onNodeWithTag("pin_scrim").assertDoesNotExist()
    }

    @Test
    fun backspaceIsLabelled() {
        controller.open("Change settings", null, null)
        show()
        compose.onNodeWithContentDescription("Delete last digit").assertExists()
    }

    @Test
    fun keysAre76dpCirclesInA400dpCard() {
        controller.open("Change settings", null, null)
        show()
        compose.onNodeWithTag("pin_key_5").assertWidthIsEqualTo(76.dp).assertHeightIsEqualTo(76.dp)
        compose.onNodeWithTag("pin_card", useUnmergedTree = true).assertWidthIsEqualTo(400.dp)
    }

    @Test
    fun withoutASheetTheCardIsCentredOnTheScreen() {
        controller.open("Change settings", null, null)
        show()
        val card = compose.onNodeWithTag("pin_card", useUnmergedTree = true).getUnclippedBoundsInRoot()
        assertThat(((card.left + card.right) / 2).value).isWithin(1f).of(640f)
    }

    @Test
    fun overASheetTheScrimCoversOnlyTheSheetAndTheCardIsCentredInIt() {
        controller.open("Change settings", null, null)
        show(overSheet = true)
        val area = compose.onNodeWithTag("pin_area", useUnmergedTree = true).getUnclippedBoundsInRoot()
        assertThat(area.left.value).isWithin(1f).of(680f)
        assertThat((area.right - area.left).value).isWithin(1f).of(600f)
        val card = compose.onNodeWithTag("pin_card", useUnmergedTree = true).getUnclippedBoundsInRoot()
        assertThat(((card.left + card.right) / 2).value).isWithin(1f).of(980f)
    }

    @Test
    fun thePinPadsLinesAreSentencesInFullWords() {
        assertThat(tooManyTries(30)).isEqualTo("Too many tries — wait 30 seconds.")
        assertThat(tooManyTries(1)).isEqualTo("Too many tries — wait 1 second.")
        // Past a minute, minutes: a doubled lock said "wait 240 seconds".
        assertThat(tooManyTries(60)).isEqualTo("Too many tries — wait 1 minute.")
        assertThat(tooManyTries(240)).isEqualTo("Too many tries — wait 4 minutes.")
        assertThat(tooManyTries(239)).isEqualTo("Too many tries — wait 3 minutes 59 seconds.")
        assertThat(tooManyTries(61)).isEqualTo("Too many tries — wait 1 minute 1 second.")
        assertThat(WRONG_PIN).isEqualTo("Wrong PIN — try again.")
        assertThat(pinReasonText(PinReason.ContinueSetup, "x")).isEqualTo("Enter your PIN to carry on setting up.")
    }

    @Test
    fun theScrimIsSilentAndEveryKeyIsANamedButton() {
        controller.open("Change settings", null, null)
        show()
        compose.onNodeWithTag("pin_scrim").assert(SemanticsMatcher.keyNotDefined(SemanticsActions.OnClick))
        listOf("pin_key_5", "pin_cancel", "pin_backspace").forEach {
            compose.onNodeWithTag(it).assert(SemanticsMatcher.expectValue(SemanticsProperties.Role, Role.Button))
        }
        compose.assertAccessible()
    }

    @Test
    fun theTitleIsAHeading() {
        controller.open("Change settings", null, null)
        show()
        compose.onNodeWithText("Who’s this?").assert(isHeading())
    }

    @Test
    fun thePadIsAPaneThatAsksForThePin() {
        controller.open("Change settings", null, null)
        show()
        compose.onNodeWithTag("pin_scrim").assert(SemanticsMatcher.expectValue(SemanticsProperties.PaneTitle, "Enter your PIN"))
    }

    private fun description(tag: String): String =
        compose.onNodeWithTag(tag).fetchSemanticsNode().config[SemanticsProperties.ContentDescription].single()

    @Test
    fun theDotsSayHowManyDigitsAreInAndSpeakEachOne() {
        controller.open("Change settings", null, null)
        show()
        assertThat(description("pin_dots")).isEqualTo("0 of 4 digits entered")
        compose.onNodeWithTag("pin_dots").assert(SemanticsMatcher.expectValue(SemanticsProperties.LiveRegion, LiveRegionMode.Polite))
        tap("1")
        assertThat(description("pin_dots")).isEqualTo("1 of 4 digits entered")
    }

    @Test
    fun aWrongPinIsAnErrorOnTheDotsAndItsMessageIsALiveRegion() {
        controller.open("Change settings", PinError.WrongPin, null)
        show()
        compose.onNodeWithTag("pin_dots").assert(SemanticsMatcher.expectValue(SemanticsProperties.Error, WRONG_PIN))
        assertThat(description("pin_message")).isEqualTo(WRONG_PIN)
        compose.onNodeWithTag("pin_message").assert(SemanticsMatcher.expectValue(SemanticsProperties.LiveRegion, LiveRegionMode.Polite))
    }

    /** How many times TalkBack speaks it is the emulator's to hear; here, its spoken text never ticks. */
    @Test
    fun theLockoutsSpokenTextStaysFixedWhileItsSecondsTick() {
        compose.mainClock.autoAdvance = false
        val start = 1_000_000L
        controller.open("Change settings", PinError.WrongPin, start + 30_000)
        // The pad's clock follows the test clock, so a first value recomputed on each tick would show.
        compose.setContent { CulveryTheme(dark = true) { PinPadHost(controller, activity, nowMillis = { start + compose.mainClock.currentTime }) } }
        compose.mainClock.advanceTimeByFrame()
        val spoken = description("pin_message")
        val dotsError = compose.onNodeWithTag("pin_dots").fetchSemanticsNode().config[SemanticsProperties.Error]
        val shown = compose.onNodeWithTag("pin_error", useUnmergedTree = true).fetchSemanticsNode().config[SemanticsProperties.Text].single().text
        compose.mainClock.advanceTimeBy(3_000)
        assertThat(description("pin_message")).isEqualTo(spoken)
        assertThat(compose.onNodeWithTag("pin_dots").fetchSemanticsNode().config[SemanticsProperties.Error]).isEqualTo(dotsError)
        assertThat(compose.onNodeWithTag("pin_error", useUnmergedTree = true).fetchSemanticsNode().config[SemanticsProperties.Text].single().text)
            .isNotEqualTo(shown)
        assertThat(spoken).isEqualTo(tooManyTries(30))
        assertThat(dotsError).isEqualTo(spoken)
    }

    /** 4d-2 §6.3: a pad left alone cancels, so authorise returns null; a touch starts the wait again; a lockout doesn't stop it. */
    @Test
    fun anIdlePadCancelsAtTwoMinutesAndATouchKeepsIt() {
        compose.mainClock.autoAdvance = false
        val request = controller.open("Change settings", PinError.WrongPin, System.currentTimeMillis() + 300_000)
        show()
        compose.mainClock.advanceTimeBy(60_000)
        activity.touch()
        compose.runOnIdle { Snapshot.sendApplyNotifications() }
        compose.mainClock.advanceTimeByFrame()
        compose.mainClock.advanceTimeBy(119_900)
        assertThat(request.answer.isCompleted).isFalse()
        compose.mainClock.advanceTimeBy(200)
        assertThat(request.answer.getCompleted()).isNull()
    }

    /** Digits typed and left are never sent: the pad cancels as its Cancel key does. */
    @Test
    fun digitsLeftOnAnIdlePadAreNotSubmitted() {
        compose.mainClock.autoAdvance = false
        val request = controller.open("Change settings", null, null)
        show()
        tap("1", "2", "3")
        compose.mainClock.advanceTimeBy(120_100)
        assertThat(request.answer.getCompleted()).isNull()
    }

    /**
     * 4d-2 §6.3: over Settings the pad outlasts the longest session, edits held included, by a second, so the session
     * always ends first and the pad's close can't move TalkBack's focus and stretch it. Settings closing re-keys the pad
     * to its own two minutes.
     */
    @Test
    fun overSettingsThePadOutlastsTheLongestSessionByASecond() {
        compose.mainClock.autoAdvance = false
        val request = controller.open("Change settings", null, null)
        show(overSettings = true)
        compose.mainClock.advanceTimeBy(EDIT_HOLD_MS + 900)
        assertThat(request.answer.isCompleted).isFalse()
        compose.mainClock.advanceTimeBy(200)
        assertThat(request.answer.getCompleted()).isNull()
    }

    /** Settings closing under the pad puts it back on its own 2 minutes, not the 10:01 it waited over Settings. */
    @Test
    fun whenSettingsClosesUnderThePadItCancelsAtTwoMinutes() {
        compose.mainClock.autoAdvance = false
        val request = controller.open("Change settings", null, null)
        var overSettings by mutableStateOf(true)
        compose.setContent { CulveryTheme(dark = true) { PinPadHost(controller, activity, overSettings = overSettings) } }
        compose.mainClock.advanceTimeBy(60_000)
        overSettings = false
        compose.runOnIdle { Snapshot.sendApplyNotifications() }
        compose.mainClock.advanceTimeByFrame()
        compose.mainClock.advanceTimeBy(119_900)
        assertThat(request.answer.isCompleted).isFalse()
        compose.mainClock.advanceTimeBy(200)
        assertThat(request.answer.getCompleted()).isNull()
    }
}
