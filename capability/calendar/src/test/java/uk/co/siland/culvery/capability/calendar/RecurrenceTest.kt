package uk.co.siland.culvery.capability.calendar

import com.google.common.truth.Truth.assertThat
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import org.junit.Assert.assertThrows
import org.junit.Test

/** 5 §A.4, 9 §3.1: what Save writes for each choice, both UNTIL forms, the words from the day, and the dates a count lands on. */
class RecurrenceTest {
    private val london = ZoneId.of("Europe/London")

    // Saturday 26 September 2026: the 4th Saturday, and also the last.
    private val saturday = LocalDate.of(2026, 9, 26)

    // Thursday 29 October 2026: a 5th Thursday.
    private val thursday = LocalDate.of(2026, 10, 29)

    private fun rule(choice: RepeatChoice, end: RepeatEnd = RepeatEnd.Never, allDay: Boolean = false, day: LocalDate = saturday) =
        recurrenceRule(choice, end, allDay, london, day)

    @Test
    fun eachChoiceWritesItsFrequencyAndInterval() {
        assertThat(RepeatChoice.entries.map { rule(it) }).containsExactly(
            null,
            "RRULE:FREQ=DAILY;INTERVAL=1",
            "RRULE:FREQ=WEEKLY;INTERVAL=1",
            "RRULE:FREQ=WEEKLY;INTERVAL=2",
            "RRULE:FREQ=MONTHLY;INTERVAL=1",
            "RRULE:FREQ=YEARLY;INTERVAL=1",
            "RRULE:FREQ=MONTHLY;INTERVAL=1;BYDAY=4SA",
        ).inOrder()
        assertThat(RepeatChoice.entries.map { it.label })
            .containsExactly("Never", "Every day", "Every week", "Every 2 weeks", "Every month", "Every year", "Every month").inOrder()
    }

    @Test
    fun theWordingFollowsTheDay() {
        assertThat(EDITOR_REPEATS.map { it.wording(saturday) }).containsExactly(
            "Doesn’t repeat",
            "Every Saturday",
            "Every 2 weeks, on Saturday",
            "Every month, on the 4th Saturday",
            "Every year, on 26 September",
            "Every day",
        ).inOrder()
    }

    @Test
    fun aFifthWeekdayIsTheLast() {
        assertThat(RepeatChoice.MonthlyOnWeekday.wording(thursday)).isEqualTo("Every month, on the last Thursday")
        assertThat(rule(RepeatChoice.MonthlyOnWeekday, day = thursday)).isEqualTo("RRULE:FREQ=MONTHLY;INTERVAL=1;BYDAY=-1TH")
    }

    @Test
    fun theFourthWeekdayIsFourthEvenWhenItIsAlsoTheLast() {
        assertThat(nthWeekday(saturday)).isEqualTo(4)
        assertThat(rule(RepeatChoice.MonthlyOnWeekday)).contains(";BYDAY=4SA")
    }

    @Test
    fun aMonthlyOnWeekdayRule() {
        assertThat(rule(RepeatChoice.MonthlyOnWeekday, RepeatEnd.After(12))).isEqualTo("RRULE:FREQ=MONTHLY;INTERVAL=1;BYDAY=4SA;COUNT=12")
    }

    @Test
    fun ordinalsReadAsWords() {
        assertThat(listOf(1, 2, 3, 4, -1, 11, 12, 13, 21, 22, 23, 31).map(::ordinal))
            .containsExactly("1st", "2nd", "3rd", "4th", "last", "11th", "12th", "13th", "21st", "22nd", "23rd", "31st").inOrder()
    }

    @Test
    fun occurrencesFollowTheRule() {
        assertThat(occurrences(RepeatChoice.Weekly, saturday, 12).last()).isEqualTo(LocalDate.of(2026, 12, 12))
        assertThat(occurrences(RepeatChoice.MonthlyOnWeekday, saturday, 3))
            .containsExactly(saturday, LocalDate.of(2026, 10, 24), LocalDate.of(2026, 11, 28)).inOrder()
        assertThat(occurrences(RepeatChoice.MonthlyOnWeekday, thursday, 3))
            .containsExactly(thursday, LocalDate.of(2026, 11, 26), LocalDate.of(2026, 12, 31)).inOrder()
        assertThat(occurrences(RepeatChoice.Yearly, LocalDate.of(2028, 2, 29), 2))
            .containsExactly(LocalDate.of(2028, 2, 29), LocalDate.of(2032, 2, 29)).inOrder()
        assertThat(occurrences(RepeatChoice.Daily, saturday, 3))
            .containsExactly(saturday, saturday.plusDays(1), saturday.plusDays(2)).inOrder()
    }

    @Test
    fun theSeriesStartIsReadFromItsOwnLine() {
        assertThat(seriesStart("DTSTART:20260926\nRRULE:FREQ=WEEKLY;COUNT=12")).isEqualTo(saturday)
        assertThat(seriesStart("RRULE:FREQ=WEEKLY")).isNull()
        assertThat(seriesStart(null)).isNull()
        assertThat(seriesStart("DTSTART:2026\nRRULE:FREQ=WEEKLY")).isNull()
    }

