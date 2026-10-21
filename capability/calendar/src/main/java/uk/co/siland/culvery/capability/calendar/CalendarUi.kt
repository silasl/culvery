package uk.co.siland.culvery.capability.calendar

import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter
import java.util.Locale
import uk.co.siland.culvery.core.household.Person
import uk.co.siland.culvery.core.household.PersonId
import uk.co.siland.culvery.core.plugin.Connection
import uk.co.siland.culvery.core.ui.Icons
import uk.co.siland.culvery.core.ui.plural

const val ALL_DAY_LABEL = "All day"
const val STALE_AFTER_MS = 30 * 60_000L
const val ADDED_FROM_PHONE = "Added from a phone"

data class EventUi(
    /** Which mirrored event this is; the same on every day a multi-day event covers. */
    val ref: EventRef,
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
    /** The source calendar's name, e.g. "Family calendar" or "School terms". */
    val sourceName: String = "",
    /** The service's name ("Google Calendar"): failure, repeating-event and delete copy (3a design D11). */
    val serviceName: String = "",
    /** Null when the tablet may change this event: a non-recurring event on the master calendar. */
    val readOnlyReason: ReadOnlyReason? = null,
    /** On the master calendar with no person tags at all: added from a phone (hand-off "untagged"). */
    val untagged: Boolean = false,
    /** A change to it is waiting in the outbox. */
    val syncing: Boolean = false,
    /** A person's name, [ADDED_FROM_PHONE], the service's name, or the calendar's own name for one nobody here can change. */
    val createdBy: String = "",
    /** The Repeats row: "Every week"… or "Yes" (3a design D12). */
    val repeats: String = REPEATS_YES,
    /** The detail sheet offers Choose who it’s for: an editable event, or a repeating one on the master (4e). */
    val assignable: Boolean = readOnlyReason == null,
    /** Which day of a multi-day event this slice is; null for an event within one day, overnight ones included. */
    val span: DaySpan? = null,
) {
    val editable: Boolean get() = readOnlyReason == null
}

/** The detail sheet's model: the event plus its "When" row. */
data class EventDetailUi(val event: EventUi, val whenLabel: String)

/** Where the add/edit sheet starts: an event as shown, with its queued changes laid over it (2b-2 design §3.1). */
data class EditableEvent(
    val ref: EventRef,
    val title: String,
    val start: EventTime,
    val end: EventTime,
    val forPerson: String?,
)

data class DayUi(val date: LocalDate, val events: List<EventUi>)

/** [people] is the legend: household members in order, then Family. [spans] are the lane's rows of multi-day bars. */
data class WeekUi(
    val start: LocalDate,
    val days: List<DayUi>,
    val people: List<Person>,
    val spans: List<List<SpanUi>> = emptyList(),
)

/** Why the last pass failed, by what the family can do about it (5 §A.3). */
sealed interface SyncProblem {
    data object Offline : SyncProblem

    data class NoAnswer(val service: String) : SyncProblem

    data class SignIn(val service: String) : SyncProblem
}

/**
 * [lastSyncMillis] is the oldest successful update across connections, so one failing calendar can't hide.
 * [serviceNames] names every connection's service (4d-2 Decision 12). [failingBeforeFirstSync] is true when a
 * connection has never been updated and is not Ok, which makes the status stale however recent the others are.
 */
data class SyncStatusUi(
    val lastSyncMillis: Long?,
    val needsSignIn: List<String>,
    val serviceNames: List<String>,
    val failingBeforeFirstSync: Boolean,
    /** The first connection that needs signing in again: what the reconnect chip reconnects (3a design §4.3). */
    val reconnect: Connection? = null,
    /** The worst connection's problem; null when every connection is Ok. */
    val problem: SyncProblem? = null,
)

/** Hand-off §7 chip and row badges, in the order they show; [spoken] follows the row's words (", repeats"). */
enum class Badge(val icon: String, val spoken: String) {
    Syncing(Icons.CLOUD_UPLOAD, "saving"),
    OtherCalendar(Icons.LOCK, "can’t change here"),
    Repeats(Icons.REPEAT, "repeats"),
}

