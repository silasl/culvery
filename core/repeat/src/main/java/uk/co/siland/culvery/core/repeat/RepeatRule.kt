package uk.co.siland.culvery.core.repeat

import java.time.DayOfWeek
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.YearMonth
import java.time.ZoneId
import java.time.ZoneOffset
import java.time.temporal.ChronoUnit

// A rule that can never land (the 31st every 12 months from February) stops after this many empty periods in a row.
private const val MAX_EMPTY_PERIODS = 1_000
private const val LAST_MONTH_DAY = 31
private const val UNTIL_DATE_LENGTH = 8
private val NTH_RANGE = 1..4
private val ALWAYS_PARTS = setOf("FREQ", "INTERVAL", "UNTIL", "COUNT", "WKST")

/** A rule the shared code can work out. [toString] prints nothing of it. */
class RepeatRule private constructor(
    private val freq: Freq,
    private val interval: Int,
    private val weekdays: List<DayOfWeek>,
    private val nth: Pair<Int, DayOfWeek>?,
    private val monthDay: Int?,
    internal val count: Int?,
    private val until: LocalDate?,
) {
    private enum class Freq { DAILY, WEEKLY, MONTHLY, YEARLY }

    override fun toString(): String = "RepeatRule"

    /** The rule's dates from [first], in order, ended by COUNT or UNTIL. */
    fun dates(first: LocalDate): Sequence<LocalDate> {
        val bounded = periodsFrom(first, 0).takeWhile { !isPastUntil(it) }
        return count?.let(bounded::take) ?: bounded
    }

    fun latestOnOrBefore(first: LocalDate, day: LocalDate): LocalDate? {
        val limit = until?.let { minOf(it, day) } ?: day
        if (limit.isBefore(first)) return null
        if (count != null) return dates(first).takeWhile { !it.isAfter(limit) }.lastOrNull()
        var period = periodOf(first, limit)
        repeat(MAX_EMPTY_PERIODS) {
            if (period < 0) return null
            periodDates(first, period--).lastOrNull { !it.isAfter(limit) }?.let { return it }
        }
        return null
    }

    fun firstAfter(first: LocalDate, day: LocalDate): LocalDate? {
        if (count != null) return dates(first).firstOrNull { it.isAfter(day) }
        return periodsFrom(first, if (day.isBefore(first)) 0 else periodOf(first, day))
            .firstOrNull { it.isAfter(day) }?.takeUnless(::isPastUntil)
    }

    private fun isPastUntil(date: LocalDate) = until != null && date.isAfter(until)

    private fun periodsFrom(first: LocalDate, start: Long): Sequence<LocalDate> = sequence {
        var period = start
        var empty = 0
        while (empty <= MAX_EMPTY_PERIODS) {
            val found = periodDates(first, period++)
            if (found.isEmpty()) empty++ else {
                empty = 0
                yieldAll(found)
            }
        }
    }

    // The period [day] falls in, counting from [first]'s.
    private fun periodOf(first: LocalDate, day: LocalDate): Long = when (freq) {
        Freq.DAILY -> ChronoUnit.DAYS.between(first, day)
        Freq.WEEKLY -> ChronoUnit.WEEKS.between(first.with(DayOfWeek.MONDAY), day.with(DayOfWeek.MONDAY))
        Freq.MONTHLY -> ChronoUnit.MONTHS.between(YearMonth.from(first), YearMonth.from(day))
        Freq.YEARLY -> (day.year - first.year).toLong()
    } / interval

    // The dates of the [period]th period from [first], in order; none before [first].
    private fun periodDates(first: LocalDate, period: Long): List<LocalDate> {
        val step = period * interval
        val found = when (freq) {
            Freq.DAILY -> listOf(first.plusDays(step))
            Freq.WEEKLY -> {
                val monday = first.with(DayOfWeek.MONDAY).plusWeeks(step)
                weekdays.ifEmpty { listOf(first.dayOfWeek) }.map { monday.plusDays(it.value - 1L) }
            }
            Freq.MONTHLY -> {
                val month = YearMonth.from(first).plusMonths(step)
                listOfNotNull(
                    nth?.let { (n, weekday) -> nthWeekdayIn(month, n, weekday) }
                        ?: (monthDay ?: first.dayOfMonth).takeIf { month.isValidDay(it) }?.let(month::atDay),
                )
            }
            Freq.YEARLY -> listOfNotNull(first.plusYears(step).takeIf { it.dayOfMonth == first.dayOfMonth })
        }
        return found.filter { !it.isBefore(first) }
    }

    companion object {
        /**
         * Null for anything but FREQ (DAILY, WEEKLY, MONTHLY, YEARLY), INTERVAL of 1 or more, a weekly BYDAY list, a
         * monthly BYDAY of one nth weekday (1 to 4, −1), a monthly BYMONTHDAY of one day, COUNT of 1 or more, UNTIL (a date,
         * or a UTC time read in [zone]) and WKST=MO. A DTSTART line is skipped. When not [strict], an unknown part is
         * ignored, an INTERVAL below 1 counts as 1 and any WKST is ignored. A COUNT that isn't 1 or more is ignored either way.
         */
        fun parse(rule: String, zone: ZoneId, strict: Boolean = true): RepeatRule? {
            val parts = ruleParts(rule) ?: return null
            val freq = Freq.entries.firstOrNull { it.name == parts["FREQ"] } ?: return null
            val byDayAllowed = freq == Freq.WEEKLY || freq == Freq.MONTHLY
            val byMonthDayAllowed = freq == Freq.MONTHLY
            val allowed = ALWAYS_PARTS + listOfNotNull("BYDAY".takeIf { byDayAllowed }, "BYMONTHDAY".takeIf { byMonthDayAllowed })
            if (strict && (parts.keys - allowed).isNotEmpty()) return null
            if (strict && parts["WKST"].let { it != null && it != DayOfWeek.MONDAY.code() }) return null
            val interval = parts["INTERVAL"]?.let { text -> text.toIntOrNull()?.takeIf { it >= 1 } ?: if (strict) return null else 1 } ?: 1
            val until = parts["UNTIL"]?.let { text -> untilDay(text, zone) ?: if (strict) return null else null }
            val byDay = parts["BYDAY"].takeIf { byDayAllowed }
            val byMonthDay = parts["BYMONTHDAY"].takeIf { byMonthDayAllowed }
            if (byDay != null && byMonthDay != null) return null
            val weekdays = if (freq == Freq.WEEKLY && byDay != null) {
                byDay.split(',').map { weekdayOf(it) ?: return null }.distinct().sortedBy { it.value }
            } else {
                emptyList()
            }
            val nth = if (freq == Freq.MONTHLY && byDay != null) nthWeekdayOf(byDay) ?: return null else null
            val monthDay = byMonthDay?.let { text -> text.toIntOrNull()?.takeIf { it in 1..LAST_MONTH_DAY } ?: return null }
            val count = parts["COUNT"]?.toIntOrNull()?.takeIf { it >= 1 }
            return RepeatRule(freq, interval, weekdays, nth, monthDay, count, until)
        }

        private fun untilDay(text: String, zone: ZoneId): LocalDate? = runCatching {
            if (text.length == UNTIL_DATE_LENGTH) {
                LocalDate.parse(text, UNTIL_DATE)
            } else {
                LocalDateTime.parse(text, UNTIL_UTC).atOffset(ZoneOffset.UTC).atZoneSameInstant(zone).toLocalDate()
            }
        }.getOrNull()

        private fun weekdayOf(code: String): DayOfWeek? = DayOfWeek.entries.firstOrNull { it.code() == code }

        // "4SA" as 4 and Saturday, "-1TH" as −1 and Thursday.
        private fun nthWeekdayOf(byDay: String): Pair<Int, DayOfWeek>? {
            val n = byDay.dropLast(2).toIntOrNull()?.takeIf { it in NTH_RANGE || it == LAST } ?: return null
            return weekdayOf(byDay.takeLast(2))?.let { n to it }
        }
    }
}

/**
 * [rule] started from its first date on or after [day]: that date, and the rule with COUNT reduced by the dates before it
 * (null when one date is left). Null when no date is left or [rule] can't be parsed. A rule with COUNT needs the series'
 * true [first] day.
 */
fun startedFrom(rule: String, first: LocalDate, day: LocalDate, zone: ZoneId): Pair<LocalDate, String?>? {
    val parsed = RepeatRule.parse(rule, zone) ?: return null
    val start = parsed.firstAfter(first, day.minusDays(1)) ?: return null
    val total = parsed.count ?: return start to withoutSeriesStart(rule)
    val remaining = total - parsed.dates(first).takeWhile { it.isBefore(start) }.count()
    val reduced = withoutSeriesStart(rule).split(';').joinToString(";") { if (it.uppercase().startsWith("COUNT=")) "COUNT=$remaining" else it }
    return start to reduced.takeIf { remaining > 1 }
}
