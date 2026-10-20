package uk.co.siland.culvery.shell.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
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
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.click
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.unit.DpRect
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
import uk.co.siland.culvery.core.ui.test.assertClickablesHaveRoleAndLabel
import uk.co.siland.culvery.shell.OverlayState

/** 5 §2.10, §5.1, 9 §4.3: the alert layer over a sheet, under the PIN pad, over a scrim that takes every tap. */
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

    /** Still composed, whether or not TalkBack can reach it. */
    private fun drawn(tag: String) = compose.onAllNodesWithTag(tag, useUnmergedTree = true).fetchSemanticsNodes().isNotEmpty()

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
        assertThat(drawn("sheet")).isTrue()
        compose.mainClock.advanceTimeBy(3 * FRAME_MS)
        compose.mainClock.advanceTimeByFrame()
        assertThat(drawn("sheet")).isFalse()
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
        assertThat(drawn("sheet")).isTrue()
        compose.mainClock.advanceTimeBy(3 * FRAME_MS)
        compose.mainClock.advanceTimeByFrame()
        assertThat(drawn("sheet")).isFalse()
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

    /**
     * 9 §4.3: Fails if a tap on the scrim, or on a part of the alert with no handler of its own, presses the button
     * beneath, reaches the sheet's scrim or counts as a touch.
     */
    @Test
    fun aTapOnTheScrimPressesNothingBeneath() {
        var pressed = 0
        val narrowSheet: @Composable () -> Unit = { Box(Modifier.width(300.dp).fillMaxHeight().testTag("sheet")) }
        compose.setContent {
            CulveryTheme(dark = true) {
                ShellLayers(
                    overlay = overlay, toast = null, onToastHidden = {}, pinShowing = false, pinPad = {},
                    activity = activity, onTouch = activity::touch, alert = alert,
                ) { Box(Modifier.size(100.dp).clickable { pressed++ }) }
            }
        }
        alert = { Box(Modifier.width(400.dp).height(200.dp).testTag("bare")) }
        compose.waitForIdle()
        val corner = Offset(10f, 10f)
        compose.onRoot().performTouchInput { click(corner) }
        compose.waitForIdle()
        assertThat(pressed).isEqualTo(0)
        assertThat(activity.touches.value).isEqualTo(0L)
        overlay.show(content = narrowSheet)
        overlay.markEdited(true)
        compose.waitForIdle()
        compose.onRoot().performTouchInput { click(corner) }
        compose.onNodeWithTag("bare").performClick()
        compose.waitForIdle()
        assertThat(overlay.content).isSameInstanceAs(narrowSheet)
        assertThat(overlay.edited).isTrue()
        assertThat(pressed).isEqualTo(0)
        assertThat(activity.touches.value).isEqualTo(0L)
    }

    /** Fails if the shell or an open sheet stays in TalkBack's tree under the door card, which a scrim can't stop. */
    @Test
    fun whatTheDoorCardCoversIsHiddenFromTalkBack() {
        compose.setContent {
            CulveryTheme(dark = true) {
                ShellLayers(
                    overlay = overlay, toast = null, onToastHidden = {}, pinShowing = false, pinPad = {},
                    activity = activity, onTouch = activity::touch, alert = alert,
                ) { Text("Sign out") }
            }
        }
        compose.waitForIdle()
        compose.onAllNodesWithText("Sign out").assertCountEquals(1)
        alert = card
        compose.waitForIdle()
        compose.onAllNodesWithText("Sign out").assertCountEquals(0)
        compose.onAllNodesWithTag("card").assertCountEquals(1)
        alert = null
        overlay.show(content = { Text("Close the sheet") })
        compose.waitForIdle()
        compose.onAllNodesWithText("Close the sheet").assertCountEquals(1)
        alert = card
        compose.waitForIdle()
        compose.onAllNodesWithText("Close the sheet").assertCountEquals(0)
        compose.assertClickablesHaveRoleAndLabel()
    }

    private fun layer(keyboard: WindowInsets) {
        compose.setContent {
            CulveryTheme(dark = true) { AlertLayer({ Box(Modifier.width(400.dp).height(200.dp).testTag("card")) }, hidden = false, keyboard = keyboard) }
        }
    }

    /** The alert keeps its own width and is centred across the screen. */
    private fun assertCentredAcross(card: DpRect, screen: DpRect) {
        assertThat((card.right - card.left).value).isWithin(0.5f).of(400f)
        assertThat(card.left.value).isWithin(0.5f).of((screen.right.value - 400f) / 2f)
    }

    /** Fails if the layer centres the card on the whole window (Got it under the keys), or sets the card's width. */
    @Test
    fun theCardStaysAboveTheKeyboardAndCentresInTheSpaceLeft() {
        val keyboard = 200.dp
        layer(WindowInsets(bottom = keyboard))
        val screen = compose.onRoot().getUnclippedBoundsInRoot()
        val card = compose.onNodeWithTag("card").getUnclippedBoundsInRoot()
        assertThat(card.bottom.value).isAtMost((screen.bottom - keyboard).value + 0.5f)
        assertThat(card.top.value).isWithin(0.5f).of(((screen.bottom - keyboard).value - (card.bottom - card.top).value) / 2f)
        assertCentredAcross(card, screen)
    }

    /** Fails if, with no keyboard, the card isn't centred on the screen at its own width. */
    @Test
    fun theCardCentresOnTheScreenWithoutAKeyboard() {
        layer(WindowInsets(0))
        val screen = compose.onRoot().getUnclippedBoundsInRoot()
        val card = compose.onNodeWithTag("card").getUnclippedBoundsInRoot()
        assertThat(card.top.value).isWithin(0.5f).of((screen.bottom.value - (card.bottom - card.top).value) / 2f)
        assertCentredAcross(card, screen)
    }
}