/** `cloud_upload` first, then `lock` (another calendar) or `repeat` (recurring). */
fun EventUi.badges(): List<Badge> = listOfNotNull(
    Badge.Syncing.takeIf { syncing },
    when {
        readOnlyReason == ReadOnlyReason.OtherCalendar || readOnlyReason == ReadOnlyReason.NotMaster -> Badge.OtherCalendar
        recurring -> Badge.Repeats
        else -> null
    },
)

/** "Updated 5 minutes ago", "Updated from Google Calendar 5 minutes ago", or "Not updated yet" (4d-2 §7.3: full words). */
fun updatedLabel(lastSyncMillis: Long?, nowMillis: Long, service: String? = null): String {
    if (lastSyncMillis == null) return "Not updated yet"
    // A wall clock that jumped backwards gives a negative age; treat it as fresh.
    val minutes = (nowMillis - lastSyncMillis).coerceAtLeast(0) / 60_000
    val hours = minutes / 60
    val ago = when {
        minutes < 1 -> "just now"
        minutes < 60 -> plural(minutes, "minute") + " ago"
        hours < 48 -> plural(hours, "hour") + " ago"
        else -> plural(hours / 24, "day") + " ago"
    }
    return if (service == null) "Updated $ago" else "Updated from $service $ago"
}

fun isStale(lastSyncMillis: Long?, nowMillis: Long): Boolean =
    lastSyncMillis != null && nowMillis - lastSyncMillis > STALE_AFTER_MS

fun SyncStatusUi.isStaleAt(nowMillis: Long): Boolean =
    failingBeforeFirstSync || isStale(lastSyncMillis, nowMillis)

/** The week's line: the service named when every connection has the same one (4d-2 Decision 12). */
fun weekSubtitle(sync: SyncStatusUi, nowMillis: Long): String =
    updatedLabel(sync.lastSyncMillis, nowMillis, sync.serviceNames.distinct().singleOrNull())

/**
 * The week's line with its state in words as well as colour: after a failed pass, what went wrong and what to do
 * (5 §A.3); otherwise its age, "Out of date — …" once the week is stale.
 */
fun weekLine(sync: SyncStatusUi, nowMillis: Long): String {
    sync.problem?.let { return problemLine(it) }
    val line = weekSubtitle(sync, nowMillis)
    return if (sync.isStaleAt(nowMillis)) "Out of date — ${line.replaceFirstChar { it.lowercase() }}" else line
}

/** 5 §A.3's table; Update now stays beside it. */
fun problemLine(problem: SyncProblem): String = when (problem) {
    SyncProblem.Offline -> "The tablet is offline. Check the Wi-Fi, then Update now."
    is SyncProblem.NoAnswer -> "${problem.service} didn’t answer. Try Update now in a few minutes."
    is SyncProblem.SignIn -> "${problem.service} needs you to sign in again. Reconnect in Settings › Calendars."
}

/** The first of [resolvePeople], with [people]'s own order as the household's. */
fun resolvePerson(forPerson: String?, sourcePerson: PersonId, people: Map<PersonId, Person>): Person =
    resolvePeople(forPerson, sourcePerson, people, people.keys.toList()).first()

internal val HOURS_MINUTES: DateTimeFormatter = DateTimeFormatter.ofPattern("HH:mm", Locale.ENGLISH)
// ENGLISH, not UK: JDK 17's CLDR data gives "Sept" for Locale.UK, and Android versions differ.
internal val SHORT_DAY: DateTimeFormatter = DateTimeFormatter.ofPattern("EEE d MMM", Locale.ENGLISH)
internal val WEEKDAY: DateTimeFormatter = DateTimeFormatter.ofPattern("EEE", Locale.ENGLISH)
/** "Sun 27": a day and its date. */
internal val DAY_AND_DATE: DateTimeFormatter = DateTimeFormatter.ofPattern("EEE d", Locale.ENGLISH)
internal val DAY_AND_MONTH: DateTimeFormatter = DateTimeFormatter.ofPattern("EEE d MMMM", Locale.ENGLISH)

