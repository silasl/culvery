package uk.co.siland.culvery.core.setup

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.github.takahirom.roborazzi.captureRoboImage
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.GraphicsMode
import uk.co.siland.culvery.core.ui.Culvery
import uk.co.siland.culvery.core.ui.CulveryTheme

@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class SetupScreenshotTest {
    @get:Rule val compose = createComposeRule()

    private fun snap(name: String, dark: Boolean, content: @Composable BoxScope.() -> Unit) {
        compose.setContent {
            CulveryTheme(dark = dark) {
                Box(Modifier.testTag("shot").size(CANVAS_W, CANVAS_H).background(Culvery.colors.bg)) { content() }
            }
        }
        compose.onNodeWithTag("shot").captureRoboImage("src/test/screenshots/$name.png")
    }

    private fun frame(name: String, dark: Boolean) = snap(name, dark) {
        WizardFrame(
            dotCount = 6,
            dotIndex = 2,
            back = {},
            forward = Forward.Next("Next", enabled = false),
            busy = false,
            onForward = {},
            onSkip = {},
        ) {
            StepTitle("Who's setting this up?", "The step's content goes here.")
        }
    }

    @Test fun wizardFrameDark() = frame("wizard_frame_dark", true)
    @Test fun wizardFrameLight() = frame("wizard_frame_light", false)

    @Test
    fun wizardPinGateDark() = snap("wizard_pin_gate_dark", true) { PinGateContent(busy = false, onEnterPin = {}) }
}
