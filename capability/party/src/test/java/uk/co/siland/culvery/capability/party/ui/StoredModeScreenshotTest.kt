package uk.co.siland.culvery.capability.party.ui

import androidx.compose.foundation.layout.padding
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import uk.co.siland.culvery.capability.party.ModesWords
import uk.co.siland.culvery.capability.party.PARTY_ICON
import uk.co.siland.culvery.capability.party.PartyRun
import uk.co.siland.culvery.core.ui.test.OneTapTray
import uk.co.siland.culvery.core.ui.test.snap

/** A stored mode's tile in the One-tap tray at 333 dp: off, on with lines, turning on, and with nothing to do (15b §4.3), dark and light. */
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = "w1280dp-h800dp-land-hdpi")
class StoredModeScreenshotTest {
    @get:Rule val compose = createComposeRule()

    private fun tile(name: String, dark: Boolean, on: Boolean, line: String? = null, running: PartyRun? = null) =
        compose.snap(name, dark, Modifier.padding(16.dp)) {
            OneTapTray("party") { StoredModeTile("party", "Party mode", PARTY_ICON, on, line, running) {} }
        }

    @Test fun tileOffDark() = tile("party_tile_off_dark", true, on = false)

    @Test fun tileOffLight() = tile("party_tile_off_light", false, on = false)

    @Test fun tileOnLinesDark() = tile("party_tile_on_lines_dark", true, on = true, line = LINES)

    @Test fun tileOnLinesLight() = tile("party_tile_on_lines_light", false, on = true, line = LINES)

    @Test fun tileTurningOnDark() = tile("party_tile_turning_on_dark", true, on = false, running = PartyRun.TURNING_ON)

    @Test fun tileTurningOnLight() = tile("party_tile_turning_on_light", false, on = false, running = PartyRun.TURNING_ON)

    @Test fun tileNothingDark() = tile("party_tile_nothing_dark", true, on = true, line = ModesWords.NOTHING_TO_DO)

    @Test fun tileNothingLight() = tile("party_tile_nothing_light", false, on = true, line = ModesWords.NOTHING_TO_DO)

    private companion object {
        val LINES = listOf("The music didn’t start. Check the Sonos app.", "Some lights didn’t change: Kitchen.").joinToString("\n")
    }
}
