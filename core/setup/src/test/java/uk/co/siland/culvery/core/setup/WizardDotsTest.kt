package uk.co.siland.culvery.core.setup

import android.graphics.Bitmap
import android.graphics.Canvas
import android.view.View
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.test.assertContentDescriptionEquals
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.google.common.truth.Truth.assertThat
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.GraphicsMode
import uk.co.siland.culvery.core.ui.Culvery
import uk.co.siland.culvery.core.ui.CulveryTheme
import uk.co.siland.culvery.core.ui.LightColors

/** Outline against fill tells the current step without colour (4d-1 §5.4). Real graphics: this reads pixels. */
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class WizardDotsTest {
    @get:Rule val compose = createComposeRule()

    @Test
    fun theCurrentStepIsAFilledDotAndTheOthersAreRings() {
        lateinit var window: View
        compose.setContent {
            window = LocalView.current.rootView
            CulveryTheme(dark = false) {
                Box(Modifier.fillMaxSize().background(Culvery.colors.bg)) {
                    WizardFrame(6, 2, back = {}, forward = Forward.Next("Next", enabled = true), busy = false, onForward = {}, onSkip = {}) {}
                }
            }
        }
        compose.waitForIdle()
        // captureToImage waits on a frame callback that Robolectric's paused main looper never delivers, so draw the window.
        val bitmap = Bitmap.createBitmap(window.width, window.height, Bitmap.Config.ARGB_8888)
        window.draw(Canvas(bitmap))
        fun centre(i: Int): Int {
            val bounds = compose.onAllNodesWithTag("wizard_dot")[i].fetchSemanticsNode().boundsInWindow
            return bitmap.getPixel(bounds.center.x.toInt(), bounds.center.y.toInt())
        }
        assertThat(centre(2)).isEqualTo(LightColors.accent.toArgb())
        assertThat(centre(0)).isEqualTo(LightColors.bg.toArgb())
    }

    @Test
    fun theDotsAreOneSpokenStep() {
        compose.setContent {
            CulveryTheme(dark = false) {
                WizardFrame(7, 0, back = null, forward = Forward.Next("Start", enabled = true), busy = false, onForward = {}, onSkip = {}) {}
            }
        }
        compose.onNodeWithTag("wizard_dots").assertContentDescriptionEquals("Step 1 of 7")
    }
}
