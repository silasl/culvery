package uk.co.siland.culvery.capability.lights.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.SemanticsNodeInteraction
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertContentDescriptionEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipeRight
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import uk.co.siland.culvery.capability.lights.Bridges
import uk.co.siland.culvery.capability.lights.HouseRun
import uk.co.siland.culvery.capability.lights.LightRoom
import uk.co.siland.culvery.capability.lights.LightsActions
import uk.co.siland.culvery.capability.lights.LightsFixture
import uk.co.siland.culvery.capability.lights.LightsFollow
import uk.co.siland.culvery.capability.lights.LightsHouse
import uk.co.siland.culvery.capability.lights.LightsPermissions
import uk.co.siland.culvery.capability.lights.LightsProblem
import uk.co.siland.culvery.capability.lights.LightsView
import uk.co.siland.culvery.capability.lights.TestAccess
import uk.co.siland.culvery.capability.lights.TestLightsProvider
import uk.co.siland.culvery.capability.lights.TestLightsSystem
import uk.co.siland.culvery.capability.lights.link
import uk.co.siland.culvery.capability.lights.problemLine
import uk.co.siland.culvery.capability.lights.room
import uk.co.siland.culvery.core.plugin.LocalShellNavigator
import uk.co.siland.culvery.core.plugin.test.RecordingNavigator
import uk.co.siland.culvery.core.plugin.test.soon
import uk.co.siland.culvery.core.ui.CulveryTheme
import uk.co.siland.culvery.core.ui.test.assertAccessible
import uk.co.siland.culvery.core.ui.test.assertClickablesHaveRoleAndLabel
import uk.co.siland.culvery.core.ui.test.assertFitsTheCard

// The card's Home size: app's HomeGridTest pins the same value.
private val rightHalfSize = DpSize(701.dp, 285.5.dp)

private val rowCardSize = DpSize(343.dp, 212.dp)

