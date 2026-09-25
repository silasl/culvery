package uk.co.siland.culvery.capability.calendar

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import java.time.Duration
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter
import java.util.Locale
import uk.co.siland.culvery.core.household.PersonId

/** Hand-off §7 Time chips. */
enum class TimeSlot(val label: String, val time: LocalTime) {
    Morning("Morning", LocalTime.of(9, 0)),
    Afternoon("Afternoon", LocalTime.of(14, 0)),
    Evening("Evening", LocalTime.of(18, 0)),
}

/** What the Time chips choose: all day, a slot, or a time from Pick time…. */
sealed interface TimeChoice {
    data object AllDay : TimeChoice
    data class Slot(val slot: TimeSlot) : TimeChoice
    data class Custom(val time: LocalTime) : TimeChoice
}

/** 2b-2 design D8: a title is at most 100 characters. */
const val MAX_TITLE_LENGTH = 100

/** Hand-off §7 Length chips: 30 min, 1 h, 2 h. */
val LENGTH_CHOICES: List<Duration> = listOf(Duration.ofMinutes(30), Duration.ofHours(1), Duration.ofHours(2))

private val DEFAULT_LENGTH: Duration = Duration.ofHours(1)
private const val MINUTES_PER_HOUR = 60L
private const val DAYS_SHOWN = 7L
private val DAY_AND_DATE: DateTimeFormatter = DateTimeFormatter.ofPattern("EEE d", Locale.ENGLISH)

/** "30 min", "1 h", "1 h 30": whole hours read "{h} h", under an hour "{m} min", otherwise "{h} h {mm}". */
fun lengthLabel(length: Duration): String {
    val hours = length.toHours()
    val minutes = length.toMinutes() % MINUTES_PER_HOUR
    return when {
        hours == 0L -> "$minutes min"
        minutes == 0L -> "$hours h"
        else -> "$hours h ${minutes.toString().padStart(2, '0')}"
    }
}

