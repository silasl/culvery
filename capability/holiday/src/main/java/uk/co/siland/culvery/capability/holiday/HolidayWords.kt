package uk.co.siland.culvery.capability.holiday

import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoUnit
import java.util.Locale

/** Holiday mode's words (11 §5.2, §5.8). */
object HolidayWords {
    const val LABEL = "Holiday mode"
    const val ON = "ON"
    const val WHEN_BACK = "When are you back?"
    const val CHANGE_DATE = "Change date"
    const val WILL = "Holiday mode will:"
    const val TURN_ON_BUTTON = "Turn on Holiday mode"
    const val CANCEL = "Cancel"
    const val TURNING_ON = "Turning on…"
    const val TURNING_OFF = "Turning off…"
    const val CLICK_ON = "Turn on"
    const val CLICK_OFF = "Turn off"
    const val DOOR = "Keep the doorbell quiet. Rings and motion still show in the Door tab."
    const val SCREEN = "Turn the screen off until someone taps it."
    const val NO_PICK = "Pick the day you’re back."
    const val STILL_ON_ECO = "The heating is still on Eco. Turn it off in the Nest app."
    const val LIGHTS_STILL_ON = "Holiday lights are still on. Turn them off in the Lights tab."
    private const val LIGHTS_MISSED = "Holiday lights didn’t switch on. Turn them on in the Lights tab."
    private const val HOOVER_MISSED = "The hoover didn’t run."

    private const val WEEKDAY_ALONE_DAYS = 6L
    private val SHORT: DateTimeFormatter = DateTimeFormatter.ofPattern("EEE d MMM", Locale.ENGLISH)
    private val FULL: DateTimeFormatter = DateTimeFormatter.ofPattern("EEEE d MMMM", Locale.ENGLISH)
    private val WEEKDAY: DateTimeFormatter = DateTimeFormatter.ofPattern("EEEE", Locale.ENGLISH)

    /** The sheet's line for [step], or null for one the sheet doesn't list. */
    fun stepLine(step: HolidayStep, back: LocalDate): String? = when (step) {
        HolidayStep.LIGHTS_ON -> "Switch on Holiday lights now."
        HolidayStep.ECO_ON -> "Put the heating on Eco now, and take it off Eco at 00:01 on ${backSpoken(back)}."
        HolidayStep.HOOVER -> "Run the hoover at 10:00 on ${backSpoken(back.minusDays(1))}."
        HolidayStep.ECO_OFF -> null
    }

    /** The tile's line for a step that was missed; none for Eco on, since Eco off still runs. */
    fun missedLine(step: HolidayStep): String? = when (step) {
        HolidayStep.LIGHTS_ON -> LIGHTS_MISSED
        HolidayStep.HOOVER -> HOOVER_MISSED
        HolidayStep.ECO_ON, HolidayStep.ECO_OFF -> null
    }

    /** The tile's line for a step that threw rather than answer with a line of its own. */
    fun failedLine(step: HolidayStep): String = when (step) {
        HolidayStep.LIGHTS_ON -> LIGHTS_MISSED
        HolidayStep.ECO_ON -> "The heating didn’t go to Eco. Check the Nest app."
        HolidayStep.HOOVER -> HOOVER_MISSED
        HolidayStep.ECO_OFF -> "The heating didn’t come off Eco. Turn it off in the Nest app."
    }

    fun backShort(date: LocalDate) = "Back ${SHORT.format(date)}"

    fun backSpoken(date: LocalDate): String = FULL.format(date)

    /** A week or more away, the weekday alone would be ambiguous, so the date is in full. */
    fun fromCalendar(pick: BackDatePick?, today: LocalDate): String {
        pick ?: return NO_PICK
        val within = ChronoUnit.DAYS.between(today, pick.back) <= WEEKDAY_ALONE_DAYS
        return "From the calendar: ${pick.title}, ends ${(if (within) WEEKDAY else FULL).format(pick.back)}."
    }

    fun tileSpoken(on: Boolean, back: LocalDate?): String = when {
        !on -> "Holiday mode, off."
        back == null -> "Holiday mode, on."
        else -> "Holiday mode, on, back ${backSpoken(back)}."
    }

    /** The tile's name while a change runs, when neither "on" nor "off" is yet true. */
    fun runningSpoken(run: HolidayRun): String = when (run) {
        HolidayRun.TURNING_ON -> "Holiday mode, turning on."
        HolidayRun.TURNING_OFF -> "Holiday mode, turning off."
    }

    /** The sheet's title and date as one item for TalkBack. */
    fun sheetSpoken(back: LocalDate) = "$WHEN_BACK ${backSpoken(back)}."
}
