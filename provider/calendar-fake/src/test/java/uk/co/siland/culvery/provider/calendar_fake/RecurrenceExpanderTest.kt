package uk.co.siland.culvery.provider.calendar_fake

import com.google.common.truth.Truth.assertThat
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import org.junit.Test
import uk.co.siland.culvery.capability.calendar.EventTime
import uk.co.siland.culvery.capability.calendar.RemoteEvent
import uk.co.siland.culvery.capability.calendar.RepeatChoice
import uk.co.siland.culvery.capability.calendar.RepeatEnd
import uk.co.siland.culvery.capability.calendar.occurrences
import uk.co.siland.culvery.capability.calendar.recurrenceRule
import uk.co.siland.culvery.capability.calendar.withSeriesStart

/** The fake provider's expansion of a series the tablet wrote, COUNT and UNTIL included (9 §4.8). */
class RecurrenceExpanderTest {
    private val london = ZoneId.of("Europe/London")

    // Friday 2 October 2026, 18:00 London (BST), an hour long.
    private val friday = LocalDate.of(2026, 10, 2)

    private fun timedSeries(rule: String, first: LocalDate = friday, at: LocalTime = LocalTime.of(18, 0)): RemoteEvent {
        val from = first.atTime(at).atZone(london).toInstant()
        return RemoteEvent("s1", "Swim", EventTime.Timed(from), EventTime.Timed(from.plusSeconds(3600)), recurring = true, recurrenceRule = rule)
    }

    private fun allDaySeries(rule: String, first: LocalDate, days: Long) = RemoteEvent(
        "s2", "Camp", EventTime.AllDay(first), EventTime.AllDay(first.plusDays(days)), recurring = true, recurrenceRule = rule,
    )

    private fun RemoteEvent.localDay(): LocalDate = (start as EventTime.Timed).instant.atZone(london).toLocalDate()

    private fun expand(series: RemoteEvent, lastDay: LocalDate) = RecurrenceExpander.expand(series, london, lastDay)

    private val weeklyFive = "RRULE:FREQ=WEEKLY;INTERVAL=1;COUNT=5"

    /** Pin: passes without the change. */
    @Test
    fun theWindowEndingOnTheLastOccurrenceKeepsAllOfThem() {
        assertThat(expand(timedSeries(weeklyFive), LocalDate.of(2026, 10, 30))).hasSize(5)
    }

    /** Pin: passes without the change. */
    @Test
    fun theWindowEndingTheDayBeforeKeepsOneFewer() {
        assertThat(expand(timedSeries(weeklyFive), LocalDate.of(2026, 10, 29))).hasSize(4)
    }

    @Test
    fun aCountedSeriesStopsAtItsCount() {
        assertThat(expand(timedSeries(weeklyFive), friday.plusYears(1))).hasSize(5)
    }

    @Test
    fun aCountOfOneIsOneOccurrence() {
        val only = expand(timedSeries("RRULE:FREQ=WEEKLY;INTERVAL=1;COUNT=1"), friday.plusYears(1)).single()
        assertThat(only.seriesId).isEqualTo("s1")
    }

    /** Pin: passes without the change. */
    @Test
    fun aBadCountIsIgnored() {
        val lastDay = friday.plusDays(21)
        for (bad in listOf("0", "-3", "abc", "")) {
            assertThat(expand(timedSeries("RRULE:FREQ=WEEKLY;INTERVAL=1;COUNT=$bad"), lastDay)).hasSize(4)
        }
    }

    /** The first case is a pin: UNTIL alone ends it. */
    @Test
    fun untilAndCountTogetherEndAtTheFirst() {
        // UNTIL 22:59:59 UTC on Fri 16 Oct (23:59:59 London) allows 3 of the 5 counted; COUNT=3 stops first when UNTIL is later.
        assertThat(expand(timedSeries("RRULE:FREQ=WEEKLY;INTERVAL=1;COUNT=5;UNTIL=20261016T225959Z"), friday.plusYears(1))).hasSize(3)
        assertThat(expand(timedSeries("RRULE:FREQ=WEEKLY;INTERVAL=1;COUNT=3;UNTIL=20261120"), friday.plusYears(1))).hasSize(3)
    }

    @Test
    fun aCountedSeriesKeepsItsLocalTimeOverTheClockChange() {
        val first = LocalDate.of(2026, 10, 16)
        val made = expand(timedSeries("RRULE:FREQ=WEEKLY;INTERVAL=1;COUNT=5", first), first.plusYears(1))
        assertThat(made.map { it.localDay() }).containsExactly(
            LocalDate.of(2026, 10, 16), LocalDate.of(2026, 10, 23), LocalDate.of(2026, 10, 30), LocalDate.of(2026, 11, 6), LocalDate.of(2026, 11, 13),
        ).inOrder()
        assertThat(made.map { (it.start as EventTime.Timed).instant.atZone(london).toLocalTime() }.toSet()).containsExactly(LocalTime.of(18, 0))
    }

