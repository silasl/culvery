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
import uk.co.siland.culvery.core.ui.plural

/** What the Time chips choose: all day, or a time from Pick a time. */
sealed interface TimeChoice {
    data object AllDay : TimeChoice
    data class Custom(val time: LocalTime) : TimeChoice
}

/** The longest an event may last, in days (the first and last day count). */
const val MAX_SPAN_DAYS = 60L

internal const val UNTIL_TOO_EARLY = "The last day is before the first day. Pick a later one."
internal const val UNTIL_TOO_LATE = "An event can last up to $MAX_SPAN_DAYS days. Pick an earlier last day."

/** 2b-2 design D8: a title is at most 100 characters. */
const val MAX_TITLE_LENGTH = 100

/** Hand-off §7 Length chips: 30 minutes, 1 hour, 2 hours. */
val LENGTH_CHOICES: List<Duration> = listOf(Duration.ofMinutes(30), Duration.ofHours(1), Duration.ofHours(2))

private val DEFAULT_LENGTH: Duration = Duration.ofHours(1)
private const val MINUTES_PER_HOUR = 60L
private const val DAYS_SHOWN = 7L
private val MORNING: LocalTime = LocalTime.of(9, 0)
private const val LAST_HOUR = 23

/** "30 minutes", "1 hour", "1 hour 30 minutes": full words, singular at one. */
fun lengthLabel(length: Duration): String {
    val hours = length.toHours()
    val minutes = length.toMinutes() % MINUTES_PER_HOUR
    return when {
        hours == 0L -> plural(minutes, "minute")
        minutes == 0L -> plural(hours, "hour")
        else -> "${plural(hours, "hour")} ${plural(minutes, "minute")}"
    }
}

/**
 * The add/edit sheet's state (2b-2 design §3.1): plain Kotlin with Compose state, so the sheet recomposes as it
 * changes and tests drive it without a UI. [today] and [now] are the household zone's date and time when the sheet
 * opened. [day] is a date, not "today + n", so a sheet left open over midnight still saves the day its chip showed.
 */
