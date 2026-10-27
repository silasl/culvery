package uk.co.siland.culvery.capability.holiday.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.google.common.truth.Truth.assertThat
import java.time.LocalDate
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import uk.co.siland.culvery.capability.holiday.FakeEvents
import uk.co.siland.culvery.capability.holiday.HolidayActions
import uk.co.siland.culvery.capability.holiday.HouseRig
import uk.co.siland.culvery.capability.holiday.TestAccess
import uk.co.siland.culvery.core.household.Role as HouseRole
import uk.co.siland.culvery.core.plugin.LocalOverlayHost
import uk.co.siland.culvery.core.plugin.test.TestOverlay
import uk.co.siland.culvery.core.ui.Culvery
import uk.co.siland.culvery.core.ui.CulveryTheme
import uk.co.siland.culvery.core.ui.HhTrayColumn
import uk.co.siland.culvery.core.ui.test.assertClickablesHaveRoleAndLabel
import uk.co.siland.culvery.core.ui.test.assertNothingReadTwice
import uk.co.siland.culvery.core.ui.test.waitFor

/** 11 §5.2: the tile's tap, the PIN, and the "When are you back?" sheet it opens. */
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = "w1280dp-h800dp-land-hdpi")
class HolidaySheetTest {
    @get:Rule val compose = createComposeRule()
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val rig = HouseRig(scope)
    private val access = TestAccess(HouseRole.ADULT)
    private val events = FakeEvents(listOf(HouseRig.CORNWALL))
    private val overlay = TestOverlay()
    private val today = HouseRig.TODAY

    @Before
    fun load() {
        runBlocking { rig.loaded() }
    }

    @After
    fun stop() = scope.cancel()

    private fun has(tag: String) = compose.onAllNodes(hasTestTag(tag)).fetchSemanticsNodes().isNotEmpty()

    private fun shows(text: String) = compose.onAllNodes(hasText(text)).fetchSemanticsNodes().isNotEmpty()

    private fun open() {
        val actions = HolidayActions(access, rig.house, events, { today })
        compose.setContent {
            CulveryTheme(dark = true) {
                CompositionLocalProvider(LocalOverlayHost provides overlay) {
                    Row(Modifier.background(Culvery.colors.bg)) {
                        HhTrayColumn("One-tap", listOf("holiday" to { HolidayTileHost(actions, rig.house) }), Modifier.width(333.dp))
                        overlay.content?.invoke()
                    }
                }
            }
        }
        compose.onNodeWithTag("holiday_tile").performClick()
        compose.waitFor { has("holiday_sheet") }
    }

    private fun day(date: LocalDate) = compose.onNodeWithTag("day_$date")

    /** Fails if the calendar's date or its line is missing, the group isn't one spoken item, or Change date isn't a button. */
    @Test
    fun aCalendarPickFillsTheDate() {
        open()
        compose.waitFor { has("holiday_steps") }
        compose.onNodeWithText("Sunday 1 November", useUnmergedTree = true).assertExists()
        compose.onNodeWithTag("holiday_from_calendar").assertTextEquals("From the calendar: Cornwall, ends Sunday.")
        compose.onNode(hasContentDescription("When are you back? Sunday 1 November.")).assertExists()
        assertThat(compose.onNodeWithTag("holiday_change_date").fetchSemanticsNode().config[SemanticsProperties.Role]).isEqualTo(Role.Button)
        compose.onNodeWithText("Change date", useUnmergedTree = true).assertExists()
        compose.onNodeWithText("Holiday mode will:", useUnmergedTree = true).assertExists()
        compose.assertNothingReadTwice()
        compose.assertClickablesHaveRoleAndLabel()
    }

    @Test
    fun noPickMeansTomorrow() {
        events.events = emptyList()
        open()
        compose.onNodeWithText("Thursday 29 October", useUnmergedTree = true).assertExists()
        compose.onNodeWithTag("holiday_from_calendar").assertTextEquals("Pick the day you’re back.")
        compose.onNode(hasContentDescription("When are you back? Thursday 29 October.")).assertExists()
        compose.assertClickablesHaveRoleAndLabel()
    }

