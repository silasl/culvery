package uk.co.siland.culvery.capability.holiday.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.GraphicsMode
import uk.co.siland.culvery.capability.holiday.HolidayActions
import uk.co.siland.culvery.capability.holiday.HouseRig
import uk.co.siland.culvery.capability.holiday.TestAccess
import uk.co.siland.culvery.capability.holiday.TestSwitch
import uk.co.siland.culvery.core.house.HouseModes
import uk.co.siland.culvery.core.house.HousePermissions
import uk.co.siland.culvery.core.household.Role
import uk.co.siland.culvery.core.plugin.HolidayLightsSection
import uk.co.siland.culvery.core.plugin.LocalOverlayHost
import uk.co.siland.culvery.core.plugin.test.TestOverlay
import uk.co.siland.culvery.core.ui.CulveryTheme
import uk.co.siland.culvery.core.ui.test.waitFor

private val FAKE_LIGHTS = object : HolidayLightsSection {
    @Composable
    override fun Content(modifier: Modifier) = Text("Mimic presence", modifier.testTag("fake_lights"))
}

/** 15b §4.2: Holiday mode's own parts of its page in Settings › Modes, over the real actions and house. */
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class HolidayModePageTest {
    @get:Rule val compose = createComposeRule()
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val rig = HouseRig(scope)
    private val overlay = TestOverlay()

    @Before fun load() = runBlocking { rig.loaded(); Unit }

    @After fun stop() = scope.cancel()

    private fun show(
        role: Role? = Role.ADULT,
        lights: Set<HolidayLightsSection> = emptySet(),
        others: Set<uk.co.siland.culvery.core.house.HouseModeSwitch> = emptySet(),
    ): uk.co.siland.culvery.core.access.test.FakeAccess {
        val access = TestAccess(role)
        val page = HolidayModePage(HolidayActions(access, rig.house, null, others, { HouseRig.TODAY }), rig.house, lights)
        compose.setContent {
            CulveryTheme(dark = true) {
                CompositionLocalProvider(LocalOverlayHost provides overlay) {
                    Column {
                        page.TurnButton()
                        page.Sections()
                        overlay.content?.invoke()
                    }
                }
            }
        }
        compose.waitFor { exists("mode_turn") }
        return access
    }

    private fun exists(tag: String) = compose.onAllNodesWithTag(tag).fetchSemanticsNodes().isNotEmpty()

    /** Fails if the button asks for anything but the Holiday PIN, or the sheet doesn't open after it. */
    @Test
    fun turnOnNowAsksThePinThenTheSheet() {
        val access = show()
        compose.onNodeWithTag("mode_turn").assertTextEquals("Turn on now").performClick()

        compose.waitFor { exists("holiday_sheet") }
        assertThat(access.asked).containsExactly(listOf(HousePermissions.HOLIDAY_MODE))
        assertThat(rig.house.holiday.value).isFalse()
    }

    /** Fails if turning off opens the sheet, skips the PIN, or leaves the button on "Turn off now". */
    @Test
    fun turnOffNowAsksThePinAndTurnsOff() {
        runBlocking { rig.house.turnOn(HouseRig.TODAY.plusDays(3), TestAccess(Role.ADULT).authorise(HousePermissions.HOLIDAY_MODE)!!) }
        val access = show()
        compose.waitFor { runCatching { compose.onNodeWithTag("mode_turn").assertTextEquals("Turn off now") }.isSuccess }
        compose.onNodeWithTag("mode_turn").performClick()

        compose.waitFor { rig.house.holiday.value == false }
        assertThat(access.asked).containsExactly(listOf(HousePermissions.HOLIDAY_MODE))
        assertThat(exists("holiday_sheet")).isFalse()
        assertThat(overlay.content).isNull()
        compose.waitFor { runCatching { compose.onNodeWithTag("mode_turn").assertTextEquals("Turn on now") }.isSuccess }
    }

    /** Fails if a Child's PIN opens the sheet or changes Holiday mode. */
    @Test
    fun aChildIsRefused() {
        val access = show(Role.CHILD)
        compose.onNodeWithTag("mode_turn").performClick()

        compose.waitFor { access.asked.isNotEmpty() }
        compose.waitForIdle()
        assertThat(exists("holiday_sheet")).isFalse()
        assertThat(rig.house.holiday.value).isFalse()
        compose.onNodeWithTag("mode_turn").assertTextEquals("Turn on now")
    }

    /** Fails if the heading, the fixed lines or the lights section are missing or out of order. */
    @Test
    fun fixedLinesShow() {
        show(lights = setOf(FAKE_LIGHTS))
        compose.onNode(hasContentDescription("Lights while away")).assertExists()
        compose.onNodeWithTag("fake_lights").assertExists()
        compose.onNodeWithTag("holiday_fixed_0").assertTextEquals("Put the heating on Eco, and take it off Eco when you’re back.")
        compose.onNodeWithTag("holiday_fixed_1").assertTextEquals("Run the hoover the day before you’re back.")
        compose.onNodeWithTag("holiday_fixed_2").assertTextEquals("Keep the doorbell quiet. Rings and motion still show in the Door tab.")
        val tops = listOf("fake_lights", "holiday_fixed_0", "holiday_fixed_1", "holiday_fixed_2").map { compose.onNodeWithTag(it).getUnclippedBoundsInRoot().top.value }
        assertThat(tops).isInOrder()
    }

    /** Fails if Lights while away shows with no Hue to fill it, or the fixed lines go with it. */
    @Test
    fun noLightsSectionWithNoHue() {
        show(lights = emptySet())
        compose.onAllNodes(hasContentDescription("Lights while away")).assertCountEquals(0)
        compose.onNodeWithTag("holiday_fixed_0").assertExists()
    }

    /** T3: fails if a stored mode being on asks for a PIN, or the page doesn't say which mode to turn off. */
    @Test
    fun refusesWhileFilmNightIsOn() {
        val film = TestSwitch(HouseModes.STORED, "Film night", MutableStateFlow(true), logName = "a stored mode")
        val access = show(others = setOf(film))
        compose.onNodeWithTag("mode_turn").performClick()

        compose.waitFor { exists("mode_turn_line") }
        compose.onNodeWithTag("mode_turn_line").assertTextEquals("Turn Film night off first.")
        assertThat(access.asked).isEmpty()
        assertThat(exists("holiday_sheet")).isFalse()
    }
}
