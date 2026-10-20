package uk.co.siland.culvery.core.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
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

/** The tabs' title block with a tray of two quiet buttons on its right. */
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class PageHeaderScreenshotTest {
    @get:Rule val compose = createComposeRule()

    private fun snap(name: String, dark: Boolean) {
        compose.setContent {
            CulveryTheme(dark = dark) {
                HhPageHeader(
                    title = "Lights",
                    subtitle = "2 of 4 rooms on · Hue Bridge",
                    modifier = Modifier.testTag("shot").width(900.dp).background(Culvery.colors.bg).padding(16.dp),
                ) {
                    HhTray(label = "One-tap") {
                        HhButton("All lights off", {}, on = ButtonSurface.Bg)
                        HhButton("Holiday mode", {}, on = ButtonSurface.Bg)
                    }
                }
            }
        }
        compose.onNodeWithTag("shot").captureRoboImage("src/test/screenshots/$name.png")
    }

    @Test fun headerDark() = snap("page_header_dark", true)
    @Test fun headerLight() = snap("page_header_light", false)
}
