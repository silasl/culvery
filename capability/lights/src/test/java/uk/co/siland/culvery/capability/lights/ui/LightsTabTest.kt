package uk.co.siland.culvery.capability.lights.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertContentDescriptionEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotSelected
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipeRight
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import uk.co.siland.culvery.capability.lights.BRIGHT
import uk.co.siland.culvery.capability.lights.Bridges
import uk.co.siland.culvery.capability.lights.HouseRun
import uk.co.siland.culvery.capability.lights.LightRoom
import uk.co.siland.culvery.capability.lights.LightsActions
import uk.co.siland.culvery.capability.lights.LightsFixture
import uk.co.siland.culvery.capability.lights.LightsFollow
import uk.co.siland.culvery.capability.lights.LightsHouse
import uk.co.siland.culvery.capability.lights.LightsProblem
import uk.co.siland.culvery.capability.lights.LightsView
import uk.co.siland.culvery.capability.lights.RELAX
import uk.co.siland.culvery.capability.lights.TestAccess
import uk.co.siland.culvery.capability.lights.TestLightsProvider
import uk.co.siland.culvery.capability.lights.link
import uk.co.siland.culvery.capability.lights.problemLine
import uk.co.siland.culvery.capability.lights.room
import uk.co.siland.culvery.core.plugin.test.soon
import uk.co.siland.culvery.core.ui.CulveryTheme
import uk.co.siland.culvery.core.ui.test.assertClickablesHaveRoleAndLabel

/** 7 §5.2, §5.4: the Lights tab's rows, controls and words; it follows the rooms while it shows. */
@RunWith(AndroidJUnit4::class)
class LightsTabTest {
    @get:Rule val compose = createComposeRule()
    @get:Rule val lights = LightsFixture()
    private val calls = mutableListOf<String>()
    private val house = HouseActions(onAllOff = { calls += "all off" }, onHoliday = { calls += "holiday" })
    private val roomActions = RoomActions(
        onOn = { r, on -> calls += "${r.name} ${if (on) "on" else "off"}" },
        onDrag = { r, p -> calls += "drag ${r.name} $p" },
        onLetGo = { r, p -> calls += "let go ${r.name} $p" },
        onScene = { r, s -> calls += "${s.name} in ${r.name}" },
    )
    private val lounge = room("Lounge", on = true, brightness = 70, scenes = listOf(RELAX, BRIGHT), active = RELAX.id)
    private val kitchen = room("Kitchen")
    private val hall = room("Hall", on = true, dimmable = false)

    private fun show(
        rooms: List<LightRoom> = listOf(lounge, kitchen, hall),
        holidayOn: Boolean = false,
        run: HouseRun? = null,
        status: String? = null,
        houseLine: String? = null,
        lines: Map<String, String> = emptyMap(),
        focus: String? = null,
        onFocused: () -> Unit = {},
        height: Int = 800,
    ) = compose.setContent {
        CulveryTheme(dark = true) {
            Box(Modifier.size(900.dp, height.dp)) {
                LightsTab(rooms, holidayOn, false, run, status, houseLine, lines, focus, onFocused, house, roomActions)
            }
        }
    }

    private fun clickLabelIs(label: String) = SemanticsMatcher("click label is $label") {
        it.config.getOrNull(SemanticsActions.OnClick)?.label == label
    }

    private fun tagCount(tag: String) = compose.onAllNodes(hasTestTag(tag)).fetchSemanticsNodes().size

    private fun top(tag: String) = compose.onNodeWithTag(tag).fetchSemanticsNode().boundsInRoot.top

    /** Fails if a room is missing, repeated, or the rows are not in the order Hue gave. */
    @Test
    fun oneRowPerRoomInHuesOrder() {
        show()
        val tops = listOf("Lounge", "Kitchen", "Hall").map { compose.onNodeWithTag("lights_row_$it-id").assertIsDisplayed(); top("lights_row_$it-id") }
        assertThat(tops).isInStrictOrder()
        assertThat(tagCount("lights_row_Lounge-id")).isEqualTo(1)
        compose.assertClickablesHaveRoleAndLabel()
    }

