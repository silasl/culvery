package uk.co.siland.culvery.core.ui

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertIsNotSelected
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.google.common.truth.Truth.assertThat
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.ZoneId
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.GraphicsMode
import uk.co.siland.culvery.core.repeat.RepeatChoice
import uk.co.siland.culvery.core.ui.test.assertClickablesHaveRoleAndLabel

/** The picker on Monday 19 October 2026, in the dark theme. */
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class RepeatPickerTest {
    @get:Rule val compose = createComposeRule()

    private val monday = LocalDate.of(2026, 10, 19)
    private val london = ZoneId.of("Europe/London")

    private fun show(
        pick: RepeatPick,
        lockedLine: (RepeatChoice) -> String? = { null },
        daysLocked: Boolean = false,
        daysLockedLine: String? = null,
    ) = compose.setContent {
        CulveryTheme(dark = true) {
            HhRepeatPicker(pick, monday, monday, london, lockedLine = lockedLine, daysLocked = daysLocked, daysLockedLine = daysLockedLine)
        }
    }

    private fun node(tag: String) = compose.onNodeWithTag(tag)

    private fun part(tag: String) = compose.onNodeWithTag(tag, useUnmergedTree = true)

    @Test
    fun theDayChipsShowOnlyForWeeklyAndFortnightly() {
        val pick = RepeatPick()
        show(pick)
        node("repeat_day_MONDAY").assertDoesNotExist()
        node("repeat_Weekly").performClick()
        DayOfWeek.entries.forEach { node("repeat_day_${it.name}").assertExists() }
        node("repeat_day_MONDAY").assertTextEquals("Mon").assertIsSelected()
        node("repeat_day_WEDNESDAY").assertTextEquals("Wed").assertIsNotSelected().performClick()
        node("repeat_day_WEDNESDAY").assertIsSelected()
        node("repeat_Weekly").assertTextEquals("Every week, on Mon, Wed")
        node("repeat_Fortnightly").assertTextEquals("Every 2 weeks, on Mon, Wed").performClick()
        node("repeat_day_WEDNESDAY").assertIsSelected()
        node("repeat_MonthlyOnWeekday").performClick()
        node("repeat_day_MONDAY").assertDoesNotExist()
        node("repeat_Never").performClick()
        node("repeat_day_MONDAY").assertDoesNotExist()
        assertThat(pick.extraDays).isEmpty()
        compose.assertClickablesHaveRoleAndLabel()
    }

    @Test
    fun aLockedRowShowsItsLineAndCannotBeChosen() {
        val pick = RepeatPick()
        val picked = mutableListOf<RepeatChoice>()
        compose.setContent {
            CulveryTheme(dark = true) {
                HhRepeatPicker(pick, monday, monday, london, lockedLine = { "Too far".takeIf { _ -> it == RepeatChoice.Daily } }, onPicked = { picked += it })
            }
        }
        node("repeat_Daily").assertIsNotEnabled().performClick()
        part("repeat_Daily_note").assertTextEquals("Too far")
        part("repeat_Weekly_note").assertDoesNotExist()
        assertThat(pick.choice).isEqualTo(RepeatChoice.Never)
        node("repeat_Weekly").performClick()
        assertThat(pick.choice).isEqualTo(RepeatChoice.Weekly)
        assertThat(picked).containsExactly(RepeatChoice.Weekly)
    }

    @Test
    fun lockedDaysCannotBeTicked() {
        val pick = RepeatPick(RepeatChoice.Weekly)
        show(pick, daysLocked = true, daysLockedLine = "Too long")
        node("repeat_day_WEDNESDAY").performClick()
        assertThat(pick.extraDays).isEmpty()
        node("repeat_day_WEDNESDAY").assertIsNotSelected()
        part("repeat_days_note").assertTextEquals("Too long")
        compose.assertClickablesHaveRoleAndLabel()
    }

    @Test
    fun theKeptRowShowsOnlyWhenThereIsOne() {
        var pick by mutableStateOf(RepeatPick(RepeatChoice.Weekly))
        compose.setContent {
            CulveryTheme(dark = true) { HhRepeatPicker(pick, monday, monday, london) }
        }
        node("repeat_kept").assertDoesNotExist()
        pick = RepeatPick.read("RRULE:FREQ=MONTHLY;INTERVAL=3", monday)
        node("repeat_kept").assertTextEquals("As the event: Every 3 months").assertIsSelected()
        node("repeat_Never").assertIsNotSelected()
        node("ends_mode").assertDoesNotExist()
        node("repeat_Weekly").performClick()
        node("repeat_kept").assertIsNotSelected()
        node("repeat_Weekly").assertIsSelected()
        node("repeat_kept").performClick()
        assertThat(pick.keeping).isTrue()
        compose.assertClickablesHaveRoleAndLabel()
    }
}
