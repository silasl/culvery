package uk.co.siland.culvery.shell.ui

import android.graphics.Bitmap
import android.graphics.Canvas
import android.view.View
import android.view.ViewGroup
import androidx.activity.ComponentActivity
import androidx.compose.material3.Text
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.google.common.truth.Truth.assertThat
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Shadows.shadowOf
import uk.co.siland.culvery.core.ui.CulveryTheme
import uk.co.siland.culvery.core.ui.ShellTokens
import uk.co.siland.culvery.shell.OverlayState
import uk.co.siland.culvery.shell.ToastMessage

/**
 * Removing a layer must invalidate the Compose view, or the window draws no new frame and the layer's pixels stay
 * on screen. A layer emitted straight into the composition root only gets `requestLayout()`, never `invalidate()`.
 * Robolectric never draws the window itself, so each test draws once by hand first, as a real frame would.
 */
@RunWith(AndroidJUnit4::class)
class ShellLayersRedrawTest {
    @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()
    private val overlay = OverlayState()
    private var toast by mutableStateOf<ToastMessage?>(null)

    private fun showShell() = compose.setContent {
        CulveryTheme(dark = true) {
            ShellLayers(
                overlay = overlay,
                toast = toast,
                onToastHidden = { id -> if (toast?.id == id) toast = null },
                pinPad = {},
                shell = { Text("Shell") },
            )
        }
    }

    /** The AndroidComposeView inside setContent's ComposeView. */
    private fun composeView(): View {
        val content = compose.activity.findViewById<ViewGroup>(android.R.id.content)
        return (content.getChildAt(0) as ViewGroup).getChildAt(0)
    }

    /** Draws a frame by hand, as the window would, then forgets the invalidations so far. */
    private fun drawAndForgetInvalidations() = compose.runOnIdle {
        val view = composeView()
        view.draw(Canvas(Bitmap.createBitmap(view.width, view.height, Bitmap.Config.ARGB_8888)))
        shadowOf(view).clearWasInvalidated()
    }

    private fun wasInvalidated() = compose.runOnIdle { shadowOf(composeView()).wasInvalidated() }

    @Test
    fun closingTheSheetByTheScrimInvalidatesTheView() {
        showShell()
        compose.runOnIdle { overlay.show { Text("Sheet body") } }
        compose.onNodeWithText("Sheet body").assertExists()
        drawAndForgetInvalidations()

        compose.onNodeWithTag("overlay_scrim").performClick()
        compose.onNodeWithText("Sheet body").assertDoesNotExist()

        assertThat(wasInvalidated()).isTrue()
    }

    @Test
    fun aToastTimingOutInvalidatesTheView() {
        showShell()
        compose.runOnIdle { toast = ToastMessage(1, "Event deleted", "info") }
        compose.onNodeWithText("Event deleted").assertExists()
        drawAndForgetInvalidations()

        compose.mainClock.advanceTimeBy(ShellTokens.TOAST_MILLIS + 100)
        compose.onNodeWithText("Event deleted").assertDoesNotExist()

        assertThat(wasInvalidated()).isTrue()
    }
}