    /** Fails if the row's switch is not read as "Lounge, on, 70 percent", "Lounge, off" or "Hall, on". */
    @Test
    fun theRowIsSpokenAsTheRoom() {
        show()
        compose.onNodeWithTag("lights_row_Lounge-id_switch").assertContentDescriptionEquals("Lounge, on, 70 percent")
        compose.onNodeWithTag("lights_row_Kitchen-id_switch").assertContentDescriptionEquals("Kitchen, off")
        compose.onNodeWithTag("lights_row_Hall-id_switch").assertContentDescriptionEquals("Hall, on")
        compose.assertClickablesHaveRoleAndLabel()
    }

    /** Fails if the switch's click label differs, or a tap asks for the state the room is already in. */
    @Test
    fun theSwitchTurnsTheRoom() {
        show()
        compose.onNodeWithTag("lights_row_Kitchen-id_switch").assert(clickLabelIs("Turn the Kitchen on")).performClick()
        compose.onNodeWithTag("lights_row_Lounge-id_switch").assert(clickLabelIs("Turn the Lounge off")).performClick()
        assertThat(calls).containsExactly("Kitchen on", "Lounge off").inOrder()
        compose.assertClickablesHaveRoleAndLabel()
    }

    /** Fails if a room that can't dim has a slider, or the slider's words, range or callbacks differ. */
    @Test
    fun theSliderIsHiddenForARoomThatCantDim() {
        show()
        compose.onNodeWithTag("lights_row_Hall-id_brightness").assertDoesNotExist()
        val slider = compose.onNodeWithTag("lights_row_Lounge-id_brightness")
        val config = slider.fetchSemanticsNode().config
        assertThat(config[SemanticsProperties.ContentDescription].single() + ", " + config[SemanticsProperties.StateDescription])
            .isEqualTo("Lounge brightness, 70 percent")
        assertThat(config[SemanticsProperties.ProgressBarRangeInfo].range).isEqualTo(1f..100f)
        slider.performTouchInput { swipeRight(startX = centerX, endX = right) }
        assertThat(calls.filter { it.startsWith("drag Lounge") }).isNotEmpty()
        assertThat(calls.filter { it.startsWith("let go Lounge") }).hasSize(1)
        assertThat(calls.last()).startsWith("let go Lounge")
        compose.assertClickablesHaveRoleAndLabel()
    }

    /** Fails if the active scene is not the selected chip, a chip's words differ, a tap recalls the wrong scene, or a room without scenes has a chip row. */
    @Test
    fun scenesAsChipsWithTheActiveOneSelected() {
        show()
        compose.onNodeWithTag("lights_row_Lounge-id_scene_relax-id").assertIsSelected().assertContentDescriptionEquals("Relax scene in the Lounge")
        compose.onNodeWithTag("lights_row_Lounge-id_scene_bright-id").assertIsNotSelected().assertContentDescriptionEquals("Bright scene in the Lounge").performClick()
        assertThat(calls).containsExactly("Bright in Lounge")
        compose.onNodeWithTag("lights_row_Kitchen-id_scenes").assertDoesNotExist()
        compose.onNodeWithTag("lights_row_Lounge-id_scenes").assertIsDisplayed()
        compose.assertClickablesHaveRoleAndLabel()
    }

    /** Fails if either button is missing from the top, or its words or action differ. */
    @Test
    fun allLightsOffAndHolidayModeAtTheTop() {
        show()
        compose.onNodeWithText("All lights off", useUnmergedTree = true).assertIsDisplayed()
        compose.onNodeWithTag("lights_card_all_off").assert(clickLabelIs("Turn all lights off")).performClick()
        compose.onNodeWithText("Holiday mode", useUnmergedTree = true).assertIsDisplayed()
        compose.onNodeWithTag("lights_card_holiday").assertContentDescriptionEquals("Holiday mode, off").assert(clickLabelIs("Turn Holiday mode on")).performClick()
        assertThat(calls).containsExactly("all off", "holiday").inOrder()
        assertThat(top("lights_card_all_off")).isLessThan(top("lights_row_Lounge-id"))
        compose.assertClickablesHaveRoleAndLabel()
    }

    /** Fails if the status line shows with nothing wrong, or doesn't show the problem's words when there is one. */
    @Test
    fun theStatusLineOnlyWhenSomethingIsWrong() {
        var status by mutableStateOf<String?>(null)
        compose.setContent {
            CulveryTheme(dark = true) {
                Box(Modifier.size(900.dp, 800.dp)) { LightsTab(listOf(lounge), false, false, null, status, null, emptyMap(), null, {}, house, roomActions) }
            }
        }
        compose.onNodeWithTag("lights_status").assertDoesNotExist()
        status = problemLine(LightsProblem.UNREACHABLE)
        compose.onNodeWithTag("lights_status").assertIsDisplayed()
        compose.onNodeWithText("Culvery can’t reach the Hue Bridge. Check it’s plugged in and the tablet is on the home Wi-Fi.", useUnmergedTree = true).assertIsDisplayed()
        compose.assertClickablesHaveRoleAndLabel()
    }

