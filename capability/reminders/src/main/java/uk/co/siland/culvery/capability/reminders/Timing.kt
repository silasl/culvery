package uk.co.siland.culvery.capability.reminders

import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import java.time.ZonedDateTime
import uk.co.siland.culvery.core.repeat.RepeatRule

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

private fun dayOf(millis: Long, zone: ZoneId): LocalDate = Instant.ofEpochMilli(millis).atZone(zone).toLocalDate()

/** A rule [RepeatRule.parse] refuses is never due. */
internal fun Reminder.due(nowMillis: Long, zone: ZoneId): Due? {
    val latest = when (schedule) {
        is Schedule.Once -> timeOn(schedule.first, time, zone).takeIf { it <= nowMillis }
        is Schedule.Repeating -> {
            val rule = RepeatRule.parse(schedule.rule, zone) ?: return null
            val onOrBeforeToday = rule.latestOnOrBefore(schedule.first, dayOf(nowMillis, zone)) ?: return null
            timeOn(onOrBeforeToday, time, zone).takeIf { it <= nowMillis }
                ?: rule.latestOnOrBefore(schedule.first, onOrBeforeToday.minusDays(1))?.let { timeOn(it, time, zone) }
        }
    }?.takeIf { it > answeredAtMillis } ?: return null
    return Due(DueKey.Mine(id), latest, snoozedUntilMillis, chimedAtMillis)
}

internal fun Reminder.nextDue(afterMillis: Long, zone: ZoneId): Long? = schedule.nextAt(time, afterMillis, zone)

/** The first of this schedule's times at [time] after [afterMillis]; a rule [RepeatRule.parse] refuses has none. */
internal fun Schedule.nextAt(time: LocalTime, afterMillis: Long, zone: ZoneId): Long? = when (this) {
    is Schedule.Once -> timeOn(first, time, zone).takeIf { it > afterMillis }
    is Schedule.Repeating -> RepeatRule.parse(rule, zone)?.let { parsed ->
        val onOrAfter = parsed.firstAfter(first, dayOf(afterMillis, zone).minusDays(1))
        onOrAfter?.let { timeOn(it, time, zone) }?.takeIf { it > afterMillis }
            ?: onOrAfter?.let { parsed.firstAfter(first, it) }?.let { timeOn(it, time, zone) }
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
