package uk.co.siland.culvery.provider.calendar_fake

import java.time.Duration
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoUnit
import uk.co.siland.culvery.capability.calendar.EventTime
import uk.co.siland.culvery.capability.calendar.RemoteEvent
import uk.co.siland.culvery.capability.calendar.instantIn

private val UNTIL_DATE: DateTimeFormatter = DateTimeFormatter.ofPattern("yyyyMMdd")
private val UNTIL_UTC: DateTimeFormatter = DateTimeFormatter.ofPattern("yyyyMMdd'T'HHmmss'Z'")

/**
 * Turns a series the tablet wrote into one event per occurrence, as Google returns it with singleEvents=true.
 * Understands what the tablet writes: FREQ of DAILY, WEEKLY, MONTHLY or YEARLY, INTERVAL, UNTIL (a date, or a
 * UTC date-time), inclusive, and COUNT (ignored unless a whole number of 1 or more; with UNTIL too, the first to end it wins).
 * It accepts any COUNT of 1 or more on purpose: a phone can write COUNT=1, though the tablet writes 2 to 52.
 * Occurrence ids are "{series}-{date}", as in [SampleEvents].
 */
internal object RecurrenceExpander {
    /** The occurrences of [series] starting on or before [lastDay] in [zone]; [series] as it is when it doesn't repeat. */
    fun expand(series: RemoteEvent, zone: ZoneId, lastDay: LocalDate): List<RemoteEvent> {
        val rule = series.recurrenceRule?.takeIf { series.recurring } ?: return listOf(series)
        val parts = rule.removePrefix("RRULE:").split(';').mapNotNull { part -> part.split('=', limit = 2).takeIf { it.size == 2 } }
            .associate { (key, value) -> key to value }
        val interval = (parts["INTERVAL"]?.toLongOrNull() ?: 1L).coerceAtLeast(1L)
        val step: (LocalDate, Long) -> LocalDate = when (parts["FREQ"]) {
            "DAILY" -> { day, n -> day.plusDays(n) }
            "WEEKLY" -> { day, n -> day.plusWeeks(n) }
            "MONTHLY" -> { day, n -> day.plusMonths(n) }
            "YEARLY" -> { day, n -> day.plusYears(n) }
            else -> return listOf(series)
        }
        val untilDay = parts["UNTIL"]?.takeIf { it.length == 8 }?.let { runCatching { LocalDate.parse(it, UNTIL_DATE) }.getOrNull() }
        val untilAt = parts["UNTIL"]?.let { runCatching { LocalDateTime.parse(it, UNTIL_UTC).toInstant(ZoneOffset.UTC) }.getOrNull() }
        val count = parts["COUNT"]?.toIntOrNull()?.takeIf { it >= 1 } ?: Int.MAX_VALUE
        val first = series.start.dateIn(zone)
        return generateSequence(0L) { it + 1 }
            .map { n -> step(first, n * interval) }
            .takeWhile { !it.isAfter(lastDay) && (untilDay == null || !it.isAfter(untilDay)) }
            .map { day -> series.on(day, zone) }
            .takeWhile { untilAt == null || !it.start.instantIn(zone).isAfter(untilAt) }
            .take(count)
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
