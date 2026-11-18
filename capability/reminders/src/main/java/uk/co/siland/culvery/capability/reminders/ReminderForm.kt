package uk.co.siland.culvery.capability.reminders

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import java.time.LocalDate
import java.time.LocalTime
import uk.co.siland.culvery.core.household.PersonId
import uk.co.siland.culvery.core.ui.ClockField
import uk.co.siland.culvery.core.ui.RepeatPick
import uk.co.siland.culvery.core.ui.toggledWho

private const val MINUTES_A_DAY = 24 * 60
private const val STEP = 5

/**
 * The add and change form's state (22 §4.6). A child ([childOnly]) is only ever themselves; everyone else starts as
 * Family. [time] starts at [now] rounded up to a five-minute mark.
 */
class ReminderForm(original: Reminder?, val today: LocalDate, now: LocalTime, val childOnly: PersonId?) {
    private val event = original?.event
    private val openingWords = original?.words.orEmpty()
    private val openingWho = original?.forPeople ?: listOf(childOnly ?: PersonId.FAMILY)
    private val openingDate = original?.schedule?.first ?: if (wrapsToTomorrow(now)) today.plusDays(1) else today
    private val openingTime = original?.time ?: roundedUp(now)

    private var typed by mutableStateOf(openingWords)

    /** The words as typed; input stops at [WORDS_MAX]. */
    var words: String
        get() = typed
        set(value) {
            typed = value.take(WORDS_MAX)
        }

    var who: List<PersonId> by mutableStateOf(openingWho)
        private set

    var date: LocalDate by mutableStateOf(openingDate)

    /** Read from the original's rule against its first day. */
    val repeat: RepeatPick = RepeatPick.read((original?.schedule as? Schedule.Repeating)?.rule, openingDate)

    var time: LocalTime by mutableStateOf(openingTime)

    /** The last person stays: Family replaces the set and a person replaces Family. A child's taps change nothing. */
    fun toggleWho(person: PersonId) {
        if (childOnly != null) return
        who = toggledWho(who, person, PersonId.FAMILY)
    }

    /** The hour moves by one, the minute to the next or last multiple of five; both wrap the day. */
    fun step(field: ClockField, up: Boolean) {
        val total = time.hour * 60 + time.minute
        val next = when (field) {
            ClockField.Hour -> if (up) total + 60 else total - 60
            ClockField.Minute -> if (up) (total / STEP + 1) * STEP else Math.floorDiv(total - 1, STEP) * STEP
        }
        val minuteOfDay = Math.floorMod(next, MINUTES_A_DAY)
        time = LocalTime.of(minuteOfDay / 60, minuteOfDay % 60)
    }

    /** Today to a year ahead. */
    fun allowedDay(day: LocalDate): Boolean = day >= today && day <= today.plusYears(1)

    val canSave: Boolean get() = tidyWords(words).isNotEmpty() && who.isNotEmpty()

    /** Anything differs from how the form opened, or the repeat has been tapped. */
    val edited: Boolean
        get() = words != openingWords || who != openingWho || date != openingDate || time != openingTime || repeat.touched

    /** Only for a form that [canSave]. Carries the original's event. */
    fun toDraft(): ReminderDraft {
        check(canSave) { "The form can't be saved yet" }
        val schedule = repeat.rule(date)?.let { Schedule.Repeating(date, it) } ?: Schedule.Once(date)
        return ReminderDraft(tidyWords(words), who, time, schedule, event)
    }
}

private fun roundedUpSeconds(now: LocalTime): Int {
    val seconds = now.toSecondOfDay() + if (now.nano > 0) 1 else 0
    return (seconds + STEP * 60 - 1) / (STEP * 60) * STEP * 60
}

private fun roundedUp(now: LocalTime) = LocalTime.ofSecondOfDay((roundedUpSeconds(now) % (24 * 3600)).toLong())

private fun wrapsToTomorrow(now: LocalTime) = roundedUpSeconds(now) >= 24 * 3600
