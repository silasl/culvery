package uk.co.siland.culvery.capability.party.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.github.takahirom.roborazzi.captureRoboImage
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import uk.co.siland.culvery.core.plugin.LocalOverlayHost
import uk.co.siland.culvery.core.plugin.test.TestOverlay
import uk.co.siland.culvery.core.ui.Culvery
import uk.co.siland.culvery.core.ui.CulveryTheme
import uk.co.siland.culvery.core.ui.Icons
import uk.co.siland.culvery.core.ui.test.snap

/** Settings › Modes in the 960 dp pane beside the page list, and the Add a mode sheet at the right edge (15b §4.1). */
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = "w1280dp-h800dp-land-hdpi")
class ModesScreenshotTest {
    @get:Rule val compose = createComposeRule()

    private val rows = listOf(
        ModeRowUi("holiday", Icons.BEACH_ACCESS, "Holiday mode", "Built in · On Home · PIN · Lights, heating, hoover, plugs, door chime"),
        ModeRowUi("party", Icons.CELEBRATION, "Party mode", "On · On Home · PIN · Sunday morning on 3 speakers · 2 rooms · 1 plug"),
        ModeRowUi("modeabcdef", Icons.MOVIE, "Film night", "Not on Home · No PIN · Nothing chosen yet"),
    )

    private fun list(name: String, dark: Boolean) = compose.snap(name, dark, Modifier.size(960.dp, 800.dp)) {
        CompositionLocalProvider(LocalOverlayHost provides TestOverlay()) {
            InSettingsPane { ModesList(rows, onOpen = {}, onAdd = {}) }
        }
    }

    private fun sheet(name: String, dark: Boolean, steps: () -> Unit) {
        compose.setContent {
            CulveryTheme(dark = dark) {
                CompositionLocalProvider(LocalOverlayHost provides TestOverlay()) {
                    Box(Modifier.background(Culvery.colors.bg).size(960.dp, 800.dp), contentAlignment = Alignment.CenterEnd) {
                        AddModeSheet(listOf("Party mode"), { _, _ -> }, {})
                    }
                }
            }
        }
        steps()
        compose.onRoot().captureRoboImage("src/test/screenshots/$name.png")
    }

    private fun typeAName() = compose.onNodeWithTag("mode_name").performTextInput("Film night")

    private fun toTheIcons() {
        typeAName()
        compose.onNodeWithTag("mode_name_next").performClick()
        compose.onNodeWithTag("mode_icon_movie").performClick()
    }

    @Test fun listDark() = list("modes_list_dark", true)

    @Test fun listLight() = list("modes_list_light", false)

    @Test fun addNameDark() = sheet("modes_add_name_dark", true, ::typeAName)

    @Test fun addIconDark() = sheet("modes_add_icon_dark", true, ::toTheIcons)
}
