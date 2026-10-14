package uk.co.siland.culvery.shell.ui

import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshots.Snapshot
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.click
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.google.common.truth.Truth.assertThat
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import uk.co.siland.culvery.core.plugin.EDIT_HOLD_MS
import uk.co.siland.culvery.core.plugin.OVERLAY_IDLE_MS
import uk.co.siland.culvery.core.plugin.test.FakeUserActivity
import uk.co.siland.culvery.core.ui.CulveryTheme
import uk.co.siland.culvery.shell.OverlayState

/** 5 §2.10, §5.1: the alert layer over a sheet, under the PIN pad, and its touches its own. */
@RunWith(AndroidJUnit4::class)
class AlertLayerTest {
    @get:Rule val compose = createComposeRule()
    private val overlay = OverlayState()
    private val activity = FakeUserActivity()
    private var alert by mutableStateOf<(@Composable () -> Unit)?>(null)
    private var pinShowing by mutableStateOf(false)
    private var padTaps = 0

    // Swallows its taps, as the door card does.
    private val card: @Composable () -> Unit = {
        Box(Modifier.fillMaxWidth().height(200.dp).testTag("card").pointerInput(Unit) { detectTapGestures {} })
    }
    private val sheet: @Composable () -> Unit = { Box(Modifier.fillMaxSize().testTag("sheet")) }

    private companion object {
        const val FRAME_MS = 16L
    }

    private fun show() {
        compose.mainClock.autoAdvance = false
        compose.setContent {
            CulveryTheme(dark = true) {
                ShellLayers(
                    overlay = overlay,
                    toast = null,
                    onToastHidden = {},
                    pinShowing = pinShowing,
                    pinPad = { if (pinShowing) Box(Modifier.fillMaxSize().testTag("pad").pointerInput(Unit) { detectTapGestures { padTaps++ } }) },
                    activity = activity,
                    onTouch = activity::touch,
                    alert = alert,
                ) { Text("Home") }
            }
        }
    }

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

    /** Fails if the alert is drawn inside the touch-counting box: the touch count becomes 1 and the sheet lives 2 more minutes. */
    @Test
    fun aTapOnTheAlertIsNotATouchAndTheSheetBeneathClosesOnItsOwnTime() {
        show()
        overlay.show(content = sheet)
        alert = card
        settle()
        // Its 2 minutes started on one of the two frames settle() ran: the checks sit a frame either side.
        val opened = compose.mainClock.currentTime
        after(OVERLAY_IDLE_MS - 2_000)
        compose.onNodeWithTag("card").performClick()
        settle()
        assertThat(activity.touches.value).isEqualTo(0L)
        compose.mainClock.advanceTimeBy(opened + OVERLAY_IDLE_MS - compose.mainClock.currentTime - 3 * FRAME_MS)
        assertThat(open("sheet")).isTrue()
        compose.mainClock.advanceTimeBy(3 * FRAME_MS)
        compose.mainClock.advanceTimeByFrame()
        assertThat(open("sheet")).isFalse()
    }

    /** Fails if a tap on the card resets or shortens an edited sheet's EDIT_HOLD_MS timer, or ends the edits. */
    @Test
    fun aTapOnTheAlertLeavesAnEditedSheetsTimerAlone() {
        show()
        overlay.show(content = sheet)
        overlay.markEdited(true)
        alert = card
        settle()
        val opened = compose.mainClock.currentTime
        after(EDIT_HOLD_MS - 30_000)
        compose.onNodeWithTag("card").performClick()
        settle()
        assertThat(activity.touches.value).isEqualTo(0L)
        assertThat(overlay.edited).isTrue()
        compose.mainClock.advanceTimeBy(opened + EDIT_HOLD_MS - compose.mainClock.currentTime - 3 * FRAME_MS)
        assertThat(open("sheet")).isTrue()
        compose.mainClock.advanceTimeBy(3 * FRAME_MS)
        compose.mainClock.advanceTimeByFrame()
        assertThat(open("sheet")).isFalse()
    }

    /** Fails if raising or removing the alert replaces or closes the open sheet, or clears its edits. */
    @Test
    fun theAlertLeavesAnEditedSheetAndItsEditsAlone() {
        show()
        overlay.show(content = sheet)
        overlay.markEdited(true)
        settle()
        alert = card
        settle()
        assertThat(open("card")).isTrue()
        alert = null
        settle()
        assertThat(overlay.content).isSameInstanceAs(sheet)
        assertThat(overlay.edited).isTrue()
        assertThat(open("sheet")).isTrue()
    }

    /** Fails if the alert is hidden from TalkBack whenever a sheet is open. */
    @Test
    fun theAlertIsReadOverASheet() {
        show()
        overlay.show(content = sheet)
        alert = card
        settle()
        assertThat(open("card")).isTrue()
    }

    /** Fails if the alert is drawn above the pad, the pad stops counting touches, or the card stays in TalkBack's tree under the pad. */
    @Test
    fun thePinPadDrawsAboveTheAlert() {
        show()
        alert = card
        pinShowing = true
        settle()
        assertThat(open("card")).isFalse()
        compose.onNodeWithTag("pad").performTouchInput { click(Offset(centerX, centerY)) }
        settle()
        assertThat(padTaps).isEqualTo(1)
        // The pad's touches still count, as before.
        assertThat(activity.touches.value).isEqualTo(1L)
    }

    /** Fails if a tap on a part of the alert with no handler of its own reaches the sheet's scrim or counts as a touch. */
    @Test
    fun aTapOnABareAlertDoesNotReachTheSheetBeneath() {
        show()
        overlay.show(content = sheet)
        overlay.markEdited(true)
        alert = { Box(Modifier.fillMaxWidth().height(200.dp).testTag("bare")) }
        settle()
        compose.onNodeWithTag("bare").performClick()
        settle()
        assertThat(overlay.content).isSameInstanceAs(sheet)
        assertThat(overlay.edited).isTrue()
        assertThat(activity.touches.value).isEqualTo(0L)
    }
}
