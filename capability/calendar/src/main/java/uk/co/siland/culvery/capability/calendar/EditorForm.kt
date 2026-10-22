package uk.co.siland.culvery.capability.calendar

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import uk.co.siland.culvery.core.household.PersonId
import uk.co.siland.culvery.core.ui.plural

/** The longest an event may last, in days (the first and last day count). */
const val MAX_SPAN_DAYS = 60L

/** 2b-2 design D8: a title is at most 100 characters. */
const val MAX_TITLE_LENGTH = 100

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
    val mode: Mode,
    val today: LocalDate,
    private val now: LocalTime,
    val zone: ZoneId,
    childOnly: PersonId?,
    preselectedDay: LocalDate?,
    private val order: List<PersonId>,
    calendarPerson: PersonId?,
    repeats: Boolean = true,
) {
    sealed interface Mode {
        data object New : Mode
        data class Edit(val original: EditableEvent) : Mode
    }

    private val original: EditableEvent? = (mode as? Mode.Edit)?.original

    private val openingWhen: EventWhen = original?.let { EventWhen.of(it.start, it.end, zone) } ?: EventWhen.defaults(preselectedDay, today, now)

    /** A mixed all-day and timed event, or one past the cap that Save would refuse, keeps its dates (9c §3.3). */
    val datesLocked: Boolean = original != null && (original.isMixed() || openingWhen.capError != null)

    /** Set while a save runs: every change is ignored, since the editor closes on what was sent. */
    var saving: Boolean by mutableStateOf(false)

    private inline fun edit(change: () -> Unit) {
        if (!saving) change()
    }

    var title: String by mutableStateOf(original?.title ?: "")
        private set

    /** Typing past [MAX_TITLE_LENGTH] is ignored; shortening is always allowed, even an edited title already over it. */
    fun updateTitle(value: String) = edit {
        if (value.length <= MAX_TITLE_LENGTH || value.length < title.length) title = value
    }

    // An edit opens on the set it shows elsewhere (9 §3.4); a new event on nobody, or on the child adding it.
    private val openingWho: List<PersonId> = original?.let { resolveIds(it.forPerson, calendarPerson, order) } ?: listOfNotNull(childOnly)

    var who: List<PersonId> by mutableStateOf(openingWho)
        private set

    val everyone: Boolean get() = who == listOf(PersonId.FAMILY)

    /** Everyone replaces the set and a person replaces Everyone; the last person can be cleared, which Save then waits on. */
    fun toggleWho(person: PersonId) = edit {
        who = toggled(who, person, mayClear = true).sortedBy(order::rankOf)
    }

    // A reordered household or a re-picked person isn't a retag (9 §3.4).
    private val whoChanged: Boolean get() = who.toSet() != openingWho.toSet()

    /** Starts, Ends and All day (9c §3); a new event opens on the default times, which aren't an edit. */
    var eventWhen: EventWhen by mutableStateOf(openingWhen)
        private set

    /** An edit, or a time or the Ends date chosen: a Starts date pick keeps the times and length, not that day's defaults (9c §3.3). */
    var timesTouched: Boolean = original != null
        private set

    private inline fun editWhen(change: () -> Unit) = edit {
        if (!datesLocked) {
            change()
            if (!repeat.fitsLength(eventWhen.repeatLength)) dropRepeat()
        }
    }

    /** Both dates stay; the times hide, and come back as they were (9c §3.1). */
    fun chooseAllDay(on: Boolean) = editWhen { eventWhen = eventWhen.withAllDay(on) }

    fun pickStartDate(date: LocalDate) = editWhen { eventWhen = eventWhen.pickStartDate(date, defaults = !timesTouched, today, now) }

    /** A day outside the start's day and the 59 after it changes nothing. */
    fun pickEndDate(date: LocalDate) = editWhen { eventWhen.pickEndDate(date)?.let(::touchWhen) }

    fun stepStart(field: ClockField, up: Boolean) = editWhen { touchWhen(eventWhen.stepStart(field, up)) }

    /** A step that would reach the start or leave the end's day changes nothing. */
    fun stepEnd(field: ClockField, up: Boolean) = editWhen { eventWhen.stepEnd(field, up)?.let(::touchWhen) }

    /** A free start from the clash line; the end follows. */
    fun useFreeStart(clock: LocalTime) = editWhen { touchWhen(eventWhen.withStart(clock)) }

    private fun touchWhen(next: EventWhen) {
        eventWhen = next
        timesTouched = true
    }

    /** Only a new event repeats here, and not a child's: a series can't be deleted from the wall (9 §4.8, 5 §A.4). */
    val canRepeat: Boolean = mode == Mode.New && repeats

    var repeat: RepeatChoice by mutableStateOf(RepeatChoice.Never)
        private set

    /** A When change ruled out the chosen repeat; the Repeats row says so until it is opened or changed. */
    var repeatTooLong: Boolean by mutableStateOf(false)
        private set

    /** Whether Repeats has been looked at or answered, which the foot's hint waits on. */
    var repeatsAnswered: Boolean by mutableStateOf(false)
        private set

    fun openRepeats() {
        repeatTooLong = false
        repeatsAnswered = true
    }

    private fun dropRepeat() {
        repeat = RepeatChoice.Never
        repeatEnd = RepeatEnd.Never
        repeatTooLong = true
    }

    var repeatEnd: RepeatEnd by mutableStateOf(RepeatEnd.Never)
        private set

    /** Back to Never drops the end: Ends only shows while the event repeats. */
    fun chooseRepeat(choice: RepeatChoice) = edit {
        if (!canRepeat) return@edit
        openRepeats()
        if (choice == repeat || !choice.fitsLength(eventWhen.repeatLength)) return@edit
        repeat = choice
        if (choice == RepeatChoice.Never) repeatEnd = RepeatEnd.Never
    }

    /** Choosing it while it is chosen keeps the count. */
    fun stopsAfter() {
        if (repeatEnd !is RepeatEnd.After) chooseEnd(RepeatEnd.After(DEFAULT_TIMES))
    }

    fun keepsGoing() = chooseEnd(RepeatEnd.Never)

    private fun chooseEnd(end: RepeatEnd) = edit {
        if (canRepeat && repeat != RepeatChoice.Never) repeatEnd = end
    }

    fun fewerTimes() = stepTimes(-1)

    fun moreTimes() = stepTimes(1)

    private fun stepTimes(by: Int) = edit {
        val end = repeatEnd as? RepeatEnd.After ?: return@edit
        repeatEnd = RepeatEnd.After((end.times + by).coerceIn(MIN_TIMES, MAX_TIMES))
    }

    /** The stepper's centre while it stops after a count: "12 times · last on Sat 12 Dec". */
    val stepperLabel: String?
        get() {
            val end = repeatEnd as? RepeatEnd.After ?: return null
            val first = eventWhen.startDate ?: return null
            return "${plural(end.times.toLong(), "time")} · last on ${occurrences(repeat, first, end.times).last().format(SHORT_DAY)}"
        }

    /** The Repeats row: "Doesn’t repeat", "Every Saturday", or "Every Saturday · 12 times". */
    val repeatsValue: String
        get() {
            // The pane offers repeats only once there is a day.
            val first = eventWhen.startDate ?: return DOESNT_REPEAT
            val times = (repeatEnd as? RepeatEnd.After)?.let { " · ${plural(it.times.toLong(), "time")}" }.orEmpty()
            return repeat.wording(first) + times
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
            if (eventWhen.startDate == null) add(Needed.Day)
        }

    /** Why Add event or Save changes is disabled: what's missing, else the 60-day cap, which a locked span never writes; null when it can save. */
    val reason: String? get() = needsLine(missing) ?: eventWhen.capError.takeUnless { datesLocked }

    val canSave: Boolean get() = reason == null

    // The When as the rows show it: clocks hidden under All day, or before there is a date, aren't an edit.
    private fun shownWhen(): Pair<EventSpan?, Boolean> = eventWhen.span(zone) to eventWhen.allDay

    private data class Answers(
        val title: String,
        val who: List<PersonId>,
        val shownWhen: Pair<EventSpan?, Boolean>,
        val repeat: RepeatChoice,
        val repeatEnd: RepeatEnd,
    )

    private fun answers() = Answers(title, who, shownWhen(), repeat, repeatEnd)

    private val opening = answers()

    /** Any answer differs from how the editor opened: closing now would lose something. Changed back, it isn't. */
    val edited: Boolean get() = answers() != opening

    // A locked edit, or one whose When shows as it opened, keeps its own start and end: rebuilt, an event in the
    // autumn's repeated hour would move, and one whose end wasn't after its start would be mended unasked.
    private val keepsOwnSpan: Boolean
        get() = original != null && (datesLocked || shownWhen() == opening.shownWhen)

    /** All day runs to the day after the last day (exclusive); a timed event between its two wall times in the household zone. */
    private fun times(): Pair<EventTime, EventTime>? {
        original?.takeIf { keepsOwnSpan }?.let { return it.start to it.end }
        return when (val span = eventWhen.span(zone)) {
            null -> null
            is EventSpan.AllDay -> EventTime.AllDay(span.first) to EventTime.AllDay(span.endExclusive)
            is EventSpan.Timed -> EventTime.Timed(span.start.toInstant()) to EventTime.Timed(span.end.toInstant())
        }
    }

    /** What Save sends; only when [canSave]. A new event that repeats carries its rule. */
    fun draft(createdBy: String?): EventDraft {
        check(canSave) { "Nothing to save: $reason" }
        val first = checkNotNull(eventWhen.startDate)
        val (start, end) = checkNotNull(times())
        val rule = recurrenceRule(repeat, repeatEnd, first)
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
                if (times() != (o.start to o.end)) add(EventField.TIMES)
                if (whoChanged) add(EventField.FOR_PERSON)
            }
        }

    /** An edit that changes nothing: the editor just closes, with no PIN. A new event never is. */
    val unchanged: Boolean get() = original != null && touched.isEmpty()
}

private fun EditableEvent.isMixed(): Boolean = (start is EventTime.AllDay) != (end is EventTime.AllDay)
