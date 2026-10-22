package uk.co.siland.culvery.capability.calendar

import java.time.LocalDate
import java.time.ZoneId
import java.time.temporal.ChronoUnit
import uk.co.siland.culvery.core.household.Person

/** Which day of a multi-day event a date is: day 2 [of] 3. */
data class DaySpan(val day: Int, val of: Int)

/**
 * One multi-day event's bar in a week's lane. [first] and [last] are clamped to the week; [startsBefore] and
 * [endsAfter] say it runs past either edge. [label] is "Mon 19 – Thu 22", or "until Sun →" when it [endsAfter].
 */
data class SpanUi(
    val ref: EventRef,
    val title: String,
    val people: List<Person>,
    val first: LocalDate,
    val last: LocalDate,
    val startsBefore: Boolean,
    val endsAfter: Boolean,
    val label: String,
    val spoken: String,
)

/**
 * The first and last dates of a span the week draws as a lane bar (9 §2.1): two or more dates, a timed one ending a
 * calendar day or more after it starts; null for one that doesn't.
 */
private fun EventSpan.multiDays(zone: ZoneId): Pair<LocalDate, LocalDate>? {
    val days = daysOf(this, zone)
    if (days.first == days.second) return null
    // A calendar day on, not 24 hours: noon to noon over the spring change counts.
    return days.takeIf { this !is EventSpan.Timed || !end.isBefore(start.plusDays(1)) }
}

internal fun EventSpan.isMultiDay(zone: ZoneId): Boolean = multiDays(zone) != null

private fun StoredEvent.multiDaySpan(zone: ZoneId): Pair<LocalDate, LocalDate>? = spanOf(start, end, zone).multiDays(zone)

/** An all-day event over two or more dates, or a timed one ending a calendar day or more after it starts. */
fun StoredEvent.isMultiDay(zone: ZoneId): Boolean = multiDaySpan(zone) != null

/** How many days from [first] to [last], both counted. */
internal fun dayCount(first: LocalDate, last: LocalDate): Int = ChronoUnit.DAYS.between(first, last).toInt() + 1

/** Which day of its span [date] is; null unless the event is multi-day and on [date]. */
fun StoredEvent.dayOfSpan(date: LocalDate, zone: ZoneId): DaySpan? {
    val (first, last) = multiDaySpan(zone) ?: return null
    if (date < first || date > last) return null
    return DaySpan(dayCount(first, date), dayCount(first, last))
}

/** The bars for [days]: one per event whose slices carry a span, in the order they first appear. */
fun weekSpans(days: List<DayUi>): List<SpanUi> =
    days.flatMap { d -> d.events.mapNotNull { e -> e.span?.let { SpanSlice(d.date, e, it) } } }
        .groupBy { it.event.ref }
        .map { (ref, slices) ->
            val head = slices.first()
            val tail = slices.last()
            val firstDay = head.date.minusDays(head.span.day - 1L)
            val lastDay = tail.date.plusDays(tail.span.of.toLong() - tail.span.day)
            val endsAfter = tail.span.day < tail.span.of
            SpanUi(
                ref = ref,
                title = head.event.title,
                people = head.event.people,
                first = head.date,
                last = tail.date,
                startsBefore = head.span.day > 1,
                endsAfter = endsAfter,
                label = if (endsAfter) "until ${lastDay.format(WEEKDAY)} →" else "${firstDay.format(DAY_AND_DATE)} – ${lastDay.format(DAY_AND_DATE)}",
                spoken = "${head.event.title}, ${spokenWho(head.event.people)}, ${firstDay.format(FULL_DATE)} to ${lastDay.format(FULL_DATE)}",
            )
        }

private class SpanSlice(val date: LocalDate, val event: EventUi, val span: DaySpan)

/** Packs bars into rows: earliest first day, then longest, then title; each takes the first row it doesn't overlap. */
fun laneRows(spans: List<SpanUi>): List<List<SpanUi>> {
    val rows = mutableListOf<MutableList<SpanUi>>()
    spans.sortedWith(
        compareBy<SpanUi> { it.first }
            .thenByDescending { ChronoUnit.DAYS.between(it.first, it.last) }
            .thenBy { it.title }
            .thenBy { it.ref.listKey },
    ).forEach { span ->
        val row = rows.firstOrNull { row -> row.none { it.first <= span.last && span.first <= it.last } }
        if (row != null) row += span else rows += mutableListOf(span)
    }
    return rows
}
