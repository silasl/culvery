package uk.co.siland.culvery.capability.calendar

import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import uk.co.siland.culvery.core.household.Person
import uk.co.siland.culvery.core.household.PersonId

const val ALL_DAY_LABEL = "All day"
const val STALE_AFTER_MS = 30 * 60_000L

data class EventUi(
    /** Stable across days and syncs: connectionId/sourceId/remoteId. */
    val key: String,
    val title: String,
    /** This day's slice: "07:45–08:30", "22:00–" (first day of several), "until 01:00" (last day), or "All day". */
    val timeLabel: String,
    /** The week chip's label: "07:45" for a one-day event, otherwise the same as [timeLabel]. */
    val startLabel: String,
    val person: Person,
    /** True for all-day events and for the middle days of a multi-day timed event; they sort first. */
    val allDay: Boolean,
    val recurring: Boolean,
    val startSort: Long,
)

data class DayUi(val date: LocalDate, val events: List<EventUi>)

/** [people] is the legend: household members in order, then Family. */
data class WeekUi(val start: LocalDate, val days: List<DayUi>, val people: List<Person>)

/**
 * [lastSyncMillis] is the oldest successful sync across connections, so one failing calendar can't hide.
 * [connectionLabels] names every connection. [failingBeforeFirstSync] is true when a connection has never
 * synced and is not Ok, which makes the status stale however recent the others are.
 */
data class SyncStatusUi(
    val lastSyncMillis: Long?,
    val needsSignIn: List<String>,
    val connectionLabels: List<String>,
    val failingBeforeFirstSync: Boolean,
)

fun syncedLabel(lastSyncMillis: Long?, nowMillis: Long, connectionLabel: String? = null): String {
    if (lastSyncMillis == null) return "not synced yet"
    val synced = if (connectionLabel == null) "synced" else "synced with $connectionLabel"
    // A wall clock that jumped backwards gives a negative age; treat it as fresh.
    val minutes = (nowMillis - lastSyncMillis).coerceAtLeast(0) / 60_000
    val hours = minutes / 60
    val ago = when {
        minutes < 1 -> "just now"
        minutes < 60 -> "$minutes min ago"
        hours < 48 -> "$hours h ago"
        else -> "${hours / 24} days ago"
    }
    return "$synced $ago"
}

fun isStale(lastSyncMillis: Long?, nowMillis: Long): Boolean =
    lastSyncMillis != null && nowMillis - lastSyncMillis > STALE_AFTER_MS

fun SyncStatusUi.isStaleAt(nowMillis: Long): Boolean =
    failingBeforeFirstSync || isStale(lastSyncMillis, nowMillis)

fun weekSubtitle(sync: SyncStatusUi, nowMillis: Long): String =
    "Family calendar · ${syncedLabel(sync.lastSyncMillis, nowMillis, sync.connectionLabels.singleOrNull())}"

/** A tag naming a real person (or Family) wins; otherwise the source's person; otherwise Family. */
fun resolvePerson(forPerson: String?, sourcePerson: PersonId, people: Map<PersonId, Person>): Person =
    forPerson?.let { people[PersonId(it)] } ?: people[sourcePerson] ?: Person.Family

private val HOURS_MINUTES = DateTimeFormatter.ofPattern("HH:mm")

private class Slice(val timeLabel: String, val startLabel: String, val allDay: Boolean)

/** How the event reads on [date]. A timed event over several days reads "22:00–", then "All day", then "until 01:00". */
private fun StoredEvent.sliceOn(date: LocalDate, zone: ZoneId): Slice {
    if (start is EventTime.AllDay) return Slice(ALL_DAY_LABEL, ALL_DAY_LABEL, allDay = true)
    val from = start.instantIn(zone).atZone(zone)
    val to = end.instantIn(zone).atZone(zone)
    val endsAtMidnight = to.toLocalTime() == LocalTime.MIDNIGHT
    // An event ending at exactly midnight ends on the day before.
    val lastDay = if (endsAtMidnight && to.toLocalDate().isAfter(from.toLocalDate())) to.toLocalDate().minusDays(1) else to.toLocalDate()
    val startClock = from.format(HOURS_MINUTES)
    val endClock = to.format(HOURS_MINUTES)
    return when {
        lastDay == from.toLocalDate() -> Slice("$startClock–$endClock", startClock, allDay = false)
        date == from.toLocalDate() -> Slice("$startClock–", "$startClock–", allDay = false)
        date == lastDay && !endsAtMidnight -> Slice("until $endClock", "until $endClock", allDay = false)
        else -> Slice(ALL_DAY_LABEL, ALL_DAY_LABEL, allDay = true)
    }
}

internal fun StoredEvent.toUi(date: LocalDate, zone: ZoneId, people: Map<PersonId, Person>): EventUi {
    val slice = sliceOn(date, zone)
    return EventUi(
        key = "$connectionId/$sourceId/$remoteId",
        title = title,
        timeLabel = slice.timeLabel,
        startLabel = slice.startLabel,
        person = resolvePerson(forPerson, sourcePerson, people),
        allDay = slice.allDay,
        recurring = recurring,
        startSort = startSort,
    )
}
