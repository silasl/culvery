package uk.co.siland.culvery.capability.calendar

import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.ZoneId
import java.time.ZonedDateTime
import uk.co.siland.culvery.core.household.Person
import uk.co.siland.culvery.core.household.PersonId

/** An event the tablet holds, with who it is for already resolved (9 §3.4). */
data class HeldEvent(val ref: EventRef, val title: String, val people: List<PersonId>, val start: EventTime, val end: EventTime)

/**
 * What the tablet has actually synced, for the editor's day dots and clash check (9 §3.1): the events from [firstDay]
 * to [lastDay], and [unhealthy], the people whose calendars may be missing events, so nobody is called free on a
 * guess.
 */
data class Held(val events: List<HeldEvent>, val lastDay: LocalDate, val unhealthy: Set<PersonId>, val firstDay: LocalDate) {
    fun holds(day: LocalDate): Boolean = day in firstDay..lastDay
}

internal fun StoredEvent.toHeld(people: Map<PersonId, Person>, order: List<PersonId>): HeldEvent =
    HeldEvent(ref, title, resolvePeople(forPerson, sourcePerson, people, order).map { it.id }, start, end)

internal const val MAX_DOTS = 3
private const val MAX_NAMED_CLASHES = 2
private const val QUARTER_MINUTES = 15L
private val FIRST_FREE_START: LocalTime = LocalTime.of(7, 0)
private val LAST_FREE_END: LocalTime = LocalTime.of(23, 0)

/** The people with an event on [date], in household [order] then Family; none outside the held days. */
fun Held.peopleOn(date: LocalDate, order: List<PersonId>, zone: ZoneId): List<PersonId> = peopleByDay(date, date, order, zone)[date].orEmpty()

/** [peopleOn] for each day from [from] to [to], in one pass over the events; a day with nobody on it is left out. */
fun Held.peopleByDay(from: LocalDate, to: LocalDate, order: List<PersonId>, zone: ZoneId): Map<LocalDate, List<PersonId>> {
    val byDay = mutableMapOf<LocalDate, LinkedHashSet<PersonId>>()
    for (event in events) {
        val (first, last) = daySpan(event.start, event.end, zone)
        var day = maxOf(first, from, firstDay)
        val end = minOf(last, to, lastDay)
        while (day <= end) {
            byDay.getOrPut(day) { LinkedHashSet() }.addAll(event.people)
            day = day.plusDays(1)
        }
    }
    return byDay.mapValues { (_, people) -> people.sortedBy(order::rankOfWithFamilyLast) }
}

/** A day's dots: the colours of [people], each once, at most [MAX_DOTS]. */
internal fun dotColours(people: List<Person>): List<Long> = people.map { it.color }.distinct().take(MAX_DOTS)

/** One chosen person's first overlapping event. */
data class Clash(val person: PersonId, val event: HeldEvent)

/**
 * The clash check's answer: nothing to say, everyone chosen is free, or who is busy with what. [Busy.clashes] holds up
 * to two busy people, the rest counted in [Busy.more]; the nearest free starts are for a span on one day only.
 */
sealed interface FreeCheck {
    data object None : FreeCheck

    data class Free(val people: List<PersonId>) : FreeCheck

    data class Busy(val clashes: List<Clash>, val more: Int, val freeStarts: List<LocalTime>) : FreeCheck
}

/** Whether the span from [from] to [to] reaches past its first day. */
internal fun spansDays(from: LocalDateTime, to: LocalDateTime): Boolean = lastDayOf(from, to) != from.toLocalDate()

/**
 * Whether [people] are free from [from] to [to], wall times in [zone] (9c §3.5).
 * [None] for nobody, for Everyone, when any day of the span is outside the held days, or when anyone chosen may be missing events.
 */
fun Held.check(
    people: List<PersonId>,
    from: LocalDateTime,
    to: LocalDateTime,
    zone: ZoneId,
    except: EventRef?,
    order: List<PersonId>,
): FreeCheck {
    if (!canCheck(people, from.toLocalDate(), lastDayOf(from, to))) return FreeCheck.None
    val busy = busyEvents(people, except)
    val chosen = people.distinct().sortedBy(order::rankOf)
    val start = from.atZone(zone).toInstant()
    val end = to.atZone(zone).toInstant()
    val clashes = chosen.mapNotNull { person ->
        busy.filter { person in it.people && it.overlaps(start, end) }.minByOrNull { it.start.instantIn(zone) }?.let { Clash(person, it) }
    }
    if (clashes.isEmpty()) return FreeCheck.Free(chosen)
    if (spansDays(from, to)) return FreeCheck.Busy(listOf(clashes.minBy { it.event.start.instantIn(zone) }), 0, emptyList())
    val starts = freeStarts(busy, from.toLocalDate(), from.toLocalTime(), Duration.between(start, end), zone)
    return FreeCheck.Busy(clashes.take(MAX_NAMED_CLASHES), (clashes.size - MAX_NAMED_CLASHES).coerceAtLeast(0), starts)
}

private fun Held.canCheck(people: List<PersonId>, first: LocalDate, last: LocalDate): Boolean =
    people.isNotEmpty() && PersonId.FAMILY !in people && holds(first) && holds(last) && people.none { it in unhealthy }

// Family and all-day events make nobody busy (9 §3.4).
private fun Held.busyEvents(people: List<PersonId>, except: EventRef?): List<HeldEvent> = events.filter { e ->
    e.ref != except && e.start is EventTime.Timed && e.end is EventTime.Timed && e.people.any { it in people }
}

/** The nearest quarter-hour before and after [start] whose whole [length] lies in 07:00–23:00 with nobody busy. */
private fun freeStarts(busy: List<HeldEvent>, day: LocalDate, start: LocalTime, length: Duration, zone: ZoneId): List<LocalTime> {
    val first = ZonedDateTime.of(day, FIRST_FREE_START, zone).toInstant()
    val last = ZonedDateTime.of(day, LAST_FREE_END, zone).toInstant()
    fun free(candidate: LocalTime): Boolean {
        val from = ZonedDateTime.of(day, candidate, zone).toInstant()
        val to = from.plus(length)
        return from >= first && to <= last && busy.none { it.overlaps(from, to) }
    }
    val minutes = start.toSecondOfDay() / 60L
    val below = (minutes - 1).floorDiv(QUARTER_MINUTES) * QUARTER_MINUTES
    val above = minutes.floorDiv(QUARTER_MINUTES) * QUARTER_MINUTES + QUARTER_MINUTES
    val firstMinute = FIRST_FREE_START.toSecondOfDay() / 60L
    val lastMinute = LAST_FREE_END.toSecondOfDay() / 60L
    val earlier = generateSequence(below) { it - QUARTER_MINUTES }.takeWhile { it >= firstMinute }
        .map { LocalTime.ofSecondOfDay(it * 60) }.firstOrNull(::free)
    val later = generateSequence(above) { it + QUARTER_MINUTES }.takeWhile { it < lastMinute }
        .map { LocalTime.ofSecondOfDay(it * 60) }.firstOrNull(::free)
    return listOfNotNull(earlier, later)
}

private fun HeldEvent.overlaps(from: Instant, to: Instant): Boolean {
    val s = (start as EventTime.Timed).instant
    val e = (end as EventTime.Timed).instant
    return s < to && e > from
}