internal val FULL_WEEKDAY: DateTimeFormatter = DateTimeFormatter.ofPattern("EEEE", Locale.ENGLISH)

/** "Tuesday 7 October": how TalkBack names a day. */
internal val FULL_DATE: DateTimeFormatter = DateTimeFormatter.ofPattern("EEEE d MMMM", Locale.ENGLISH)

/** How many weeks past this one the Calendar tab steps to (4c D10): four weeks in all, inside the synced window. */
const val MAX_WEEKS_AHEAD = 3

/** Home's Today row for a multi-day event: where the time would be (9 §2.1). */
fun dayOf(span: DaySpan): String = "Day ${span.day} of ${span.of}"

/** The last day the Calendar tab can show from [today]: the seventh day of its furthest week. */
fun lastShownDay(today: LocalDate): LocalDate = today.plusWeeks(MAX_WEEKS_AHEAD + 1L).minusDays(1)

/** The Calendar tab's title for the week [weeksAhead] after this one. */
fun weekTitle(weeksAhead: Int): String = when (weeksAhead) {
    0 -> "This week"
    1 -> "Next week"
    else -> "In $weeksAhead weeks"
}

/** 4c §6.6: the toast after adding an event the Calendar tab can't reach, so the family know where it went. */
fun eventAddedFor(day: LocalDate): String = "Event added on ${day.format(DAY_AND_MONTH)}."

/** An event ending at exactly midnight ends on the day before. */
private fun lastDayOf(from: ZonedDateTime, to: ZonedDateTime): LocalDate {
    val endsAtMidnight = to.toLocalTime() == LocalTime.MIDNIGHT
    return if (endsAtMidnight && to.toLocalDate().isAfter(from.toLocalDate())) to.toLocalDate().minusDays(1) else to.toLocalDate()
}

internal fun dayLabel(date: LocalDate, today: LocalDate): String = when (date) {
    today -> "Today"
    today.plusDays(1) -> "Tomorrow"
    today.minusDays(1) -> "Yesterday"
    else -> date.format(SHORT_DAY)
}

/** An event's first and last day in [zone]. An all-day end is exclusive; a timed end at exactly midnight belongs to the day before. */
internal fun daySpan(start: EventTime, end: EventTime, zone: ZoneId): Pair<LocalDate, LocalDate> =
    if (start is EventTime.AllDay && end is EventTime.AllDay) {
        start.date to maxOf(start.date, end.date.minusDays(1))
    } else {
        val from = start.instantIn(zone).atZone(zone)
        from.toLocalDate() to lastDayOf(from, end.instantIn(zone).atZone(zone))
    }

/** The detail sheet's "When": "Today · 19:30–21:00", "Sat 26 Sep · All day", or the span of a longer event. */
fun whenLabel(start: EventTime, end: EventTime, zone: ZoneId, today: LocalDate): String {
    val (first, last) = daySpan(start, end, zone)
    if (start is EventTime.AllDay && end is EventTime.AllDay) {
        return if (last == first) {
            "${dayLabel(first, today)} · $ALL_DAY_LABEL"
        } else {
            "${dayLabel(first, today)} – ${dayLabel(last, today)} · $ALL_DAY_LABEL"
        }
    }
    val from = start.instantIn(zone).atZone(zone)
    val to = end.instantIn(zone).atZone(zone)
    return if (last == first) {
        "${dayLabel(first, today)} · ${from.format(HOURS_MINUTES)}–${to.format(HOURS_MINUTES)}"
    } else {
        "${dayLabel(first, today)} ${from.format(HOURS_MINUTES)} – ${dayLabel(to.toLocalDate(), today)} ${to.format(HOURS_MINUTES)}"
    }
}

private class Slice(val timeLabel: String, val startLabel: String, val allDay: Boolean)

