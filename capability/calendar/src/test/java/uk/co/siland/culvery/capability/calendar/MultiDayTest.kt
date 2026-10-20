package uk.co.siland.culvery.capability.calendar

import com.google.common.truth.Truth.assertThat
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId
import org.junit.Test
import uk.co.siland.culvery.core.household.PersonId

class MultiDayTest {
    private val london = ZoneId.of("Europe/London")
    private val catalog = SourceCatalog(emptyList(), emptyList(), emptySet())

    private fun date(month: Int, day: Int) = LocalDate.of(2026, month, day)

    private fun at(month: Int, day: Int, hour: Int, minute: Int = 0) =
        EventTime.Timed(LocalDateTime.of(2026, month, day, hour, minute).atZone(london).toInstant())

    private fun allDay(month: Int, day: Int) = EventTime.AllDay(date(month, day))

    private fun event(title: String, start: EventTime, end: EventTime) = StoredEvent(
        "c1", "s-family", title, title, start, end,
        recurring = false, forPerson = null, createdBy = null, sourcePerson = PersonId.FAMILY,
        startSort = start.instantIn(london).toEpochMilli(), endSort = end.instantIn(london).toEpochMilli(),
    )

    /** The week from [start] as the repository builds it: each date's slices of the events on it. */
    private fun week(start: LocalDate, vararg events: StoredEvent): List<DayUi> = (0L..6L).map { i ->
        val d = start.plusDays(i)
        DayUi(d, events.filter { it.isOn(d, london) }.map { it.toUi(d, london, emptyMap(), catalog) })
    }

    private val halfTerm = event("Half term", allDay(10, 19), allDay(10, 24))

    @Test
    fun anAllDayEventOfTwoDatesIsMultiDay() {
        assertThat(event("Away", allDay(10, 19), allDay(10, 21)).isMultiDay(london)).isTrue()
    }

    @Test
    fun oneAllDayDateIsNot() {
        assertThat(event("Bin day", allDay(10, 20), allDay(10, 21)).isMultiDay(london)).isFalse()
    }

    @Test
    fun anOvernightTimedEventIsNot() {
        assertThat(event("Night shift", at(10, 19, 22), at(10, 20, 1)).isMultiDay(london)).isFalse()
    }

    @Test
    fun aDayLaterIsMultiDay() {
        assertThat(event("Course", at(10, 19, 9), at(10, 20, 9)).isMultiDay(london)).isTrue()
        assertThat(event("Course", at(10, 19, 9), at(10, 20, 8, 59)).isMultiDay(london)).isFalse()
    }

    @Test
    fun midnightToMidnightIsOneDay() {
        val e = event("Open day", at(10, 19, 0), at(10, 20, 0))
        assertThat(e.isMultiDay(london)).isFalse()
        assertThat(e.dayOfSpan(date(10, 19), london)).isNull()
    }

    @Test
    fun noonToNoonOverTheSpringChangeCounts() {
        // Clocks go forward on Sunday 29 March 2026: 23 hours pass.
        assertThat(event("Camping", at(3, 28, 12), at(3, 29, 12)).isMultiDay(london)).isTrue()
    }

    @Test
    fun endingAtMidnightDoesNotCountTheNextDate() {
        val e = event("Course", at(10, 19, 10), at(10, 21, 0))
        assertThat(e.dayOfSpan(date(10, 20), london)).isEqualTo(DaySpan(2, 2))
        assertThat(e.dayOfSpan(date(10, 21), london)).isNull()
    }

    @Test
    fun theDayOfASpan() {
        val e = event("Half term", allDay(10, 19), allDay(10, 24))
        assertThat(e.dayOfSpan(date(10, 21), london)).isEqualTo(DaySpan(3, 5))
        assertThat(e.dayOfSpan(date(10, 24), london)).isNull()
        assertThat(e.dayOfSpan(date(10, 18), london)).isNull()
    }

    @Test
    fun aTimedSpanOverTheClockChangeCountsItsDays() {
        // Clocks go back on Sunday 25 October 2026.
        val e = event("Gran visiting", at(10, 24, 12), at(10, 26, 12))
        assertThat((24..26).map { e.dayOfSpan(date(10, it), london) })
            .containsExactly(DaySpan(1, 3), DaySpan(2, 3), DaySpan(3, 3)).inOrder()
    }

    @Test
    fun anAllDaySpanAcrossTheClockChange() {
        val e = event("Holiday", allDay(10, 23), allDay(10, 27))
        assertThat(e.dayOfSpan(date(10, 25), london)).isEqualTo(DaySpan(3, 4))
    }

    @Test
    fun aSpanStartingBeforeTheWeekIsClampedAndMarked() {
        val before = event("Inset days", allDay(10, 15), allDay(10, 21))
        val after = event("Holiday", allDay(10, 23), allDay(11, 2))
        val spans = weekSpans(week(date(10, 19), before, after)).associateBy { it.title }

        val b = spans.getValue("Inset days")
        assertThat(listOf(b.first, b.last)).containsExactly(date(10, 19), date(10, 20)).inOrder()
        assertThat(b.startsBefore).isTrue()
        assertThat(b.endsAfter).isFalse()
        assertThat(b.label).isEqualTo("Thu 15 – Tue 20")

        val a = spans.getValue("Holiday")
        assertThat(listOf(a.first, a.last)).containsExactly(date(10, 23), date(10, 25)).inOrder()
        assertThat(a.startsBefore).isFalse()
        assertThat(a.endsAfter).isTrue()
        // An all-day end is exclusive: ending Mon 2 Nov, its last day is Sun 1 Nov.
        assertThat(a.label).isEqualTo("until Sun →")
        assertThat(a.spoken).isEqualTo("Holiday, Family, Friday 23 October to Sunday 1 November")
    }

    @Test
    fun onlySlicesWithASpanBecomeBars() {
        val overnight = event("Night shift", at(10, 20, 22), at(10, 21, 1))
        val days = week(date(10, 19), overnight, halfTerm)
        assertThat(days.flatMap { it.events }.filter { it.title == "Night shift" }).hasSize(2)
        assertThat(weekSpans(days).map { it.title }).containsExactly("Half term")
    }

    @Test
    fun theLabelAndSpokenName() {
        val span = weekSpans(week(date(10, 19), event("Half term", allDay(10, 19), allDay(10, 23)))).single()
        assertThat(span.label).isEqualTo("Mon 19 – Thu 22")
        assertThat(span.spoken).isEqualTo("Half term, Family, Monday 19 October to Thursday 22 October")
        assertThat(span.ref).isEqualTo(EventRef("c1", "s-family", "Half term"))
        assertThat(span.person.name).isEqualTo("Family")
    }
}
