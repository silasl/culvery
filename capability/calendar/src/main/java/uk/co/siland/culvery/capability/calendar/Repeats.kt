package uk.co.siland.culvery.capability.calendar

import java.time.ZoneId

/** The Repeats row when the rule isn't simple, or unknown. */
const val REPEATS_YES = "Yes"

private val UNITS = mapOf("DAILY" to "day", "WEEKLY" to "week", "MONTHLY" to "month", "YEARLY" to "year")

/** Parts that only bound the series or restate its start; the series' end (UNTIL, COUNT) isn't shown. */
private val PLAIN_PARTS = setOf("FREQ", "INTERVAL", "UNTIL", "COUNT", "WKST")

/**
 * The detail sheet's Repeats row (3a design D12, §3.5): "Every day", "Every week", "Every month", "Every year", or
 * "Every {n} days/weeks/months/years", when [rule] is simple. Simple allows a weekly BYDAY of the start's own weekday
 * and a monthly BYMONTHDAY of its own day, which Google writes for "Weekly on Tuesday". Anything else is "Yes".
 */
fun repeatsLabel(rule: String?, start: EventTime, zone: ZoneId): String {
    val parts = rule?.removePrefix("RRULE:")?.split(';')?.associate { part ->
        val pair = part.split('=', limit = 2)
        if (pair.size != 2) return REPEATS_YES
        pair[0].uppercase() to pair[1].uppercase()
    } ?: return REPEATS_YES
    val freq = parts["FREQ"] ?: return REPEATS_YES
    val unit = UNITS[freq] ?: return REPEATS_YES
    val day = start.instantIn(zone).atZone(zone).toLocalDate()
    val simple = parts.all { (key, value) ->
        key in PLAIN_PARTS ||
            (key == "BYDAY" && freq == "WEEKLY" && value == day.dayOfWeek.name.take(2)) ||
            (key == "BYMONTHDAY" && freq == "MONTHLY" && value == day.dayOfMonth.toString())
    }
    val interval = parts["INTERVAL"]?.let { it.toIntOrNull() ?: return REPEATS_YES } ?: 1
    if (!simple || interval < 1) return REPEATS_YES
    return if (interval == 1) "Every $unit" else "Every $interval ${unit}s"
}
