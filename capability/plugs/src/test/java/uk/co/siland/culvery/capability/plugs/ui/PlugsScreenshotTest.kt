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
import uk.co.siland.culvery.capability.plugs.HOLIDAY
import uk.co.siland.culvery.capability.plugs.LAMP
import uk.co.siland.culvery.capability.plugs.ModeChoice
import uk.co.siland.culvery.capability.plugs.PlugPower
import uk.co.siland.culvery.capability.plugs.PlugPrefs
import uk.co.siland.culvery.capability.plugs.PlugState
import uk.co.siland.culvery.core.house.HouseModeInfo
import uk.co.siland.culvery.core.ui.test.snap

/** The Plugs tab in the shell's content area (12 §4.3). */
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class PlugsScreenshotTest {
    @get:Rule val compose = createComposeRule()

    private val four = listOf(LAMP_ON, FAN_OFF, FAIRY_LOST, PlugState(DESK, "Desk", PlugPower.UNKNOWN, null))
    private val long = listOf(
        PlugState(LAMP, "Fairy lights above the bookcase and the window in the long living room at the back of the house, behind the old upright piano", PlugPower.ON, 12),
        PlugState(FAN, "Fan", PlugPower.OFF, null),
    )
    private val prefs = PlugPrefs()
        .showing(LAMP).showing(FAN).showing(FAIRY).showing(DESK)
        .copy(needsPin = setOf(LAMP))
        .with(HOLIDAY, LAMP, ModeChoice.OFF)
        .with(HOLIDAY, FAN, ModeChoice.ON)

    private fun tab(name: String, dark: Boolean, plugs: List<PlugState>, editing: Boolean = false, modes: List<HouseModeInfo> = listOf(HOLIDAY_MODE)) =
        compose.snap(name, dark, Modifier.size(1116.dp, 690.dp)) {
            Box(Modifier.size(1116.dp, 690.dp)) {
                PlugsTab(
                    plugs, prefs.needsPin, emptySet(), null, modes, prefs, editing,
                    onSwitch = {}, onEdit = {}, onDone = {}, onChoice = { _, _, _ -> },
                )
            }
        }

    @Test fun rowsDark() = tab("plugs_tab_rows_dark", true, four)

    @Test fun rowsLight() = tab("plugs_tab_rows_light", false, four)

    @Test fun longNameDark() = tab("plugs_tab_long_name_dark", true, long)

    @Test fun longNameLight() = tab("plugs_tab_long_name_light", false, long)

    @Test fun longNameEditDark() = tab("plugs_tab_long_name_edit_dark", true, long, editing = true, modes = listOf(HOLIDAY_MODE, PARTY_MODE))

    @Test fun longNameEditLight() = tab("plugs_tab_long_name_edit_light", false, long, editing = true, modes = listOf(HOLIDAY_MODE, PARTY_MODE))

    @Test fun editDark() = tab("plugs_tab_edit_dark", true, four, editing = true)

    @Test fun editLight() = tab("plugs_tab_edit_light", false, four, editing = true)
}
