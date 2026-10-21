package uk.co.siland.culvery.capability.calendar

import java.time.DayOfWeek
import java.time.LocalDate
import java.time.MonthDay
import java.time.YearMonth
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.TextStyle
import java.util.Locale

/**
 * How a series repeats; the editor words each choice from its day with [wording] (9 §3.1). [Monthly] (by date) is never
 * offered, but the detail sheet reads a phone's monthly-by-date rule as it.
 */
enum class RepeatChoice(val freq: String?, val interval: Int) {
    Never(null, 1),
    Daily("DAILY", 1),
    Weekly("WEEKLY", 1),
    Fortnightly("WEEKLY", 2),
    Monthly("MONTHLY", 1),
    Yearly("YEARLY", 1),
    MonthlyOnWeekday("MONTHLY", 1),
}

/** The editor's Repeats choices, in the pane's order (9 §3.1). */
val EDITOR_REPEATS: List<RepeatChoice> = listOf(
    RepeatChoice.Never,
    RepeatChoice.Weekly,
    RepeatChoice.Fortnightly,
    RepeatChoice.MonthlyOnWeekday,
    RepeatChoice.Yearly,
    RepeatChoice.Daily,
)

const val DOESNT_REPEAT = "Doesn’t repeat"

private val DATE_IN_YEAR: DateTimeFormatter = DateTimeFormatter.ofPattern("d MMMM", Locale.ENGLISH)
private const val LAST = -1
private const val FIFTH = 5

internal fun weekdayName(day: LocalDate): String = day.dayOfWeek.getDisplayName(TextStyle.FULL, Locale.ENGLISH)

/** "Every Saturday", "Every 2 weeks, on Saturday", "Every month, on the 4th Saturday", "Every year, on 26 September"… */
fun RepeatChoice.wording(day: LocalDate): String = when (this) {
    RepeatChoice.Never -> DOESNT_REPEAT
    RepeatChoice.Daily -> "Every day"
    RepeatChoice.Weekly -> "Every ${weekdayName(day)}"
    RepeatChoice.Fortnightly -> "Every 2 weeks, on ${weekdayName(day)}"
    RepeatChoice.Monthly -> "Every month, on the ${ordinal(day.dayOfMonth)}"
    RepeatChoice.MonthlyOnWeekday -> "Every month, on the ${ordinal(nthWeekday(day))} ${weekdayName(day)}"
    RepeatChoice.Yearly -> "Every year, on ${day.format(DATE_IN_YEAR)}"
}

/** Which of its weekday in the month [day] is: 1 to 4, or −1 for a 5th, which is always the last. */
fun nthWeekday(day: LocalDate): Int = ((day.dayOfMonth - 1) / DAYS_PER_WEEK + 1).let { if (it == FIFTH) LAST else it }

/** "1st", "2nd", "3rd", "4th", "11th", "22nd"; −1 is "last". */
fun ordinal(n: Int): String {
    if (n == LAST) return "last"
    val suffix = when {
        n % 100 in 11..13 -> "th"
        n % 10 == 1 -> "st"
        n % 10 == 2 -> "nd"
        n % 10 == 3 -> "rd"
        else -> "th"
    }
    return "$n$suffix"
}

// RRULE's two-letter weekday, "SA".
internal fun DayOfWeek.code(): String = name.take(2)

/** The [n]th (−1: last) [weekday] of [month]. */
private fun nthWeekdayIn(month: YearMonth, n: Int, weekday: DayOfWeek): LocalDate {
    if (n == LAST) {
        val last = month.atEndOfMonth()
        return last.minusDays(((last.dayOfWeek.value - weekday.value + DAYS_PER_WEEK) % DAYS_PER_WEEK).toLong())
    }
    val first = month.atDay(1)
    return first.plusDays(((weekday.value - first.dayOfWeek.value + DAYS_PER_WEEK) % DAYS_PER_WEEK).toLong()).plusWeeks(n - 1L)
}

/**
 * The first [count] dates of [choice] from [day], as the RRULE has them: a month or year with no such date (the 31st,
 * or 29 February outside a leap year) is skipped, not moved. [nth] is [RepeatChoice.MonthlyOnWeekday]'s place in the
 * month, −1 for last; a phone may say last where the editor says 4th. The one place a series' last date is worked out (9 §3.1).
 */
fun occurrences(choice: RepeatChoice, day: LocalDate, count: Int, nth: Int = nthWeekday(day)): List<LocalDate> {
    val step = choice.interval.toLong()
    val month = YearMonth.from(day)
    val candidate: (Long) -> LocalDate? = when (choice) {
        RepeatChoice.Never -> return listOf(day).take(count)
        RepeatChoice.Daily -> { k -> day.plusDays(k * step) }
        RepeatChoice.Weekly, RepeatChoice.Fortnightly -> { k -> day.plusWeeks(k * step) }
        RepeatChoice.Monthly -> { k -> month.plusMonths(k * step).takeIf { it.isValidDay(day.dayOfMonth) }?.atDay(day.dayOfMonth) }
        RepeatChoice.MonthlyOnWeekday -> { k -> nthWeekdayIn(month.plusMonths(k * step), nth, day.dayOfWeek) }
        RepeatChoice.Yearly -> { k ->
            val year = Math.toIntExact(day.year + k * step)
            MonthDay.from(day).takeIf { it.isValidYear(year) }?.atYear(year)
        }
    }
    return generateSequence(0L) { it + 1 }.mapNotNull(candidate).take(count).toList()
}

