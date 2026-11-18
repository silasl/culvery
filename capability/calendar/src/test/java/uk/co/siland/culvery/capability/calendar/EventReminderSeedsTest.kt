package uk.co.siland.culvery.capability.calendar

import com.google.common.truth.Truth.assertThat
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import org.junit.Test
import uk.co.siland.culvery.core.household.PersonId
import uk.co.siland.culvery.core.repeat.withSeriesStart

class EventReminderSeedsTest {
    private val london = ZoneId.of("Europe/London")
    private val today = LocalDate.of(2026, 11, 18)
    private val sam = PersonId("sam")

    // 10:00 on [today].
    private val now = today.atTime(10, 0).atZone(london).toInstant().toEpochMilli()

    private fun timed(day: LocalDate, hour: Int, minute: Int = 0) = EventTime.Timed(day.atTime(hour, minute).atZone(london).toInstant())

    private fun event(
        start: EventTime,
        end: EventTime = start,
        id: String = "e1",
        rule: String? = null,
        seriesId: String? = null,
        bins: List<String>? = null,
    ) = StoredEvent(
        "c1", "s1", id, "Swimming", start, end, recurring = rule != null, forPerson = "sam", createdBy = null,
        sourcePerson = PersonId.FAMILY, startSort = start.instantIn(london).toEpochMilli(), endSort = end.instantIn(london).toEpochMilli(),
        recurrenceRule = rule, seriesId = seriesId, bins = bins,
    )

    private fun occurrence(day: LocalDate, rule: String, hour: Int = 17) =
        event(timed(day, hour), timed(day, hour + 1), id = "series_$day", rule = rule, seriesId = "series")

    private fun seed(event: StoredEvent, siblings: List<StoredEvent> = emptyList()) = reminderSeed(event, siblings, listOf(sam), now, london)

    @Test
    fun aOneOffSeedsItsStart() {
        val e = event(timed(today.plusDays(2), 19, 30))
        val seed = seed(e)!!
        assertThat(seed.day).isEqualTo(today.plusDays(2))
        assertThat(seed.time).isEqualTo(LocalTime.of(19, 30))
        assertThat(seed.rule).isNull()
        assertThat(seed.title).isEqualTo("Swimming")
        assertThat(seed.people).containsExactly(sam)
        assertThat(seed.key).isEqualTo(eventKey(e))
        assertThat(seed.now.day).isEqualTo(today.plusDays(2))
        assertThat(seed.now.time).isEqualTo(LocalTime.of(19, 30))
        assertThat(seed.now.rule).isNull()
    }

    @Test
    fun anAllDayEventHasNoTime() {
        val seed = seed(event(EventTime.AllDay(today), EventTime.AllDay(today.plusDays(1))))!!
        assertThat(seed.day).isEqualTo(today)
        assertThat(seed.time).isNull()
        assertThat(seed.now.time).isNull()
    }

    @Test
    fun aStartedOneOffHasNoSeed() {
        assertThat(seed(event(timed(today, 9, 59)))).isNull()
        assertThat(seed(event(timed(today, 10, 0)))).isNull()
        assertThat(seed(event(EventTime.AllDay(today.minusDays(1)), EventTime.AllDay(today)))).isNull()
    }

    @Test
    fun aBinCollectionHasNoSeed() {
        assertThat(seed(event(EventTime.AllDay(today.plusDays(1)), EventTime.AllDay(today.plusDays(2)), bins = listOf("Food")))).isNull()
    }

    @Test
    fun aSeriesSeedsTheDateTapped() {
        val rule = withSeriesStart("RRULE:FREQ=WEEKLY;INTERVAL=1", today.minusWeeks(3))
        val seed = seed(occurrence(today.plusWeeks(1), rule))!!
        assertThat(seed.day).isEqualTo(today.plusWeeks(1))
        assertThat(seed.time).isEqualTo(LocalTime.of(17, 0))
        assertThat(seed.rule).isEqualTo("RRULE:FREQ=WEEKLY;INTERVAL=1")
        assertThat(seed.key).isEqualTo(eventKey("c1", "s1", "series"))
        assertThat(seed.now.day).isEqualTo(today.minusWeeks(3))
        assertThat(seed.now.rule).isEqualTo("RRULE:FREQ=WEEKLY;INTERVAL=1")
    }

    @Test
    fun aPastDateOfASeriesSeedsTheNext() {
        val rule = withSeriesStart("RRULE:FREQ=WEEKLY;INTERVAL=1", today.minusWeeks(3))
        assertThat(seed(occurrence(today.minusWeeks(1), rule))!!.day).isEqualTo(today)
        // Today's 09:00 has gone by 10:00, so the next is a week on.
        assertThat(seed(occurrence(today, rule, hour = 9))!!.day).isEqualTo(today.plusWeeks(1))
    }

