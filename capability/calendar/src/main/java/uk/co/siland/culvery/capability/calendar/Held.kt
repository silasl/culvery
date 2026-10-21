package uk.co.siland.culvery.capability.calendar

import java.time.Duration
import java.time.Instant
import java.time.LocalDate
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

/** The hours of [day] in which any of [people] is busy (9 §3.4); none when the tablet can't know. */
fun Held.busyHours(people: List<PersonId>, day: LocalDate, zone: ZoneId, except: EventRef?): Set<Int> =
    if (canCheck(people, day)) busyHoursOf(busyEvents(people, except), day, zone) else emptySet()

private fun busyHoursOf(busy: List<HeldEvent>, day: LocalDate, zone: ZoneId): Set<Int> {
    // Each hour's start, then the next midnight.
    val bounds = (0 until HOURS_PER_DAY).map { h -> ZonedDateTime.of(day, LocalTime.of(h, 0), zone).toInstant() } + day.plusDays(1).atStartOfDay(zone).toInstant()
    return (0 until HOURS_PER_DAY).filterTo(mutableSetOf()) { h -> busy.any { it.overlaps(bounds[h], bounds[h + 1]) } }
}

/** [busyHours], and [check] when there is a [start], with the busy events found once: what the Time pane reads. */
internal fun Held.busyHoursAndCheck(
    people: List<PersonId>,
    day: LocalDate,
    start: LocalTime?,
    length: Duration,
    zone: ZoneId,
    except: EventRef?,
    order: List<PersonId>,
): Pair<Set<Int>, FreeCheck> {
    if (!canCheck(people, day)) return emptySet<Int>() to FreeCheck.None
    val busy = busyEvents(people, except)
    return busyHoursOf(busy, day, zone) to (start?.let { checkOf(busy, people, day, it, length, zone, order) } ?: FreeCheck.None)
}

/** The clash check's answer: nothing to say, everyone chosen is free, or who is busy with what and the nearest free starts. */
sealed interface FreeCheck {
    data object None : FreeCheck

    data class Free(val people: List<PersonId>) : FreeCheck

    data class Busy(val person: PersonId, val event: HeldEvent, val freeStarts: List<LocalTime>) : FreeCheck
}

/**
 * Whether [people] are free from [start] on [day] for [length], ending at midnight at most as the draft does (9 §3.1).
 * [None] for nobody, for Everyone, outside the held days, or when anyone chosen may be missing events.
 */
fun Held.check(
    people: List<PersonId>,
    day: LocalDate,
    start: LocalTime,
    length: Duration,
    zone: ZoneId,
    except: EventRef?,
    order: List<PersonId>,
): FreeCheck = if (canCheck(people, day)) checkOf(busyEvents(people, except), people, day, start, length, zone, order) else FreeCheck.None

// [busy] is [busyEvents] for [people].
private fun checkOf(
    busy: List<HeldEvent>,
    people: List<PersonId>,
    day: LocalDate,
    start: LocalTime,
    length: Duration,
    zone: ZoneId,
    order: List<PersonId>,
): FreeCheck {
    val chosen = people.distinct().sortedBy(order::rankOf)
    val (from, to) = draftSpan(day, start, length, zone)
    for (person in chosen) {
        val clash = busy.filter { person in it.people && it.overlaps(from, to) }.minByOrNull { it.start.instantIn(zone) } ?: continue
        return FreeCheck.Busy(person, clash, freeStarts(busy, day, start, length, zone))
    }
    return FreeCheck.Free(chosen)
}

private fun Held.canCheck(people: List<PersonId>, day: LocalDate): Boolean =
    people.isNotEmpty() && PersonId.FAMILY !in people && holds(day) && people.none { it in unhealthy }

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