    /** 11 §5.2: tomorrow to 90 days ahead. Fails if today or the 91st day can be tapped, or a pick leaves the old date in the steps. */
    @Test
    fun changeDateOffersTomorrowTo90Days() {
        events.events = emptyList()
        open()
        compose.onNodeWithTag("holiday_change_date").performClick()
        compose.waitFor { has("day_$today") }
        day(today).assertIsNotEnabled()
        day(today.plusDays(1)).assertIsEnabled()
        repeat(3) { compose.onNodeWithTag("month_next").performClick() }
        day(today.plusDays(90)).assertIsEnabled()
        day(today.plusDays(91)).assertIsNotEnabled()
        compose.assertClickablesHaveRoleAndLabel()

        day(today.plusDays(90)).performClick()
        compose.waitFor { has("holiday_steps") }

        compose.onNodeWithText("Tuesday 26 January", useUnmergedTree = true).assertExists()
        compose.onNode(hasContentDescription("When are you back? Tuesday 26 January.")).assertExists()
        compose.waitFor { shows("Put the heating on Eco now, and take it off Eco at 00:01 on Tuesday 26 January.") }
        compose.onNodeWithText("Run the hoover at 10:00 on Monday 25 January.", useUnmergedTree = true).assertExists()
    }

    /** 11 §5.2 "lists only the steps that will happen": fails if a hoover that isn't set up still has its line. */
    @Test
    fun onlyTheStepsThatWillHappenAreListed() {
        rig.hoover.readyNow.value = false
        open()
        compose.waitFor { shows("Switch on Holiday lights now.") }
        compose.onNodeWithText("Put the heating on Eco now, and take it off Eco at 00:01 on Sunday 1 November.", useUnmergedTree = true).assertExists()
        compose.onNodeWithText("Keep the doorbell quiet. Rings and motion still show in the Door tab.", useUnmergedTree = true).assertExists()
        compose.onNodeWithText("Turn the screen off until someone taps it.", useUnmergedTree = true).assertExists()
        compose.onAllNodes(hasText("Run the hoover", substring = true), useUnmergedTree = true).assertCountEquals(0)
    }

    /** Review Focus 4. Fails if Turn on doesn't turn on once with the date shown, or the sheet closes before the change is made. */
    @Test
    fun turnOnTurnsOnAndCloses() {
        val gate = CompletableDeferred<Unit>()
        rig.lights.gate = gate
        open()
        compose.waitFor { shows("Switch on Holiday lights now.") }
        compose.onNodeWithTag("holiday_turn_on").performClick()
        compose.waitFor { rig.lights.calls.size == 1 }
        compose.onNode(hasText("Turning on…") and hasAnyAncestor(hasTestTag("holiday_turn_on")), useUnmergedTree = true).assertExists()
        compose.onNodeWithTag("holiday_turn_on").assertIsNotEnabled()
        compose.onNodeWithTag("holiday_cancel").assertIsNotEnabled()
        assertThat(overlay.content).isNotNull()

        gate.complete(Unit)
        compose.waitFor { overlay.content == null }

        assertThat(rig.lights.calls).containsExactly(true)
        assertThat(rig.heating.calls).containsExactly(true)
        assertThat(rig.house.record.value!!.on).isTrue()
        assertThat(rig.house.record.value!!.back).isEqualTo(today.plusDays(4))
        assertThat(access.asked).containsExactly(listOf("holiday.mode"))
    }

    /** Review Focus 4. Fails if Cancel turns anything on or leaves the sheet open. */
    @Test
    fun aCancelledSheetChangesNothing() {
        open()
        compose.onNodeWithTag("holiday_cancel").performClick()
        compose.waitFor { overlay.content == null }
        assertThat(rig.house.holiday.value).isFalse()
        assertThat(rig.lights.calls).isEmpty()
        assertThat(rig.heating.calls).isEmpty()
    }

    /** Fails if a day tapped on the grid while Holiday mode is turning on changes the date being turned on. */
    @Test
    fun aDayTappedWhileTurningOnChangesNothing() {
        events.events = emptyList()
        val gate = CompletableDeferred<Unit>()
        rig.lights.gate = gate
        open()
        compose.onNodeWithTag("holiday_change_date").performClick()
        compose.waitFor { has("day_${today.plusDays(2)}") }
        compose.onNodeWithTag("holiday_turn_on").performClick()
        compose.waitFor { rig.lights.calls.size == 1 }
        day(today.plusDays(2)).performClick()
        compose.waitForIdle()
        compose.onNode(hasContentDescription("When are you back? Thursday 29 October.")).assertExists()
        gate.complete(Unit)
        compose.waitFor { overlay.content == null }
        assertThat(rig.house.record.value!!.back).isEqualTo(today.plusDays(1))
    }
}