/** 7 §5.1, §5.4: the Home card's states, its controls and their words; it follows the rooms while it shows. */
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
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
        allOffLit: Boolean = true,
        run: HouseRun? = null,
        status: String? = null,
        line: String? = null,
    ) = compose.setContent {
        CulveryTheme(dark = true) { Box(Modifier.size(rightHalfSize)) { LightsCard(main, holidayOn, allOffLit, run, status, line, mainRoom, house) } }
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
        compose.setContent { CulveryTheme(dark = true) { Box(Modifier.size(rightHalfSize)) { LightsCard(lounge, false, true, run, null, null, mainRoom, house) } } }
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

    /** The card in the side-by-side row (10 §4.9). Fails if a control or line spills, overlaps or is cut at 343 x 212, with or without a line. */
    @Test
    @Config(qualifiers = "w1280dp-h800dp")
    fun theCardFitsTheSideBySideRow() {
        var dark by mutableStateOf(true)
        var main by mutableStateOf(room("Upstairs landing and hallway", on = true, brightness = 70))
        var holidayOn by mutableStateOf(false)
        var line by mutableStateOf<String?>(null)
        compose.setContent {
            CulveryTheme(dark = dark) {
                Box(Modifier.size(rowCardSize).testTag("fit_card")) { LightsCard(main, holidayOn, true, null, null, line, mainRoom, house) }
            }
        }
        for (theme in listOf(true, false)) {
            dark = theme
            compose.waitForIdle()
            compose.assertFitsTheCard(textStaysOffControls = false)
            holidayOn = true
            compose.waitForIdle()
            compose.assertFitsTheCard(textStaysOffControls = false)
            for (message in listOf(
                "2 rooms didn’t turn off. Try again.",
                "Lights are off, but Culvery couldn’t start Mimic presence. Start it in the Hue app.",
            )) {
                line = message
                compose.waitForIdle()
                compose.assertFitsTheCard(textStaysOffControls = false)
            }
            line = null
            holidayOn = false
            main = room("Lounge", on = false)
            compose.waitForIdle()
            compose.assertFitsTheCard(textStaysOffControls = false)
            main = room("Upstairs landing and hallway", on = true, brightness = 70)
        }
    }

    private class Host(val view: LightsView, val actions: LightsActions, val house: LightsHouse, val follow: LightsFollow, val access: TestAccess, val system: TestLightsSystem)

    private suspend fun host(vararg rooms: LightRoom): Host {
        val access = TestAccess(null)
        val settings = lights.settings
        settings.connect("lights.hue", link())
        val provider = TestLightsProvider()
        val bridges = Bridges(setOf(provider), settings, lights.scope)
        soon { bridges.system.first { it != null } }
        val system = provider.systems.single()
        system.rooms.value = rooms.toList()
        val view = LightsView(bridges, settings, lights.scope)
        val house = LightsHouse(bridges, settings)
        return Host(
            view,
            LightsActions({ bridges.system.value }, lights.scope, System::currentTimeMillis, house, access),
            house,
            LightsFollow(bridges.system, MutableStateFlow(true), lights.scope),
            access,
            system,
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

    private fun showHost(h: Host) = compose.setContent {
        CulveryTheme(dark = true) {
            CompositionLocalProvider(LocalShellNavigator provides RecordingNavigator()) {
                Box(Modifier.size(rightHalfSize)) { LightsCardHost(h.view, h.actions, h.house, h.follow) }
            }
        }
    }

    /** 9 §2.5. Fails if an off room's slider is enabled, hides its remembered level, or isn't spoken as "Off". */
    @Test
    fun theSliderIsDisabledWhileTheRoomIsOff() {
        show(room("Lounge", brightness = 40))
        val slider = compose.onNodeWithTag("lights_card_slider")
        slider.assertIsNotEnabled()
        val config = slider.fetchSemanticsNode().config
        assertThat(config[SemanticsProperties.ContentDescription].single() + ", " + config[SemanticsProperties.StateDescription]).isEqualTo("Lounge brightness, Off")
        assertThat(config[SemanticsProperties.ProgressBarRangeInfo].current).isEqualTo(40f)
        compose.assertClickablesHaveRoleAndLabel()
    }

    /** 9 §2.5. Fails if a swipe on an off room's slider sends a brightness, or turns the room on. */
    @Test
    fun aSwipeOnAnOffRoomSendsNothing() = runBlocking {
        val h = host(room("Lounge", brightness = 40))
        showHost(h)
        compose.waitUntil(5_000) { compose.waitForIdle(); tagCount("lights_card_slider") == 1 }
        compose.onNodeWithTag("lights_card_slider").performTouchInput { swipeRight(startX = centerX, endX = right) }
        compose.waitForIdle()
        assertThat(h.system.calls.filter { it.startsWith("setBrightness") || it.startsWith("setOn") }).isEmpty()
        compose.assertClickablesHaveRoleAndLabel()
    }

    /** 9 §4.2. Fails if a swipe on an on room's slider doesn't reach the bridge as a brightness in 0..100. */
    @Test
    fun movingTheSliderSendsTheBrightness() = runBlocking {
        val h = host(lounge)
        showHost(h)
        compose.waitUntil(5_000) { compose.waitForIdle(); tagCount("lights_card_slider") == 1 }
        compose.onNodeWithTag("lights_card_slider").performTouchInput { swipeRight(startX = centerX, endX = right) }
        compose.waitForIdle()
        compose.waitUntil(5_000) { h.system.calls.any { it.startsWith("setBrightness Lounge-id ") } }
        val sent = h.system.calls.filter { it.startsWith("setBrightness Lounge-id ") }.map { it.substringAfterLast(' ').toInt() }
        assertThat(sent).isNotEmpty()
        assertThat(sent.last()).isIn(0..100)
        compose.assertClickablesHaveRoleAndLabel()
    }

    private fun levelIs(slider: SemanticsNodeInteraction, percent: Int) =
        slider.assert(SemanticsMatcher.expectValue(SemanticsProperties.StateDescription, "$percent percent"))

    /** Runs the virtual clock to [ms] since the start, drawing every frame on the way. */
    private fun at(ms: Long) {
        compose.waitForIdle()
        compose.mainClock.advanceTimeBy(ms - compose.mainClock.currentTime)
        compose.waitForIdle()
    }

    private fun after(ms: Long) = at(compose.mainClock.currentTime + ms)

    /** Lets go at [percent] and returns the time the hold began: the first frame after the let-go. */
    private fun letGoAt(slider: SemanticsNodeInteraction, percent: Float): Long {
        slider.performSemanticsAction(SemanticsActions.SetProgress) { it(percent) }
        after(16)
        return compose.mainClock.currentTime
    }

    private fun showOnVirtualTime(main: () -> LightRoom) {
        compose.mainClock.autoAdvance = false
        compose.setContent {
            CulveryTheme(dark = true) { Box(Modifier.size(rightHalfSize)) { LightsCard(main(), false, true, null, null, null, mainRoom, house) } }
        }
    }

    /**
     * Commands sent mid-drag are reported back after the finger is up, out of step and late. Fails if the thumb follows a
     * stale report (60, 80) instead of staying at 90 until the room reports 90, however late that is (here 2 s).
     */
    @Test
    fun theLevelLetGoStaysUntilTheRoomReportsIt() {
        var main by mutableStateOf(lounge)
        showOnVirtualTime { main }
        val slider = compose.onNodeWithTag("lights_card_slider")
        val start = letGoAt(slider, 90f)
        levelIs(slider, 90)
        main = lounge.copy(brightness = 60)
        after(32)
        levelIs(slider, 90)
        main = lounge.copy(brightness = 80)
        at(start + 1_500 + 16)
        levelIs(slider, 90)
        at(start + 2_000)
        main = lounge.copy(brightness = 90)
        after(32)
        levelIs(slider, 90)
        main = lounge.copy(brightness = 30)
        after(32)
        levelIs(slider, 30)
    }

    /** A refused command never reports the level: still held one frame before the ceiling, the room's level at it plus one frame. */
    @Test
    fun theHoldEndsAtTheCeilingWhenTheLevelNeverComes() {
        var main by mutableStateOf(lounge)
        showOnVirtualTime { main }
        val slider = compose.onNodeWithTag("lights_card_slider")
        val start = letGoAt(slider, 90f)
        at(start + LIGHTS_HOLD_MS - 16)
        levelIs(slider, 90)
        at(start + LIGHTS_HOLD_MS + 16)
        levelIs(slider, 70)
    }

    /** A second let-go inside the hold starts the ceiling again. Fails if the first let-go's ceiling releases the second. */
    @Test
    fun aSecondLetGoRestartsTheHold() {
        var main by mutableStateOf(lounge)
        showOnVirtualTime { main }
        val slider = compose.onNodeWithTag("lights_card_slider")
        val first = letGoAt(slider, 90f)
        at(first + 3_000)
        val second = letGoAt(slider, 40f)
        at(first + LIGHTS_HOLD_MS + 16)
        levelIs(slider, 40)
        at(second + LIGHTS_HOLD_MS - 16)
        levelIs(slider, 40)
        at(second + LIGHTS_HOLD_MS + 16)
        levelIs(slider, 70)
    }

    /** Pin. Fails if a room that can't dim gets a slider. */
    @Test
    fun aRoomThatCantDimHasNoSlider() {
        show(room("Hall", on = true, dimmable = false))
        compose.onNodeWithTag("lights_card_slider").assertDoesNotExist()
        compose.assertClickablesHaveRoleAndLabel()
    }

    /** Pin (9 §2.3, §6.2). Fails if the card's Holiday mode doesn't reach toggleHoliday and ask for the Holiday permission. */
    @Test
    fun holidayModeAsksForTheFreshPin() = runBlocking {
        val h = host(lounge)
        showHost(h)
        compose.waitUntil(5_000) { compose.waitForIdle(); tagCount("lights_card_holiday") == 1 }
        compose.onNodeWithTag("lights_card_holiday").performClick()
        compose.waitUntil(5_000) { h.access.asked.isNotEmpty() }
        assertThat(h.access.asked.single()).containsExactly(LightsPermissions.HOLIDAY)
        compose.assertClickablesHaveRoleAndLabel()
    }
}
