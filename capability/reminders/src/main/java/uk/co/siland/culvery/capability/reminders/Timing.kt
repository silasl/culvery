package uk.co.siland.culvery.capability.reminders

import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import java.time.ZonedDateTime

sealed interface DueKey {
    data class Mine(val id: Long) : DueKey
    data class Bins(val date: LocalDate) : DueKey
}

/** One waiting thing: the latest of its times after "answered at" and at or before now. [toString] prints the key only. */
data class Due(val key: DueKey, val dueAtMillis: Long, val snoozedUntilMillis: Long?, val chimedAtMillis: Long?) {
    override fun toString() = "Due($key)"
}

internal fun timeOn(date: LocalDate, time: LocalTime, zone: ZoneId): Long =
    ZonedDateTime.of(date, time, zone).toInstant().toEpochMilli()

internal fun Reminder.due(nowMillis: Long, zone: ZoneId): Due? {
    val latest = when (schedule) {
        is Schedule.Once -> listOf(timeOn(schedule.date, time, zone))
        is Schedule.Weekly -> {
            val today = Instant.ofEpochMilli(nowMillis).atZone(zone).toLocalDate()
            (0L..7L).map { today.minusDays(it) }
                .filter { it.dayOfWeek in schedule.days }
                .map { timeOn(it, time, zone) }
        }
    }.filter { it > answeredAtMillis && it <= nowMillis }.maxOrNull() ?: return null
    return Due(DueKey.Mine(id), latest, snoozedUntilMillis, chimedAtMillis)
}

internal fun Reminder.nextDue(afterMillis: Long, zone: ZoneId): Long? = when (schedule) {
    is Schedule.Once -> timeOn(schedule.date, time, zone).takeIf { it > afterMillis }
    is Schedule.Weekly -> {
        val from = Instant.ofEpochMilli(afterMillis).atZone(zone).toLocalDate()
        (0L..7L).map { from.plusDays(it) }
            .filter { it.dayOfWeek in schedule.days }
            .map { timeOn(it, time, zone) }
            .firstOrNull { it > afterMillis }
    }
}

internal fun Due.snoozed(nowMillis: Long): Boolean {
    val until = snoozedUntilMillis ?: return false
    return nowMillis < until && until - nowMillis <= LATER_MS && dueAtMillis <= until - LATER_MS
}

internal fun Due.chimeOwed(nowMillis: Long, heldEndedAtMillis: Long?): Boolean {
    if (snoozed(nowMillis)) return false
    val chimedAt = chimedAtMillis?.takeIf { it <= nowMillis } ?: return true
    val latest = listOfNotNull(dueAtMillis, snoozedUntilMillis, heldEndedAtMillis).filter { it <= nowMillis }.maxOrNull() ?: return false
    return latest > chimedAt
}

internal fun inOrder(dues: List<Due>): List<Due> = dues.sortedWith(
    compareBy<Due> { it.dueAtMillis }
        .thenBy { if (it.key is DueKey.Bins) 0 else 1 }
        .thenBy { (it.key as? DueKey.Mine)?.id ?: 0L },
)