/** How the event reads on [date]. A timed event over several days reads "22:00–", then "All day", then "until 01:00". */
private fun StoredEvent.sliceOn(date: LocalDate, zone: ZoneId): Slice {
    if (start is EventTime.AllDay) return Slice(ALL_DAY_LABEL, ALL_DAY_LABEL, allDay = true)
    val from = start.instantIn(zone).atZone(zone)
    val to = end.instantIn(zone).atZone(zone)
    val endsAtMidnight = to.toLocalTime() == LocalTime.MIDNIGHT
    val lastDay = lastDayOf(from, to)
    val startClock = from.format(HOURS_MINUTES)
    val endClock = to.format(HOURS_MINUTES)
    return when {
        lastDay == from.toLocalDate() -> Slice("$startClock–$endClock", startClock, allDay = false)
        date == from.toLocalDate() -> Slice("$startClock–", "$startClock–", allDay = false)
        date == lastDay && !endsAtMidnight -> Slice("until $endClock", "until $endClock", allDay = false)
        else -> Slice(ALL_DAY_LABEL, ALL_DAY_LABEL, allDay = true)
    }
}

/** How TalkBack reads a slice's time: "09:00 to 10:30", "from 22:00", "until 01:00", "all day"; a dash isn't read well. */
internal fun spokenTime(label: String): String = when {
    label == ALL_DAY_LABEL -> "all day"
    label.endsWith("–") -> "from ${label.dropLast(1)}"
    else -> label.replace("–", " to ")
}

/** Sources, connection labels, service names and which providers can write: what the UI needs beyond the event row. */
internal class SourceCatalog(
    sources: List<StoredSource>,
    connections: List<StoredConnection>,
    private val writerIds: Set<String>,
    private val serviceNames: Map<String, String> = emptyMap(),
) {
    private val sources = sources.associateBy { it.connectionId to it.source.id }
    private val connections = connections.associate { it.connection.id to it.connection }

    fun source(connectionId: String, sourceId: String): StoredSource? = sources[connectionId to sourceId]

    /** The provider's display name; the connection label when the provider isn't installed. */
    fun serviceName(connectionId: String): String = connections[connectionId]?.let { serviceNameOf(it, serviceNames::get) }.orEmpty()

    fun hasWriter(connectionId: String): Boolean = connections[connectionId]?.providerId in writerIds
}

/** Only the master calendar carries the tablet's tags; another writable calendar was filled in [serviceName]; one nobody here can change is named itself. */
internal fun createdByLabel(createdBy: String?, reason: ReadOnlyReason?, people: Map<PersonId, Person>, serviceName: String, sourceName: String): String =
    when (reason) {
        ReadOnlyReason.OtherCalendar -> sourceName
        ReadOnlyReason.NotMaster -> serviceName
        else -> createdBy?.let { people[PersonId(it)]?.name } ?: ADDED_FROM_PHONE
    }

internal fun StoredEvent.toUi(
    date: LocalDate,
    zone: ZoneId,
    people: Map<PersonId, Person>,
    catalog: SourceCatalog,
    syncing: Boolean = false,
): EventUi {
    val slice = sliceOn(date, zone)
    val source = catalog.source(connectionId, sourceId)
    val serviceName = catalog.serviceName(connectionId)
    val reason = readOnlyReason(this, source, catalog.hasWriter(connectionId))
    return EventUi(
        ref = ref,
        title = title,
        timeLabel = slice.timeLabel,
        startLabel = slice.startLabel,
        person = resolvePerson(forPerson, sourcePerson, people),
        allDay = slice.allDay,
        recurring = recurring,
        startSort = startSort,
        sourceName = source?.source?.name.orEmpty(),
        serviceName = serviceName,
        readOnlyReason = reason,
        untagged = source?.isMaster == true && source.source.writable && forPerson == null && createdBy == null,
        syncing = syncing,
        createdBy = createdByLabel(createdBy, reason, people, serviceName, source?.source?.name.orEmpty()),
        repeats = repeatsLabel(recurrenceRule, start, zone),
        assignable = mayAssign(this, reason),
        span = dayOfSpan(date, zone),
    )
}
