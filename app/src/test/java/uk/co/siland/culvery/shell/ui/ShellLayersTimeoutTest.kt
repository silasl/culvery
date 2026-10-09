package uk.co.siland.culvery.shell.ui

import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Text
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshots.Snapshot
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.google.common.truth.Truth.assertThat
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import uk.co.siland.culvery.core.access.SESSION_TIMEOUT_MS
import uk.co.siland.culvery.core.plugin.EDIT_HOLD_MS
import uk.co.siland.culvery.core.plugin.OVERLAY_IDLE_MS
import uk.co.siland.culvery.core.plugin.test.FakeUserActivity
import uk.co.siland.culvery.core.ui.CulveryTheme
import uk.co.siland.culvery.core.ui.HhSheet
import uk.co.siland.culvery.shell.OverlayState

/** 4d-2 §6.2, §6.3 under the Compose test clock: what closes when, and the two warnings. */
@RunWith(AndroidJUnit4::class)
class ShellLayersTimeoutTest {
    @get:Rule val compose = createComposeRule()
    private val overlay = OverlayState()
    private val activity = FakeUserActivity()
    private var expiresAt by mutableStateOf<Long?>(null)
    private var pinShowing by mutableStateOf(false)
    // MainActivity: false while Settings is open or the wizard shows.
    private var overlaysClose by mutableStateOf(true)

    private fun show() {
        compose.mainClock.autoAdvance = false
        compose.setContent {
            CulveryTheme(dark = true) {
                ShellLayers(
                    overlay = overlay,
                    toast = null,
                    onToastHidden = {},
                    pinShowing = pinShowing,
                    // Its own scrim takes taps, as the real pad's does, so a tap on it never reaches the sheet's.
                    pinPad = { if (pinShowing) Box(Modifier.fillMaxSize().testTag("pad").pointerInput(Unit) { detectTapGestures {} }) },
                    activity = activity,
                    onTouch = activity::touch,
                    settingsExpiresAt = expiresAt,
                    nowMillis = { compose.mainClock.currentTime },
                    overlaysCloseWhenIdle = overlaysClose,
                ) { Text("Home") }
            }
        }
    }

    /** Two frames: one recomposes on the new state, the next draws anything an effect wrote back. */
    private fun settle() {
        repeat(2) {
            compose.runOnIdle { Snapshot.sendApplyNotifications() }
            compose.mainClock.advanceTimeByFrame()
        }
    }

    private fun after(millis: Long) {
        compose.mainClock.advanceTimeBy(millis)
        compose.mainClock.advanceTimeByFrame()
    }

    private fun open(tag: String) = compose.onAllNodesWithTag(tag).fetchSemanticsNodes().isNotEmpty()

    @Test
    fun settingsWarnsTwentySecondsBeforeItsSessionEndsAndATouchHidesIt() {
        show()
        expiresAt = compose.mainClock.currentTime + SESSION_TIMEOUT_MS
        settle()
        after(SESSION_TIMEOUT_MS - 20_000 - 100)
        assertThat(open("settings_warning")).isFalse()
        after(100)
        compose.onNodeWithTag("settings_warning")
            .assert(SemanticsMatcher.expectValue(SemanticsProperties.LiveRegion, LiveRegionMode.Assertive))
            .assertTextEquals("Settings closes in 20 seconds. Touch the screen to stay.")
        // A touch moves the session on; the warning goes until the next countdown's last 20 seconds.
        expiresAt = compose.mainClock.currentTime + SESSION_TIMEOUT_MS
        settle()
        assertThat(open("settings_warning")).isFalse()
        after(SESSION_TIMEOUT_MS - 20_000 - 100)
        assertThat(open("settings_warning")).isFalse()
        after(100)
        assertThat(open("settings_warning")).isTrue()
    }

    /** A sheet and the PIN pad hide what they cover from TalkBack; the warning sits above both, so it is still read. */
    @Test
    fun theWarningIsReadOverAnOpenSheetAndThePinPad() {
        show()
        overlay.show { Box(Modifier.fillMaxSize().testTag("sheet")) }
        pinShowing = true
        expiresAt = compose.mainClock.currentTime + SESSION_TIMEOUT_MS
        settle()
        after(SESSION_TIMEOUT_MS - 20_000)
        assertThat(open("pad")).isTrue()
        compose.onNodeWithTag("settings_warning").assertTextEquals(SETTINGS_CLOSING)
    }

    @Test
    fun noSessionNoWarning() {
        show()
        after(30 * 60_000L)
        assertThat(open("settings_warning")).isFalse()
    }

    private fun sheet(edited: Boolean = false, closesWhenIdle: Boolean = true) {
        compose.runOnIdle {
            overlay.show(closesWhenIdle) { HhSheet(PaddingValues(), Modifier.testTag("sheet")) { Text("Sheet body") } }
            if (edited) overlay.markEdited(true)
        }
        settle()
    }

    /** As MainActivity has it while Settings is open: the session's end, and no overlay closing by itself. */
    private fun openSettings(endsIn: Long) {
        expiresAt = compose.mainClock.currentTime + endsIn
        overlaysClose = false
    }

    @Test
    fun anUntouchedSheetClosesAtTwoMinutesAndNotBefore() {
        show()
        sheet()
        after(OVERLAY_IDLE_MS - 100)
        assertThat(open("sheet")).isTrue()
        after(100)
        assertThat(open("sheet")).isFalse()
    }