    /** Fails if a room's own line shows anywhere but under that room, or the last All lights off or Holiday mode line is lost. */
    @Test
    fun aRowsLineShowsUnderIt() {
        show(lines = mapOf("Kitchen-id" to "Hue didn’t do that. Try again."), houseLine = "2 rooms didn’t turn off. Try again.")
        compose.onNodeWithTag("lights_row_Kitchen-id_line").assertIsDisplayed()
        assertThat(top("lights_row_Kitchen-id_line")).isGreaterThan(top("lights_row_Kitchen-id_switch"))
        compose.onNodeWithTag("lights_row_Lounge-id_line").assertDoesNotExist()
        compose.onNodeWithTag("lights_tab_line").assertIsDisplayed()
        compose.assertClickablesHaveRoleAndLabel()
    }

    /** Fails if the focused room is not brought into view, or the focus is not handed back once it has been. */
    @Test
    fun theFocusedRoomIsScrolledTo() {
        val rooms = (1..12).map { room("Room $it") }
        var focused = 0
        show(rooms, focus = "Room 12-id", onFocused = { focused++ }, height = 400)
        compose.waitUntil(5_000) { compose.waitForIdle(); focused == 1 }
        compose.onNodeWithTag("lights_row_Room 1-id").assertDoesNotExist()
        compose.onNodeWithTag("lights_row_Room 12-id").assertIsDisplayed()
        compose.assertClickablesHaveRoleAndLabel()
    }

    /** Fails if a focus naming a room Hue doesn't list is kept for ever instead of dropped. */
    @Test
    fun aFocusOnAnUnknownRoomIsDropped() {
        var focused = 0
        show(focus = "gone-id", onFocused = { focused++ })
        compose.waitUntil(5_000) { compose.waitForIdle(); focused == 1 }
        compose.assertClickablesHaveRoleAndLabel()
    }

    /** Fails if the tab shows nothing, rather than that it is looking, before the first read. */
    @Test
    fun beforeTheFirstReadItIsLooking() {
        show(rooms = emptyList())
        compose.onNodeWithText("Looking for the lights…").assertIsDisplayed()
        compose.assertClickablesHaveRoleAndLabel()
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
        val lightsHouse = LightsHouse(bridges, settings)
        return Host(
            view,
            LightsActions({ bridges.system.value }, lights.scope, System::currentTimeMillis, lightsHouse, TestAccess(null)),
            lightsHouse,
            LightsFollow(bridges.system, MutableStateFlow(true), lights.scope),
        )
    }

    /** 7 §4.2: Fails if the rooms aren't followed while the tab is on screen, or still are after it goes. */
    @Test
    fun theHostWatchesWhileShown() = runBlocking {
        val h = host(lounge)
        var shown by mutableStateOf(true)
        compose.setContent {
            CulveryTheme(dark = true) {
                if (shown) Box(Modifier.size(900.dp, 800.dp)) { LightsTabHost(h.view, h.actions, h.house, h.follow) }
            }
        }
        compose.waitUntil(5_000) { compose.waitForIdle(); h.follow.watching == 1 }
        compose.waitUntil(5_000) { compose.waitForIdle(); tagCount("lights_row_Lounge-id") == 1 }
        compose.assertClickablesHaveRoleAndLabel()
        shown = false
        compose.waitUntil(5_000) { compose.waitForIdle(); h.follow.watching == 0 }
    }

    /** Fails if the host leaves the focus set after scrolling to it, so the next visit scrolls again. */
    @Test
    fun theHostClearsTheFocusOnceScrolledTo() = runBlocking {
        val h = host(lounge, kitchen)
        h.view.focus.value = "Kitchen-id"
        compose.setContent { CulveryTheme(dark = true) { Box(Modifier.size(900.dp, 800.dp)) { LightsTabHost(h.view, h.actions, h.house, h.follow) } } }
        compose.waitUntil(5_000) { compose.waitForIdle(); h.view.focus.value == null }
        compose.assertClickablesHaveRoleAndLabel()
    }
}
