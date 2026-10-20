package uk.co.siland.culvery.capability.lights.ui

import android.graphics.Bitmap
import android.graphics.Canvas
import android.view.View
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.boundsInWindow
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertContentDescriptionEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertIsNotSelected
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipeRight
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.runBlocking
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.GraphicsMode
import uk.co.siland.culvery.capability.lights.BRIGHT
import uk.co.siland.culvery.capability.lights.HouseRun
import uk.co.siland.culvery.capability.lights.LightRoom
import uk.co.siland.culvery.capability.lights.LightsFixture
import uk.co.siland.culvery.capability.lights.LightScene
import uk.co.siland.culvery.capability.lights.LightsPermissions
import uk.co.siland.culvery.capability.lights.LightsProblem
import uk.co.siland.culvery.capability.lights.RELAX
import uk.co.siland.culvery.capability.lights.TestAccess
import uk.co.siland.culvery.capability.lights.problemLine
import uk.co.siland.culvery.capability.lights.room
import uk.co.siland.culvery.core.ui.Culvery
import uk.co.siland.culvery.core.ui.CulveryTheme
import uk.co.siland.culvery.core.ui.DarkColors
import uk.co.siland.culvery.core.ui.test.assertClickablesHaveRoleAndLabel

/** 7 §5.2, §5.4: the Lights tab's room cards, controls and words; it follows the rooms while it shows. */
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
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
        allOffLit: Boolean = true,
        run: HouseRun? = null,
        status: String? = null,
        houseLine: String? = null,
        lines: Map<String, String> = emptyMap(),
    ) = compose.setContent {
        CulveryTheme(dark = true) {
            Box(Modifier.size(900.dp, 800.dp)) {
                LightsTab(rooms, holidayOn, allOffLit, run, status, houseLine, lines, null, {}, house, roomActions)
            }
        }
    }

    private fun clickLabelIs(label: String) = SemanticsMatcher("click label is $label") {
        it.config.getOrNull(SemanticsActions.OnClick)?.label == label
    }

    private fun tagCount(tag: String) = compose.onAllNodes(hasTestTag(tag)).fetchSemanticsNodes().size

    private fun top(tag: String) = compose.onNodeWithTag(tag).fetchSemanticsNode().boundsInRoot.top

    /** Fails if the card's switch is not read as "Lounge, on, 70 percent", "Lounge, off" or "Hall, on". */
    @Test
    fun theCardIsSpokenAsTheRoom() {
        show()
        compose.onNodeWithTag("lights_room_Lounge-id_switch").assertContentDescriptionEquals("Lounge, on, 70 percent")
        compose.onNodeWithTag("lights_room_Kitchen-id_switch").assertContentDescriptionEquals("Kitchen, off")
        compose.onNodeWithTag("lights_room_Hall-id_switch").assertContentDescriptionEquals("Hall, on")
        compose.assertClickablesHaveRoleAndLabel()
    }

    /** Fails if the switch's click label differs, or a tap asks for the state the room is already in. */
    @Test
    fun theSwitchTurnsTheRoom() {
        show()
        compose.onNodeWithTag("lights_room_Kitchen-id_switch").assert(clickLabelIs("Turn the Kitchen on")).performClick()
        compose.onNodeWithTag("lights_room_Lounge-id_switch").assert(clickLabelIs("Turn the Lounge off")).performClick()
        assertThat(calls).containsExactly("Kitchen on", "Lounge off").inOrder()
        compose.assertClickablesHaveRoleAndLabel()
    }

    /** Fails if a room that can't dim has a slider, or the slider's words, range or callbacks differ. */
    @Test
    fun theSliderIsHiddenForARoomThatCantDim() {
        show()
        compose.onNodeWithTag("lights_room_Hall-id_brightness").assertDoesNotExist()
        val slider = compose.onNodeWithTag("lights_room_Lounge-id_brightness")
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

    /** The card's thumb stays where it was let go while the bridge reports the drag's earlier values. Fails if it follows the 60. */
    @Test
    fun aCardsSliderStaysWhereItWasLetGoUntilTheRoomReportsIt() {
        var rooms by mutableStateOf(listOf(lounge))
        compose.mainClock.autoAdvance = false
        compose.setContent {
            CulveryTheme(dark = true) {
                Box(Modifier.size(900.dp, 800.dp)) { LightsTab(rooms, false, true, null, null, null, emptyMap(), null, {}, house, roomActions) }
            }
        }
        val slider = compose.onNodeWithTag("lights_room_Lounge-id_brightness")
        fun levelIs(percent: Int) {
            compose.waitForIdle()
            repeat(2) { compose.mainClock.advanceTimeByFrame() }
            slider.assert(SemanticsMatcher.expectValue(SemanticsProperties.StateDescription, "$percent percent"))
        }
        slider.performSemanticsAction(SemanticsActions.SetProgress) { it(90f) }
        levelIs(90)
        rooms = listOf(lounge.copy(brightness = 60))
        levelIs(90)
        rooms = listOf(lounge.copy(brightness = 90))
        levelIs(90)
        rooms = listOf(lounge.copy(brightness = 30))
        levelIs(30)
    }

    /** Pin. Fails if the active scene is not the selected chip, a chip's words differ, a tap recalls the wrong scene, or a room without scenes has a chip row. */
    @Test
    fun aSceneChipIsSpokenAndRecallsItsScene() {
        show()
        compose.onNodeWithTag("lights_room_Lounge-id_scene_relax-id").assertIsSelected().assertContentDescriptionEquals("Relax scene in the Lounge")
        compose.onNodeWithTag("lights_room_Lounge-id_scene_bright-id").assertIsNotSelected().assertContentDescriptionEquals("Bright scene in the Lounge").performClick()
        assertThat(calls).containsExactly("Bright in Lounge")
        compose.onNodeWithTag("lights_room_Kitchen-id_scenes").assertDoesNotExist()
        compose.onNodeWithTag("lights_room_Lounge-id_scenes").assertIsDisplayed()
        compose.assertClickablesHaveRoleAndLabel()
    }

    /** 9 §4.4. Fails if the title isn't a heading, or the subtitle doesn't count the rooms on. */
    @Test
    fun theHeaderCountsTheRoomsOn() {
        show()
        compose.onNodeWithText("Lights").assert(SemanticsMatcher.keyIsDefined(SemanticsProperties.Heading)).assertIsDisplayed()
        compose.onNodeWithText("2 of 3 rooms on · Hue Bridge").assertIsDisplayed()
        compose.assertClickablesHaveRoleAndLabel()
    }

    /** 9 §2.3. Fails if either button is missing from the header's row, not right of the title, or its words or action differ. */
    @Test
    fun theTrayHoldsAllLightsOffAndHolidayMode() {
        show(holidayOn = true)
        compose.onNodeWithText("All lights off", useUnmergedTree = true).assertIsDisplayed()
        compose.onNodeWithTag("lights_tray_all_off").assert(clickLabelIs("Turn all lights off")).performClick()
        compose.onNodeWithText("Holiday mode", useUnmergedTree = true).assertIsDisplayed()
        compose.onNode(hasText("On") and hasAnyAncestor(hasTestTag("lights_tray_holiday")), useUnmergedTree = true).assertIsDisplayed()
        compose.onNodeWithTag("lights_tray_holiday").assertContentDescriptionEquals("Holiday mode, on").assert(clickLabelIs("Turn Holiday mode off")).performClick()
        assertThat(calls).containsExactly("all off", "holiday").inOrder()
        val title = compose.onNodeWithText("Lights").fetchSemanticsNode().boundsInRoot
        val allOff = compose.onNodeWithTag("lights_tray_all_off").fetchSemanticsNode().boundsInRoot
        val holiday = compose.onNodeWithTag("lights_tray_holiday").fetchSemanticsNode().boundsInRoot
        assertThat(allOff.left).isAtLeast(title.right - 1f)
        assertThat(holiday.left).isGreaterThan(allOff.left)
        assertThat(allOff.center.y).isWithin(2f).of(title.center.y)
        compose.onNodeWithTag("lights_card_all_off").assertDoesNotExist()
        compose.onNodeWithTag("lights_card_holiday").assertDoesNotExist()
        compose.assertClickablesHaveRoleAndLabel()
    }

    /** Pin: Holiday mode from the tab asks for the fresh PIN, as Home does. Fails if the tray button asks for anything else. */
    @Test
    fun theTrayHolidayAsksForTheFreshPin() = runBlocking {
        val access = TestAccess(null)
        val h = lights.tabHost(lounge, access = access)
        compose.setContent { CulveryTheme(dark = true) { Box(Modifier.size(900.dp, 800.dp)) { LightsTabHost(h.view, h.actions, h.house, h.follow) } } }
        compose.waitUntil(5_000) { compose.waitForIdle(); tagCount("lights_tray_holiday") == 1 }
        compose.onNodeWithTag("lights_tray_holiday").performClick()
        compose.waitUntil(5_000) { access.asked.isNotEmpty() }
        assertThat(access.asked).containsExactly(listOf(LightsPermissions.HOLIDAY))
        compose.assertClickablesHaveRoleAndLabel()
    }

    /** Fails if the no-bridge line is above the title block, or the count shows with no rooms read. */
    @Test
    fun noBridgeShowsUnderTheNewTitle() {
        show(rooms = emptyList(), status = problemLine(LightsProblem.UNPAIRED))
        compose.onNodeWithTag("lights_status").assertIsDisplayed()
        assertThat(top("lights_status")).isAtLeast(compose.onNodeWithText("Lights").fetchSemanticsNode().boundsInRoot.bottom - 1f)
        compose.onNodeWithText("0 of 0 rooms on · Hue Bridge").assertDoesNotExist()
        compose.assertClickablesHaveRoleAndLabel()
    }

    /** 9 §2.5. Fails if an off room's slider can be moved, loses its remembered level, or doesn't read "Off". */
    @Test
    fun anOffRoomsSliderIsDisabledAndReadsOff() {
        show(rooms = listOf(room("Kitchen", brightness = 40)))
        val slider = compose.onNodeWithTag("lights_room_Kitchen-id_brightness").assertIsNotEnabled()
        val config = slider.fetchSemanticsNode().config
        assertThat(config[SemanticsProperties.ContentDescription].single() + ", " + config[SemanticsProperties.StateDescription]).isEqualTo("Kitchen brightness, Off")
        assertThat(config[SemanticsProperties.ProgressBarRangeInfo].current).isEqualTo(40f)
        compose.assertClickablesHaveRoleAndLabel()
    }

    /** Pin: the old row already sent it; this guards the new card's wiring. Fails if a chip on an off room sends anything but its scene. */
    @Test
    fun aSceneChipOnAnOffRoomTurnsItOnWithThatScene() = runBlocking {
        val h = lights.tabHost(room("Kitchen", scenes = listOf(RELAX, BRIGHT)))
        compose.setContent { CulveryTheme(dark = true) { Box(Modifier.size(900.dp, 800.dp)) { LightsTabHost(h.view, h.actions, h.house, h.follow) } } }
        compose.waitUntil(5_000) { compose.waitForIdle(); tagCount("lights_room_Kitchen-id_scene_relax-id") == 1 }
        compose.onNodeWithTag("lights_room_Kitchen-id_scene_relax-id").performClick()
        compose.waitUntil(5_000) { h.system.calls.contains("recallScene Kitchen-id relax-id") }
        compose.assertClickablesHaveRoleAndLabel()
    }

    /** Pin (the hold, carried to the card). Fails if the thumb leaves 70 before the ceiling, or stays after it. */
    @Test
    fun theRoomCardsSliderHoldsWhereItWasLetGo() {
        compose.mainClock.autoAdvance = false
        compose.setContent {
            CulveryTheme(dark = true) {
                Box(Modifier.size(900.dp, 800.dp)) { LightsTab(listOf(lounge.copy(brightness = 40)), false, true, null, null, null, emptyMap(), null, {}, house, roomActions) }
            }
        }
        fun at(ms: Long) {
            compose.waitForIdle()
            compose.mainClock.advanceTimeBy(ms - compose.mainClock.currentTime)
            compose.waitForIdle()
        }
        val slider = compose.onNodeWithTag("lights_room_Lounge-id_brightness")
        slider.performSemanticsAction(SemanticsActions.SetProgress) { it(70f) }
        at(compose.mainClock.currentTime + 16)
        val start = compose.mainClock.currentTime
        at(start + LIGHTS_HOLD_MS - 16)
        slider.assert(SemanticsMatcher.expectValue(SemanticsProperties.StateDescription, "70 percent"))
        at(start + LIGHTS_HOLD_MS + 16)
        slider.assert(SemanticsMatcher.expectValue(SemanticsProperties.StateDescription, "40 percent"))
        compose.assertClickablesHaveRoleAndLabel()
    }

    /** Fails if the card shows no percent beside the slider. */
    @Test
    fun theRoomCardShowsThePercent() {
        show()
        compose.onNode(hasText("70%") and hasAnyAncestor(hasTestTag("lights_room_Lounge-id")), useUnmergedTree = true).assertIsDisplayed()
        compose.assertClickablesHaveRoleAndLabel()
    }

    /** 9 §2.5. Fails if eight scenes at a large font wrap or are cut: the last is reached by scrolling the one row. */
    @Test
    fun manyScenesScrollAcrossNotClipped() {
        val scenes = (1..8).map { LightScene("s$it-id", "Scene number $it") }
        compose.setContent {
            CompositionLocalProvider(LocalDensity provides Density(LocalDensity.current.density, fontScale = 1.3f)) {
                CulveryTheme(dark = true) {
                    Box(Modifier.size(900.dp, 800.dp)) {
                        LightsTab(listOf(room("Lounge", on = true, scenes = scenes)), false, true, null, null, null, emptyMap(), null, {}, house, roomActions)
                    }
                }
            }
        }
        val row = compose.onNodeWithTag("lights_room_Lounge-id_scenes")
        row.performScrollToNode(hasTestTag("lights_room_Lounge-id_scene_s8-id"))
        compose.onNodeWithText("Scene number 8", useUnmergedTree = true).assertIsDisplayed()
        val chip = compose.onNodeWithTag("lights_room_Lounge-id_scene_s8-id").assertIsDisplayed().fetchSemanticsNode().boundsInRoot
        val rowBounds = row.fetchSemanticsNode().boundsInRoot
        assertThat(chip.top).isAtLeast(rowBounds.top)
        assertThat(chip.bottom).isAtMost(rowBounds.bottom)
        compose.assertClickablesHaveRoleAndLabel()
    }

    /** 9 §2.5. Fails if a scene chip isn't filled (surf2 off, accent on), or loses its selected state. Real graphics: this reads pixels. */
    @Test
    fun everySceneChipIsFilled() {
        lateinit var window: View
        compose.setContent {
            window = LocalView.current.rootView
            CulveryTheme(dark = true) {
                Box(Modifier.size(900.dp, 800.dp).background(Culvery.colors.bg)) {
                    LightsTab(listOf(lounge), false, true, null, null, null, emptyMap(), null, {}, house, roomActions)
                }
            }
        }
        compose.waitForIdle()
        val bitmap = Bitmap.createBitmap(window.width, window.height, Bitmap.Config.ARGB_8888)
        window.draw(Canvas(bitmap))
        // 4 dp in from the chip's left edge, level with its centre: inside the fill, left of the label.
        fun fillOf(tag: String): Int {
            val bounds = compose.onNodeWithTag(tag).fetchSemanticsNode().boundsInWindow
            return bitmap.getPixel((bounds.left + with(compose.density) { 4.dp.toPx() }).toInt(), bounds.center.y.toInt())
        }
        assertThat(fillOf("lights_room_Lounge-id_scene_bright-id")).isEqualTo(DarkColors.surf2.toArgb())
        assertThat(fillOf("lights_room_Lounge-id_scene_relax-id")).isEqualTo(DarkColors.accent.toArgb())
        compose.onNodeWithTag("lights_room_Lounge-id_scene_relax-id").assertIsSelected()
        compose.onNodeWithTag("lights_room_Lounge-id_scene_bright-id").assertIsNotSelected()
        compose.assertClickablesHaveRoleAndLabel()
    }

    /** Fails if the status line shows with nothing wrong, or doesn't show the problem's words when there is one. */
    @Test
    fun theStatusLineOnlyWhenSomethingIsWrong() {
        var status by mutableStateOf<String?>(null)
        compose.setContent {
            CulveryTheme(dark = true) {
                Box(Modifier.size(900.dp, 800.dp)) { LightsTab(listOf(lounge), false, true, null, status, null, emptyMap(), null, {}, house, roomActions) }
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
    fun aCardsLineShowsUnderIt() {
        show(lines = mapOf("Kitchen-id" to "Hue didn’t do that. Try again."), houseLine = "2 rooms didn’t turn off. Try again.")
        compose.onNodeWithTag("lights_room_Kitchen-id_line").assertIsDisplayed()
        assertThat(top("lights_room_Kitchen-id_line")).isGreaterThan(top("lights_room_Kitchen-id_switch"))
        compose.onNodeWithTag("lights_room_Lounge-id_line").assertDoesNotExist()
        compose.onNodeWithTag("lights_tab_line").assertIsDisplayed()
        compose.assertClickablesHaveRoleAndLabel()
    }

    /** Fails if the tab shows nothing, rather than that it is looking, before the first read. */
    @Test
    fun beforeTheFirstReadItIsLooking() {
        show(rooms = emptyList())
        compose.onNodeWithText("Looking for the lights…").assertIsDisplayed()
        compose.assertClickablesHaveRoleAndLabel()
    }

    /** 7 §4.2: Fails if the rooms aren't followed while the tab is on screen, or still are after it goes. */
    @Test
    fun theHostWatchesWhileShown() = runBlocking {
        val h = lights.tabHost(lounge)
        var shown by mutableStateOf(true)
        compose.setContent {
            CulveryTheme(dark = true) {
                if (shown) Box(Modifier.size(900.dp, 800.dp)) { LightsTabHost(h.view, h.actions, h.house, h.follow) }
            }
        }
        compose.waitUntil(5_000) { compose.waitForIdle(); h.follow.watching == 1 }
        compose.waitUntil(5_000) { compose.waitForIdle(); tagCount("lights_room_Lounge-id") == 1 }
        compose.assertClickablesHaveRoleAndLabel()
        shown = false
        compose.waitUntil(5_000) { compose.waitForIdle(); h.follow.watching == 0 }
    }

    /** Fails if the host leaves the focus set after scrolling to it, so the next visit scrolls again. */
    @Test
    fun theHostClearsTheFocusOnceScrolledTo() = runBlocking {
        val h = lights.tabHost(lounge, kitchen)
        h.view.focus.value = "Kitchen-id"
        compose.setContent { CulveryTheme(dark = true) { Box(Modifier.size(900.dp, 800.dp)) { LightsTabHost(h.view, h.actions, h.house, h.follow) } } }
        compose.waitUntil(5_000) { compose.waitForIdle(); h.view.focus.value == null }
        compose.assertClickablesHaveRoleAndLabel()
    }
}
