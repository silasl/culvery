package uk.co.siland.culvery.capability.door

import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

/** One household date's events, newest first. */
data class DoorDay(val date: LocalDate, val events: List<DoorEvent>)

/** The household's date at [millis]. */
internal fun dateIn(millis: Long, zone: ZoneId): LocalDate = Instant.ofEpochMilli(millis).atZone(zone).toLocalDate()

/** The first moment kept: the household's midnight [keep] days back, today included (15a §3.1). */
fun keepFrom(today: LocalDate, zone: ZoneId, keep: KeepDays): Long =
    today.minusDays(keep.days - 1L).atStartOfDay(zone).toInstant().toEpochMilli()

/**
 * [events] (newest first) by the household's dates (5 §5.2), newest day first and empty days left out. Anything before
 * the [keep] cut-off is dropped, so the tab never shows a day the prune hasn't reached yet.
 */
fun doorDays(events: List<DoorEvent>, today: LocalDate, zone: ZoneId, keep: KeepDays): List<DoorDay> {
    val cut = keepFrom(today, zone, keep)
    return events.filter { it.atMillis >= cut }
        .groupBy { dateIn(it.atMillis, zone) }
        .map { (date, onThatDay) -> DoorDay(date, onThatDay) }
        .sortedByDescending { it.date }
}
