package uk.co.siland.culvery.capability.party.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.github.takahirom.roborazzi.captureRoboImage
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancel
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import uk.co.siland.culvery.capability.party.ModeRun
import uk.co.siland.culvery.capability.party.ModesPrefs
import uk.co.siland.culvery.capability.party.ModesWords
import uk.co.siland.culvery.capability.party.MusicChoice
import uk.co.siland.culvery.capability.party.PARTY_ICON
import uk.co.siland.culvery.capability.party.PARTY_MODE
import uk.co.siland.culvery.core.house.AddedPlug
import uk.co.siland.culvery.core.house.HouseModes
import uk.co.siland.culvery.core.house.LightChoice
import uk.co.siland.culvery.core.house.PlugOption
import uk.co.siland.culvery.core.house.RoomChoice
import uk.co.siland.culvery.core.plugin.BuiltInModePage
import uk.co.siland.culvery.core.plugin.LocalOverlayHost
import uk.co.siland.culvery.core.plugin.test.RecordingToaster
import uk.co.siland.culvery.core.plugin.test.TestOverlay
import uk.co.siland.culvery.core.ui.ButtonSurface
import uk.co.siland.culvery.core.ui.ButtonTone
import uk.co.siland.culvery.core.ui.Culvery
import uk.co.siland.culvery.core.ui.CulveryTheme
import uk.co.siland.culvery.core.ui.HhButton
import uk.co.siland.culvery.core.ui.HhSectionLabel
import uk.co.siland.culvery.core.ui.test.OneTapTray
import uk.co.siland.culvery.core.ui.test.snap
import uk.co.siland.culvery.core.ui.test.waitFor

/**
 * A stored mode's tile in the One-tap tray at 333 dp: off, on with lines, turning on, and with nothing to do (15b §4.3), dark and light.
 * Its page (15b §4.2): Party mode's in both themes, the light choice sheet and the Delete card, in 960 dp.
 */
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = "w1280dp-h800dp-land-hdpi")
class StoredModeScreenshotTest {
    @get:Rule val compose = createComposeRule()

    private fun tile(name: String, dark: Boolean, on: Boolean, line: String? = null, running: ModeRun? = null) =
        compose.snap(name, dark, Modifier.padding(16.dp)) {
            OneTapTray("party", line = line, lineTag = storedModeTileLineTag("party")) { StoredModeTile("party", "Party mode", PARTY_ICON, on, running) {} }
        }

    @Test fun tileOffDark() = tile("party_tile_off_dark", true, on = false)

    @Test fun tileOffLight() = tile("party_tile_off_light", false, on = false)

    @Test fun tileOnLinesDark() = tile("party_tile_on_lines_dark", true, on = true, line = LINES)

    @Test fun tileOnLinesLight() = tile("party_tile_on_lines_light", false, on = true, line = LINES)

    @Test fun tileTurningOnDark() = tile("party_tile_turning_on_dark", true, on = false, running = ModeRun.TURNING_ON)

    @Test fun tileTurningOnLight() = tile("party_tile_turning_on_light", false, on = false, running = ModeRun.TURNING_ON)

    @Test fun tileNothingDark() = tile("party_tile_nothing_dark", true, on = true, line = ModesWords.NOTHING_TO_DO)

    @Test fun tileNothingLight() = tile("party_tile_nothing_light", false, on = true, line = ModesWords.NOTHING_TO_DO)

    private val scope = CoroutineScope(Job())
    private val toaster = RecordingToaster()

    @After fun stop() = scope.cancel()

    /** Party mode with music, three kinds of light and two plugs, one of them locked, on its own page. */
    private fun pagePrepared(): PageRig {
        val rig = PageRig(scope)
        rig.plugs.offered = listOf(PlugOption("lamp", "Lamp", false), PlugOption("heater", "Heater", true))
        rig.plugs.addedNow.value = listOf(AddedPlug("lamp", on = true, needsPin = false), AddedPlug("heater", on = false, needsPin = true))
        val party = PARTY_MODE.copy(
            music = MusicChoice(MIX.id, MIX.name),
            lights = listOf(
                RoomChoice("r1", "Kitchen", LightChoice.Scene("s2", "Relax")),
                RoomChoice("r2", "Lounge", LightChoice.On(60)),
                RoomChoice("r3", "Hall", LightChoice.Off),
            ),
        )
        runBlocking { rig.settings.update { ModesPrefs(true, listOf(party)) } }
        rig.loaded()
        return rig
    }

