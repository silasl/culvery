package uk.co.siland.culvery.capability.calendar

import com.google.common.truth.Truth.assertThat
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import org.junit.Test

class RepeatsTest {
    private val london = ZoneId.of("Europe/London")

    // Tuesday 22 September 2026.
    private val tuesday = EventTime.AllDay(LocalDate.of(2026, 9, 22))

    private fun label(rule: String?) = repeatsLabel(rule, tuesday, london)

    @Test
    fun simpleRulesReadAsWords() {
        val expected = mapOf(
            "RRULE:FREQ=DAILY" to "Every day",
            "RRULE:FREQ=WEEKLY" to "Every week",
            "RRULE:FREQ=WEEKLY;BYDAY=TU" to "Every week",
            "RRULE:FREQ=WEEKLY;INTERVAL=2;BYDAY=TU" to "Every 2 weeks",
            "RRULE:FREQ=MONTHLY;BYMONTHDAY=22" to "Every month",
            "RRULE:FREQ=YEARLY" to "Every year",
            "RRULE:FREQ=DAILY;INTERVAL=3" to "Every 3 days",
            "RRULE:FREQ=MONTHLY;INTERVAL=6" to "Every 6 months",
            "RRULE:FREQ=WEEKLY;UNTIL=20261231T000000Z;WKST=MO" to "Every week",
            "RRULE:FREQ=WEEKLY;COUNT=10" to "Every week",
        )
        expected.forEach { (rule, words) -> assertThat(label(rule)).isEqualTo(words) }
    }

    @Test
    fun anythingElseIsYes() {
        listOf(
            null,
            "RRULE:FREQ=WEEKLY;BYDAY=MO,WE",
            "RRULE:FREQ=WEEKLY;BYDAY=WE",
            "RRULE:FREQ=MONTHLY;BYDAY=4TU",
            "RRULE:FREQ=MONTHLY;BYMONTHDAY=1",
            "RRULE:FREQ=HOURLY",
            "RRULE:FREQ=DAILY;INTERVAL=0",
            "RRULE:FREQ=WEEKLY;BYSETPOS=1",
            "not a rule",
        ).forEach { assertThat(label(it)).isEqualTo(REPEATS_YES) }
    }

    @Test
    fun aTimedStartIsReadInTheHouseholdZone() {
        // 23:30 UTC on Monday 21 September is 00:30 on Tuesday 22 September in London.
        assertThat(repeatsLabel("RRULE:FREQ=WEEKLY;BYDAY=TU", EventTime.Timed(Instant.parse("2026-09-21T23:30:00Z")), london))
            .isEqualTo("Every week")
    }
}
