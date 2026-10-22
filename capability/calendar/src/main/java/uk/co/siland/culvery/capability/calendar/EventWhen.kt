package uk.co.siland.culvery.capability.calendar

import java.time.Duration
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.ZoneId
import java.time.ZonedDateTime
import java.time.temporal.ChronoUnit

internal const val UNTIL_TOO_LATE = "An event can last up to $MAX_SPAN_DAYS days. Pick an earlier end."

/** What an event covers: two wall times in the household zone, or whole days with the end exclusive. */
sealed interface EventSpan {
    data class Timed(val start: ZonedDateTime, val end: ZonedDateTime) : EventSpan
    data class AllDay(val first: LocalDate, val endExclusive: LocalDate) : EventSpan
}

enum class ClockField { Hour, Minute }

/** The other mode's end, kept while All day is flipped so toggling never loses it. */
data class EndAside(val endDays: Int, val endClock: LocalTime)

/**
 * The Starts row, the Ends row and the All day switch (9c §3). [endDays] is the end's date minus the start's
 * (all day: the last day minus the first), so the end follows the start while no date is chosen. The end is
 * always after the start; every way in keeps it so.
 */
data class EventWhen(
    val allDay: Boolean,
    val startDate: LocalDate?,
    val startClock: LocalTime,
    val endDays: Int,
    val endClock: LocalTime,
    val aside: EndAside? = null,
) {
    val endDate: LocalDate? get() = startDate?.plusDays(endDays.toLong())

    /** Real wall-clock minutes from the start to the end. */
    val length: Duration
        get() = Duration.ofMinutes(endDays * MINUTES_PER_DAY + minutesOf(endClock) - minutesOf(startClock))

    val capError: String? get() = UNTIL_TOO_LATE.takeIf { endDays > MAX_SPAN_DAYS - 1 }

    /** Null with no start date. A timed end is not capped at midnight; an ambiguous autumn time takes the earlier offset. */
    fun span(zone: ZoneId): EventSpan? {
        val first = startDate ?: return null
        return if (allDay) {
            EventSpan.AllDay(first, first.plusDays(endDays + 1L))
        } else {
            EventSpan.Timed(
                ZonedDateTime.of(first, startClock, zone),
                ZonedDateTime.of(first.plusDays(endDays.toLong()), endClock, zone),
            )
        }
    }

    /** A Starts date pick keeps the length, or with [defaults] (times untouched) starts over from that date's defaults. */
    fun pickStartDate(date: LocalDate, defaults: Boolean, today: LocalDate, now: LocalTime): EventWhen =
        if (defaults && !allDay) defaults(date, today, now) else copy(startDate = date)

    /** Null when [date] is before the start or more than 59 days after it. */
    fun pickEndDate(date: LocalDate): EventWhen? {
        val first = startDate ?: return null
        val days = ChronoUnit.DAYS.between(first, date)
        if (days !in 0..MAX_SPAN_DAYS - 1) return null
        val picked = copy(endDays = days.toInt())
        return if (!allDay && picked.endDays == 0 && !endClock.isAfter(startClock)) picked.withEndAnHourAfterStart() else picked
    }

    /** A Starts step wraps within the day; the end follows, keeping the length. */
    fun stepStart(field: ClockField, up: Boolean): EventWhen =
        withStart(startClock.plusMinutes((stepped(startClock, field, up) - minutesOf(startClock)).toLong()))

    fun withStart(clock: LocalTime): EventWhen {
        if (allDay) return this
        val start = clock.truncatedTo(ChronoUnit.MINUTES)
        val end = minutesOf(start) + length.toMinutes()
        return copy(startClock = start, endDays = (end / MINUTES_PER_DAY).toInt(), endClock = clockOf(end % MINUTES_PER_DAY))
    }

    /** Null when the step would leave the end's day, or reach or pass the start. */
    fun stepEnd(field: ClockField, up: Boolean): EventWhen? {
        if (allDay) return null
        val minutes = stepped(endClock, field, up)
        if (minutes !in 0 until MINUTES_PER_DAY.toInt()) return null
        val clock = clockOf(minutes.toLong())
        if (endDays == 0 && !clock.isAfter(startClock)) return null
        return copy(endClock = clock)
    }

    fun canStepEnd(field: ClockField, up: Boolean): Boolean = stepEnd(field, up) != null

    /** Both dates stay; the other mode's end comes back, or is worked out from this one. */
    fun withAllDay(on: Boolean): EventWhen {
        if (on == allDay) return this
        val target = aside ?: derived(toAllDay = on)
        val flipped = copy(allDay = on, endDays = target.endDays, endClock = target.endClock, aside = null)
        val left = EndAside(endDays, endClock)
        return flipped.copy(aside = left.takeIf { it != flipped.derived(toAllDay = !on) })
    }

    private fun derived(toAllDay: Boolean): EndAside = if (toAllDay) {
        // A timed end at exactly midnight belongs to the day before (as daySpan reads it).
        EndAside(if (endClock == LocalTime.MIDNIGHT && endDays > 0) endDays - 1 else endDays, endClock)
    } else {
        hourAfter(startClock)
    }

    private fun withEndAnHourAfterStart(): EventWhen = hourAfter(startClock).let { copy(endDays = it.endDays, endClock = it.endClock) }

    companion object {
        private const val MINUTES_PER_DAY = 24L * 60
        private const val QUARTER = 15
        private val MORNING: LocalTime = LocalTime.of(9, 0)
        private const val LAST_START_HOUR = 23

        /** All day off; the next whole hour when [startDate] is today (never past 23:00), else 09:00; an hour long. */
        fun defaults(startDate: LocalDate?, today: LocalDate, now: LocalTime): EventWhen {
            val start = if (startDate == null || startDate == today) {
                LocalTime.of(minOf(now.hour + 1, LAST_START_HOUR), 0)
            } else {
                MORNING
            }
            val end = hourAfter(start)
            return EventWhen(false, startDate, start, end.endDays, end.endClock)
        }

        /** Opens on the event's own values; an end at or before the start becomes an hour after it. */
        fun of(span: EventSpan, zone: ZoneId): EventWhen = when (span) {
            is EventSpan.AllDay -> {
                val days = ChronoUnit.DAYS.between(span.first, span.endExclusive) - 1
                val end = hourAfter(MORNING)
                EventWhen(true, span.first, MORNING, days.coerceAtLeast(0).toInt(), end.endClock)
            }
            is EventSpan.Timed -> {
                val start = span.start.withZoneSameInstant(zone).toLocalDateTime()
                val rawEnd = span.end.withZoneSameInstant(zone).toLocalDateTime()
                val end: LocalDateTime = if (span.end.isAfter(span.start)) rawEnd else start.plusHours(1)
                EventWhen(
                    allDay = false,
                    startDate = start.toLocalDate(),
                    startClock = start.toLocalTime().truncatedTo(ChronoUnit.MINUTES),
                    endDays = ChronoUnit.DAYS.between(start.toLocalDate(), end.toLocalDate()).toInt(),
                    endClock = end.toLocalTime().truncatedTo(ChronoUnit.MINUTES),
                )
            }
        }

        /**
         * From provider times. A start and end of different kinds (the editor locks these) read by the start's kind:
         * an all-day start takes the end's day, a timed start takes an all-day end's midnight.
         */
        fun of(start: EventTime, end: EventTime, zone: ZoneId): EventWhen = when (start) {
            is EventTime.AllDay -> {
                val last = when (end) {
                    is EventTime.AllDay -> end.date.minusDays(1)
                    is EventTime.Timed -> daySpan(start, end, zone).second
                }
                of(EventSpan.AllDay(start.date, maxOf(start.date, last).plusDays(1)), zone)
            }
            is EventTime.Timed -> of(
                EventSpan.Timed(start.instant.atZone(zone), end.instantIn(zone).atZone(zone)),
                zone,
            )
        }

        private fun hourAfter(start: LocalTime): EndAside {
            val minutes = minutesOf(start) + 60
            return EndAside((minutes / MINUTES_PER_DAY).toInt(), clockOf(minutes % MINUTES_PER_DAY))
        }

        private fun minutesOf(clock: LocalTime): Int = clock.hour * 60 + clock.minute

        private fun clockOf(minutes: Long): LocalTime = LocalTime.of((minutes / 60).toInt(), (minutes % 60).toInt())

        /** [clock] one step on, as minutes since midnight (outside 0..1439 when it leaves the day). An off-quarter minute snaps. */
        private fun stepped(clock: LocalTime, field: ClockField, up: Boolean): Int {
            val total = minutesOf(clock)
            return when (field) {
                ClockField.Hour -> if (up) total + 60 else total - 60
                ClockField.Minute -> if (up) (total / QUARTER + 1) * QUARTER else Math.floorDiv(total - 1, QUARTER) * QUARTER
            }
        }
    }
}