    @Test
    fun aPastDateMovedOnAPhoneSeedsTheSeriesUsualTime() {
        val rule = withSeriesStart("RRULE:FREQ=WEEKLY;INTERVAL=1", today.minusWeeks(3))
        val moved = occurrence(today.minusWeeks(1), rule, hour = 15)
        val siblings = listOf(moved, occurrence(today, rule), occurrence(today.plusWeeks(1), rule))
        val seed = seed(moved, siblings)!!
        assertThat(seed.day).isEqualTo(today)
        assertThat(seed.time).isEqualTo(LocalTime.of(17, 0))
    }

    @Test
    fun aMovedDateOfASeriesSeedsTheNextRuleDate() {
        // Mondays; this week's moved to today, Wednesday, and not yet started. Moved dates aren't followed.
        val monday = LocalDate.of(2026, 10, 26)
        val rule = withSeriesStart("RRULE:FREQ=WEEKLY;INTERVAL=1", monday)
        val moved = occurrence(today, rule)
        val seed = seed(moved, listOf(moved, occurrence(LocalDate.of(2026, 11, 23), rule)))!!
        assertThat(seed.day).isEqualTo(LocalDate.of(2026, 11, 23))
        assertThat(seed.time).isEqualTo(LocalTime.of(17, 0))
    }

    @Test
    fun anAllDaySeriesTappedOnAPastDateSeedsToday() {
        val rule = withSeriesStart("RRULE:FREQ=WEEKLY;INTERVAL=1", today.minusWeeks(3))
        val day = today.minusWeeks(1)
        val seed = seed(event(EventTime.AllDay(day), EventTime.AllDay(day.plusDays(1)), id = "series_$day", rule = rule, seriesId = "series"))!!
        assertThat(seed.day).isEqualTo(today)
        assertThat(seed.time).isNull()
    }

    @Test
    fun aSeriesFromALaterDateCarriesWhatIsLeft() {
        val rule = withSeriesStart("RRULE:FREQ=WEEKLY;INTERVAL=1;COUNT=10", today.minusWeeks(4))
        val seed = seed(occurrence(today.plusWeeks(1), rule))!!
        assertThat(seed.day).isEqualTo(today.plusWeeks(1))
        assertThat(seed.rule).isEqualTo("RRULE:FREQ=WEEKLY;INTERVAL=1;COUNT=5")
        assertThat(seed.now.rule).isEqualTo("RRULE:FREQ=WEEKLY;INTERVAL=1;COUNT=10")
    }

    @Test
    fun aFinishedSeriesHasNoSeed() {
        val rule = withSeriesStart("RRULE:FREQ=WEEKLY;INTERVAL=1;COUNT=3", today.minusWeeks(3))
        assertThat(seed(occurrence(today.minusWeeks(1), rule))).isNull()
    }

    @Test
    fun aRuleThatCannotBeWorkedOutSeedsAOneOff() {
        val refused = seed(occurrence(today.plusDays(1), withSeriesStart("RRULE:FREQ=HOURLY;INTERVAL=1", today.minusWeeks(1))))!!
        assertThat(refused.day).isEqualTo(today.plusDays(1))
        assertThat(refused.rule).isNull()
        // A count with no first day can't be reduced.
        val counted = seed(occurrence(today.plusWeeks(1), "RRULE:FREQ=WEEKLY;INTERVAL=1;COUNT=10"))!!
        assertThat(counted.day).isEqualTo(today.plusWeeks(1))
        assertThat(counted.rule).isNull()
    }

    @Test
    fun aOneOffAndItsLaterSeriesShareAKey() {
        val oneOff = event(timed(today.plusDays(1), 17), id = "abc")
        val occurrence = event(timed(today.plusDays(8), 17), id = "abc_20261126", rule = "RRULE:FREQ=WEEKLY;INTERVAL=1", seriesId = "abc")
        assertThat(eventKey(occurrence)).isEqualTo(eventKey(oneOff))
        assertThat(eventKey("c/1", "s1", "abc")).isNotEqualTo(eventKey("c", "1/s1", "abc"))
    }

    @Test
    fun aSeriesTimeIsItsCommonestComingTime() {
        val rule = withSeriesStart("RRULE:FREQ=WEEKLY;INTERVAL=1", today.minusWeeks(4))
        val siblings = listOf(
            occurrence(today.minusWeeks(2), rule, hour = 8),
            occurrence(today.minusWeeks(1), rule, hour = 8),
            occurrence(today.minusWeeks(1).plusDays(1), rule, hour = 8),
            occurrence(today, rule, hour = 18),
            occurrence(today.plusWeeks(1), rule, hour = 17),
            occurrence(today.plusWeeks(2), rule, hour = 18),
        )
        assertThat(eventTimes(siblings[4], siblings, london, today).time).isEqualTo(LocalTime.of(18, 0))
        // Equally common: the earliest coming date's.
        val tied = listOf(occurrence(today.plusWeeks(1), rule, hour = 18), occurrence(today, rule, hour = 17))
        assertThat(eventTimes(tied[0], tied, london, today).time).isEqualTo(LocalTime.of(17, 0))
        // With none ahead, all of them count.
        assertThat(eventTimes(siblings[0], siblings.take(3), london, today).time).isEqualTo(LocalTime.of(8, 0))
    }
}
