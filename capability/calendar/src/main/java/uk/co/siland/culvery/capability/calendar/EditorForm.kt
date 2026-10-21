package uk.co.siland.culvery.capability.calendar

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import java.time.Duration
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import java.time.ZonedDateTime
import java.time.temporal.ChronoUnit
import uk.co.siland.culvery.core.household.PersonId
import uk.co.siland.culvery.core.ui.UNTIL
import uk.co.siland.culvery.core.ui.clockText
import uk.co.siland.culvery.core.ui.plural

/** The Time pane's length chips (9 §3.1); any other length shows as "Until {end}". */
val EDITOR_LENGTHS: List<Duration> = listOf(Duration.ofMinutes(30), Duration.ofMinutes(45), Duration.ofHours(1), Duration.ofHours(2))

internal const val END_BEFORE_START = "The end is before the start. Pick a later time."

private const val UNTIL_UNPICKED = "$UNTIL…"
private val DEFAULT_LENGTH: Duration = Duration.ofHours(1)
private const val MINUTES_PER_HOUR = 60L
private val MORNING: LocalTime = LocalTime.of(9, 0)
private const val LAST_HOUR = 23

/** "30 min", "45 min", "1 hour", "2 hours": a chip's words; TalkBack hears [lengthLabel]. */
fun lengthChip(length: Duration): String {
    val hours = length.toHours()
    val minutes = length.toMinutes() % MINUTES_PER_HOUR
    return when {
        hours == 0L -> "$minutes min"
        minutes == 0L -> plural(hours, "hour")
        else -> "${plural(hours, "hour")} $minutes min"
    }
}

/** What Add event still waits for, in the order the foot names them. */
enum class Needed { Title, Who, Day }

/** "Needs a title", "Needs who and a day", "Needs a title, who and a day"; null when nothing is missing. */
fun needsLine(missing: List<Needed>): String? {
    if (missing.isEmpty()) return null
    val words = missing.map {
        when (it) {
            Needed.Title -> "a title"
            Needed.Who -> "who"
            Needed.Day -> "a day"
        }
    }
    return "Needs ${spokenNames(words)}"
}

/**
 * The full-screen editor's state (9 §3.1): plain Kotlin with Compose state, driven by tests without a UI. [order] is the
 * household's people in order, for the tag and for dropping removed people; [calendarPerson] is the person the event's
 * calendar is mapped to, which an untagged edit opens on.
 */
