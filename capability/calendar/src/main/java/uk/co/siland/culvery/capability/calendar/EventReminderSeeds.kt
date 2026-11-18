package uk.co.siland.culvery.capability.calendar

import java.net.URLEncoder
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import java.time.temporal.ChronoUnit
import uk.co.siland.culvery.core.house.EventSeed
import uk.co.siland.culvery.core.house.EventTimes
import uk.co.siland.culvery.core.household.PersonId
import uk.co.siland.culvery.core.repeat.RepeatRule
import uk.co.siland.culvery.core.repeat.ruleParts
import uk.co.siland.culvery.core.repeat.seriesStart
import uk.co.siland.culvery.core.repeat.startedFrom
import uk.co.siland.culvery.core.repeat.withoutSeriesStart

/** What an event's card offers: to add a reminder, or to change the one it has (22 §10.4). */
enum class ReminderButton { Add, Change }

/** The link's key for an event: its connection, source and id, each URL-encoded, joined with `/`. It never reaches a log. */
internal fun eventKey(connectionId: String, sourceId: String, id: String): String =
    listOf(connectionId, sourceId, id).joinToString("/") { URLEncoder.encode(it, "UTF-8") }

/** One key for a whole series, the same as the one-off it may have been. */
internal fun eventKey(event: StoredEvent): String = eventKey(event.connectionId, event.sourceId, event.seriesId ?: event.remoteId)

/**
 * [event]'s times for the link. A series' rule is [event]'s own, its day the rule's first-date line, and its time the
 * commonest among [siblings] from [today] on (all of them when none is ahead).
 */
internal fun eventTimes(event: StoredEvent, siblings: List<StoredEvent>, zone: ZoneId, today: LocalDate): EventTimes {
    val rule = seriesRule(event, siblings, zone, today) ?: return EventTimes(event.start.dayIn(zone), event.start.timeIn(zone), null)
    val dated = siblings.ifEmpty { listOf(event) }.sortedBy { it.startSort }
    val counted = dated.filter { !it.start.dayIn(zone).isBefore(today) }.ifEmpty { dated }
    val time = counted.groupingBy { it.start.timeIn(zone) }.eachCount().maxBy { it.value }.key
    return EventTimes(seriesStart(rule), time, withoutSeriesStart(rule))
}

/**
 * What the reminder form opens with for [event]; null when no reminder can be added: a bin collection, a one-off that
 * has started, a series with no date left. A series opens on the date tapped, or the next one if that has passed, at its
 * usual time. A rule
 * the shared code can't work out, or a count with no first day, gives a one-off for [event]'s own date.
 */
internal fun reminderSeed(event: StoredEvent, siblings: List<StoredEvent>, people: List<PersonId>, nowMillis: Long, zone: ZoneId): EventSeed? {
    if (event.bins != null) return null
    val now = Instant.ofEpochMilli(nowMillis)
    val today = now.atZone(zone).toLocalDate()
    val time = event.start.timeIn(zone)
    val times = eventTimes(event, siblings, zone, today)
    // An all-day date has started only once it is over (22 §10.5).
    fun started(day: LocalDate, at: LocalTime?) = if (at == null) day.isBefore(today) else !day.atTime(at).atZone(zone).toInstant().isAfter(now)
    val tapped = event.start.dayIn(zone)
    val tappedStarted = started(tapped, time)
    val rule = seriesRule(event, siblings, zone, today)?.takeIf { workable(it, zone) }
    val (day, left) = if (rule == null) {
        if (tappedStarted) return null
        tapped to null
    } else {
        val from = when {
            !tappedStarted -> tapped
            started(today, times.time) -> today.plusDays(1)
            else -> today
        }
        startedFrom(rule, seriesStart(rule) ?: tapped, from, zone) ?: return null
    }
    // Past the date tapped, the series' usual time, not the one that date may have been moved to.
    return EventSeed(eventKey(event), event.title, people, day, if (tappedStarted) times.time else time, left, times)
}

// A date whose own rule wasn't read takes its series' from another stored date: the earliest coming one's, else the latest.
private fun seriesRule(event: StoredEvent, siblings: List<StoredEvent>, zone: ZoneId, today: LocalDate): String? {
    event.recurrenceRule?.let { return it }
    val ruled = siblings.filter { it.recurrenceRule != null }.sortedBy { it.startSort }
    return (ruled.firstOrNull { !it.start.dayIn(zone).isBefore(today) } ?: ruled.lastOrNull())?.recurrenceRule
}

// A COUNT can be reduced only from the series' true first day.
private fun workable(rule: String, zone: ZoneId): Boolean =
    RepeatRule.parse(rule, zone) != null && (seriesStart(rule) != null || ruleParts(rule)?.containsKey("COUNT") != true)

private fun EventTime.dayIn(zone: ZoneId): LocalDate = when (this) {
    is EventTime.AllDay -> date
    is EventTime.Timed -> instant.atZone(zone).toLocalDate()
}

private fun EventTime.timeIn(zone: ZoneId): LocalTime? = when (this) {
    is EventTime.AllDay -> null
    is EventTime.Timed -> instant.atZone(zone).toLocalTime().truncatedTo(ChronoUnit.MINUTES)
}