    private fun page(name: String, dark: Boolean, rig: PageRig = pagePrepared(), steps: () -> Unit = {}) {
        val overlay = TestOverlay()
        val page = rig.page(toaster)
        compose.setContent {
            CulveryTheme(dark = dark) {
                CompositionLocalProvider(LocalOverlayHost provides overlay) {
                    Box(Modifier.background(Culvery.colors.bg).size(960.dp, 1360.dp)) {
                        InSettingsPane { page.Content() }
                    }
                }
            }
        }
        compose.waitFor { compose.onAllNodesWithTag("modes_add").fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithTag("mode_row_party").performClick()
        compose.waitFor { compose.onAllNodesWithTag("plug_row_1").fetchSemanticsNodes().isNotEmpty() && compose.onAllNodesWithTag("light_row_2").fetchSemanticsNodes().isNotEmpty() }
        steps()
        compose.onRoot().captureRoboImage("src/test/screenshots/$name.png")
    }

    @Test
    @Config(qualifiers = "w1000dp-h1450dp-port-hdpi")
    fun partyPageDark() = page("stored_mode_party_page_dark", true)

    @Test
    @Config(qualifiers = "w1000dp-h1450dp-port-hdpi")
    fun partyPageLight() = page("stored_mode_party_page_light", false)

    @Test
    @Config(qualifiers = "w1000dp-h1450dp-port-hdpi")
    fun deleteConfirmDark() = page("stored_mode_delete_confirm_dark", true) {
        compose.onNodeWithTag("mode_delete").performScrollTo().performClick()
        compose.waitFor { compose.onAllNodesWithTag("mode_delete_confirm").fetchSemanticsNodes().isNotEmpty() }
    }

    /** Holiday mode's page with stand-ins for its own parts, plus the plugs section; the real parts are in the holiday module's shots. */
    private fun holidayPage(name: String, dark: Boolean) {
        val rig = PageRig(scope)
        rig.plugs.offered = listOf(PlugOption("lamp", "Lamp", false))
        rig.plugs.addedNow.value = listOf(AddedPlug("lamp", on = true, needsPin = false))
        runBlocking { rig.settings.update { ModesPrefs(true, listOf(PARTY_MODE)) } }
        rig.loaded()
        val parts = object : BuiltInModePage {
            override val modeId = HouseModes.HOLIDAY

            @Composable
            override fun TurnButton() = HhButton("Turn on now", {}, tone = ButtonTone.Primary, on = ButtonSurface.Bg)

            @Composable
            override fun TurnLine() = Unit

            @Composable
            override fun Sections() = HhSectionLabel("Lights while away")
        }
        val overlay = TestOverlay()
        val page = rig.page(toaster, setOf(parts))
        compose.setContent {
            CulveryTheme(dark = dark) {
                CompositionLocalProvider(LocalOverlayHost provides overlay) {
                    Box(Modifier.background(Culvery.colors.bg).size(960.dp, 900.dp)) { InSettingsPane { page.Content() } }
                }
            }
        }
        compose.waitFor { compose.onAllNodesWithTag("modes_add").fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithTag("mode_row_holiday").performClick()
        compose.waitFor { compose.onAllNodesWithTag("plug_row_0").fetchSemanticsNodes().isNotEmpty() }
        compose.onRoot().captureRoboImage("src/test/screenshots/$name.png")
    }

    @Test
    @Config(qualifiers = "w1000dp-h950dp-port-hdpi")
    fun holidayPageDark() = holidayPage("holiday_mode_page_dark", true)

    @Test
    @Config(qualifiers = "w1000dp-h950dp-port-hdpi")
    fun holidayPageLight() = holidayPage("holiday_mode_page_light", false)

    @Test
    fun lightChoiceSheetDark() {
        compose.setContent {
            CulveryTheme(dark = true) {
                Box(Modifier.background(Culvery.colors.bg).size(960.dp, 800.dp), contentAlignment = Alignment.CenterEnd) {
                    LightChoiceSheet(LIGHT_OPTIONS.rooms[0], LightChoice.On(60), {}, {})
                }
            }
        }
        compose.onRoot().captureRoboImage("src/test/screenshots/stored_mode_light_sheet_dark.png")
    }

    private companion object {
        val LINES = listOf("The music didn’t start. Check the Sonos app.", "Some lights didn’t change: Kitchen.").joinToString("\n")
    }
}