/** On [today], the next slot still to come ([now] before its time), All day once Evening has begun; Morning on any other day. */
internal fun defaultTime(day: LocalDate, today: LocalDate, now: LocalTime): TimeChoice =
    if (day != today) {
        TimeChoice.Slot(TimeSlot.Morning)
    } else {
        TimeSlot.entries.firstOrNull { now < it.time }?.let { TimeChoice.Slot(it) } ?: TimeChoice.AllDay
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
) {
    sealed interface Mode {
        data object New : Mode
        data class Edit(val original: EditableEvent) : Mode
    }

    private val original: EditableEvent? = (mode as? Mode.Edit)?.original

    /** Editing an event over several days: only the title and who can change (2b-2 design D3). */
    val datesLocked: Boolean = original?.let { spansDays(it.start, it.end, zone) } ?: false

    var title: String by mutableStateOf(original?.title ?: "")
        private set

    // An edit starts on the event's person (untagged reads as Family); a new event on whoever is signed in (D2).
    var who: PersonId by mutableStateOf(
        if (original != null) original.forPerson?.let(::PersonId) ?: PersonId.FAMILY else signedIn ?: PersonId.FAMILY,
    )
        private set

    var day: LocalDate by mutableStateOf(original?.let { startDate(it.start, zone) } ?: preselectedDay ?: today)
        private set

    var time: TimeChoice by mutableStateOf(original?.let { timeOf(it.start, zone) } ?: defaultTime(preselectedDay ?: today, today, now))
        private set

    var length: Duration by mutableStateOf(original?.let(::lengthOf) ?: DEFAULT_LENGTH)
        private set

    private var whoTouched = false

    // Until a Time chip is touched, changing the day re-applies the default (a new event only).
    private var timeTouched = original != null

    // Until Day, Time or Length is touched, an edit keeps the event's own start and end.
    private var whenTouched = false

    /** The Length chips: 30 min, 1 h, 2 h, and an edited event's own length when it is none of those (D3). */
    val lengths: List<Duration> = LENGTH_CHOICES + listOfNotNull(original?.let(::lengthOf)?.takeIf { it !in LENGTH_CHOICES })

    /** Today and the six days after it: the Day chips before Pick date…. */
    val dayChoices: List<LocalDate> = (0 until DAYS_SHOWN).map { today.plusDays(it) }

    val canSave: Boolean get() = title.trim().isNotEmpty()

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

    fun chooseDay(date: LocalDate) {
        day = date
        whenTouched = true
        if (!timeTouched) time = defaultTime(date, today, now)
    }

    /** A picked time that is a slot's time selects that slot's chip. */
    fun chooseTime(choice: TimeChoice) {
        time = if (choice is TimeChoice.Custom) {
            TimeSlot.entries.firstOrNull { it.time == choice.time }?.let { TimeChoice.Slot(it) } ?: choice
        } else {
            choice
        }
        timeTouched = true
        whenTouched = true
    }

    fun chooseLength(value: Duration) {
        length = value
        whenTouched = true
    }

    /** The start time a timed choice gives; null for All day. */
    val startTime: LocalTime?
        get() = when (val t = time) {
            TimeChoice.AllDay -> null
            is TimeChoice.Slot -> t.slot.time
            is TimeChoice.Custom -> t.time
        }

    /** Where the time picker opens: the chosen start, or the default slot's time (Evening's) when All day is chosen. */
    val pickerTime: LocalTime
        get() = startTime ?: (defaultTime(day, today, now) as? TimeChoice.Slot)?.slot?.time ?: TimeSlot.Evening.time

    /** Pick date…'s label: the chosen date, e.g. "Mon 5 Oct", when it isn't one of the seven Day chips. */
    val pickedDateLabel: String?
        get() = day.takeIf { it !in dayChoices }?.format(SHORT_DAY)

    /** A locked multi-day edit's one line: "Mon 22 – Wed 24 · change dates on your phone". */
    val lockedDatesLabel: String?
        get() = original?.takeIf { datesLocked }?.let { o ->
            val (first, last) = daySpan(o.start, o.end, zone)
            "${first.format(DAY_AND_DATE)} – ${last.format(DAY_AND_DATE)} · change dates on your phone"
        }

    /**
     * What Save sends. All day is one whole date. A timed event starts at the chosen local time in the household zone
     * (in a spring-forward gap it moves forward; an ambiguous autumn time takes the earlier offset) and lasts
     * [length] of real time, but ends at the next midnight at the latest, since new events can't span days (D3). A
     * locked edit, or one whose Day, Time and Length weren't touched, keeps its own start and end: rebuilt from the
     * chips, an event in the autumn's repeated hour would move. An edit whose Who wasn't touched keeps its tag
     * exactly (an untagged event stays untagged).
     */
    fun draft(createdBy: String?): EventDraft {
        val o = original
        val forPerson = if (o != null && !whoTouched) o.forPerson else who.value
        if (o != null && (datesLocked || !whenTouched)) return EventDraft(title.trim(), o.start, o.end, forPerson, createdBy)
        val start = startTime
        return if (start == null) {
            EventDraft(title.trim(), EventTime.AllDay(day), EventTime.AllDay(day.plusDays(1)), forPerson, createdBy)
        } else {
            val from = ZonedDateTime.of(day, start, zone).toInstant()
            val midnight = day.plusDays(1).atStartOfDay(zone).toInstant()
            EventDraft(title.trim(), EventTime.Timed(from), EventTime.Timed(minOf(from.plus(length), midnight)), forPerson, createdBy)
        }
    }

    /** An edit that changes nothing: the sheet just closes, with no PIN (2b-2 design §3.3). A new event never is. */
    val unchanged: Boolean
        get() {
            val o = original ?: return false
            val d = draft(createdBy = null)
            return d.title == o.title.trim() && d.start == o.start && d.end == o.end && d.forPerson == o.forPerson
        }

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
    is EventTime.Timed -> {
        val t = start.instant.atZone(zone).toLocalTime()
        TimeSlot.entries.firstOrNull { it.time == t }?.let { TimeChoice.Slot(it) } ?: TimeChoice.Custom(t)
    }
}

/** A timed event's length in real time; null for an all-day event, or one whose end isn't after its start (bad provider data). */
private fun lengthOf(event: EditableEvent): Duration? {
    val start = event.start
    val end = event.end
    if (start !is EventTime.Timed || end !is EventTime.Timed) return null
    return Duration.between(start.instant, end.instant).takeIf { !it.isNegative && !it.isZero }
}

private fun spansDays(start: EventTime, end: EventTime, zone: ZoneId): Boolean {
    val (first, last) = daySpan(start, end, zone)
    return last.isAfter(first)
}