    @Test
    fun aRuleIsReadBackAsTheChoiceThatWroteIt() {
        for (choice in EDITOR_REPEATS - RepeatChoice.Never) {
            for (day in listOf(saturday, thursday)) {
                assertThat(choiceOf(rule(choice, day = day)!!, day)).isEqualTo(choice)
                assertThat(choiceOf(withSeriesStart(rule(choice, RepeatEnd.After(5), day = day)!!, day), day)).isEqualTo(choice)
            }
        }
        // By date, or by a weekday the start isn't on, is nothing the editor makes.
        assertThat(choiceOf("RRULE:FREQ=MONTHLY;BYMONTHDAY=26", saturday)).isNull()
        assertThat(choiceOf("RRULE:FREQ=MONTHLY;BYDAY=-1SA", saturday)).isNull()
        assertThat(choiceOf("RRULE:FREQ=WEEKLY;BYDAY=MO,WE", saturday)).isNull()
    }

    @Test
    fun anAllDaySeriesEndsOnADate() {
        assertThat(rule(RepeatChoice.Weekly, RepeatEnd.OnDate(LocalDate.of(2026, 12, 31)), allDay = true))
            .isEqualTo("RRULE:FREQ=WEEKLY;INTERVAL=1;UNTIL=20261231")
    }

    @Test
    fun aTimedSeriesEndsAtTheEndOfThatDayInTheHouseholdZoneAsUtc() {
        assertThat(rule(RepeatChoice.Weekly, RepeatEnd.OnDate(LocalDate.of(2026, 12, 31))))
            .isEqualTo("RRULE:FREQ=WEEKLY;INTERVAL=1;UNTIL=20261231T235959Z")
        assertThat(rule(RepeatChoice.Daily, RepeatEnd.OnDate(LocalDate.of(2027, 6, 30))))
            .isEqualTo("RRULE:FREQ=DAILY;INTERVAL=1;UNTIL=20270630T225959Z")
    }

    /** The change happens in the small hours, so the end of the day is in the evening's offset. */
    @Test
    fun onTheDaysTheClocksChangeUntilUsesThatEveningsOffset() {
        assertThat(rule(RepeatChoice.Weekly, RepeatEnd.OnDate(LocalDate.of(2026, 10, 25))))
            .isEqualTo("RRULE:FREQ=WEEKLY;INTERVAL=1;UNTIL=20261025T235959Z")
        assertThat(rule(RepeatChoice.Weekly, RepeatEnd.OnDate(LocalDate.of(2026, 3, 29))))
            .isEqualTo("RRULE:FREQ=WEEKLY;INTERVAL=1;UNTIL=20260329T225959Z")
    }

    /** Never "Yes": whatever either editor writes, the detail sheet's Repeats row starts with the words for it. */
    @Test
    fun whatSaveWritesReadsBackInItsWords() {
        val starts = listOf(
            EventTime.AllDay(LocalDate.of(2026, 9, 23)) to true,
            EventTime.Timed(Instant.parse("2026-09-23T17:00:00Z")) to false,
        )
        val day = LocalDate.of(2026, 9, 23)
        for ((start, allDay) in starts) {
            for (choice in RepeatChoice.entries - RepeatChoice.Never) {
                for (end in listOf(RepeatEnd.Never, RepeatEnd.OnDate(LocalDate.of(2026, 12, 31)), RepeatEnd.After(5))) {
                    assertThat(repeatsLabel(rule(choice, end, allDay, day), start, london)).startsWith(choice.wording(day))
                }
            }
        }
    }

    @Test
    fun aCountedSeriesWritesCountAndNoUntil() {
        assertThat(rule(RepeatChoice.Weekly, RepeatEnd.After(5))).isEqualTo("RRULE:FREQ=WEEKLY;INTERVAL=1;COUNT=5")
    }

    /** Pin: passes without the change. */
    @Test
    fun neverBothUntilAndCountForAnyRepeatEnd() {
        val ends = listOf(RepeatEnd.Never, RepeatEnd.OnDate(LocalDate.of(2026, 12, 31)), RepeatEnd.After(2), RepeatEnd.After(52))
        for (choice in RepeatChoice.entries - RepeatChoice.Never) {
            for (end in ends) {
                for (allDay in listOf(true, false)) {
                    val rule = rule(choice, end, allDay)!!
                    assertThat(rule.contains("UNTIL=") && rule.contains("COUNT=")).isFalse()
                }
            }
        }
    }

    @Test
    fun aCountOutsideTheRangeCannotBeBuilt() {
        for (times in listOf(1, 53, 0)) {
            assertThrows(IllegalArgumentException::class.java) { RepeatEnd.After(times) }
        }
    }

    @Test
    fun theChipsAreSplitIntoCommonAndMore() {
        assertThat(RepeatChoice.COMMON).containsExactly(RepeatChoice.Never, RepeatChoice.Weekly, RepeatChoice.Fortnightly).inOrder()
        assertThat(RepeatChoice.MORE).containsExactly(RepeatChoice.Daily, RepeatChoice.Monthly, RepeatChoice.Yearly).inOrder()
    }
}
