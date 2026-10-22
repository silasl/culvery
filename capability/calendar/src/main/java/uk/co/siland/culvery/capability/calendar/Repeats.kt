package uk.co.siland.culvery.capability.calendar

import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.ZoneOffset
import uk.co.siland.culvery.core.ui.plural

/** The Repeats row when the rule isn't simple, or unknown. */
const val REPEATS_YES = "Yes"

private val UNITS = mapOf("DAILY" to "day", "WEEKLY" to "week", "MONTHLY" to "month", "YEARLY" to "year")

/**
 * The detail sheet's Repeats row (9 §3.4), in the editor's words from the series' first date (else [start]): "Every
 * Saturday", "Every month, on the last Thursday", "Every month, on the 22nd"; another interval reads "Every 3 months".
 * Then " · 12 times · last on Sat 12 Dec" (" · 12 times" when the rule has no first date) or " · until Sat 12 Dec".
 * Anything else is "Yes".
 */
fun repeatsLabel(rule: String?, start: EventTime, zone: ZoneId): String {
    val parts = rule?.let(::ruleParts) ?: return REPEATS_YES
    val first = seriesStart(rule)
    val day = first ?: start.instantIn(zone).atZone(zone).toLocalDate()
    val choice = readChoice(parts, day)
    val nth = nthOf(rule, day)
    val words = if (choice != null) choice.wording(day, nth) else otherInterval(parts, day) ?: return REPEATS_YES
    val dates: ((LocalDate, Int) -> List<LocalDate>)? = choice?.let { c -> { from, n -> occurrences(c, from, n, nth) } }
    val count = parts["COUNT"]?.toIntOrNull()?.takeIf { it >= 1 }?.let { n ->
        val last = if (dates != null && first != null) " · last on ${dates(first, n).last().format(SHORT_DAY)}" else ""
        " · ${plural(n.toLong(), "time")}$last"
    }.orEmpty()
    val until = parts["UNTIL"]?.let { untilDay(it, zone) }?.let { " · until ${it.format(SHORT_DAY)}" }.orEmpty()
    return words + count + until
}

/** "Every 3 months": an interval no choice writes, when its BYDAY or BYMONTHDAY is [day]'s own. */
private fun otherInterval(parts: Map<String, String>, day: LocalDate): String? {
    val freq = parts["FREQ"]
    val unit = UNITS[freq] ?: return null
    val interval = parts["INTERVAL"]?.toIntOrNull() ?: return null
    val simple = parts.all { (key, value) ->
        key in BOUNDING_PARTS ||
            (key == "BYDAY" && freq == "WEEKLY" && value == day.dayOfWeek.name.take(2)) ||
            (key == "BYMONTHDAY" && freq == "MONTHLY" && value == day.dayOfMonth.toString())
    }
    return if (simple && interval > 1) "Every $interval ${unit}s" else null
}

/** UNTIL as a date, or as a UTC time (Google's form for a timed series) read in [zone]; null when neither. */
private fun untilDay(value: String, zone: ZoneId): LocalDate? =
    runCatching { LocalDate.parse(value, UNTIL_DATE) }.getOrNull()
        ?: runCatching { LocalDateTime.parse(value, UNTIL_UTC).toInstant(ZoneOffset.UTC) }.getOrNull()?.atZone(zone)?.toLocalDate()
