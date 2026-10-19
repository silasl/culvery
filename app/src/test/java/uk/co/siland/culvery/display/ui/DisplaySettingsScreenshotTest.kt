package uk.co.siland.culvery.display.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.github.takahirom.roborazzi.captureRoboImage
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import uk.co.siland.culvery.core.ui.Culvery
import uk.co.siland.culvery.core.ui.CulveryTheme
import uk.co.siland.culvery.display.CameraState
import uk.co.siland.culvery.display.DisplayPrefs

/** Settings › Display as Settings shows it: in the page area beside the 320 dp page list. */
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = "w1280dp-h1000dp-land-hdpi")
class DisplaySettingsScreenshotTest {
    @get:Rule val compose = createComposeRule()

    private val actions = DisplaySettingsActions({ _, _ -> }, {}, {}, {}, {}, {}, {})

    private fun snap(name: String, dark: Boolean, camera: CameraState) {
        compose.setContent {
            CulveryTheme(dark = dark) {
                Box(Modifier.size(960.dp, 1000.dp).background(Culvery.colors.bg)) {
                    InSettingsPane { DisplaySettingsScreen(DisplaySettingsUi(DisplayPrefs(), camera, sunKnown = true), actions) }
                }
            }
        }
        compose.onRoot().captureRoboImage("src/test/screenshots/$name.png")
    }

    @Test fun allowedDark() = snap("settings_display_allowed_dark", true, CameraState.ALLOWED)

    @Test fun allowedLight() = snap("settings_display_allowed_light", false, CameraState.ALLOWED)

    @Test fun notAskedDark() = snap("settings_display_not_asked_dark", true, CameraState.NOT_ASKED)

    @Test fun refusedDark() = snap("settings_display_refused_dark", true, CameraState.REFUSED)
}
