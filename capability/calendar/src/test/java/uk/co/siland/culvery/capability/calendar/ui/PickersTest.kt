package uk.co.siland.culvery.capability.calendar.ui

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.click
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTouchInput
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.google.common.truth.Truth.assertThat
import java.time.LocalDate
import java.time.LocalTime
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import uk.co.siland.culvery.core.ui.CulveryTheme

@RunWith(AndroidJUnit4::class)
class PickersTest {
    @get:Rule val compose = createComposeRule()

    /** Wednesday 23 September 2026, the hand-off's day. */
    private val today = LocalDate.of(2026, 9, 23)
    private val picked = mutableListOf<LocalDate>()
    private val set = mutableListOf<LocalTime>()
    private var cancelled = 0

    private fun showDates(selected: LocalDate = today) = compose.setContent {
        CulveryTheme(dark = true) {
            PickerLayer(onDismiss = { cancelled++ }) {
                DatePickerCard(today, selected, onPick = { picked += it }, onCancel = { cancelled++ })
            }
        }
    }

    private fun showTime(initial: LocalTime) = compose.setContent {
        CulveryTheme(dark = true) {
            PickerLayer(onDismiss = { cancelled++ }) {
                TimePickerCard(initial, onSet = { set += it }, onCancel = { cancelled++ })
            }
        }
    }

    @Test
    fun pagesAreFiveWeeksFromThisWeeksMonday() {
        assertThat(firstPageStart(today)).isEqualTo(LocalDate.of(2026, 9, 21))
        assertThat(pageRangeLabel(pageStart(0, today))).isEqualTo("Mon 21 Sep – Sun 25 Oct")
        assertThat(pageOf(LocalDate.of(2026, 10, 25), today)).isEqualTo(0)
        assertThat(pageOf(LocalDate.of(2026, 10, 26), today)).isEqualTo(1)
        assertThat(pageOf(LocalDate.of(2026, 9, 20), today)).isEqualTo(-1)
        assertThat(dateCellLabel(LocalDate.of(2026, 10, 1))).isEqualTo("1 Oct")
        assertThat(dateCellLabel(LocalDate.of(2026, 10, 2))).isEqualTo("2")
    }

    @Test
    fun hoursWrapWithoutChangingTheDayAndMinutesStepByQuarters() {
        assertThat(hourStep(23, up = true)).isEqualTo(0)
        assertThat(hourStep(0, up = false)).isEqualTo(23)
        assertThat(minuteStep(45, up = true)).isEqualTo(0)
        assertThat(minuteStep(0, up = false)).isEqualTo(45)
        // An off-quarter start moves to the next or previous quarter first.
        assertThat(minuteStep(37, up = true)).isEqualTo(45)
        assertThat(minuteStep(37, up = false)).isEqualTo(30)
    }

    @Test
    fun aPastDayCanBePicked() {
        showDates()
        compose.onNodeWithTag("date_range").assertTextEquals("Mon 21 Sep – Sun 25 Oct")
        compose.onNodeWithTag("date_cell_2026-09-21").performClick()
        assertThat(picked).containsExactly(LocalDate.of(2026, 9, 21))
    }

    @Test
    fun theArrowsPageByFiveWeeks() {
        showDates()
        compose.onNodeWithTag("date_next").performClick()
        compose.onNodeWithTag("date_range").assertTextEquals("Mon 26 Oct – Sun 29 Nov")
        compose.onNodeWithTag("date_cell_2026-11-10").performClick()
        compose.onNodeWithTag("date_prev").performClick()
        compose.onNodeWithTag("date_prev").performClick()
        compose.onNodeWithTag("date_range").assertTextEquals("Mon 17 Aug – Sun 20 Sep")
        assertThat(picked).containsExactly(LocalDate.of(2026, 11, 10))
    }

    @Test
    fun thePickerOpensOnThePageHoldingTheSelectedDay() {
        showDates(selected = LocalDate.of(2026, 11, 10))
        compose.onNodeWithTag("date_range").assertTextEquals("Mon 26 Oct – Sun 29 Nov")
        compose.onNodeWithTag("date_cell_2026-11-10").assertExists()
    }

    @Test
    fun cancelAndTheScrimPickNothing() {
        showDates()
        compose.onNodeWithTag("picker_cancel").performClick()
        // The scrim's top-left corner is clear of the centred card.
        compose.onNodeWithTag("picker_scrim").performTouchInput { click(Offset(10f, 10f)) }
        assertThat(cancelled).isEqualTo(2)
        assertThat(picked).isEmpty()
    }

    @Test
    fun theTimeStepsWrapAndSetTime() {
        showTime(LocalTime.of(23, 45))
        compose.onNodeWithTag("hour_up").performClick()
        compose.onNodeWithTag("hour_value").assertTextEquals("00")
        compose.onNodeWithTag("minute_up").performClick()
        compose.onNodeWithTag("minute_value").assertTextEquals("00")
        compose.onNodeWithTag("hour_value").assertTextEquals("00")
        compose.onNodeWithTag("picker_set").performClick()
        assertThat(set).containsExactly(LocalTime.of(0, 0))
    }

    @Test
    fun anOffQuarterTimeShowsAsItIsThenStepsToAQuarter() {
        showTime(LocalTime.of(19, 37))
        compose.onNodeWithTag("minute_value").assertTextEquals("37")
        compose.onNodeWithTag("minute_down").performClick()
        compose.onNodeWithTag("minute_value").assertTextEquals("30")
        compose.onNodeWithTag("hour_down").performClick()
        compose.onNodeWithTag("picker_set").performClick()
        assertThat(set).containsExactly(LocalTime.of(18, 30))
    }

    @Test
    fun cancelSetsNoTime() {
        showTime(LocalTime.of(16, 15))
        compose.onNodeWithTag("hour_up").performClick()
        compose.onNodeWithTag("picker_cancel").performClick()
        assertThat(set).isEmpty()
        assertThat(cancelled).isEqualTo(1)
    }
}
