package uk.co.siland.culvery.capability.party.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import uk.co.siland.culvery.capability.party.KITCHEN_BRIGHT
import uk.co.siland.culvery.capability.party.LIGHTS_BEFORE
import uk.co.siland.culvery.capability.party.LOUNGE_RELAX
import uk.co.siland.culvery.capability.party.MADE
import uk.co.siland.culvery.capability.party.PARTY_MIX
import uk.co.siland.culvery.capability.party.PartyPlan
import uk.co.siland.culvery.capability.party.PartyRecord
import uk.co.siland.culvery.capability.party.PartyWords
import uk.co.siland.culvery.core.house.PlugChange
import uk.co.siland.culvery.core.ui.HhTrayColumn
import uk.co.siland.culvery.core.ui.test.snap

/** The Party mode tile in the One-tap tray at 333 dp, and its sheet, on and off (13 §2.6), dark and light. */
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = "w1280dp-h800dp-land-hdpi")
class PartyScreenshotTest {
    @get:Rule val compose = createComposeRule()

    private fun tile(name: String, dark: Boolean, on: Boolean, line: String? = null) =
        compose.snap(name, dark, Modifier.padding(16.dp)) {
            HhTrayColumn("One-tap", listOf("party" to { PartyTile(on, line, null) {} }), Modifier.width(333.dp))
        }

    private fun sheet(name: String, dark: Boolean, turningOn: Boolean, lines: List<String>) =
        compose.snap(name, dark, Modifier.size(1_116.dp, 800.dp)) {
            Box(Modifier.size(1_116.dp, 800.dp), contentAlignment = Alignment.CenterEnd) {
                PartySheet(turningOn, lines, onConfirm = {}, onCancel = {})
            }
        }

    @Test fun tileOffDark() = tile("party_tile_off_dark", true, on = false)

    @Test fun tileOffLight() = tile("party_tile_off_light", false, on = false)

    @Test fun tileOnLinesDark() = tile("party_tile_on_lines_dark", true, on = true, line = LINES)

    @Test fun tileOnLinesLight() = tile("party_tile_on_lines_light", false, on = true, line = LINES)

    @Test fun sheetOnDark() = sheet("party_sheet_on_dark", true, true, WILL)

    @Test fun sheetOnLight() = sheet("party_sheet_on_light", false, true, WILL)

    @Test fun sheetNothingDark() = sheet("party_sheet_nothing_dark", true, true, NOTHING)

    @Test fun sheetNothingLight() = sheet("party_sheet_nothing_light", false, true, NOTHING)

    @Test fun sheetOffDark() = sheet("party_sheet_off_dark", true, false, BACK)

    @Test fun sheetOffLight() = sheet("party_sheet_off_light", false, false, BACK)

    private companion object {
        val LINES = listOf("The music didn’t start. Check the Sonos app.", "Some lights didn’t change: Kitchen.").joinToString("\n")
        val WILL = PartyWords.willLines(PartyPlan(listOf(PlugChange("Lamp", false), PlugChange("Desk", true)), PARTY_MIX, listOf(KITCHEN_BRIGHT, LOUNGE_RELAX)))
        val NOTHING = PartyWords.willLines(PartyPlan(emptyList(), null, emptyList()))
        val BACK = PartyWords.backLines(PartyRecord(on = true, made = MADE, lights = LIGHTS_BEFORE), plugsListed = true)
    }
}
