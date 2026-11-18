package uk.co.siland.culvery.provider.calendar_fake

import java.time.Duration
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.ZoneOffset
import java.time.temporal.ChronoUnit
import uk.co.siland.culvery.capability.calendar.EventTime
import uk.co.siland.culvery.capability.calendar.RemoteEvent
import uk.co.siland.culvery.capability.calendar.instantIn
import uk.co.siland.culvery.core.repeat.RepeatRule
import uk.co.siland.culvery.core.repeat.UNTIL_UTC
import uk.co.siland.culvery.core.repeat.ruleParts

/**
 * Turns a series the tablet wrote into one event per occurrence, as Google returns it with singleEvents=true.
 * Reads the rule with [RepeatRule] (not strict: a part it doesn't know is ignored) and cuts at UNTIL's exact instant. A
 * series whose rule it can't read is returned as it is. The series' first-date line is skipped.
 * It accepts any COUNT of 1 or more on purpose: a phone can write COUNT=1, though the tablet writes 2 to 52.
 * Occurrence ids are "{series}-{date}", as in [SampleEvents].
 */
internal object RecurrenceExpander {
    /** The occurrences of [series] starting on or before [lastDay] in [zone]; [series] as it is when it doesn't repeat. */
    fun expand(series: RemoteEvent, zone: ZoneId, lastDay: LocalDate): List<RemoteEvent> {
        val rule = series.recurrenceRule?.takeIf { series.recurring } ?: return listOf(series)
        val repeat = RepeatRule.parse(rule, zone, strict = false) ?: return listOf(series)
        val untilAt = ruleParts(rule)?.get("UNTIL")?.let { runCatching { LocalDateTime.parse(it, UNTIL_UTC).toInstant(ZoneOffset.UTC) }.getOrNull() }
        return repeat.dates(series.start.dateIn(zone))
            .takeWhile { !it.isAfter(lastDay) }
            .map { day -> series.on(day, zone) }
            .takeWhile { untilAt == null || !it.start.instantIn(zone).isAfter(untilAt) }
            .toList()
    }

    private fun EventTime.dateIn(zone: ZoneId): LocalDate = when (this) {
        is EventTime.Timed -> instant.atZone(zone).toLocalDate()
        is EventTime.AllDay -> date
    }

    private fun RemoteEvent.on(day: LocalDate, zone: ZoneId): RemoteEvent {
        val (newStart, newEnd) = when (val s = start) {
            is EventTime.Timed -> {
                val length = Duration.between(s.instant, (end as EventTime.Timed).instant)
                val at = day.atTime(s.instant.atZone(zone).toLocalTime()).atZone(zone).toInstant()
                EventTime.Timed(at) to EventTime.Timed(at.plus(length))
            }
            is EventTime.AllDay -> {
                val days = ChronoUnit.DAYS.between(s.date, (end as EventTime.AllDay).date)
                EventTime.AllDay(day) to EventTime.AllDay(day.plusDays(days))
            }
        }
        return copy(remoteId = "$remoteId-$day", start = newStart, end = newEnd, seriesId = remoteId)
    }
}