const val MIN_TIMES = 2
const val MAX_TIMES = 52
const val DEFAULT_TIMES = 10

/** How a new series ends (9 §3.1): never, or after a number of times. */
sealed interface RepeatEnd {
    data object Never : RepeatEnd

    data class After(val times: Int) : RepeatEnd {
        init {
            require(times in MIN_TIMES..MAX_TIMES) { "A count runs from $MIN_TIMES to $MAX_TIMES, not $times" }
        }
    }
}

/** A new series: its RRULE line and the household zone, which Google needs to repeat a timed series. */
data class Recurrence(val rule: String, val zone: ZoneId)

internal val UNTIL_DATE: DateTimeFormatter = DateTimeFormatter.ofPattern("yyyyMMdd")
internal val UNTIL_UTC: DateTimeFormatter = DateTimeFormatter.ofPattern("yyyyMMdd'T'HHmmss'Z'")
private const val SERIES_START = "DTSTART:"

/**
 * `RRULE:FREQ=…;INTERVAL=n`, then `COUNT=n` for [RepeatEnd.After], nothing for [RepeatEnd.Never].
 * [RepeatChoice.MonthlyOnWeekday] first adds `BYDAY` for [day]'s place in its month ("4SA", "-1TH"). Null for
 * [RepeatChoice.Never].
 */
fun recurrenceRule(choice: RepeatChoice, end: RepeatEnd, day: LocalDate): String? {
    val freq = choice.freq ?: return null
    val byDay = if (choice == RepeatChoice.MonthlyOnWeekday) ";BYDAY=${nthWeekday(day)}${day.dayOfWeek.code()}" else ""
    val ending = when (end) {
        RepeatEnd.Never -> ""
        is RepeatEnd.After -> ";COUNT=${end.times}"
    }
    return "RRULE:FREQ=$freq;INTERVAL=${choice.interval}$byDay$ending"
}

/** [rule] with the series' first date on a line before it, as both providers store it (9 §3.4). */
fun withSeriesStart(rule: String, first: LocalDate): String = "$SERIES_START${first.format(UNTIL_DATE)}\n$rule"

/** The series' first date kept with [rule]; null when the rule was stored without it. */
fun seriesStart(rule: String?): LocalDate? = rule?.lineSequence()?.firstOrNull { it.startsWith(SERIES_START) }
    ?.let { runCatching { LocalDate.parse(it.removePrefix(SERIES_START), UNTIL_DATE) }.getOrNull() }

/** [rule] without the series' first date: what anything reading the rule's parts reads. */
fun withoutSeriesStart(rule: String): String = rule.lineSequence().filterNot { it.startsWith(SERIES_START) }.joinToString("\n")

/** [rule]'s parts by name, upper case, with the series' first date skipped; null when a part isn't `NAME=value`. */
internal fun ruleParts(rule: String): Map<String, String>? =
    withoutSeriesStart(rule).removePrefix("RRULE:").split(';').associate { part ->
        val pair = part.split('=', limit = 2)
        if (pair.size != 2) return null
        pair[0].uppercase() to pair[1].uppercase()
    }

/** Parts that only bound the series or name the week's start; they don't change which days it lands on. */
internal val BOUNDING_PARTS = setOf("FREQ", "INTERVAL", "UNTIL", "COUNT", "WKST")

/**
 * The choice that writes what [parts] say from [start], where a BYDAY or BYMONTHDAY must be [start]'s own. A monthly
 * rule by date is [RepeatChoice.Monthly]. Null for anything no choice writes.
 */
internal fun readChoice(parts: Map<String, String>, start: LocalDate): RepeatChoice? {
    val interval = parts["INTERVAL"]?.let { it.toIntOrNull() ?: return null } ?: 1
    val rest = parts - BOUNDING_PARTS
    val byDay = rest["BYDAY"]
    val byMonthDay = rest["BYMONTHDAY"]
    if ((rest.keys - setOf("BYDAY", "BYMONTHDAY")).isNotEmpty()) return null
    val weekday = start.dayOfWeek.code()
    return when (parts["FREQ"]) {
        "DAILY" -> RepeatChoice.Daily.takeIf { rest.isEmpty() && interval == 1 }
        "WEEKLY" -> when {
            byMonthDay != null || (byDay != null && byDay != weekday) -> null
            interval == 1 -> RepeatChoice.Weekly
            interval == 2 -> RepeatChoice.Fortnightly
            else -> null
        }
        "MONTHLY" -> when {
            interval != 1 || (byDay != null && byMonthDay != null) -> null
            byDay != null -> RepeatChoice.MonthlyOnWeekday.takeIf { byDay == "${nthWeekday(start)}$weekday" }
            else -> RepeatChoice.Monthly.takeIf { byMonthDay == null || byMonthDay == start.dayOfMonth.toString() }
        }
        "YEARLY" -> RepeatChoice.Yearly.takeIf { rest.isEmpty() && interval == 1 }
        else -> null
    }
}

/** The editor's choice that [rule] is, read from the series' [start]; null for anything the editor can't make. */
fun choiceOf(rule: String, start: LocalDate): RepeatChoice? =
    ruleParts(rule)?.let { readChoice(it, start) }?.takeIf { it in EDITOR_REPEATS }
