package uk.co.siland.culvery.provider.calendar_fake

import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import uk.co.siland.culvery.capability.calendar.EventTime
import uk.co.siland.culvery.capability.calendar.RemoteEvent
import uk.co.siland.culvery.provider.calendar_fake.FakeCalendarProvider.Companion.SOURCE_FAMILY
import uk.co.siland.culvery.provider.calendar_fake.FakeCalendarProvider.Companion.SOURCE_SCHOOL

/**
 * The hand-off's week (screenshots/calendar-sheets/02-calendar-week-dark.png, durations from Culvery.dc.html),
 * relative to today, on the one shared "Family calendar" as Culvery would write it: each event tagged with who it
 * is for and who added it, by person name. [forSource] turns names into household ids; "Family" is always
 * "family". Plumber quote call has no tags, as if added from a phone. INSET day comes from the read-only school feed.
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
        val forName: String? = null,
        val byName: String? = null,
    )

    private class AllDay(
        val id: String,
        val source: String,
        val title: String,
        val day: Long,
        val days: Long = 1,
        val weekly: Boolean = false,
        val forName: String? = null,
        val byName: String? = null,
    )

    private fun t(hour: Int, minute: Int) = LocalTime.of(hour, minute)

    private val timed = listOf(
        Timed("school-run", SOURCE_FAMILY, "School run", 0, t(7, 45), 45, forName = "Sam", byName = "Sam"),
        Timed("boiler", SOURCE_FAMILY, "Boiler service", 0, t(10, 0), 60, forName = "Family", byName = "Alex"),
        Timed("plumber", SOURCE_FAMILY, "Plumber quote call", 0, t(13, 0), 30),
        Timed("swimming", SOURCE_FAMILY, "Swimming", 0, t(16, 0), 60, weekly = true, forName = "Mia", byName = "Sam"),
        Timed("dinner", SOURCE_FAMILY, "Dinner with Jo & Priya", 0, t(19, 30), 90, forName = "Alex", byName = "Alex"),
        Timed("office", SOURCE_FAMILY, "Office day", 1, t(9, 0), 480, forName = "Alex", byName = "Alex"),
        // Mia added this herself, so she may delete it.
        Timed("football", SOURCE_FAMILY, "Football", 1, t(18, 0), 60, forName = "Mia", byName = "Mia"),
        Timed("dentist", SOURCE_FAMILY, "Dentist", 2, t(12, 30), 60, forName = "Sam", byName = "Sam"),
        Timed("piano", SOURCE_FAMILY, "Piano", 3, t(15, 30), 60, weekly = true, forName = "Mia", byName = "Alex"),
        Timed("book-club", SOURCE_FAMILY, "Book club", 3, t(20, 0), 120, forName = "Sam", byName = "Sam"),
        Timed("pizza", SOURCE_FAMILY, "Pizza night", 4, t(19, 0), 240, forName = "Family", byName = "Mia"),
        Timed("parkrun", SOURCE_FAMILY, "Parkrun", 5, t(9, 30), 90, forName = "Alex", byName = "Alex"),
        Timed("party", SOURCE_FAMILY, "Birthday party", 5, t(14, 0), 180, forName = "Mia", byName = "Sam"),
        Timed("lunch", SOURCE_FAMILY, "Sunday lunch at Gran's", 6, t(12, 0), 180, forName = "Family", byName = "Alex"),
        // Outside a 15-day window: the contract suite's out-of-range fixture.
        Timed("school-trip", SOURCE_FAMILY, "School trip", 20, t(8, 30), 420, forName = "Mia", byName = "Alex"),
    )

    private val allDay = listOf(
        AllDay("bins", SOURCE_FAMILY, "Bin day", 2, weekly = true, forName = "Family", byName = "Alex"),
        AllDay("inset", SOURCE_SCHOOL, "INSET day — no school", 3),
        // After the visible week, so the week view matches the hand-off.
        AllDay("half-term", SOURCE_FAMILY, "Half term", 8, days = 3, forName = "Family", byName = "Alex"),
    )

    /** Day offsets of each occurrence: a weekly event runs from last week to three weeks ahead. */
    private fun occurrences(day: Long, weekly: Boolean): List<Long> =
        if (weekly) (-1..3).map { week -> day + 7L * week } else listOf(day)

    private fun tag(name: String?, idsByName: Map<String, String>): String? = when (name) {
        null -> null
        "Family" -> "family"
        else -> idsByName[name]
    }

    fun forSource(sourceId: String, today: LocalDate, zone: ZoneId, idsByName: Map<String, String> = emptyMap()): List<RemoteEvent> =
        buildList {
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
                            forPerson = tag(e.forName, idsByName),
                            createdBy = tag(e.byName, idsByName),
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
                            forPerson = tag(e.forName, idsByName),
                            createdBy = tag(e.byName, idsByName),
                        ),
                    )
                }
            }
        }

    /** Whether the Family calendar sample with [remoteId] repeats; null when it isn't a sample. */
    fun familySampleRepeats(remoteId: String): Boolean? =
        (timed.filter { it.source == SOURCE_FAMILY }.map { it.id to it.weekly } +
            allDay.filter { it.source == SOURCE_FAMILY }.map { it.id to it.weekly })
            .firstOrNull { (id, _) -> remoteId.startsWith("$id-") }
            ?.second
}