    @Test
    fun aCountedAllDaySpanKeepsItsDays() {
        val made = expand(allDaySeries("RRULE:FREQ=WEEKLY;INTERVAL=1;COUNT=3", LocalDate.of(2026, 10, 23), 3), LocalDate.of(2027, 10, 1))
        assertThat(made).hasSize(3)
        assertThat(made.map { (it.end as EventTime.AllDay).date.toEpochDay() - (it.start as EventTime.AllDay).date.toEpochDay() }.toSet())
            .containsExactly(3L)
        assertThat(made.map { (it.start as EventTime.AllDay).date }).containsExactly(
            LocalDate.of(2026, 10, 23), LocalDate.of(2026, 10, 30), LocalDate.of(2026, 11, 6),
        ).inOrder()
    }

    // Saturday 26 September 2026, the 4th Saturday.
    private val saturday = LocalDate.of(2026, 9, 26)

    @Test
    fun aMonthlyNthWeekdayFollowsTheWeekday() {
        val made = expand(timedSeries("RRULE:FREQ=MONTHLY;INTERVAL=1;BYDAY=4SA;COUNT=3", saturday, LocalTime.of(10, 0)), saturday.plusYears(1))
        assertThat(made.map { it.localDay() })
            .containsExactly(saturday, LocalDate.of(2026, 10, 24), LocalDate.of(2026, 11, 28)).inOrder()
        assertThat(made.map { (it.start as EventTime.Timed).instant.atZone(london).toLocalTime() }.toSet()).containsExactly(LocalTime.of(10, 0))
    }

    @Test
    fun theLastWeekdayIsTheLastInEachMonth() {
        val first = LocalDate.of(2026, 10, 29)
        val made = expand(timedSeries("RRULE:FREQ=MONTHLY;INTERVAL=1;BYDAY=-1TH", first), LocalDate.of(2027, 1, 31))
        assertThat(made.map { it.localDay() })
            .containsExactly(first, LocalDate.of(2026, 11, 26), LocalDate.of(2026, 12, 31), LocalDate.of(2027, 1, 28)).inOrder()
    }

    /** Pin: passes without the change. */
    @Test
    fun aCountedNthSeriesStopsAtItsCount() {
        assertThat(expand(timedSeries("RRULE:FREQ=MONTHLY;INTERVAL=1;BYDAY=4SA;COUNT=5", saturday), saturday.plusYears(2))).hasSize(5)
    }

    /** The series' first date kept with its rule is not part of the rule. */
    @Test
    fun theSeriesStartLineIsSkipped() {
        val made = expand(timedSeries(withSeriesStart("RRULE:FREQ=WEEKLY;INTERVAL=1;COUNT=3", friday)), friday.plusYears(1))
        assertThat(made.map { it.localDay() }).containsExactly(friday, friday.plusDays(7), friday.plusDays(14)).inOrder()
    }

    @Test
    fun aYearlySeriesFrom29FebruaryOnlyLandsInLeapYears() {
        val leap = LocalDate.of(2028, 2, 29)
        val made = expand(allDaySeries("RRULE:FREQ=YEARLY;INTERVAL=1;COUNT=2", leap, 1), LocalDate.of(2033, 1, 1))
        assertThat(made.map { (it.start as EventTime.AllDay).date }).containsExactly(leap, LocalDate.of(2032, 2, 29)).inOrder()
    }

    /** The editor's last date and the fake's series never drift apart (9 §3.1). */
    @Test
    fun theExpanderAgreesWithOccurrences() {
        val days = (22..31).map { LocalDate.of(2026, 10, it) } + LocalDate.of(2028, 2, 29)
        for (choice in RepeatChoice.entries - RepeatChoice.Never) {
            for (day in days) {
                for (n in listOf(2, 10, 52)) {
                    val expected = occurrences(choice, day, n)
                    for (allDay in listOf(true, false)) {
                        val rule = recurrenceRule(choice, RepeatEnd.After(n), allDay, london, day)!!
                        val series = if (allDay) allDaySeries(rule, day, 1) else timedSeries(rule, day, LocalTime.of(10, 0))
                        val made = expand(series, day.plusYears(250)).map {
                            when (val s = it.start) {
                                is EventTime.AllDay -> s.date
                                is EventTime.Timed -> s.instant.atZone(london).toLocalDate()
                            }
                        }
                        assertThat(made).isEqualTo(expected)
                    }
                }
            }
        }
    }

    /** A phone's "last Saturday" from a 4th Saturday that is also the last: the read-back's last date agrees too. */
    @Test
    fun theExpanderAgreesWithOccurrencesForALastWeekdayFromAFourth() {
        for (n in listOf(2, 10, 52)) {
            val made = expand(allDaySeries("RRULE:FREQ=MONTHLY;INTERVAL=1;BYDAY=-1SA;COUNT=$n", saturday, 1), saturday.plusYears(10))
            assertThat(made.map { (it.start as EventTime.AllDay).date }).isEqualTo(occurrences(RepeatChoice.MonthlyOnWeekday, saturday, n, nth = -1))
        }
    }
}
