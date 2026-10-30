package uk.co.siland.culvery.capability.plugs.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.GraphicsMode
import uk.co.siland.culvery.capability.plugs.DESK
import uk.co.siland.culvery.capability.plugs.FAN
import uk.co.siland.culvery.capability.plugs.PlugPower
import uk.co.siland.culvery.capability.plugs.PlugState
import uk.co.siland.culvery.core.ui.test.snap

/** The plug tiles as the Lights tab draws them (15a §4.5). */
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class PlugsScreenshotTest {
    @get:Rule val compose = createComposeRule()

    // An on plug, an unreachable one, a checking one, and an off one that needs a PIN.
    private val plugs = listOf(LAMP_ON, FAIRY_LOST, PlugState(DESK, "Desk", PlugPower.UNKNOWN, null), FAN_OFF)

    private fun tiles(name: String, dark: Boolean) = compose.snap(name, dark, Modifier.size(1116.dp, 170.dp)) {
        Box(Modifier.size(1116.dp, 170.dp)) {
            PlugsSectionBody(plugs, needsPin = setOf(FAN), switching = emptySet(), problem = null, onSwitch = {})
        }
    }

    @Test fun tilesDark() = tiles("plugs_tiles_dark", true)

    @Test fun tilesLight() = tiles("plugs_tiles_light", false)
}
