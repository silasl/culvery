package uk.co.siland.culvery.core.repeat

import com.google.common.truth.Truth.assertThat
import java.time.DayOfWeek
import java.time.Duration
import java.time.LocalDate
import org.junit.Assert.assertThrows
import org.junit.Test

/** 5 §A.4, 9 §3.1: what Save writes for each choice, the words from the day, and the dates a count lands on. */
class RecurrenceTest {
    // Saturday 26 September 2026: the 4th Saturday, and also the last.
    private val saturday = LocalDate.of(2026, 9, 26)

    // Thursday 29 October 2026: a 5th Thursday.
    private val thursday = LocalDate.of(2026, 10, 29)

    private fun rule(choice: RepeatChoice, end: RepeatEnd = RepeatEnd.Never, day: LocalDate = saturday) =
        recurrenceRule(choice, end, day)

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

    /** 20 §4.4: a series with a date in every period has its last date worked out, not listed. */
    @Test
    fun theDirectLastDateMatchesTheListedOne() {
        listOf(
            Triple(RepeatChoice.Daily, saturday, nthWeekday(saturday)),
            Triple(RepeatChoice.Weekly, saturday, nthWeekday(saturday)),
            Triple(RepeatChoice.Fortnightly, saturday, nthWeekday(saturday)),
            Triple(RepeatChoice.MonthlyOnWeekday, thursday, LAST),
        ).forEach { (choice, first, nth) ->
            assertThat(lastOccurrence(choice, first, 10_000, nth)).isEqualTo(occurrences(choice, first, 10_000, nth).last())
        }
    }

    /** 20 §4.4: a series that can skip a month or a year is counted, up to the cap. */
    @Test
    fun underTheCapTheLastDateIsTheListedOne() {
        val the31st = LocalDate.of(2026, 10, 31)
        val leapDay = LocalDate.of(2028, 2, 29)
        assertThat(lastOccurrence(RepeatChoice.Monthly, the31st, 1_000, nthWeekday(the31st)))
            .isEqualTo(occurrences(RepeatChoice.Monthly, the31st, 1_000).last())
        assertThat(lastOccurrence(RepeatChoice.Yearly, leapDay, 1_000, nthWeekday(leapDay)))
            .isEqualTo(occurrences(RepeatChoice.Yearly, leapDay, 1_000).last())
        assertThat(lastOccurrence(RepeatChoice.Monthly, the31st, COUNTED_LAST_CAP + 1, nthWeekday(the31st))).isNull()
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
        assertThat(choiceOf("RRULE:FREQ=MONTHLY;BYDAY=-1SA", LocalDate.of(2026, 9, 19))).isNull()
        assertThat(choiceOf("RRULE:FREQ=WEEKLY;BYDAY=MO,WE", saturday)).isNull()
    }

    @Test
    fun aPhonesLastWeekdayIsMonthlyOnWeekdayWithNthMinusOne() {
        val lastThursday = LocalDate.of(2026, 9, 24)
        val rule = "RRULE:FREQ=MONTHLY;INTERVAL=1;BYDAY=-1TH"
        assertThat(choiceOf(rule, lastThursday)).isEqualTo(RepeatChoice.MonthlyOnWeekday)
        assertThat(nthOf(rule, lastThursday)).isEqualTo(-1)
        assertThat(choiceOf(rule, LocalDate.of(2026, 9, 17))).isNull()
        assertThat(RepeatChoice.MonthlyOnWeekday.wording(lastThursday, nthOf(rule, lastThursday))).isEqualTo("Every month, on the last Thursday")
        assertThat(nthOf("RRULE:FREQ=MONTHLY;INTERVAL=1;BYDAY=4TH", lastThursday)).isEqualTo(4)
        assertThat(recurrenceRule(RepeatChoice.MonthlyOnWeekday, RepeatEnd.Never, lastThursday, nth = -1)).isEqualTo(rule)
    }

