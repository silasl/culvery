package uk.co.siland.culvery.shell.ui

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.material3.Text
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.click
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.google.common.truth.Truth.assertThat
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import uk.co.siland.culvery.capability.calendar.EventForm
import uk.co.siland.culvery.capability.calendar.ui.EditorPicker
import uk.co.siland.culvery.capability.calendar.ui.EventEditorSheet
import uk.co.siland.culvery.core.plugin.test.FakeUserActivity
import uk.co.siland.culvery.core.ui.CulveryTheme
import uk.co.siland.culvery.core.ui.HhSheet
import uk.co.siland.culvery.shell.OverlayState
import uk.co.siland.culvery.shell.ToastMessage

@RunWith(AndroidJUnit4::class)
class OverlayLayersTest {
    @get:Rule val compose = createComposeRule()
    private val overlay = OverlayState()

    private fun showLayer() = compose.setContent { CulveryTheme(dark = true) { OverlayLayer(overlay) } }

    @Test
    fun nothingIsDrawnUntilSomethingIsShown() {
        showLayer()
        compose.onNodeWithTag("overlay_scrim").assertDoesNotExist()
        assertThat(overlay.isShowing).isFalse()
    }

    @Test
    fun tappingTheScrimDismissesTheContent() {
        showLayer()
        compose.runOnIdle { overlay.show { Text("Sheet body") } }
        compose.onNodeWithText("Sheet body").assertExists()
        compose.onNodeWithTag("overlay_scrim").performClick()
        compose.onNodeWithText("Sheet body").assertDoesNotExist()
        assertThat(overlay.isShowing).isFalse()
    }

    @Test
    fun tapsOnTheSheetDoNotDismissIt() {
        showLayer()
        compose.runOnIdle { overlay.show { HhSheet(padding = PaddingValues(0.dp)) { Text("Sheet body") } } }
        compose.onNodeWithText("Sheet body").performClick()
        assertThat(overlay.isShowing).isTrue()
    }

    @Test
    fun showReplacesTheCurrentContentAndDismissHidesIt() {
        showLayer()
        compose.runOnIdle { overlay.show { Text("First") } }
        compose.runOnIdle { overlay.show { Text("Second") } }
        compose.onNodeWithText("First").assertDoesNotExist()
        compose.onNodeWithText("Second").assertExists()
        compose.runOnIdle { overlay.dismiss() }
        compose.onNodeWithText("Second").assertDoesNotExist()
    }

    private val activity = FakeUserActivity()
    private var toast by mutableStateOf<ToastMessage?>(null)

    private fun showToasts() {
        compose.mainClock.autoAdvance = false
        compose.setContent { CulveryTheme(dark = true) { ToastLayer(toast, activity) { id -> if (toast?.id == id) toast = null } } }
    }

    private fun post(message: ToastMessage) {
        toast = message
        compose.waitForIdle()
        compose.mainClock.advanceTimeByFrame()
    }

    private fun shownAt(millis: Long, text: String): Boolean {
        compose.mainClock.advanceTimeBy(millis)
        compose.mainClock.advanceTimeByFrame()
        return compose.onAllNodesWithText(text).fetchSemanticsNodes().isNotEmpty()
    }

    @Test
    fun aPlainToastLastsFourSeconds() {
        showToasts()
        post(ToastMessage(1, "Event deleted.", "info"))
        assertThat(shownAt(3_880, "Event deleted.")).isTrue()
        assertThat(shownAt(120, "Event deleted.")).isFalse()
    }

    @Test
    fun anUrgentToastLastsSixSeconds() {
        showToasts()
        post(ToastMessage(1, "Couldn’t save — try again.", "info", urgent = true))
        assertThat(shownAt(5_880, "Couldn’t save — try again.")).isTrue()
        assertThat(shownAt(120, "Couldn’t save — try again.")).isFalse()
    }

    @Test
    fun withTalkBacksLongerTimeAToastStaysForIt() {
        activity.timeouts = { base, hasControls -> if (hasControls) base else 20_000L }
        showToasts()
        post(ToastMessage(1, "Event deleted.", "info"))
        assertThat(shownAt(19_880, "Event deleted.")).isTrue()
        assertThat(shownAt(120, "Event deleted.")).isFalse()
    }

    /** The plain toast that replaces an urgent one lasts its own 4 s and reads politely. */
    @Test
    fun aPlainToastAfterAnUrgentOneKeepsItsOwnTimeAndPoliteness() {
        showToasts()
        post(ToastMessage(1, "Couldn’t save — try again.", "info", urgent = true))
        compose.mainClock.advanceTimeBy(1_000)
        post(ToastMessage(2, "Event added.", "info"))
        compose.onNodeWithTag("toast").assert(SemanticsMatcher.expectValue(SemanticsProperties.LiveRegion, LiveRegionMode.Polite))
        assertThat(shownAt(3_880, "Event added.")).isTrue()
        assertThat(shownAt(120, "Event added.")).isFalse()
    }

    @Test
    fun theToastSitsAboveTheKeyboard() {
        val keyboard = 320.dp
        compose.setContent {
            CulveryTheme(dark = true) {
                ToastLayer(ToastMessage(1, "Mia can only add events for themselves.", "info"), activity, keyboard = WindowInsets(bottom = keyboard)) {}
            }
        }
        val screen = compose.onRoot().getUnclippedBoundsInRoot()
        val toast = compose.onNodeWithTag("toast").getUnclippedBoundsInRoot()
        // Hand-off §7: 28 dp above the keyboard, which puts it about 340 dp from the bottom.
        assertThat(toast.bottom.value).isWithin(0.5f).of((screen.bottom - keyboard - 28.dp).value)
    }

    @Test
    fun tappingTheScrimClosesTheAddEditSheet() {
        showLayer()
        val form = EventForm(EventForm.Mode.New, LocalDate.of(2026, 9, 23), LocalTime.of(11, 54), ZoneId.of("Europe/London"), null, null)
        compose.runOnIdle {
            overlay.show {
                EventEditorSheet(
                    form = form, people = emptyList(), childOnly = null, busy = false, failure = null,
                    picker = EditorPicker.None, onClose = overlay::dismiss, onSave = {}, onDelete = {},
                    onRefusedWho = {}, onPicker = {}, focusTitleOnOpen = false,
                )
            }
        }
        compose.onNodeWithTag("editor_sheet").assertExists()
        // The scrim's top-left corner is clear of the sheet against the right edge.
        compose.onNodeWithTag("overlay_scrim").performTouchInput { click(Offset(10f, 10f)) }
        compose.onNodeWithTag("editor_sheet").assertDoesNotExist()
        assertThat(overlay.isShowing).isFalse()
    }

    @Test
    fun theScrimIsSilent() {
        showLayer()
        compose.runOnIdle { overlay.show { Text("Sheet body") } }
        compose.onNodeWithTag("overlay_scrim").assert(SemanticsMatcher.keyNotDefined(SemanticsActions.OnClick))
    }

    @Test
    fun theToastLayerSpeaksAnUrgentToastAssertively() {
        compose.setContent { CulveryTheme(dark = true) { ToastLayer(ToastMessage(1, "Couldn’t save — try again.", "info", urgent = true), activity) {} } }
        compose.onNodeWithTag("toast").assert(SemanticsMatcher.expectValue(SemanticsProperties.LiveRegion, LiveRegionMode.Assertive))
    }
}
