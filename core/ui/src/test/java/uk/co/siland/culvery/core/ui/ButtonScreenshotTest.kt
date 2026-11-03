package uk.co.siland.culvery.core.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
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

/** Tones down; Regular, Regular with an icon and Large across, each enabled then disabled; on `bg`, then on `surf`. */
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class ButtonScreenshotTest {
    @get:Rule val compose = createComposeRule()

    @Composable
    private fun Panel(on: ButtonSurface, background: Color) {
        Column(verticalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.background(background).padding(16.dp)) {
            ButtonTone.entries.forEach { tone ->
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
                    listOf(true, false).forEach { enabled ->
                        HhButton("Next", {}, tone = tone, enabled = enabled, on = on)
                        HhButton("Add event", {}, tone = tone, icon = Icons.ADD, enabled = enabled, on = on)
                        HhButton("Save", {}, tone = tone, size = ButtonSize.Large, icon = Icons.CHECK, enabled = enabled, on = on)
                    }
                }
            }
        }
    }

    private fun snap(name: String, dark: Boolean) {
        compose.setContent {
            CulveryTheme(dark = dark) {
                val c = Culvery.colors
                Column(Modifier.testTag("shot").background(c.bg)) {
                    Panel(ButtonSurface.Bg, c.bg)
                    Panel(ButtonSurface.Surf, c.surf)
                }
            }
        }
        compose.onNodeWithTag("shot").captureRoboImage("src/test/screenshots/$name.png")
    }

    @Composable
    private fun BirthdayStrip(colour: Long, dark: Boolean) {
        CulveryTheme(dark = dark, birthday = Birthday(colour, listOf("Sam"))) {
            val c = Culvery.colors
            Row(Modifier.background(c.bg).padding(16.dp), horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
                HhButton("Next", {}, tone = ButtonTone.Primary, on = ButtonSurface.Bg)
                HhButton("Back", {}, tone = ButtonTone.Quiet, on = ButtonSurface.Bg)
                Column(Modifier.background(c.surf).padding(12.dp)) {
                    Text("Ink on surf", color = c.ink)
                    Text("Mute on surf", color = c.mute)
                }
                Box(Modifier.background(c.accentSoft, RoundedCornerShape(50)).padding(horizontal = 14.dp, vertical = 6.dp)) {
                    Text("Happy birthday Sam", color = c.ink)
                }
            }
        }
    }

    private fun birthdays(name: String, dark: Boolean) {
        compose.setContent {
            Column(Modifier.testTag("shot")) { PersonPalette.colors.forEach { BirthdayStrip(it, dark) } }
        }
        compose.onNodeWithTag("shot").captureRoboImage("src/test/screenshots/$name.png")
    }

    @Test fun birthdayColoursDark() = birthdays("birthday_colours_dark", true)
    @Test fun birthdayColoursLight() = birthdays("birthday_colours_light", false)

    @Test fun matrixDark() = snap("button_matrix_dark", true)
    @Test fun matrixLight() = snap("button_matrix_light", false)
}
