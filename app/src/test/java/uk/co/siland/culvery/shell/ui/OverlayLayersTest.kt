package uk.co.siland.culvery.shell.ui

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.material3.Text
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.google.common.truth.Truth.assertThat
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
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

    @Test
    fun toastHidesAfterThreeAndAHalfSeconds() {
        var toast by mutableStateOf<ToastMessage?>(null)
        compose.mainClock.autoAdvance = false
        compose.setContent {
            CulveryTheme(dark = true) { ToastLayer(toast) { id -> if (toast?.id == id) toast = null } }
        }
        toast = ToastMessage(1, "Event deleted", "info")
        compose.waitForIdle()
        compose.mainClock.advanceTimeByFrame()
        compose.onNodeWithText("Event deleted").assertExists()
        compose.mainClock.advanceTimeBy(3_000)
        compose.onNodeWithText("Event deleted").assertExists()
        compose.mainClock.advanceTimeBy(600)
        compose.mainClock.advanceTimeByFrame()
        compose.onNodeWithText("Event deleted").assertDoesNotExist()
    }

    @Test
    fun aNewToastRestartsTheTimer() {
        var toast by mutableStateOf<ToastMessage?>(ToastMessage(1, "First", "info"))
        compose.mainClock.autoAdvance = false
        compose.setContent {
            CulveryTheme(dark = true) { ToastLayer(toast) { id -> if (toast?.id == id) toast = null } }
        }
        compose.mainClock.advanceTimeBy(3_000)
        toast = ToastMessage(2, "Second", "info")
        compose.waitForIdle()
        compose.mainClock.advanceTimeBy(1_000)
        compose.mainClock.advanceTimeByFrame()
        compose.onNodeWithText("Second").assertExists()
        compose.onNodeWithText("First").assertDoesNotExist()
        // The second toast's own 3.5 s runs from about 3.0 s: still showing at 6.3 s, gone by 6.6 s.
        compose.mainClock.advanceTimeBy(2_300)
        compose.onNodeWithText("Second").assertExists()
        compose.mainClock.advanceTimeBy(300)
        compose.mainClock.advanceTimeByFrame()
        compose.onNodeWithText("Second").assertDoesNotExist()
    }
}
