package uk.co.siland.culvery.core.repeat

import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

/** The Repeats row when the rule isn't simple, or unknown. */
const val REPEATS_YES = "Yes"

private val UNITS = mapOf("DAILY" to "day", "WEEKLY" to "week", "MONTHLY" to "month", "YEARLY" to "year")

/**
 * The detail sheet's Repeats row (9 §3.4), in the editor's words from the series' first date (else [day]): "Every
 * Saturday", "Every month, on the last Thursday", "Every month, on the 22nd"; another interval reads "Every 3 months".
 * Then " · 12 times · last on Sat 12 Dec" (" · 12 times" when the rule has no first date, or past the count
 * [lastOccurrence] works out) or " · until Sat 12 Dec"; that last date has its year when it isn't [today]'s.
 * Anything else is "Yes".
 */
fun repeatWords(rule: String?, day: LocalDate, zone: ZoneId, today: LocalDate): String {
    val parts = rule?.let(::ruleParts) ?: return REPEATS_YES
    val first = seriesStart(rule)
    val firstDay = first ?: day
    val choice = readChoice(parts, firstDay)
    val nth = nthOf(rule, firstDay)
    val days = daysOf(rule, firstDay)
    val words = if (choice != null) choice.wording(firstDay, nth, days) else otherInterval(parts, firstDay) ?: return REPEATS_YES
    val count = parts["COUNT"]?.toIntOrNull()?.takeIf { it >= 1 }?.let { n ->
        val lastDay = if (choice != null && first != null) lastOccurrence(choice, first, n, nth, days) else null
        val last = lastDay?.let { " · last on ${shortDay(it, today)}" }.orEmpty()
        " · ${"%,d".format(Locale.UK, n)} ${if (n == 1) "time" else "times"}$last"
    }.orEmpty()
    val until = parts["UNTIL"]?.let { untilDay(it, zone) }?.let { " · until ${shortDay(it, today)}" }.orEmpty()
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

// ENGLISH, not UK: JDK 17's CLDR data gives "Sept" for Locale.UK, and Android versions differ.
val SHORT_DAY: DateTimeFormatter = DateTimeFormatter.ofPattern("EEE d MMM", Locale.ENGLISH)
private val SHORT_DAY_AND_YEAR: DateTimeFormatter = DateTimeFormatter.ofPattern("EEE d MMM yyyy", Locale.ENGLISH)

/** "Sat 12 Dec", or "Tue 2 Mar 2027" when [date] isn't in [today]'s year. */
fun shortDay(date: LocalDate, today: LocalDate): String = date.format(if (date.year == today.year) SHORT_DAY else SHORT_DAY_AND_YEAR)
