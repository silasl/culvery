package uk.co.siland.culvery.provider.calendar_fake

import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import uk.co.siland.culvery.capability.calendar.EventTime
import uk.co.siland.culvery.capability.calendar.RemoteEvent
import uk.co.siland.culvery.provider.calendar_fake.FakeCalendarProvider.Companion.SOURCE_ALEX
import uk.co.siland.culvery.provider.calendar_fake.FakeCalendarProvider.Companion.SOURCE_FAMILY
import uk.co.siland.culvery.provider.calendar_fake.FakeCalendarProvider.Companion.SOURCE_MIA
import uk.co.siland.culvery.provider.calendar_fake.FakeCalendarProvider.Companion.SOURCE_SAM
import uk.co.siland.culvery.provider.calendar_fake.FakeCalendarProvider.Companion.SOURCE_SCHOOL

/**
 * The hand-off's week (screenshots/calendar-sheets/02-calendar-week-dark.png, durations from Culvery.dc.html),
 * relative to today, plus the fixtures the contract suite needs.
 */
internal object SampleEvents {
    private class Timed(
        val id: String,
        val source: String,
        val title: String,
        val day: Long,
        val start: LocalTime,
        val minutes: Long,
        val weekly: Boolean = false,
    )

    private class AllDay(
        val id: String,
        val source: String,
        val title: String,
        val day: Long,
        val days: Long = 1,
        val weekly: Boolean = false,
    )

    private fun t(hour: Int, minute: Int) = LocalTime.of(hour, minute)

    private val timed = listOf(
        Timed("school-run", SOURCE_SAM, "School run", 0, t(7, 45), 45),
        Timed("boiler", SOURCE_FAMILY, "Boiler service", 0, t(10, 0), 60),
        Timed("plumber", SOURCE_FAMILY, "Plumber quote call", 0, t(13, 0), 30),
        Timed("swimming", SOURCE_MIA, "Swimming", 0, t(16, 0), 60, weekly = true),
        Timed("dinner", SOURCE_ALEX, "Dinner with Jo & Priya", 0, t(19, 30), 90),
        Timed("office", SOURCE_ALEX, "Office day", 1, t(9, 0), 480),
        Timed("football", SOURCE_MIA, "Football", 1, t(18, 0), 60),
        Timed("dentist", SOURCE_SAM, "Dentist", 2, t(12, 30), 60),
        Timed("piano", SOURCE_MIA, "Piano", 3, t(15, 30), 60, weekly = true),
        Timed("book-club", SOURCE_SAM, "Book club", 3, t(20, 0), 120),
        Timed("pizza", SOURCE_FAMILY, "Pizza night", 4, t(19, 0), 240),
        Timed("parkrun", SOURCE_ALEX, "Parkrun", 5, t(9, 30), 90),
        Timed("party", SOURCE_MIA, "Birthday party", 5, t(14, 0), 180),
        Timed("lunch", SOURCE_FAMILY, "Sunday lunch at Gran's", 6, t(12, 0), 180),
        // Outside a 15-day window: the contract suite's out-of-range fixture.
        Timed("school-trip", SOURCE_MIA, "School trip", 20, t(8, 30), 420),
    )

    private val allDay = listOf(
        AllDay("bins", SOURCE_FAMILY, "Bin day", 2, weekly = true),
        AllDay("inset", SOURCE_SCHOOL, "INSET day — no school", 3),
        // After the visible week, so the week view matches the hand-off.
        AllDay("half-term", SOURCE_FAMILY, "Half term", 8, days = 3),
    )

    /** Day offsets of each occurrence: a weekly event runs from last week to three weeks ahead. */
    private fun occurrences(day: Long, weekly: Boolean): List<Long> =
        if (weekly) (-1..3).map { week -> day + 7L * week } else listOf(day)

    fun forSource(sourceId: String, today: LocalDate, zone: ZoneId): List<RemoteEvent> = buildList {
        timed.filter { it.source == sourceId }.forEach { e ->
            occurrences(e.day, e.weekly).forEach { offset ->
                val date = today.plusDays(offset)
                val start = date.atTime(e.start).atZone(zone).toInstant()
                add(
                    RemoteEvent(
                        "${e.id}-$date",
                        e.title,
                        EventTime.Timed(start),
                        EventTime.Timed(start.plusSeconds(e.minutes * 60)),
                        recurring = e.weekly,
                    ),
                )
            }
        }
        allDay.filter { it.source == sourceId }.forEach { e ->
            occurrences(e.day, e.weekly).forEach { offset ->
                val date = today.plusDays(offset)
                add(
                    RemoteEvent(
                        "${e.id}-$date",
                        e.title,
                        EventTime.AllDay(date),
                        EventTime.AllDay(date.plusDays(e.days)),
                        recurring = e.weekly,
                    ),
                )
            }
        }
    }
}
