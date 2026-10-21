package uk.co.siland.culvery.core.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
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

/** The footer enabled, then disabled with its reason; a field error; a locked chip unselected, then chosen; the count's round buttons. */
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class FormPiecesScreenshotTest {
    @get:Rule val compose = createComposeRule()

    private fun snap(name: String, dark: Boolean) {
        compose.setContent {
            CulveryTheme(dark = dark) {
                val c = Culvery.colors
                Column(
                    Modifier.testTag("shot").background(c.bg).padding(16.dp).width(549.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp),
                ) {
                    HhFooterButton("Add event", {}, "enabled", icon = Icons.CHECK)
                    HhFooterButton("Add event", {}, "disabled", enabled = false, reason = "Add a title first")
                    HhStatusLine("Someone is already called Sam.", "error", icon = Icons.ERROR, style = ControlType.fieldError)
                    HhStatusLine("Can’t reach the bridge.", "info")
                    Row(horizontalArrangement = Arrangement.spacedBy(ControlTokens.chipGap)) {
                        listOf(false, true).forEach { chosen ->
                            HhChoiceChip(
                                "Sam", selected = chosen, tag = "locked_$chosen", onClick = {}, locked = true,
                                leading = { Row(Modifier.size(ControlTokens.chipDot).background(c.accent, CircleShape)) {} },
                            )
                        }
                        HhChoiceChip("Mia", selected = false, tag = "plain", onClick = {})
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        HhRoundButton(Icons.REMOVE, "Fewer times", {}, on = ButtonSurface.Bg, iconSize = ButtonTokens.icon, repeatWhileHeld = true)
                        HhRoundButton(Icons.ADD, "More times", {}, on = ButtonSurface.Bg, iconSize = ButtonTokens.icon, repeatWhileHeld = true, enabled = false)
                    }
                }
            }
        }
        compose.onNodeWithTag("shot").captureRoboImage("src/test/screenshots/$name.png")
    }

    @Test fun formPiecesDark() = snap("form_pieces_dark", true)
    @Test fun formPiecesLight() = snap("form_pieces_light", false)
}
