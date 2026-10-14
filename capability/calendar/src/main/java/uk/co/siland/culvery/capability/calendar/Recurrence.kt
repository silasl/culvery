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
    Yearly("Every year", "YEARLY", 1),
}

/** A new series: its RRULE line and the household zone, which Google needs to repeat a timed series. */
data class Recurrence(val rule: String, val zone: ZoneId)

private val UNTIL_DATE: DateTimeFormatter = DateTimeFormatter.ofPattern("yyyyMMdd")
private val UNTIL_UTC: DateTimeFormatter = DateTimeFormatter.ofPattern("yyyyMMdd'T'HHmmss'Z'")
private val END_OF_DAY: LocalTime = LocalTime.of(23, 59, 59)

/**
 * `RRULE:FREQ=…;INTERVAL=n`, with `UNTIL` when the series [endsOn] a day: that date for an [allDay] series; for a timed
 * one, 23:59:59 that day in [zone], as UTC (an hour earlier in BST). Null for [RepeatChoice.Never].
 */
fun recurrenceRule(choice: RepeatChoice, endsOn: LocalDate?, allDay: Boolean, zone: ZoneId): String? {
    val freq = choice.freq ?: return null
    val until = endsOn?.let { day ->
        if (allDay) day.format(UNTIL_DATE) else day.atTime(END_OF_DAY).atZone(zone).withZoneSameInstant(ZoneOffset.UTC).format(UNTIL_UTC)
    }
    return "RRULE:FREQ=$freq;INTERVAL=${choice.interval}" + (until?.let { ";UNTIL=$it" } ?: "")
}