    @Test
    fun aTouchInsideTheSheetStartsTheWaitAgain() {
        show()
        sheet()
        after(OVERLAY_IDLE_MS - 10_000)
        activity.touch()
        settle()
        after(OVERLAY_IDLE_MS - 100)
        assertThat(open("sheet")).isTrue()
        after(100)
        assertThat(open("sheet")).isFalse()
    }

    @Test
    fun anEditedSheetWarnsAt940AndClosesAtTenMinutes() {
        show()
        sheet(edited = true)
        after(EDIT_HOLD_MS - 20_000 - 100)
        assertThat(open("sheet_warning")).isFalse()
        after(100)
        compose.onNodeWithTag("sheet_warning")
            .assert(SemanticsMatcher.expectValue(SemanticsProperties.LiveRegion, LiveRegionMode.Assertive))
            .assertTextEquals("This closes in 20 seconds. Touch the screen to keep your changes.")
        after(20_000 - 200)
        assertThat(open("sheet")).isTrue()
        after(200)
        assertThat(open("sheet")).isFalse()
        assertThat(open("sheet_warning")).isFalse()
    }

    /** Closing by itself is a dismiss: the edits go with the sheet, and the next sheet opens with none. */
    @Test
    fun anEditedSheetLeftAloneTakesItsEditsWithIt() {
        show()
        sheet(edited = true)
        after(EDIT_HOLD_MS)
        assertThat(overlay.isShowing).isFalse()
        assertThat(overlay.edited).isFalse()
    }

    @Test
    fun aTouchHidesTheSheetWarningAndStartsTheTenMinutesAgain() {
        show()
        sheet(edited = true)
        after(EDIT_HOLD_MS - 10_000)
        assertThat(open("sheet_warning")).isTrue()
        activity.touch()
        settle()
        assertThat(open("sheet_warning")).isFalse()
        after(EDIT_HOLD_MS - 100)
        assertThat(open("sheet")).isTrue()
        after(100)
        assertThat(open("sheet")).isFalse()
    }

    /** Undone, the sheet has nothing to lose again: 2 minutes and no warning. */
    @Test
    fun aSheetWhoseEditsAreUndoneClosesAtTwoMinutesWithoutAWarning() {
        show()
        sheet(edited = true)
        compose.runOnIdle { overlay.markEdited(false) }
        settle()
        after(OVERLAY_IDLE_MS - 100)
        assertThat(open("sheet")).isTrue()
        assertThat(open("sheet_warning")).isFalse()
        after(100)
        assertThat(open("sheet")).isFalse()
    }

    @Test
    fun theConnectingCardIsStillOpenAtThirtyMinutes() {
        show()
        sheet(closesWhenIdle = false)
        after(30 * 60_000L)
        assertThat(open("sheet")).isTrue()
    }

    @Test
    fun withTalkBacksLongerTimeAnUntouchedSheetStaysFiveMinutes() {
        activity.timeouts = { base, _ -> if (base == OVERLAY_IDLE_MS) 300_000L else base }
        show()
        sheet()
        after(300_000L - 100)
        assertThat(open("sheet")).isTrue()
        after(100)
        assertThat(open("sheet")).isFalse()
    }

    /** The pad over the sheet comes and goes; a tap on it is a touch; the edited sheet keeps its ten minutes. */
    @Test
    fun anEditedSheetStaysWhileAPadOverItComesAndGoes() {
        show()
        sheet(edited = true)
        pinShowing = true
        settle()
        after(OVERLAY_IDLE_MS)
        compose.onNodeWithTag("pad").performClick()
        settle()
        pinShowing = false
        settle()
        after(EDIT_HOLD_MS - 100)
        assertThat(open("sheet")).isTrue()
        after(100)
        assertThat(open("sheet")).isFalse()
    }

    /** 4d-2 §6.3: inside Settings no overlay closes by itself; Settings' own end takes it. */
    @Test
    fun insideSettingsASheetDoesNotCloseByItselfAndOnlySettingsWarns() {
        show()
        openSettings(endsIn = EDIT_HOLD_MS)
        sheet(edited = true)
        after(EDIT_HOLD_MS - 10_000)
        assertThat(open("settings_warning")).isTrue()
        assertThat(open("sheet_warning")).isFalse()
        after(OVERLAY_IDLE_MS + EDIT_HOLD_MS)
        assertThat(open("sheet")).isTrue()
    }

    @Test
    fun insideSettingsAnUneditedSheetOutlastsTheOverlayIdleTime() {
        show()
        openSettings(endsIn = 60 * 60_000L)
        sheet()
        after(OVERLAY_IDLE_MS + 100)
        assertThat(open("sheet")).isTrue()
    }

    /** In the wizard, as in Settings, a sheet closing by itself would move TalkBack's focus and stretch the setup session. */
    @Test
    fun inTheWizardNoSheetClosesByItselfOrWarns() {
        overlaysClose = false
        show()
        sheet(edited = true)
        after(EDIT_HOLD_MS - 10_000)
        assertThat(open("sheet_warning")).isFalse()
        after(OVERLAY_IDLE_MS + EDIT_HOLD_MS)
        assertThat(open("sheet")).isTrue()
    }
}
