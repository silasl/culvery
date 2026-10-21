package uk.co.siland.culvery.capability.calendar

import com.google.common.truth.Truth.assertThat
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import org.junit.Test

/** The detail sheet reads a series back in the editor's words (9 §3.4). */
class RepeatsTest {
    private val london = ZoneId.of("Europe/London")

    // Saturday 26 September 2026.
    private val saturday = LocalDate.of(2026, 9, 26)

    private fun label(rule: String?, day: LocalDate = saturday) = repeatsLabel(rule, EventTime.AllDay(day), london)

    @Test
    fun rulesReadInTheEditorsWords() {
        val expected = mapOf(
            "RRULE:FREQ=WEEKLY" to "Every Saturday",
            "RRULE:FREQ=WEEKLY;BYDAY=SA" to "Every Saturday",
            "RRULE:FREQ=WEEKLY;INTERVAL=2" to "Every 2 weeks, on Saturday",
            "RRULE:FREQ=WEEKLY;INTERVAL=2;BYDAY=SA" to "Every 2 weeks, on Saturday",
            "RRULE:FREQ=MONTHLY;BYDAY=4SA" to "Every month, on the 4th Saturday",
            "RRULE:FREQ=MONTHLY;BYMONTHDAY=26" to "Every month, on the 26th",
            "RRULE:FREQ=MONTHLY" to "Every month, on the 26th",
            "RRULE:FREQ=YEARLY" to "Every year, on 26 September",
            "RRULE:FREQ=DAILY" to "Every day",
            "RRULE:FREQ=WEEKLY;WKST=MO" to "Every Saturday",
        )
        expected.forEach { (rule, words) -> assertThat(label(rule)).isEqualTo(words) }
    }

    @Test
    fun theLastWeekdayReadsAsLast() {
        assertThat(label("RRULE:FREQ=MONTHLY;BYDAY=-1TH", LocalDate.of(2026, 10, 29))).isEqualTo("Every month, on the last Thursday")
    }

    /** Google offers "last Saturday" for a 4th Saturday that is also the last; the editor writes the 4th there. */
    @Test
    fun aLastWeekdayFromAFourthThatIsAlsoTheLastReadsAsLast() {
        assertThat(label("DTSTART:20260926\nRRULE:FREQ=MONTHLY;BYDAY=-1SA;COUNT=3"))
            .isEqualTo("Every month, on the last Saturday · 3 times · last on Sat 28 Nov")
        assertThat(label("RRULE:FREQ=MONTHLY;INTERVAL=1;BYDAY=-1SA")).isEqualTo("Every month, on the last Saturday")
        // The 19th is no month's last Saturday.
        assertThat(label("RRULE:FREQ=MONTHLY;BYDAY=-1SA", LocalDate.of(2026, 9, 19))).isEqualTo(REPEATS_YES)
    }

    @Test
    fun aMonthlyRuleByDateReadsItsDay() {
        assertThat(label("RRULE:FREQ=MONTHLY;BYMONTHDAY=22", LocalDate.of(2026, 9, 22))).isEqualTo("Every month, on the 22nd")
    }

    @Test
    fun otherIntervalsReadAsTheirUnits() {
        assertThat(label("RRULE:FREQ=MONTHLY;INTERVAL=3")).isEqualTo("Every 3 months")
        assertThat(label("RRULE:FREQ=DAILY;INTERVAL=3")).isEqualTo("Every 3 days")
        assertThat(label("RRULE:FREQ=WEEKLY;INTERVAL=3;BYDAY=SA")).isEqualTo("Every 3 weeks")
    }

    @Test
    fun aCountWithTheSeriesStartSaysTheLastDate() {
        assertThat(label("DTSTART:20260926\nRRULE:FREQ=WEEKLY;COUNT=12")).isEqualTo("Every Saturday · 12 times · last on Sat 12 Dec")
    }

    /** The series' own first date, not this occurrence's, is where the count starts. */
    @Test
    fun aLaterOccurrenceCountsFromTheSeriesStart() {
        assertThat(label("DTSTART:20260926\nRRULE:FREQ=WEEKLY;COUNT=12", LocalDate.of(2026, 10, 31)))
            .isEqualTo("Every Saturday · 12 times · last on Sat 12 Dec")
        assertThat(label("DTSTART:20261029\nRRULE:FREQ=MONTHLY;BYDAY=-1TH;COUNT=3", LocalDate.of(2026, 11, 26)))
            .isEqualTo("Every month, on the last Thursday · 3 times · last on Thu 31 Dec")
    }

    @Test
    fun aCountWithNoSeriesStartSaysOnlyTheCount() {
        assertThat(label("RRULE:FREQ=WEEKLY;COUNT=12")).isEqualTo("Every Saturday · 12 times")
    }

    @Test
    fun untilReadsAsItsDay() {
        assertThat(label("RRULE:FREQ=WEEKLY;UNTIL=20261212")).isEqualTo("Every Saturday · until Sat 12 Dec")
    }

    /** Google writes a timed series' UNTIL as a UTC time; it is read in the household zone. */
    @Test
    fun aTimedUntilReadsInTheHouseholdZone() {
        assertThat(label("RRULE:FREQ=WEEKLY;UNTIL=20261212T095959Z")).isEqualTo("Every Saturday · until Sat 12 Dec")
        // 23:30 UTC on 1 Aug is 00:30 on 2 Aug in London.
        assertThat(label("RRULE:FREQ=DAILY;UNTIL=20260801T233000Z")).isEqualTo("Every day · until Sun 2 Aug")
    }

    @Test
    fun anythingElseIsYes() {
        listOf(
            null,
            "RRULE:FREQ=WEEKLY;BYDAY=MO,WE",
            "RRULE:FREQ=WEEKLY;BYDAY=WE",
            "RRULE:FREQ=MONTHLY;BYDAY=2SA",
            "RRULE:FREQ=MONTHLY;BYMONTHDAY=1",
            "RRULE:FREQ=HOURLY",
            "RRULE:FREQ=DAILY;INTERVAL=0",
            "RRULE:FREQ=WEEKLY;BYSETPOS=1",
            "not a rule",
        ).forEach { assertThat(label(it)).isEqualTo(REPEATS_YES) }
    }

    @Test
    fun aTimedStartIsReadInTheHouseholdZone() {
        // 23:30 UTC on Friday 25 September is 00:30 on Saturday 26 September in London.
        assertThat(repeatsLabel("RRULE:FREQ=WEEKLY;BYDAY=SA", EventTime.Timed(Instant.parse("2026-09-25T23:30:00Z")), london))
            .isEqualTo("Every Saturday")
    }

    @Test
    fun theReadBackAgreesWithTheEditor() {
        for (choice in EDITOR_REPEATS - RepeatChoice.Never) {
            val rule = recurrenceRule(choice, RepeatEnd.Never, day = saturday)
            assertThat(repeatsLabel(rule, EventTime.AllDay(saturday), london)).isEqualTo(choice.wording(saturday))
        }
    }
}