class EditorForm(
    val mode: EventForm.Mode,
    val today: LocalDate,
    private val now: LocalTime,
    val zone: ZoneId,
    val signedIn: PersonId?,
    childOnly: PersonId?,
    preselectedDay: LocalDate?,
    private val order: List<PersonId>,
    repeats: Boolean = true,
    calendarPerson: PersonId? = null,
) {
    private val original: EditableEvent? = (mode as? EventForm.Mode.Edit)?.original

    /** A timed, mixed or over-long (past [MAX_SPAN_DAYS]) event over several days keeps its dates; a shorter all-day span's dates edit. */
    val datesLocked: Boolean = original?.let(::locksDates) ?: false

    private fun locksDates(e: EditableEvent): Boolean {
        val (first, last) = daySpan(e.start, e.end, zone)
        if (!last.isAfter(first)) return false
        return !e.isAllDay() || ChronoUnit.DAYS.between(first, last) + 1 > MAX_SPAN_DAYS
    }

    var title: String by mutableStateOf(original?.title ?: "")
        private set

    /** Typing past [MAX_TITLE_LENGTH] is ignored; shortening is always allowed, even an edited title already over it. */
    fun updateTitle(value: String) {
        if (value.length <= MAX_TITLE_LENGTH || value.length < title.length) title = value
    }

    // An edit opens on the set it shows elsewhere (9 §3.4); a new event on nobody, or on the child adding it.
    private val openingWho: List<PersonId> = original?.let { whoOf(it.forPerson, calendarPerson) } ?: listOfNotNull(childOnly)

    var who: List<PersonId> by mutableStateOf(openingWho)
        private set

    val everyone: Boolean get() = who == listOf(PersonId.FAMILY)

    /** Everyone replaces the set and a person replaces Everyone; the last person can be cleared, which Save then waits on. */
    fun toggleWho(person: PersonId) {
        who = toggled(who, person, mayClear = true).sortedBy(::rankOf)
    }

    private fun rankOf(id: PersonId): Int = order.indexOf(id).takeIf { it >= 0 } ?: Int.MAX_VALUE

    // As resolvePeople, by id: known people in household order, a set with nobody left is Family, else the calendar's person.
    private fun whoOf(forPerson: String?, calendarPerson: PersonId?): List<PersonId> {
        val ids = peopleIn(forPerson)
        val known = ids.filter { it == PersonId.FAMILY || it in order }
        return when {
            known.isNotEmpty() -> known.sortedBy(::rankOf)
            ids.size > 1 -> listOf(PersonId.FAMILY)
            else -> listOf(calendarPerson?.takeIf { it in order } ?: PersonId.FAMILY)
        }
    }

    // A reordered household or a re-picked person isn't a retag (9 §3.4).
    private val whoChanged: Boolean get() = who.toSet() != openingWho.toSet()

    private val openingSpan: Pair<LocalDate, LocalDate>? = original?.let { daySpan(it.start, it.end, zone) }

    var day: LocalDate? by mutableStateOf(openingSpan?.first ?: preselectedDay)
        private set

    var multiDay: Boolean by mutableStateOf(
        openingSpan != null && !datesLocked && original?.isAllDay() == true && openingSpan.second > openingSpan.first,
    )
        private set

    /** The last day of a multi-day event; null while it is one day or waiting for the last tap. */
    var until: LocalDate? by mutableStateOf(openingSpan?.second?.takeIf { multiDay })
        private set

    private var awaiting by mutableStateOf(false)

    /** A first day is tapped and the next tap sets the last. */
    val awaitingLastDay: Boolean get() = multiDay && awaiting

    // Set aside by Lasts more than a day; turned off with the same Day, it comes back.
    private var timeAside: Pair<LocalDate?, LocalTime>? = null

    // Until Day, Time or Length is touched, an edit keeps the event's own start and end.
    private var whenTouched = false

    // multiDay's private setter already takes the JVM name setMultiDay.
    /** On, the event is all day and the next tap is its first day; off, it is one day again. */
    @JvmName("chooseMultiDay")
    fun setMultiDay(on: Boolean) {
        if (datesLocked || on == multiDay) return
        multiDay = on
        until = null
        awaiting = false
        if (on) {
            timeAside = startTime?.let { day to it }
            time = TimeChoice.AllDay
            endError = null
        } else {
            timeAside?.takeIf { it.first == day }?.let { time = TimeChoice.Custom(it.second) }
            timeAside = null
        }
        whenTouched = true
    }

    /**
     * One day: the day. Several (9 §3.1): the first tap sets the day and waits; the next sets the last day, or one day if
     * it is the same date, or starts again from a date before the first. Once a range is set, a tap starts a new one.
     */
    fun tapDay(date: LocalDate) {
        if (datesLocked) return
        val first = day
        when {
            !multiDay -> if (date == first) return else day = date
            !awaiting || first == null || date < first -> {
                day = date
                until = null
                awaiting = true
            }
            else -> {
                until = date.takeIf { it != first }
                awaiting = false
            }
        }
        whenTouched = true
    }

    val untilError: String?
        get() {
            val first = day ?: return null
            return until?.takeIf { multiDay && it > first.plusDays(MAX_SPAN_DAYS - 1) }?.let { UNTIL_TOO_LATE }
        }

    var time: TimeChoice by mutableStateOf(original?.let { timeOf(it.start, zone) } ?: TimeChoice.AllDay)
        private set

    var length: Duration by mutableStateOf(original?.let(::lengthOf) ?: DEFAULT_LENGTH)
        private set

    /** A refused end time's message, shown in the Time pane until the time or length changes; it never holds Save back. */
    var endError: String? by mutableStateOf(null)
        private set

    val startTime: LocalTime?
        get() = when (val t = time) {
            TimeChoice.AllDay -> null
            is TimeChoice.Custom -> t.time
        }

    fun chooseAllDay() = applyTime(TimeChoice.AllDay)

    /** On the hour, or at the minute already chosen. */
    fun chooseHour(hour: Int) = applyTime(TimeChoice.Custom(startTime?.withHour(hour) ?: LocalTime.of(hour, 0)))

    /** Ignored while All day. */
    fun chooseMinute(minute: Int) {
        val start = startTime ?: return
        applyTime(TimeChoice.Custom(start.withMinute(minute)))
    }

    fun chooseTime(time: LocalTime) = applyTime(TimeChoice.Custom(time))

    // A time can't be chosen while the event lasts more than a day, nor on a locked span; re-choosing changes nothing.
    private fun applyTime(choice: TimeChoice) {
        if (datesLocked || (multiDay && choice is TimeChoice.Custom)) return
        endError = null
        if (choice == time) return
        time = choice
        whenTouched = true
    }

    fun chooseLength(length: Duration) {
        if (datesLocked) return
        endError = null
        if (length == this.length) return
        this.length = length
        whenTouched = true
    }

    /** An end on the start's day sets the length; one at or before the start is refused and changes nothing. */
    fun chooseEndTime(end: LocalTime) {
        val start = startTime ?: return
        if (!end.isAfter(start)) {
            endError = END_BEFORE_START
            return
        }
        chooseLength(Duration.between(start, end))
    }

    /** "Until…", or "Until 18:30" when the length is none of [EDITOR_LENGTHS]. */
    val untilTimeLabel: String
        get() {
            val start = startTime
            return if (start == null || length in EDITOR_LENGTHS) UNTIL_UNPICKED else "$UNTIL ${clockText(start.plus(length))}"
        }

    /** Where the start picker opens: the chosen start; with none, the next whole hour (23:00 at the latest), or 09:00 on another day. */
    val pickerTime: LocalTime
        get() = startTime ?: when {
            day != null && day != today -> MORNING
            now.hour >= LAST_HOUR -> LocalTime.of(LAST_HOUR, 0)
            else -> LocalTime.of(now.hour + 1, 0)
        }

    val endPickerTime: LocalTime get() = pickerTime.plus(length)

    /** Only a new event repeats here, and not a child's: a series can't be deleted from the wall (9 §4.8, 5 §A.4). */
    val canRepeat: Boolean = mode == EventForm.Mode.New && repeats

    var repeat: RepeatChoice by mutableStateOf(RepeatChoice.Never)
        private set

    var repeatEnd: RepeatEnd by mutableStateOf(RepeatEnd.Never)
        private set

    /** Back to Never drops the end: Ends only shows while the event repeats. */
    fun chooseRepeat(choice: RepeatChoice) {
        if (!canRepeat || choice == repeat) return
        repeat = choice
        if (choice == RepeatChoice.Never) repeatEnd = RepeatEnd.Never
    }

    /** Choosing it while it is chosen keeps the count. */
    fun stopsAfter() {
        if (repeatEnd !is RepeatEnd.After) chooseEnd(RepeatEnd.After(DEFAULT_TIMES))
    }

    fun keepsGoing() = chooseEnd(RepeatEnd.Never)

    private fun chooseEnd(end: RepeatEnd) {
        if (canRepeat && repeat != RepeatChoice.Never) repeatEnd = end
    }

    fun fewerTimes() = stepTimes(-1)

    fun moreTimes() = stepTimes(1)

    private fun stepTimes(by: Int) {
        val end = repeatEnd as? RepeatEnd.After ?: return
        repeatEnd = RepeatEnd.After((end.times + by).coerceIn(MIN_TIMES, MAX_TIMES))
    }

    /**
     * A locked multi-day edit's one line: "Mon 22 – Wed 24 · change dates in Google Calendar on your phone", or, when the
     * span crosses a month, "Wed 30 Sep – Fri 2 Oct · …" on both ends; [service] names where to change them.
     */
    fun lockedDatesLabel(service: String): String? = original?.takeIf { datesLocked }?.let { o ->
        val (first, last) = daySpan(o.start, o.end, zone)
        val formatter = if (first.month == last.month && first.year == last.year) DAY_AND_DATE else SHORT_DAY
        "${first.format(formatter)} – ${last.format(formatter)} · change dates in $service on your phone"
    }

    val missing: List<Needed>
        get() = buildList {
            if (title.isBlank()) add(Needed.Title)
            if (who.isEmpty()) add(Needed.Who)
            if (day == null) add(Needed.Day)
        }

    /** Why Add event or Save changes is disabled: what's missing, else the 60-day cap; null when it can save. */
    val reason: String? get() = needsLine(missing) ?: untilError

    val canSave: Boolean get() = reason == null

    private data class Answers(
        val title: String,
        val who: List<PersonId>,
        val day: LocalDate?,
        val multiDay: Boolean,
        val until: LocalDate?,
        val time: TimeChoice,
        val length: Duration,
        val repeat: RepeatChoice,
        val repeatEnd: RepeatEnd,
    )

    private fun answers() = Answers(title, who, day, multiDay, until, time, length, repeat, repeatEnd)

    private val opening = answers()

    /** Any answer differs from how the editor opened: closing now would lose something. Changed back, it isn't. */
    val edited: Boolean get() = answers() != opening

    // A locked edit, or one whose times weren't touched and whose own span was valid, keeps its own start and end:
    // rebuilt from the answers, an event in the autumn's repeated hour would move.
    private val keepsOwnSpan: Boolean
        get() {
            val o = original ?: return false
            return datesLocked || (!whenTouched && (o.start is EventTime.AllDay || lengthOf(o) != null))
        }

    /**
     * All day runs to the day after the last day (exclusive). A timed event starts at the local time in the household
     * zone (a spring-forward gap moves it forward; an ambiguous autumn time takes the earlier offset) and lasts [length]
     * of real time, but ends at the next midnight at the latest.
     */
    private fun span(first: LocalDate): Pair<EventTime, EventTime> {
        original?.takeIf { keepsOwnSpan }?.let { return it.start to it.end }
        val start = startTime ?: return EventTime.AllDay(first) to EventTime.AllDay((until ?: first).plusDays(1))
        val from = ZonedDateTime.of(first, start, zone).toInstant()
        val midnight = first.plusDays(1).atStartOfDay(zone).toInstant()
        return EventTime.Timed(from) to EventTime.Timed(minOf(from.plus(length), midnight))
    }

    /** What Save sends; only when [canSave]. A new event that repeats carries its rule. */
    fun draft(createdBy: String?): EventDraft {
        check(canSave) { "Nothing to save: $reason" }
        val (start, end) = span(checkNotNull(day))
        val rule = recurrenceRule(repeat, repeatEnd, allDay = start is EventTime.AllDay, zone)
        // An edit whose set wasn't changed keeps its tag exactly, so an untagged event stays untagged.
        val forPerson = if (original != null && !whoChanged) original.forPerson else tagFor(who, order)
        return EventDraft(title.trim(), start, end, forPerson, createdBy, recurrence = rule?.let { Recurrence(it, zone) })
    }

    /** What an edit changed from the event as it opened: the fields its update sends. A new event is every field. */
    val touched: Set<EventField>
        get() {
            val o = original ?: return EventField.entries.toSet()
            return buildSet {
                if (title.trim() != o.title.trim()) add(EventField.TITLE)
                if (day?.let(::span) != (o.start to o.end)) add(EventField.TIMES)
                if (whoChanged) add(EventField.FOR_PERSON)
            }
        }

    /** An edit that changes nothing: the editor just closes, with no PIN. A new event never is. */
    val unchanged: Boolean get() = original != null && touched.isEmpty()
}

private fun timeOf(start: EventTime, zone: ZoneId): TimeChoice = when (start) {
    is EventTime.AllDay -> TimeChoice.AllDay
    is EventTime.Timed -> TimeChoice.Custom(start.instant.atZone(zone).toLocalTime())
}

/** A timed event's length in real time; null for an all-day event, or one whose end isn't after its start (bad provider data). */
private fun lengthOf(event: EditableEvent): Duration? {
    val start = event.start
    val end = event.end
    if (start !is EventTime.Timed || end !is EventTime.Timed) return null
    return Duration.between(start.instant, end.instant).takeIf { !it.isNegative && !it.isZero }
}

private fun EditableEvent.isAllDay(): Boolean = start is EventTime.AllDay && end is EventTime.AllDay
