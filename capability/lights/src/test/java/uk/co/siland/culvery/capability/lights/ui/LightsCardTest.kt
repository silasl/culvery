package uk.co.siland.culvery.capability.lights.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertContentDescriptionEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import uk.co.siland.culvery.capability.lights.Bridges
import uk.co.siland.culvery.capability.lights.HouseRun
import uk.co.siland.culvery.capability.lights.LightRoom
import uk.co.siland.culvery.capability.lights.LightsActions
import uk.co.siland.culvery.capability.lights.LightsFixture
import uk.co.siland.culvery.capability.lights.LightsFollow
import uk.co.siland.culvery.capability.lights.LightsHouse
import uk.co.siland.culvery.capability.lights.LightsProblem
import uk.co.siland.culvery.capability.lights.LightsView
import uk.co.siland.culvery.capability.lights.TestAccess
import uk.co.siland.culvery.capability.lights.TestLightsProvider
import uk.co.siland.culvery.capability.lights.link
import uk.co.siland.culvery.capability.lights.problemLine
import uk.co.siland.culvery.capability.lights.room
import uk.co.siland.culvery.capability.lights.soon
import uk.co.siland.culvery.core.plugin.LocalShellNavigator
import uk.co.siland.culvery.core.plugin.test.RecordingNavigator
import uk.co.siland.culvery.core.ui.CulveryTheme
import uk.co.siland.culvery.core.ui.test.assertAccessible

/** 7 §5.1, §5.4: the Home card's states, its controls and their words; it follows the rooms while it shows. */
@RunWith(AndroidJUnit4::class)
class LightsCardTest {
    @get:Rule val compose = createComposeRule()
    @get:Rule val lights = LightsFixture()
    private val calls = mutableListOf<String>()
    private val mainRoom = MainRoomActions(onToggle = { calls += "toggle" }, onOpen = { calls += "open" })
    private val house = HouseActions(onAllOff = { calls += "all off" }, onHoliday = { calls += "holiday" })
    private val lounge = room("Lounge", on = true, brightness = 70)

    private fun show(
        main: LightRoom? = lounge,
        holidayOn: Boolean = false,
        run: HouseRun? = null,
        status: String? = null,
        line: String? = null,
    ) = compose.setContent {
        CulveryTheme(dark = true) { Box(Modifier.size(345.dp, 279.dp)) { LightsCard(main, holidayOn, run, status, line, mainRoom, house) } }
    }

    private fun clickLabelIs(label: String) = SemanticsMatcher("click label is $label") {
        it.config.getOrNull(SemanticsActions.OnClick)?.label == label
    }

    /** Fails if the room, its brightness, the words TalkBack hears or the action it offers differ from the spec's. */
    @Test
    fun theMainRoomOnShowsItsBrightness() {
        show()
        compose.onNodeWithText("Lounge", useUnmergedTree = true).assertIsDisplayed()
        compose.onNodeWithText("On · 70%", useUnmergedTree = true).assertIsDisplayed()
        compose.onNodeWithTag("lights_card_main").assertContentDescriptionEquals("Lounge, on, 70 percent").assert(clickLabelIs("Turn the Lounge off"))
        compose.assertAccessible()
    }

    /** Fails if an off room shows a brightness or offers to turn itself off. */
    @Test
    fun offSaysOff() {
        show(room("Lounge"))
        compose.onNode(hasText("Off") and hasAnyAncestor(hasTestTag("lights_card_main")), useUnmergedTree = true).assertIsDisplayed()
        compose.onNodeWithTag("lights_card_main").assertContentDescriptionEquals("Lounge, off").assert(clickLabelIs("Turn the Lounge on"))
        compose.assertAccessible()
    }

    /** Fails if a tap on the room doesn't toggle, the chevron toggles instead of opening, or the chevron is named wrongly. */
    @Test
    fun aTapTogglesAndTheChevronOpensTheTab() {
        show()
        compose.onNodeWithTag("lights_card_main").performClick()
        compose.onNodeWithTag("lights_card_open").assertContentDescriptionEquals("Open the Lounge on the Lights tab").performClick()
        assertThat(calls).containsExactly("toggle", "open").inOrder()
        compose.assertAccessible()
    }

    /** Fails if either button's name, label or action differs, or Holiday mode's two lines are wrong. */
    @Test
    fun allLightsOffAndHolidayMode() {
        show()
        compose.onNodeWithText("All lights off", useUnmergedTree = true).assertIsDisplayed()
        compose.onNodeWithTag("lights_card_all_off").assert(clickLabelIs("Turn all lights off")).performClick()
        compose.onNodeWithText("Holiday mode", useUnmergedTree = true).assertIsDisplayed()
        compose.onNodeWithText("Off", useUnmergedTree = true).assertIsDisplayed()
        compose.onNodeWithTag("lights_card_holiday").assert(clickLabelIs("Turn Holiday mode on")).performClick()
        assertThat(calls).containsExactly("all off", "holiday").inOrder()
        compose.assertAccessible()
    }

