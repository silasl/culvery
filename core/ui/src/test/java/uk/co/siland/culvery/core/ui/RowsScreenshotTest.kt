package uk.co.siland.culvery.core.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
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

/** The one row look: a list row with a subtitle and a trailing icon, then switch rows on, off and disabled-on. */
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class RowsScreenshotTest {
    @get:Rule val compose = createComposeRule()

    private fun snap(name: String, dark: Boolean) {
        compose.setContent {
            CulveryTheme(dark = dark) {
                Column(
                    Modifier.testTag("shot").background(Culvery.colors.bg).padding(16.dp).width(549.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    HhListRow("Alex", subtitle = "Admin · PIN set", onClick = {}, clickLabel = "change Alex’s details", trailing = {
                        HhIcon(Icons.CHEVRON_RIGHT, size = ListRowTokens.icon, tint = Culvery.colors.mute)
                    })
                    HhListRow("Sam", minHeight = 75.dp)
                    HhSwitchRow("Night clock", "A dim clock after dark.", checked = true, tag = "on", onChange = {})
                    HhSwitchRow("Wake by camera", null, checked = false, tag = "off", onChange = {})
                    HhSwitchRow("Always shown", null, checked = true, tag = "disabled_on", onChange = {}, enabled = false)
                }
            }
        }
        compose.onNodeWithTag("shot").captureRoboImage("src/test/screenshots/$name.png")
    }

    @Test fun rowsDark() = snap("rows_dark", true)
    @Test fun rowsLight() = snap("rows_light", false)
}
