package uk.co.siland.culvery.capability.calendar.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.github.takahirom.roborazzi.captureRoboImage
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.GraphicsMode
import uk.co.siland.culvery.core.ui.Culvery
import uk.co.siland.culvery.core.ui.CulveryTheme
import uk.co.siland.culvery.core.ui.ShellTokens

/** The canvas the connecting card sits on. */
private val CANVAS_W = 1280.dp
private val CANVAS_H = 800.dp

@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class ConnectScreenshotTest {
    @get:Rule val compose = createComposeRule()

    private fun snap(name: String, dark: Boolean, content: @Composable () -> Unit) {
        compose.setContent { CulveryTheme(dark = dark) { content() } }
        compose.onNodeWithTag("shot").captureRoboImage("src/test/screenshots/$name.png")
    }

    private fun connecting(name: String, dark: Boolean) = snap(name, dark) {
        Box(Modifier.testTag("shot").size(CANVAS_W, CANVAS_H).background(Culvery.colors.bg)) {
            Box(Modifier.fillMaxSize().background(ShellTokens.sheetScrim))
            ConnectingCard("Google Calendar", onCancel = {})
        }
    }

    @Test fun connectingDark() = connecting("connecting_dark", true)
    @Test fun connectingLight() = connecting("connecting_light", false)
}
