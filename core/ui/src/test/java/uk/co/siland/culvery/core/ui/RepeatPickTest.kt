package uk.co.siland.culvery.core.ui

import androidx.test.ext.junit.runners.AndroidJUnit4
import com.google.common.truth.Truth.assertThat
import java.time.DayOfWeek
import java.time.LocalDate
import org.junit.Test
import org.junit.runner.RunWith
import uk.co.siland.culvery.core.repeat.MAX_TIMES
import uk.co.siland.culvery.core.repeat.MIN_TIMES
import uk.co.siland.culvery.core.repeat.RepeatChoice
import uk.co.siland.culvery.core.repeat.RepeatEnd

/** Monday 19 October 2026. Robolectric backs `mutableStateOf`. */
@RunWith(AndroidJUnit4::class)
class RepeatPickTest {
    private val monday = LocalDate.of(2026, 10, 19)

    @Test
    fun choosingNeverClearsTheEndAndTheDays() {
        val pick = RepeatPick(RepeatChoice.Weekly).apply {
            toggleDay(DayOfWeek.WEDNESDAY, own = DayOfWeek.MONDAY)
            stopsAfter()
        }
        pick.choose(RepeatChoice.Never)
        assertThat(pick.end).isEqualTo(RepeatEnd.Never)
        assertThat(pick.extraDays).isEmpty()
        assertThat(pick.repeats).isFalse()
        assertThat(pick.rule(monday)).isNull()
    }

    @Test
    fun theOwnDayCannotBeUnticked() {
        val pick = RepeatPick(RepeatChoice.Weekly)
        pick.toggleDay(DayOfWeek.MONDAY, own = DayOfWeek.MONDAY)
        assertThat(pick.daysOn(monday)).containsExactly(DayOfWeek.MONDAY)
        pick.toggleDay(DayOfWeek.WEDNESDAY, own = DayOfWeek.MONDAY)
        pick.toggleDay(DayOfWeek.MONDAY, own = DayOfWeek.MONDAY)
        assertThat(pick.daysOn(monday)).containsExactly(DayOfWeek.MONDAY, DayOfWeek.WEDNESDAY)
        pick.toggleDay(DayOfWeek.WEDNESDAY, own = DayOfWeek.MONDAY)
        assertThat(pick.daysOn(monday)).containsExactly(DayOfWeek.MONDAY)
    }

    @Test
    fun theRuleCarriesTheDays() {
        val pick = RepeatPick(RepeatChoice.Weekly).apply {
            toggleDay(DayOfWeek.WEDNESDAY, own = DayOfWeek.MONDAY)
            stopsAfter()
        }
        assertThat(pick.rule(monday)).isEqualTo("RRULE:FREQ=WEEKLY;INTERVAL=1;BYDAY=MO,WE;COUNT=10")
        pick.choose(RepeatChoice.Fortnightly)
        assertThat(pick.rule(monday)).isEqualTo("RRULE:FREQ=WEEKLY;INTERVAL=2;BYDAY=MO,WE;COUNT=10")
        pick.choose(RepeatChoice.MonthlyOnWeekday)
        assertThat(pick.daysOn(monday)).isEmpty()
        assertThat(pick.rule(monday)).isEqualTo("RRULE:FREQ=MONTHLY;INTERVAL=1;BYDAY=3MO;COUNT=10")
    }

    @Test
    fun aRuleThePickerShowsReadsBackWhole() {
        val rule = "RRULE:FREQ=WEEKLY;BYDAY=MO,WE;COUNT=6"
        val pick = RepeatPick.read(rule, monday)
        assertThat(pick.keeping).isFalse()
        assertThat(pick.choice).isEqualTo(RepeatChoice.Weekly)
        assertThat(pick.end).isEqualTo(RepeatEnd.After(6))
        assertThat(pick.daysOn(monday)).containsExactly(DayOfWeek.MONDAY, DayOfWeek.WEDNESDAY)
        assertThat(pick.touched).isFalse()
        assertThat(pick.rule(monday)).isEqualTo(rule)
        assertThat(pick.rule(monday.plusWeeks(1))).isEqualTo("RRULE:FREQ=WEEKLY;INTERVAL=1;BYDAY=MO,WE;COUNT=6")
        pick.moreTimes()
        assertThat(pick.touched).isTrue()
        assertThat(pick.rule(monday)).isEqualTo("RRULE:FREQ=WEEKLY;INTERVAL=1;BYDAY=MO,WE;COUNT=7")
        assertThat(RepeatPick.read(null, monday).choice).isEqualTo(RepeatChoice.Never)
        assertThat(RepeatPick.read(null, monday).kept).isNull()
    }

    @Test
    fun aRuleItCannotShowIsKept() {
        listOf(
            "RRULE:FREQ=MONTHLY;INTERVAL=3",
            "RRULE:FREQ=WEEKLY;COUNT=100",
            "RRULE:FREQ=WEEKLY;UNTIL=20261231",
        ).forEach { rule ->
            val pick = RepeatPick.read(rule, monday)
            assertThat(pick.kept).isEqualTo(rule)
            assertThat(pick.keeping).isTrue()
            assertThat(pick.repeats).isTrue()
            assertThat(pick.rule(monday.plusDays(3))).isEqualTo(rule)
        }
    }

    @Test
    fun choosingARowLeavesTheKeptRule() {
        val rule = "RRULE:FREQ=MONTHLY;INTERVAL=3"
        val pick = RepeatPick.read(rule, monday)
        pick.choose(RepeatChoice.Weekly)
        assertThat(pick.keeping).isFalse()
        assertThat(pick.kept).isEqualTo(rule)
        assertThat(pick.rule(monday)).isEqualTo("RRULE:FREQ=WEEKLY;INTERVAL=1")
        pick.keep()
        assertThat(pick.keeping).isTrue()
        assertThat(pick.rule(monday)).isEqualTo(rule)
        RepeatPick(RepeatChoice.Weekly).apply { keep() }.let { assertThat(it.keeping).isFalse() }
    }

    @Test
    fun theCountStaysBetweenTwoAndFiftyTwo() {
        val pick = RepeatPick(RepeatChoice.Weekly).apply { stopsAfter() }
        assertThat(pick.end).isEqualTo(RepeatEnd.After(10))
        repeat(20) { pick.fewerTimes() }
        assertThat(pick.end).isEqualTo(RepeatEnd.After(MIN_TIMES))
        repeat(60) { pick.moreTimes() }
        assertThat(pick.end).isEqualTo(RepeatEnd.After(MAX_TIMES))
        pick.stopsAfter()
        assertThat(pick.end).isEqualTo(RepeatEnd.After(MAX_TIMES))
        pick.keepsGoing()
        assertThat(pick.end).isEqualTo(RepeatEnd.Never)
        RepeatPick().apply { stopsAfter() }.let { assertThat(it.end).isEqualTo(RepeatEnd.Never) }
    }

    @Test
    fun theStepperSaysTheCountAndTheLastDate() {
        val pick = RepeatPick(RepeatChoice.Weekly).apply { toggleDay(DayOfWeek.WEDNESDAY, own = DayOfWeek.MONDAY) }
        assertThat(pick.stepperLabel(monday)).isNull()
        pick.stopsAfter()
        assertThat(pick.stepperLabel(monday)).isEqualTo("10 times · last on Wed 18 Nov")
    }
}
