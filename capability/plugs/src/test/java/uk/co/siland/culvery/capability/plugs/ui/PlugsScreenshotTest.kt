package uk.co.siland.culvery.capability.plugs.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
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
import uk.co.siland.culvery.capability.plugs.PlugCandidates
import uk.co.siland.culvery.capability.plugs.PlugPower
import uk.co.siland.culvery.capability.plugs.PlugPrefs
import uk.co.siland.culvery.capability.plugs.PlugState
import uk.co.siland.culvery.core.ui.test.snap

/** The Plugs tab in the shell's content area, and Add a plug beside it (12 §4.3). */
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class PlugsScreenshotTest {
    @get:Rule val compose = createComposeRule()

    private val four = listOf(LAMP_ON, FAN_OFF, FAIRY_LOST, PlugState(DESK, "Desk", PlugPower.OFF, null))
    private val prefs = PlugPrefs()
        .adding(LAMP).adding(FAN).adding(FAIRY).adding(DESK)
        .copy(needsPin = setOf(FAN))
        .with(HOLIDAY, LAMP, ModeChoice.OFF)
        .with(HOLIDAY, DESK, ModeChoice.ON)

    @Composable
    private fun Tab(plugs: List<PlugState>, editing: Boolean = false) = Box(Modifier.size(1116.dp, 690.dp)) {
        PlugsTab(
            plugs, prefs.needsPin, setOf(DESK).takeIf { plugs.size == 4 }.orEmpty(), null, listOf(HOLIDAY_MODE), prefs, editing,
            onSwitch = {}, onSettings = {}, onAdd = {}, onEdit = {}, onDone = {}, onChoice = { _, _, _ -> },
        )
    }

    private fun tab(name: String, dark: Boolean, plugs: List<PlugState>, editing: Boolean = false) =
        compose.snap(name, dark, Modifier.size(1116.dp, 690.dp)) { Tab(plugs, editing) }

    private fun addSheet(name: String, dark: Boolean) = compose.snap(name, dark, Modifier.size(1280.dp, 800.dp)) {
        Tab(four.take(1))
        Box(Modifier.size(1280.dp, 800.dp), contentAlignment = Alignment.CenterEnd) {
            AddPlugSheet(PlugCandidates.Found(CANDIDATES), added = listOf(LAMP), onPick = {}, onClose = {})
        }
    }

    @Test fun emptyDark() = tab("plugs_tab_empty_dark", true, emptyList())

    @Test fun emptyLight() = tab("plugs_tab_empty_light", false, emptyList())

    @Test fun fourDark() = tab("plugs_tab_four_dark", true, four)

    @Test fun fourLight() = tab("plugs_tab_four_light", false, four)

    @Test fun editDark() = tab("plugs_tab_edit_dark", true, four, editing = true)

    @Test fun editLight() = tab("plugs_tab_edit_light", false, four, editing = true)

    @Test fun addSheetDark() = addSheet("plugs_add_sheet_dark", true)

    @Test fun addSheetLight() = addSheet("plugs_add_sheet_light", false)
}
