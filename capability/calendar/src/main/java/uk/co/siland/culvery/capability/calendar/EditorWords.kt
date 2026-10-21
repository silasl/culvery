package uk.co.siland.culvery.capability.calendar

import java.time.Duration
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale
import uk.co.siland.culvery.core.household.Person
import uk.co.siland.culvery.core.household.PersonId
import uk.co.siland.culvery.core.ui.clockText
import uk.co.siland.culvery.core.ui.plural

const val CHOOSE_WHO = "Choose who"
const val CHOOSE_A_DAY = "Choose a day"
const val EVERYONE = "Everyone"
const val CLASHES_ALLOWED_TAIL = "or keep it; clashes are allowed."
const val CLASHES_ALLOWED = "Clashes are allowed."

private const val FIRST_FEW = 4
private const val MINUTES_PER_DAY = 24 * 60L
private val DAY_MONTH: DateTimeFormatter = DateTimeFormatter.ofPattern("d MMM", Locale.ENGLISH)
private val DAY_AND_CLOCK: DateTimeFormatter = DateTimeFormatter.ofPattern("EEE HH:mm", Locale.ENGLISH)

/** "Every Saturday" as it reads mid-sentence: "every Saturday". */
fun lowerFirst(s: String): String = s.replaceFirstChar { it.lowercase() }

/** "Mia is free then", "Sam and Mia are free then". */
fun freeLine(check: FreeCheck.Free, names: (PersonId) -> String): String = "${subject(check.people.map(names))} free then"

/** "Overlaps Piano, 15:30–16:15" when one person is chosen ([single]); otherwise who: "Sam has Dentist, 12:30–13:00". */
fun clashLine(check: FreeCheck.Busy, single: Boolean, names: (PersonId) -> String, zone: ZoneId): String {
    val what = "${check.event.title}, ${clockRange(check.event, zone)}"
    return if (single) "Overlaps $what" else "${names(check.person)} has $what"
}

/** "Mia is free at", "Sam and Mia are free at": what the free start chips follow. */
fun freeAtLine(people: List<String>): String = "${subject(people)} free at"

private fun subject(names: List<String>) = "${spokenNames(names)} ${if (names.size == 1) "is" else "are"}"

// An event over several days names its days: "Fri 18:00 – Sun 16:00".
private fun clockRange(event: HeldEvent, zone: ZoneId): String {
    val from = event.start.instantIn(zone).atZone(zone)
    val to = event.end.instantIn(zone).atZone(zone)
    val (first, last) = daySpan(event.start, event.end, zone)
    return if (first == last) "${from.format(HOURS_MINUTES)}–${to.format(HOURS_MINUTES)}" else "${from.format(DAY_AND_CLOCK)} – ${to.format(DAY_AND_CLOCK)}"
}

/** The Who row: "Choose who", "Everyone", "Sam + Mia". */
fun whoValue(form: EditorForm, people: List<Person>): String = when {
    form.who.isEmpty() -> CHOOSE_WHO
    form.everyone -> EVERYONE
    else -> whoLine(chosen(form, people))
}

/** The Day row: "Choose a day", "Sat 26 Sep", "Thu 29 Oct – Sun 1 Nov". */
fun dayValue(form: EditorForm): String = datesOf(form) ?: CHOOSE_A_DAY

/** The Time row: "All day", "10:00–10:45". */
fun timeValue(form: EditorForm): String = timesOf(form) ?: ALL_DAY_LABEL

/**
 * The Check pane's sentence (handoff 6g): "Guitar lesson for Mia, every Saturday, 10:00–10:45, from 26 Sep to 12 Dec. 12
 * times in all.", "Half term for Sam and Mia, Mon 19 – Fri 23 Oct, all day."
 */
fun checkSentence(form: EditorForm, people: List<Person>): String {
    val who = if (form.everyone) lowerFirst(EVERYONE) else spokenWho(chosen(form, people))
    val first = form.day
    val series = form.repeat.takeIf { first != null && it != RepeatChoice.Never }
    val parts = listOfNotNull(
        "${form.title.trim()} for $who",
        if (first != null && series != null) lowerFirst(series.wording(first)) else datesOf(form),
        timesOf(form) ?: lowerFirst(ALL_DAY_LABEL),
    ).joinToString(", ")
    if (first == null || series == null) return "$parts."
    val from = first.format(DAY_MONTH)
    return when (val end = form.repeatEnd) {
        is RepeatEnd.After -> {
            val last = occurrences(series, first, end.times).last()
            "$parts, from $from to ${last.format(DAY_MONTH)}. ${plural(end.times.toLong(), "time")} in all."
        }
        else -> "$parts, from $from."
    }
}

/** The Check pane's first few dates, and how many more a counted series has after them; one for a one-off. */
fun firstDates(form: EditorForm): Pair<List<LocalDate>, Int> {
    val first = form.day ?: return emptyList<LocalDate>() to 0
    if (form.repeat == RepeatChoice.Never) return listOf(first) to 0
    return when (val end = form.repeatEnd) {
        is RepeatEnd.After -> occurrences(form.repeat, first, minOf(end.times, FIRST_FEW)) to maxOf(end.times - FIRST_FEW, 0)
        else -> occurrences(form.repeat, first, FIRST_FEW) to 0
    }
}

private fun chosen(form: EditorForm, people: List<Person>): List<Person> {
    val byId = people.associateBy { it.id }
    return form.who.mapNotNull { byId[it] }
}

// "Sat 26 Sep", or a range: "Mon 19 – Fri 23 Oct", with the month on both ends across months.
private fun datesOf(form: EditorForm): String? {
    val first = form.day ?: return null
    val last = form.until?.takeIf { form.multiDay } ?: return first.format(SHORT_DAY)
    val from = if (first.month == last.month && first.year == last.year) first.format(DAY_AND_DATE) else first.format(SHORT_DAY)
    return "$from – ${last.format(SHORT_DAY)}"
}

// "10:00–10:45", ending at midnight at most as the draft does; null when all day.
private fun timesOf(form: EditorForm): String? {
    val start = form.startTime ?: return null
    return "${clockText(start)}–${clockText(endOf(start, form.length))}"
}

private fun endOf(start: LocalTime, length: Duration): LocalTime {
    val toMidnight = MINUTES_PER_DAY - start.toSecondOfDay() / 60L
    return if (length.toMinutes() >= toMidnight) LocalTime.MIDNIGHT else start.plus(length)
}