class EventForm(
    val mode: Mode,
    val today: LocalDate,
    private val now: LocalTime,
    val zone: ZoneId,
    signedIn: PersonId?,
    preselectedDay: LocalDate?,
    repeats: Boolean = true,
) {
    sealed interface Mode {
        data object New : Mode
        data class Edit(val original: EditableEvent) : Mode
    }

    private val original: EditableEvent? = (mode as? Mode.Edit)?.original

    /** A timed, mixed or over-long (past [MAX_SPAN_DAYS]) event over several days keeps its dates (2b-2 design D3); a shorter all-day span's dates edit. */
    val datesLocked: Boolean = original?.let(::locksDates) ?: false

    private fun locksDates(e: EditableEvent): Boolean {
        val (first, last) = daySpan(e.start, e.end, zone)
        if (!last.isAfter(first)) return false
        return !e.isAllDay() || ChronoUnit.DAYS.between(first, last) + 1 > MAX_SPAN_DAYS
    }

    var title: String by mutableStateOf(original?.title ?: "")
        private set

    // An edit starts on the event's person (untagged reads as Family); a new event on whoever is signed in (D2).
    var who: PersonId by mutableStateOf(
        if (original != null) original.forPerson?.let(::PersonId) ?: PersonId.FAMILY else signedIn ?: PersonId.FAMILY,
    )
        private set

    var day: LocalDate by mutableStateOf(original?.let { startDate(it.start, zone) } ?: preselectedDay ?: today)
        private set

    var time: TimeChoice by mutableStateOf(original?.let { timeOf(it.start, zone) } ?: TimeChoice.AllDay)
        private set

    var length: Duration by mutableStateOf(original?.let(::lengthOf) ?: DEFAULT_LENGTH)
        private set

    /** Only a new event repeats here, and not a child's: a series can't be deleted from the wall (9 §4.8, 5 §A.4). */
    val canRepeat: Boolean = mode == Mode.New && repeats

    var repeat: RepeatChoice by mutableStateOf(RepeatChoice.Never)
        private set

    /** How the series ends; only a repeating new event has one other than Never. */
    var repeatEnd: RepeatEnd by mutableStateOf(RepeatEnd.Never)
        private set

    private var opened by mutableStateOf(false)

    /** The rest of the Repeats choices and the Ends row show: opened, or the form already holds a choice that lives there (9 §4.8). */
    val moreRepeats: Boolean get() = opened || repeat in RepeatChoice.MORE || repeatEnd != RepeatEnd.Never

    /** Opens and never closes; ignored unless the event can repeat. */
    fun showMoreRepeats() {
        if (canRepeat) opened = true
    }

    /** The end is before the event's day, so Save waits (5 §A.4). */
    val endsBeforeStart: Boolean get() = (repeatEnd as? RepeatEnd.OnDate)?.let { it.date < day } ?: false

    /** The last day of an all-day event; null means one day. */
    var until: LocalDate? by mutableStateOf(openingUntil())
        private set

    private fun openingUntil(): LocalDate? = original
        ?.takeIf { !datesLocked && it.isAllDay() }
        ?.let { daySpan(it.start, it.end, zone) }
        ?.let { (first, last) -> last.takeIf { it > first } }

    // Set aside by choosing a time; All day with the same Day puts it back.
    private var untilAside: Pair<LocalDate, LocalDate>? = null

    val offersUntil: Boolean get() = time == TimeChoice.AllDay && !datesLocked

    /** "Until", or "Until Thu 22 Oct" once picked. */
    val untilLabel: String get() = until?.let { "$UNTIL ${it.format(SHORT_DAY)}" } ?: UNTIL

    val untilError: String?
        get() = until?.takeIf { offersUntil }?.let {
            when {
                it < day -> UNTIL_TOO_EARLY
                it > day.plusDays(MAX_SPAN_DAYS - 1) -> UNTIL_TOO_LATE
                else -> null
            }
        }

    /** Ignored unless [offersUntil]; the day itself means one day. */
    fun chooseUntil(date: LocalDate) {
        if (!offersUntil) return
        until = date.takeIf { it != day }
        whenTouched = true
    }

    private data class Answers(
        val title: String,
        val who: PersonId,
        val day: LocalDate,
        val time: TimeChoice,
        val length: Duration,
        val repeat: RepeatChoice,
        val repeatEnd: RepeatEnd,
        val until: LocalDate?,
    )

    private val opening = Answers(title, who, day, time, length, repeat, repeatEnd, until)

    /** Any field differs from how the sheet opened: closing it now would lose something (4d-2 §6.3). Changed back, it isn't. */
    val edited: Boolean get() = Answers(title, who, day, time, length, repeat, repeatEnd, until) != opening

    private var whoTouched = false

    // Until Day, Time or Length is touched, an edit keeps the event's own start and end.
    private var whenTouched = false

    /** The Length chips: 30 minutes, 1 hour, 2 hours, and an edited event's own length when it is none of those (D3). */
    val lengths: List<Duration> = LENGTH_CHOICES + listOfNotNull(original?.let(::lengthOf)?.takeIf { it !in LENGTH_CHOICES })

    /** Today and the six days after it: the Day chips before Pick a date. */
    val dayChoices: List<LocalDate> = (0 until DAYS_SHOWN).map { today.plusDays(it) }

    val canSave: Boolean get() = title.trim().isNotEmpty() && !endsBeforeStart && untilError == null

    /** Typing past [MAX_TITLE_LENGTH] is ignored; shortening is always allowed, even an edited title already over it. */
    fun updateTitle(value: String) {
        if (value.length <= MAX_TITLE_LENGTH || value.length < title.length) title = value
    }

    /** Choosing the person already chosen changes nothing, so an untagged event stays untagged. */
    fun chooseWho(person: PersonId) {
        if (person == who) return
        who = person
        whoTouched = true
    }

    /** Choosing the day already chosen changes nothing (as [chooseWho]), so re-tapping it can't nudge a timed event. */
    fun chooseDay(date: LocalDate) {
        if (date == day) return
        until = until?.plusDays(ChronoUnit.DAYS.between(day, date))
        day = date
        whenTouched = true
    }

    /** Re-choosing the time already chosen changes nothing. */
    fun chooseTime(choice: TimeChoice) {
        if (choice == time) return
        if (choice is TimeChoice.Custom) {
            until?.let { untilAside = day to it }
            until = null
        } else {
            until = untilAside?.takeIf { it.first == day }?.second
            untilAside = null
        }
        time = choice
        whenTouched = true
    }

    /** Choosing the length already chosen changes nothing. */
    fun chooseLength(value: Duration) {
        if (value == length) return
        length = value
        whenTouched = true
    }

    /** Back to Never drops the end: Ends only shows while the event repeats. */
    fun chooseRepeat(choice: RepeatChoice) {
        if (!canRepeat || choice == repeat) return
        repeat = choice
        if (choice == RepeatChoice.Never) repeatEnd = RepeatEnd.Never
    }

    /** Ignored unless the event repeats; changing between repeats keeps the end. */
    fun chooseEnd(end: RepeatEnd) {
        if (canRepeat && repeat != RepeatChoice.Never) repeatEnd = end
    }

    /** Choosing it while it is chosen keeps the count. */
    fun chooseAfter() {
        if (repeatEnd is RepeatEnd.After) return
        chooseEnd(RepeatEnd.After(DEFAULT_TIMES))
    }

    fun fewerTimes() = stepTimes(-1)

    fun moreTimes() = stepTimes(1)

    private fun stepTimes(by: Int) {
        val end = repeatEnd as? RepeatEnd.After ?: return
        repeatEnd = RepeatEnd.After((end.times + by).coerceIn(MIN_TIMES, MAX_TIMES))
    }

    /** The start time a timed choice gives; null for All day. */
    val startTime: LocalTime?
        get() = when (val t = time) {
            TimeChoice.AllDay -> null
            is TimeChoice.Custom -> t.time
        }

    /** Where the time picker opens: the chosen start; with none, the next whole hour today (23:00 at the latest), else 09:00. */
    val pickerTime: LocalTime
        get() = startTime ?: if (day != today) MORNING else if (now.hour >= LAST_HOUR) LocalTime.of(LAST_HOUR, 0) else LocalTime.of(now.hour + 1, 0)

    /** Pick a date's label: the chosen date, e.g. "Mon 5 Oct", when it isn't one of the seven Day chips. */
    val pickedDateLabel: String?
        get() = day.takeIf { it !in dayChoices }?.format(SHORT_DAY)

    /**
     * A locked multi-day edit's one line: "Mon 22 – Wed 24 · change dates in Google Calendar on your phone", or, when the
     * span crosses a month, "Wed 30 Sep – Fri 2 Oct · …" on both ends; [service] names where to change them.
     */
    fun lockedDatesLabel(service: String): String? = original?.takeIf { datesLocked }?.let { o ->
        val (first, last) = daySpan(o.start, o.end, zone)
        val formatter = if (first.month == last.month && first.year == last.year) DAY_AND_DATE else SHORT_DAY
        "${first.format(formatter)} – ${last.format(formatter)} · change dates in $service on your phone"
    }

    /**
     * What Save sends. All day is one whole date. A timed event starts at the chosen local time in the household zone
     * (in a spring-forward gap it moves forward; an ambiguous autumn time takes the earlier offset) and lasts
     * [length] of real time, but ends at the next midnight at the latest, since new timed events can't span days. An all-day event with an Until ends the day after it (exclusive). A new event that repeats carries its rule (5 §A.4). A
     * locked edit, or one whose Day, Time and Length weren't touched and whose own span was valid (all-day, or a
     * positive length), keeps its own start and end: rebuilt from the chips, an event in the autumn's repeated hour
     * would move. A zero- or negative-length original (bad provider data) is always rebuilt from the chips, so Save
     * can't send an end at or before its start. An edit whose Who wasn't touched keeps its tag exactly (an untagged
     * event stays untagged).
     */
    fun draft(createdBy: String?): EventDraft {
        val o = original
        val forPerson = if (o != null && !whoTouched) o.forPerson else who.value
        val keepsOwnSpan = o != null && (datesLocked || (!whenTouched && (o.start is EventTime.AllDay || lengthOf(o) != null)))
        if (o != null && keepsOwnSpan) return EventDraft(title.trim(), o.start, o.end, forPerson, createdBy)
        val start = startTime
        return if (start == null) {
            EventDraft(title.trim(), EventTime.AllDay(day), EventTime.AllDay(maxOf(until ?: day, day).plusDays(1)), forPerson, createdBy, recurrence = recurrence(allDay = true))
        } else {
            val from = ZonedDateTime.of(day, start, zone).toInstant()
            val midnight = day.plusDays(1).atStartOfDay(zone).toInstant()
            EventDraft(title.trim(), EventTime.Timed(from), EventTime.Timed(minOf(from.plus(length), midnight)), forPerson, createdBy, recurrence = recurrence(allDay = false))
        }
    }

    private fun recurrence(allDay: Boolean): Recurrence? =
        recurrenceRule(repeat, repeatEnd, allDay, zone)?.let { Recurrence(it, zone) }

    /**
     * What an edit changed from the event as it opened (3a design C3): the fields its update sends, so a change made
     * on a phone meanwhile to any other field is kept. A field changed and changed back isn't touched. A new event is
     * every field.
     */
    val touched: Set<EventField>
        get() {
            val o = original ?: return EventField.entries.toSet()
            val d = draft(createdBy = null)
            return buildSet {
                if (d.title != o.title.trim()) add(EventField.TITLE)
                if (d.start != o.start || d.end != o.end) add(EventField.TIMES)
                if (d.forPerson != o.forPerson) add(EventField.FOR_PERSON)
            }
        }

    /** An edit that changes nothing: the sheet just closes, with no PIN (2b-2 design §3.3). A new event never is. */
    val unchanged: Boolean get() = original != null && touched.isEmpty()

    /**
     * The header's live line, e.g. "Tomorrow · 14:00–15:00 · Mia": the When row's wording and the person's name. A tag
     * whose person has left the household has no name ([nameOf] gives null), so the line ends at the time.
     */
    fun summary(nameOf: (PersonId) -> String?): String {
        val d = draft(createdBy = null)
        val time = whenLabel(d.start, d.end, zone, today)
        return nameOf(who)?.let { "$time · $it" } ?: time
    }
}

private fun startDate(start: EventTime, zone: ZoneId): LocalDate = when (start) {
    is EventTime.AllDay -> start.date
    is EventTime.Timed -> start.instant.atZone(zone).toLocalDate()
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