    @Test
    fun aCountedSeriesWritesCountAndNoUntil() {
        assertThat(rule(RepeatChoice.Weekly, RepeatEnd.After(5))).isEqualTo("RRULE:FREQ=WEEKLY;INTERVAL=1;COUNT=5")
    }

    /** Pin: passes without the change. */
    @Test
    fun saveNeverWritesAnUntil() {
        val ends = listOf(RepeatEnd.Never, RepeatEnd.After(2), RepeatEnd.After(52))
        for (choice in RepeatChoice.entries - RepeatChoice.Never) {
            for (end in ends) {
                assertThat(rule(choice, end)!!).doesNotContain("UNTIL=")
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
    fun eachRepeatFitsOnlySpansThatEndBeforeTheNextStarts() {
        fun fits(choice: RepeatChoice, length: Duration) = choice.fitsLength(length)
        assertThat(fits(RepeatChoice.Daily, Duration.ofHours(24))).isTrue()
        assertThat(fits(RepeatChoice.Daily, Duration.ofHours(25))).isFalse()
        assertThat(fits(RepeatChoice.Weekly, Duration.ofDays(7))).isTrue()
        assertThat(fits(RepeatChoice.Weekly, Duration.ofDays(8))).isFalse()
        assertThat(fits(RepeatChoice.Fortnightly, Duration.ofDays(14))).isTrue()
        assertThat(fits(RepeatChoice.Fortnightly, Duration.ofDays(15))).isFalse()
        assertThat(fits(RepeatChoice.MonthlyOnWeekday, Duration.ofDays(28))).isTrue()
        assertThat(fits(RepeatChoice.MonthlyOnWeekday, Duration.ofDays(29))).isFalse()
        assertThat(fits(RepeatChoice.Yearly, Duration.ofDays(60))).isTrue()
        assertThat(fits(RepeatChoice.Never, Duration.ofDays(60))).isTrue()
    }

    // Monday 21 September 2026.
    private val monday = LocalDate.of(2026, 9, 21)

    @Test
    fun severalDaysAreWrittenMondayFirst() {
        val rule = recurrenceRule(RepeatChoice.Weekly, RepeatEnd.After(6), monday, days = setOf(DayOfWeek.WEDNESDAY))
        assertThat(rule).isEqualTo("RRULE:FREQ=WEEKLY;INTERVAL=1;BYDAY=MO,WE;COUNT=6")
        assertThat(recurrenceRule(RepeatChoice.Fortnightly, RepeatEnd.Never, saturday, days = setOf(DayOfWeek.MONDAY)))
            .isEqualTo("RRULE:FREQ=WEEKLY;INTERVAL=2;BYDAY=MO,SA")
    }

    @Test
    fun oneDayWritesNoByDay() {
        assertThat(recurrenceRule(RepeatChoice.Weekly, RepeatEnd.Never, monday, days = setOf(DayOfWeek.MONDAY)))
            .isEqualTo("RRULE:FREQ=WEEKLY;INTERVAL=1")
        assertThat(recurrenceRule(RepeatChoice.Weekly, RepeatEnd.Never, monday)).isEqualTo("RRULE:FREQ=WEEKLY;INTERVAL=1")
    }

    @Test
    fun aListWithTheStartsDayReadsBackAsWeekly() {
        assertThat(choiceOf("RRULE:FREQ=WEEKLY;BYDAY=MO,WE", monday)).isEqualTo(RepeatChoice.Weekly)
        assertThat(choiceOf("RRULE:FREQ=WEEKLY;INTERVAL=2;BYDAY=MO,WE", monday)).isEqualTo(RepeatChoice.Fortnightly)
        assertThat(daysOf("RRULE:FREQ=WEEKLY;BYDAY=WE,MO", monday)).containsExactly(DayOfWeek.MONDAY, DayOfWeek.WEDNESDAY)
        assertThat(daysOf("RRULE:FREQ=WEEKLY;BYDAY=MO", monday)).isEmpty()
    }

    @Test
    fun aListWithoutTheStartsDayIsNoChoice() {
        assertThat(choiceOf("RRULE:FREQ=WEEKLY;BYDAY=TU,WE", monday)).isNull()
        assertThat(choiceOf("RRULE:FREQ=WEEKLY;BYDAY=MO,XX", monday)).isNull()
        assertThat(daysOf("RRULE:FREQ=WEEKLY;BYDAY=TU,WE", monday)).isEmpty()
    }

    @Test
    fun severalDaysAreWorded() {
        fun words(choice: RepeatChoice, vararg days: DayOfWeek) = choice.wording(monday, days = days.toSet())
        assertThat(words(RepeatChoice.Weekly, DayOfWeek.WEDNESDAY)).isEqualTo("Every week, on Mon, Wed")
        assertThat(words(RepeatChoice.Weekly, DayOfWeek.TUESDAY, DayOfWeek.FRIDAY, DayOfWeek.THURSDAY, DayOfWeek.WEDNESDAY))
            .isEqualTo("Every week, on Mon to Fri")
        assertThat(words(RepeatChoice.Weekly, *DayOfWeek.entries.toTypedArray())).isEqualTo("Every day")
        assertThat(words(RepeatChoice.Fortnightly, DayOfWeek.WEDNESDAY)).isEqualTo("Every 2 weeks, on Mon, Wed")
        assertThat(words(RepeatChoice.Weekly, DayOfWeek.TUESDAY)).isEqualTo("Every week, on Mon, Tue")
        assertThat(words(RepeatChoice.Weekly, DayOfWeek.MONDAY)).isEqualTo("Every Monday")
        // Sat, Sun, Mon is not a run: the week runs Monday to Sunday.
        assertThat(RepeatChoice.Weekly.wording(saturday, days = setOf(DayOfWeek.SUNDAY, DayOfWeek.MONDAY)))
            .isEqualTo("Every week, on Mon, Sat, Sun")
    }

    @Test
    fun severalDaysCountThroughTheWeeks() {
        val days = setOf(DayOfWeek.WEDNESDAY)
        assertThat(occurrences(RepeatChoice.Weekly, monday, 4, days = days)).containsExactly(
            LocalDate.of(2026, 9, 21), LocalDate.of(2026, 9, 23), LocalDate.of(2026, 9, 28), LocalDate.of(2026, 9, 30),
        ).inOrder()
        assertThat(occurrences(RepeatChoice.Fortnightly, monday, 3, days = days)).containsExactly(
            LocalDate.of(2026, 9, 21), LocalDate.of(2026, 9, 23), LocalDate.of(2026, 10, 5),
        ).inOrder()
    }

    @Test
    fun aCountedListsLastDate() {
        val days = setOf(DayOfWeek.WEDNESDAY)
        val nth = nthWeekday(monday)
        assertThat(lastOccurrence(RepeatChoice.Weekly, monday, 6, nth, days)).isEqualTo(LocalDate.of(2026, 10, 7))
        assertThat(lastOccurrence(RepeatChoice.Weekly, monday, COUNTED_LAST_CAP + 1, nth, days)).isNull()
    }

    @Test
    fun severalDaysFitOnlyADay() {
        val two = setOf(DayOfWeek.MONDAY, DayOfWeek.WEDNESDAY)
        assertThat(RepeatChoice.Weekly.fitsLength(Duration.ofDays(1), two)).isTrue()
        assertThat(RepeatChoice.Weekly.fitsLength(Duration.ofDays(2), two)).isFalse()
        assertThat(RepeatChoice.Fortnightly.fitsLength(Duration.ofDays(2), two)).isFalse()
        assertThat(RepeatChoice.Fortnightly.fitsLength(Duration.ofDays(10))).isTrue()
    }
}