    /** Fails if Holiday mode while on doesn't show "On" and offer to turn off. */
    @Test
    fun holidayModeOn() {
        show(holidayOn = true)
        compose.onNodeWithText("On", useUnmergedTree = true).assertIsDisplayed()
        compose.onNodeWithTag("lights_card_holiday").assertContentDescriptionEquals("Holiday mode, on").assert(clickLabelIs("Turn Holiday mode off"))
        compose.assertAccessible()
    }

    /** Fails if a run doesn't replace the state with "Turning on…" or "Turning off…". */
    @Test
    fun whileItRunsTheButtonSaysTurningOn() {
        var run by mutableStateOf<HouseRun?>(HouseRun.TURNING_ON)
        compose.setContent { CulveryTheme(dark = true) { Box(Modifier.size(345.dp, 279.dp)) { LightsCard(lounge, false, run, null, null, mainRoom, house) } } }
        compose.onNodeWithText("Turning on…", useUnmergedTree = true).assertIsDisplayed()
        compose.onNodeWithTag("lights_card_holiday").assertContentDescriptionEquals("Holiday mode, turning on")
        compose.assertAccessible()
        run = HouseRun.TURNING_OFF
        compose.onNodeWithText("Turning off…", useUnmergedTree = true).assertIsDisplayed()
        compose.assertAccessible()
    }

    /** Fails if a problem leaves the controls on the card, or the line isn't the problem's. */
    @Test
    fun aProblemShowsTheLineAndNoControls() {
        show(status = problemLine(LightsProblem.OFFLINE))
        compose.onNodeWithText("The tablet is offline. Check the Wi-Fi.").assertIsDisplayed()
        compose.onNodeWithTag("lights_card_main").assertDoesNotExist()
        compose.onNodeWithTag("lights_card_all_off").assertDoesNotExist()
        compose.onNodeWithTag("lights_card_holiday").assertDoesNotExist()
        compose.assertAccessible()
    }

    /** Fails if the card shows controls, or nothing, before the first read lists a room. */
    @Test
    fun beforeTheFirstReadItIsLooking() {
        show(main = null)
        compose.onNodeWithText("Looking for the lights…").assertIsDisplayed()
        compose.onNodeWithTag("lights_card_all_off").assertDoesNotExist()
        compose.assertAccessible()
    }

    /** Fails if the line from the last command isn't shown under the buttons. */
    @Test
    fun aLineFromTheLastCommandShowsUnderTheButtons() {
        show(line = "2 rooms didn’t turn off. Try again.")
        compose.onNodeWithTag("lights_card_line").assertIsDisplayed()
        compose.assertAccessible()
    }

    private class Host(val view: LightsView, val actions: LightsActions, val house: LightsHouse, val follow: LightsFollow)

    private suspend fun host(vararg rooms: LightRoom): Host {
        val settings = lights.settings
        settings.connect("lights.hue", link())
        val provider = TestLightsProvider()
        val bridges = Bridges(setOf(provider), settings, lights.scope)
        soon { bridges.system.first { it != null } }
        provider.systems.single().rooms.value = rooms.toList()
        val view = LightsView(bridges, settings, lights.scope)
        val house = LightsHouse(bridges, settings)
        return Host(
            view,
            LightsActions({ bridges.system.value }, lights.scope, System::currentTimeMillis, house, TestAccess(null)),
            house,
            LightsFollow(bridges.system, MutableStateFlow(true), lights.scope),
        )
    }

    private fun tagCount(tag: String) = compose.onAllNodes(hasTestTag(tag)).fetchSemanticsNodes().size

    /** 7 §4.2: Fails if the rooms aren't followed while the card is on screen, or still are after it goes. */
    @Test
    fun theHostWatchesWhileShown() = runBlocking {
        val h = host(lounge)
        var shown by mutableStateOf(true)
        compose.setContent {
            CulveryTheme(dark = true) {
                CompositionLocalProvider(LocalShellNavigator provides RecordingNavigator()) {
                    if (shown) LightsCardHost(h.view, h.actions, h.house, h.follow)
                }
            }
        }
        compose.waitUntil(5_000) { compose.waitForIdle(); h.follow.watching == 1 }
        compose.waitUntil(5_000) { compose.waitForIdle(); tagCount("lights_card_main") == 1 }
        shown = false
        compose.waitUntil(5_000) { compose.waitForIdle(); h.follow.watching == 0 }
    }

    /** 7 §2.3: Fails if the › doesn't open the Lights tab, or doesn't name the room the tab should scroll to. */
    @Test
    fun theChevronOpensTheLightsTabOnThatRoom() = runBlocking {
        val h = host(lounge)
        val navigator = RecordingNavigator()
        compose.setContent {
            CulveryTheme(dark = true) {
                CompositionLocalProvider(LocalShellNavigator provides navigator) { LightsCardHost(h.view, h.actions, h.house, h.follow) }
            }
        }
        compose.waitUntil(5_000) { compose.waitForIdle(); tagCount("lights_card_open") == 1 }
        compose.onNodeWithTag("lights_card_open").performClick()
        assertThat(navigator.tabs).containsExactly("lights")
        assertThat(h.view.focus.value).isEqualTo("Lounge-id")
    }
}
