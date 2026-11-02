package uk.co.siland.culvery.capability.party.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertIsOff
import androidx.compose.ui.test.assertIsOn
import androidx.compose.ui.test.assertTextContains
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import uk.co.siland.culvery.capability.party.ModesPrefs
import uk.co.siland.culvery.capability.party.PARTY_MODE
import uk.co.siland.culvery.core.house.AddedPlug
import uk.co.siland.culvery.core.house.HouseModes
import uk.co.siland.culvery.core.house.PlugOption
import uk.co.siland.culvery.core.plugin.BuiltInModePage
import uk.co.siland.culvery.core.plugin.LocalOverlayHost
import uk.co.siland.culvery.core.plugin.test.RecordingToaster
import uk.co.siland.culvery.core.plugin.test.TestOverlay
import uk.co.siland.culvery.core.ui.CulveryTheme
import uk.co.siland.culvery.core.ui.test.assertClickablesHaveRoleAndLabel
import uk.co.siland.culvery.core.ui.test.waitFor

private val LAMP = PlugOption("lamp", "Lamp", needsPin = false)

/** Holiday mode's own parts, as Holiday's capability draws them. */
private val FAKE_HOLIDAY = object : BuiltInModePage {
    override val modeId = HouseModes.HOLIDAY

    @Composable
    override fun TurnButton() = Text("Turn on now", Modifier.testTag("holiday_fake_turn"))

    @Composable
    override fun Sections() = Text("Lights while away", Modifier.testTag("holiday_fake_sections"))
}

/** 15b §4.2: Holiday mode's page in Settings › Modes, over a fake of its own parts. */
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = "w1000dp-h2400dp-land-hdpi")
class HolidayPageTest {
    @get:Rule val compose = createComposeRule()
    private val overlay = TestOverlay()
    private val scope = CoroutineScope(Job())
    private val toaster = RecordingToaster()
    private val rig = PageRig(scope)

    @After fun stop() = scope.cancel()

    private fun show(prefs: ModesPrefs = ModesPrefs(true, listOf(PARTY_MODE)), builtIn: Set<BuiltInModePage> = setOf(FAKE_HOLIDAY)) {
        runBlocking { rig.settings.update { prefs } }
        rig.loaded()
        val page = rig.page(toaster, builtIn)
        compose.setContent {
            CulveryTheme(dark = true) {
                CompositionLocalProvider(LocalOverlayHost provides overlay) {
                    Box(Modifier.size(960.dp, 2400.dp)) {
                        InSettingsPane { page.Content() }
                        overlay.content?.invoke()
                    }
                }
            }
        }
        compose.waitFor { exists("modes_add") }
        compose.onNodeWithTag("mode_row_holiday").performClick()
        compose.waitFor { exists("mode_pin") }
    }

    private fun exists(tag: String) = compose.onAllNodesWithTag(tag).fetchSemanticsNodes().isNotEmpty()

    private fun gone(tag: String) = compose.onAllNodesWithTag(tag).assertCountEquals(0)

    private fun saved() = runBlocking { rig.settings.prefs.first() }

    /** Fails if the PIN switch can be turned off, is off, or lacks its reason. */
    @Test
    fun pinHeldOn() {
        show()
        compose.onNodeWithTag("mode_pin").assertIsOn().assertIsNotEnabled().assertTextContains("Holiday mode always needs a PIN.")
        compose.onNodeWithTag("mode_pin").performClick()
        compose.onNodeWithTag("mode_pin").assertIsOn()
    }

    /** Fails if Holiday's page lets the user change its icon or name, choose music or lights, or delete it. */
    @Test
    fun noRenameNoMusicNoDelete() {
        show()
        compose.onNodeWithTag("mode_header_icon").performClick()
        compose.onNodeWithTag("mode_header_name").performClick()
        assertThat(overlay.content).isNull()
        assertThat(overlay.shown).isEqualTo(0)
        gone("mode_delete")
        listOf("Music", "Lights · every other light is left alone").forEach {
            compose.onAllNodes(hasContentDescription(it)).assertCountEquals(0)
        }
    }

    /** Fails if its own button and sections are missing, or sit out of order: button, switches, sections, plugs. */
    @Test
    fun itsOwnPartsSitInOrder() {
        rig.plugs.offered = listOf(LAMP)
        show()
        compose.waitFor { exists("plug_add") }
        val tops = listOf("holiday_fake_turn", "mode_home", "holiday_fake_sections", "plug_add").map {
            compose.onNodeWithTag(it).performScrollTo()
            compose.onNodeWithTag(it).getUnclippedBoundsInRoot().top.value
        }
        assertThat(tops).isInOrder()
    }

    /** Fails if a build without Holiday's page shows its button, or loses the rest of the page. */
    @Test
    fun withoutHolidayThereIsNoButton() {
        show(builtIn = emptySet())
        gone("holiday_fake_turn")
        compose.onNodeWithTag("mode_home").assertIsOn()
    }

    /** Fails if turning Show on Home off isn't saved, or leaves Holiday in the tray. */
    @Test
    fun showOnHomeOffTakesHolidayOffHome() {
        show()
        compose.onNodeWithTag("mode_home").assertIsOn().performClick()

        compose.waitFor { !saved().holidayOnHome }
        compose.waitFor { runBlocking { rig.controls.place(HouseModes.HOLIDAY).first() } == null }
        compose.onNodeWithTag("mode_home").assertIsOff()
    }

    /** Fails if Holiday can be put back on Home when Home has no room. */
    @Test
    fun showOnHomeIsRefusedWithNoRoom() {
        rig.room.value = 1
        show(ModesPrefs(false, listOf(PARTY_MODE.copy(showOnHome = true))))
        compose.onNodeWithTag("mode_home").assertIsOff().assertIsNotEnabled().assertTextContains("Home has room for 1 mode.")
    }

    /** Fails if the plugs section doesn't work on mode id holiday: Add a plug stores it under "holiday". */
    @Test
    fun plugsForHoliday() {
        rig.plugs.offered = listOf(LAMP)
        show()
        compose.waitFor { exists("plug_add") }
        compose.onNodeWithTag("plug_add").performScrollTo().performClick()
        compose.onNodeWithTag("plug_option_0").performClick()

        compose.waitFor { rig.plugs.chosen.isNotEmpty() }
        assertThat(rig.plugs.chosen.single().first).isEqualTo("holiday")
        rig.plugs.addedNow.value = listOf(AddedPlug("lamp", on = true, needsPin = false))
        compose.waitFor { exists("plug_chip_0") }
        compose.assertClickablesHaveRoleAndLabel()
    }
}
