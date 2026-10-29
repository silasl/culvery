package uk.co.siland.culvery.capability.holiday.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.google.common.truth.Truth.assertThat
import java.time.LocalDate
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.runBlocking
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import uk.co.siland.culvery.capability.holiday.HolidayActions
import uk.co.siland.culvery.capability.holiday.HolidayRun
import uk.co.siland.culvery.capability.holiday.HouseRig
import uk.co.siland.culvery.capability.holiday.TestAccess
import uk.co.siland.culvery.capability.holiday.TestSwitch
import uk.co.siland.culvery.core.house.HouseModes
import uk.co.siland.culvery.core.household.Role
import uk.co.siland.culvery.core.plugin.LocalOverlayHost
import uk.co.siland.culvery.core.plugin.test.TestOverlay
import uk.co.siland.culvery.core.ui.Culvery
import uk.co.siland.culvery.core.ui.CulveryTheme
import uk.co.siland.culvery.core.ui.HhTrayColumn
import uk.co.siland.culvery.core.ui.test.assertClickablesHaveRoleAndLabel
import uk.co.siland.culvery.core.ui.test.assertNothingReadTwice
import uk.co.siland.culvery.core.ui.test.waitFor

/** 11 §5.2: the Holiday mode tile in the One-tap tray. */
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = "w1280dp-h800dp-land-hdpi")
class HolidayTileTest {
    @get:Rule val compose = createComposeRule()
    private var taps = 0
    private var on by mutableStateOf(false)
    private var back by mutableStateOf<LocalDate?>(null)
    private var line by mutableStateOf<String?>(null)
    private var running by mutableStateOf<HolidayRun?>(null)

    private fun show() {
        compose.setContent {
            CulveryTheme(dark = true) {
                Box(Modifier.background(Culvery.colors.bg)) {
                    HhTrayColumn("One-tap", listOf("holiday" to { HolidayTile(on, back, line, running) { taps++ } }), Modifier.width(333.dp))
                }
            }
        }
    }

    private fun clickLabel() = compose.onNodeWithTag("holiday_tile").fetchSemanticsNode().config.getOrNull(SemanticsActions.OnClick)?.label

    /** Fails if the words, what TalkBack hears or its click label differ from the spec. */
    @Test
    fun offReadsAsTheSpecSays() {
        show()
        compose.onNodeWithTag("holiday_tile").assertIsDisplayed()
        compose.onNodeWithText("Holiday mode", useUnmergedTree = true).assertExists()
        compose.onNodeWithTag("holiday_tile_on").assertDoesNotExist()
        compose.onNodeWithTag("holiday_tile_back").assertDoesNotExist()
        compose.onNode(hasContentDescription("Holiday mode, off.")).assertExists()
        assertThat(clickLabel()).isEqualTo("Turn on")
        compose.assertNothingReadTwice()
        compose.assertClickablesHaveRoleAndLabel()
        compose.onNodeWithTag("holiday_tile").performClick()
        assertThat(taps).isEqualTo(1)
    }

    @Test
    fun onShowsOnAndTheBackDate() {
        on = true
        back = LocalDate.of(2026, 10, 31)
        show()
        compose.onNodeWithTag("holiday_tile_on", useUnmergedTree = true).assertTextEquals("ON")
        compose.onNodeWithTag("holiday_tile_back", useUnmergedTree = true).assertTextEquals("Back Sat 31 Oct")
        compose.onNode(hasContentDescription("Holiday mode, on, back Saturday 31 October.")).assertExists()
        assertThat(clickLabel()).isEqualTo("Turn off")
        compose.assertNothingReadTwice()
        compose.assertClickablesHaveRoleAndLabel()
    }

    /** A step that didn't happen shows under the tile, and is read after it. */
    @Test
    fun aStepsLineShowsUnderIt() {
        on = true
        back = LocalDate.of(2026, 10, 31)
        line = "The hoover didn’t run."
        show()
        compose.onNodeWithTag("holiday_tile_line").assertTextEquals("The hoover didn’t run.")
        val tile = compose.onNodeWithTag("holiday_tile").getUnclippedBoundsInRoot()
        val under = compose.onNodeWithTag("holiday_tile_line").getUnclippedBoundsInRoot()
        assertThat(under.top).isAtLeast(tile.bottom)
        line = null
        compose.waitForIdle()
        compose.onNodeWithTag("holiday_tile_line").assertDoesNotExist()
        compose.assertClickablesHaveRoleAndLabel()
    }

    /** Review Focus 4. Fails if a tap while a change runs reaches [onTap], or the words don't say what is happening. */
    @Test
    fun tapsWhileTurningAreIgnored() {
        running = HolidayRun.TURNING_ON
        show()
        compose.onNodeWithTag("holiday_tile_state", useUnmergedTree = true).assertTextEquals("Turning on…")
        compose.onNodeWithTag("holiday_tile_back", useUnmergedTree = true).assertDoesNotExist()
        compose.onNodeWithTag("holiday_tile").performClick()
        assertThat(taps).isEqualTo(0)

        on = true
        back = LocalDate.of(2026, 10, 31)
        running = HolidayRun.TURNING_OFF
        compose.waitForIdle()
        compose.onNodeWithTag("holiday_tile_state", useUnmergedTree = true).assertTextEquals("Turning off…")
        compose.onNodeWithTag("holiday_tile_back", useUnmergedTree = true).assertDoesNotExist()
        compose.onNodeWithTag("holiday_tile").performClick()
        assertThat(taps).isEqualTo(0)
        compose.assertNothingReadTwice()
        compose.assertClickablesHaveRoleAndLabel()
    }

    /** Review Focus 5. Fails if a tap with Party mode on asks for a PIN, or the tile doesn't say which mode to turn off. */
    @Test
    fun aRefusalShowsAsTheLine() {
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
        try {
            val rig = HouseRig(scope)
            runBlocking { rig.loaded() }
            val access = TestAccess(Role.ADULT)
            val party = MutableStateFlow<Boolean?>(true)
            val actions = HolidayActions(access, rig.house, null, setOf(TestSwitch(HouseModes.PARTY, "Party mode", party)), { HouseRig.TODAY })
            val overlay = TestOverlay()
            compose.setContent {
                CulveryTheme(dark = true) {
                    CompositionLocalProvider(LocalOverlayHost provides overlay) {
                        Box(Modifier.background(Culvery.colors.bg)) {
                            HhTrayColumn("One-tap", listOf("holiday" to { HolidayTileHost(actions, rig.house) }), Modifier.width(333.dp))
                        }
                    }
                }
            }
            compose.onNodeWithTag("holiday_tile_line").assertDoesNotExist()

            compose.onNodeWithTag("holiday_tile").performClick()
            compose.waitFor { compose.onAllNodes(hasTestTag("holiday_tile_line")).fetchSemanticsNodes().isNotEmpty() }

            compose.onNodeWithTag("holiday_tile_line").assertTextEquals("Turn Party mode off first.")
            assertThat(access.asked).isEmpty()
            assertThat(overlay.content).isNull()
            compose.assertClickablesHaveRoleAndLabel()

            party.value = false
            compose.waitFor { compose.onAllNodes(hasTestTag("holiday_tile_line")).fetchSemanticsNodes().isEmpty() }
        } finally {
            scope.cancel()
        }
    }
}
