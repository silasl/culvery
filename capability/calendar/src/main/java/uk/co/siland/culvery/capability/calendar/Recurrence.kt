package uk.co.siland.culvery.capability.calendar

import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter

/** The add sheet's Repeats chips (5 §A.4); [label] is also what `repeatsLabel` reads the written rule back as. */
enum class RepeatChoice(val label: String, val freq: String?, val interval: Int) {
    Never("Never", null, 1),
    Daily("Every day", "DAILY", 1),
    Weekly("Every week", "WEEKLY", 1),
    Fortnightly("Every 2 weeks", "WEEKLY", 2),
    Monthly("Every month", "MONTHLY", 1),
    Yearly("Every year", "YEARLY", 1);

    companion object {
        /** The first row of Repeats chips; the rest sit under More options (9 §4.8). */
        val COMMON: List<RepeatChoice> = listOf(Never, Weekly, Fortnightly)
        val MORE: List<RepeatChoice> = listOf(Daily, Monthly, Yearly)
    }
}

const val MIN_TIMES = 2
const val MAX_TIMES = 52
const val DEFAULT_TIMES = 10

/** How a new series ends (9 §4.8): never, on a date, or after a number of times. One value, so UNTIL and COUNT never both appear. */
sealed interface RepeatEnd {
    data object Never : RepeatEnd

    data class OnDate(val date: LocalDate) : RepeatEnd

    data class After(val times: Int) : RepeatEnd {
        init {
            require(times in MIN_TIMES..MAX_TIMES) { "A count runs from $MIN_TIMES to $MAX_TIMES, not $times" }
        }
    }
}

/** A new series: its RRULE line and the household zone, which Google needs to repeat a timed series. */
data class Recurrence(val rule: String, val zone: ZoneId)

private val UNTIL_DATE: DateTimeFormatter = DateTimeFormatter.ofPattern("yyyyMMdd")
private val UNTIL_UTC: DateTimeFormatter = DateTimeFormatter.ofPattern("yyyyMMdd'T'HHmmss'Z'")
private val END_OF_DAY: LocalTime = LocalTime.of(23, 59, 59)

/**
 * `RRULE:FREQ=…;INTERVAL=n`, then by [end]: `UNTIL` for [RepeatEnd.OnDate] (that date for an [allDay] series; for a timed
 * one, 23:59:59 that day in [zone], as UTC, an hour earlier in BST), `COUNT=n` for [RepeatEnd.After], nothing for
 * [RepeatEnd.Never]. Null for [RepeatChoice.Never].
 */
fun recurrenceRule(choice: RepeatChoice, end: RepeatEnd, allDay: Boolean, zone: ZoneId): String? {
    val freq = choice.freq ?: return null
    val ending = when (end) {
        RepeatEnd.Never -> ""
        is RepeatEnd.After -> ";COUNT=${end.times}"
        is RepeatEnd.OnDate -> {
            val until = if (allDay) end.date.format(UNTIL_DATE)
            else end.date.atTime(END_OF_DAY).atZone(zone).withZoneSameInstant(ZoneOffset.UTC).format(UNTIL_UTC)
            ";UNTIL=$until"
        }
    }
    return "RRULE:FREQ=$freq;INTERVAL=${choice.interval}$ending"
}
