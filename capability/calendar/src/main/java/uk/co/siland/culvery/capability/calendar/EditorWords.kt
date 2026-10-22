package uk.co.siland.culvery.capability.calendar

import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoUnit
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
private val DAY_MONTH: DateTimeFormatter = DateTimeFormatter.ofPattern("d MMM", Locale.ENGLISH)
private val DAY_AND_CLOCK: DateTimeFormatter = DateTimeFormatter.ofPattern("EEE HH:mm", Locale.ENGLISH)

/** "Every Saturday" as it reads mid-sentence: "every Saturday". */
fun lowerFirst(s: String): String = s.replaceFirstChar { it.lowercase() }

/** "Mia is free then", "Sam and Mia are free then". */
fun freeLine(check: FreeCheck.Free, names: (PersonId) -> String): String = "${subject(check.people.map(names))} free then"

/**
 * "Overlaps Piano, 15:30–16:15" when one person is chosen ([single]); otherwise who, up to two: "Sam has Dentist, 12:30–13:00;
 * Mia has Piano, 12:45–13:30", then " … and 1 more". Over several days ([overDays]) a one-day event reads with its date.
 */
fun clashLine(check: FreeCheck.Busy, single: Boolean, overDays: Boolean, names: (PersonId) -> String, zone: ZoneId): String {
    fun what(event: HeldEvent) = "${event.title}, ${clockRange(event, zone, withDate = overDays)}"
    if (single) return "Overlaps ${what(check.clashes.first().event)}"
    val named = check.clashes.joinToString("; ") { "${names(it.person)} has ${what(it.event)}" }
    return if (check.more > 0) "$named … and ${check.more} more" else named
}

/** "Mia is free at", "Sam and Mia are free at": what the free start chips follow. */
fun freeAtLine(people: List<String>): String = "${subject(people)} free at"

private fun subject(names: List<String>) = "${spokenNames(names)} ${if (names.size == 1) "is" else "are"}"

// An event over several days names its days: "Fri 18:00 – Sun 16:00"; [withDate] dates a one-day event: "Sat 24 Oct 12:30–13:00".
private fun clockRange(event: HeldEvent, zone: ZoneId, withDate: Boolean): String {
    val from = event.start.instantIn(zone).atZone(zone)
    val to = event.end.instantIn(zone).atZone(zone)
    val (first, last) = daySpan(event.start, event.end, zone)
    if (first != last) return "${from.format(DAY_AND_CLOCK)} – ${to.format(DAY_AND_CLOCK)}"
    val date = if (withDate) "${from.format(SHORT_DAY)} " else ""
    return "$date${from.format(HOURS_MINUTES)}–${to.format(HOURS_MINUTES)}"
}

/** The Who row: "Choose who", "Everyone", "Sam + Mia". */
fun whoValue(form: EditorForm, people: List<Person>): String = when {
    form.who.isEmpty() -> CHOOSE_WHO
    form.everyone -> EVERYONE
    else -> whoLine(chosen(form, people))
}

/** The Time row: "All day", "10:00–10:45". */
fun timeValue(form: EditorForm): String = timesOf(form) ?: ALL_DAY_LABEL

/**
 * The Check pane's sentence (handoff 6g): "Guitar lesson for Mia, every Saturday, 10:00–10:45, from 26 Sep to 12 Dec. 12
 * times in all.", "Half term for Sam and Mia, Mon 19 – Fri 23 Oct, all day.", "Camp for Mia, Fri 23 Oct 18:00 – Sun 25 Oct
 * 16:00." A one-off words its span as [spanWords] does; a series words its repeat and then the time of day.
 */
fun checkSentence(form: EditorForm, people: List<Person>): String {
    val who = if (form.everyone) lowerFirst(EVERYONE) else spokenWho(chosen(form, people))
    val first = form.eventWhen.startDate
    val series = form.repeat.takeIf { first != null && it != RepeatChoice.Never }
    val span = form.eventWhen.span(form.zone)
    val whenWords = when {
        first != null && series != null -> "${lowerFirst(series.wording(first, form.nthOn(first)))}, ${seriesTime(form)}"
        span != null -> spanWords(span, form.zone)
        else -> null
    }
    val parts = listOfNotNull("${form.title.trim()} for $who", whenWords).joinToString(", ")
    if (first == null || series == null) return "$parts."
    val from = first.format(DAY_MONTH)
    return when (val end = form.repeatEnd) {
        is RepeatEnd.After -> {
            val last = occurrences(series, first, end.times, form.nthOn(first)).last()
            "$parts, from $from to ${last.format(DAY_MONTH)}. ${plural(end.times.toLong(), "time")} in all."
        }
        else -> "$parts, from $from."
    }
}

// A series' time of day: "10:00–10:45", "18:00 – Sun 16:00" over several days, "all day" or "for 3 days, all day".
private fun seriesTime(form: EditorForm): String {
    val w = form.eventWhen
    val days = w.endDays + 1
    return when {
        w.allDay && days > 1 -> "for $days days, ${lowerFirst(ALL_DAY_LABEL)}"
        w.allDay -> lowerFirst(ALL_DAY_LABEL)
        w.lastDay == w.startDate -> "${clockText(w.startClock)}–${clockText(w.endClock)}"
        else -> "${clockText(w.startClock)} – ${checkNotNull(w.endDate).format(WEEKDAY)} ${clockText(w.endClock)}"
    }
}

/** The Check pane's first few dates, and how many more a counted series has after them; one for a one-off. */
fun firstDates(form: EditorForm): Pair<List<LocalDate>, Int> {
    val first = form.eventWhen.startDate ?: return emptyList<LocalDate>() to 0
    return when (val end = form.repeatEnd) {
        is RepeatEnd.After -> occurrences(form.repeat, first, minOf(end.times, FIRST_FEW), form.nthOn(first)) to maxOf(end.times - FIRST_FEW, 0)
        else -> occurrences(form.repeat, first, FIRST_FEW, form.nthOn(first)) to 0
    }
}

/** An occurrence starting on [date] as its Check chip words it: "Sat 26 Sep", or "Fri 23 – Sun 25 Oct" for a span of days. */
fun dateChipWords(form: EditorForm, date: LocalDate): String {
    val (first, last) = form.eventWhen.span(form.zone)?.let { daysOf(it, form.zone) } ?: return date.format(SHORT_DAY)
    return if (first == last) date.format(SHORT_DAY) else dayRange(date, date.plusDays(ChronoUnit.DAYS.between(first, last)))
}

private fun chosen(form: EditorForm, people: List<Person>): List<Person> {
    val byId = people.associateBy { it.id }
    return form.who.mapNotNull { byId[it] }
}

// "10:00–10:45"; null when all day.
private fun timesOf(form: EditorForm): String? {
    val w = form.eventWhen
    if (w.allDay) return null
    return "${clockText(w.startClock)}–${clockText(w.endClock)}"
}
