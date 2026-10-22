package uk.co.siland.culvery.capability.calendar

import java.time.Duration
import java.time.LocalDate
import java.time.MonthDay
import java.time.temporal.ChronoUnit
import uk.co.siland.culvery.core.household.Person
import uk.co.siland.culvery.core.household.PersonId
import uk.co.siland.culvery.core.ui.clockText
import uk.co.siland.culvery.core.ui.plural

/** What "Use this" filled in, each cleared when its row changes (9c §4.4). */
enum class CopiedRow(val word: String) { Who("who"), Starts("time"), Repeats("repeats") }

private const val PICKS = 3
private const val LEAP_GAP_YEARS = 8

// The date a repeat counts from: the series' first date, else the date the title is remembered by.
internal fun Suggestion.anchor(): LocalDate = seriesStart ?: rememberedDate

/** The repeat the suggestion remembers, when the editor offers it. */
internal fun Suggestion.choice(): RepeatChoice? = rule?.let { choiceOf(it, anchor()) }?.takeIf { it != RepeatChoice.Never }

internal fun Suggestion.nth(): Int = rule?.let { nthOf(it, anchor()) } ?: nthWeekday(anchor())

private fun Suggestion.eventLength(): Duration? = if (allDay) days?.let { Duration.ofDays(it.toLong()) } else length

// Dropped when the event is longer than the repeat allows (9c §3.3).
private fun Suggestion.filledRepeat(): RepeatChoice? = choice()?.takeIf { c -> eventLength()?.let(c::fitsLength) ?: true }

/** The people the suggestion is for, still in the household; Family when none are. */
internal fun Suggestion.whoNow(people: List<Person>): List<Person> =
    resolvePeople(forPerson, PersonId.FAMILY, people.associateBy { it.id }, people.map { it.id })

private fun durationWords(length: Duration): String {
    val minutes = length.toMinutes()
    return listOf(
        minutes / MINUTES_PER_DAY to "day",
        minutes % MINUTES_PER_DAY / MINUTES_PER_HOUR to "hour",
        minutes % MINUTES_PER_HOUR to "minute",
    ).filter { it.first > 0 }.joinToString(" ") { plural(it.first, it.second) }
}

// "10:00–10:45" (spoken: "10:00 to 10:45"), "all day", "all day for 3 days"; past midnight, the start and how long.
private fun Suggestion.timeWords(spoken: Boolean): String {
    if (allDay) return days?.takeIf { it > 1 }?.let { "all day for ${plural(it.toLong(), "day")}" } ?: "all day"
    val from = start ?: return ""
    val long = length ?: return clockText(from)
    if (from.minuteOfDay() + long.toMinutes() > MINUTES_PER_DAY) return "${clockText(from)} for ${durationWords(long)}"
    return "${clockText(from)}${if (spoken) " to " else "–"}${clockText(from.plus(long))}"
}

private fun Suggestion.lastOrNext(today: LocalDate): String =
    "${if (rememberedDate > today) "next" else "last"} on ${rememberedDate.format(SHORT_DAY)}"

/**
 * The muted line under a suggestion's title (9c §4.3): who, the pattern, and when: "Mia · Saturdays 10:00–10:45 · last
 * on Sat 12 Sep"; a one-off is "Family · Sat 11 Jul, 10:30". [people] are the household's.
 */
fun patternLine(s: Suggestion, people: List<Person>, today: LocalDate): String {
    val who = whoLine(s.whoNow(people))
    val choice = s.choice()
    if (choice == null) {
        val last = s.days?.takeIf { s.allDay && it > 1 }?.let { s.rememberedDate.plusDays(it - 1L) }
        val date = dayRangeOrDay(s.rememberedDate, last ?: s.rememberedDate)
        val clock = if (s.allDay) "all day" else s.start?.let(::clockText).orEmpty()
        return listOf(who, "$date, $clock").joinToString(" · ")
    }
    val anchor = s.anchor()
    val time = s.timeWords(spoken = false)
    val pattern = if (choice == RepeatChoice.Weekly) "${weekdayName(anchor)}s $time" else "${choice.wording(anchor, s.nth())}, $time"
    return listOf(who, pattern, s.lastOrNext(today)).joinToString(" · ")
}

private fun fills(s: Suggestion, people: List<Person>, childOnly: Boolean, spoken: Boolean): String {
    val who = s.whoNow(people).let { if (it.singleOrNull()?.isFamily == true) EVERYONE else whoLine(it) }
    val repeat = s.filledRepeat()?.let { lowerFirst(it.wording(s.anchor(), s.nth())) }
    val time = s.timeWords(spoken)
    return when {
        childOnly -> "Fills in $time"
        // An all-day phrase follows who with a comma: "Fills in Sam, all day for 3 days".
        s.allDay -> "Fills in $who, ${spokenNames(listOfNotNull(time, repeat))}"
        else -> "Fills in ${spokenNames(listOfNotNull(who, time, repeat))}"
    }
}

/** The accent line (9c §4.3): "Fills in Mia, 10:00–10:45 and every Saturday". A child's leaves out who and the repeat. */
fun fillLine(s: Suggestion, people: List<Person>, childOnly: Boolean): String = fills(s, people, childOnly, spoken = false)

/** What TalkBack reads for the row: "Guitar lesson. Fills in Mia, 10:00 to 10:45 and every Saturday". */
fun spokenSuggestion(s: Suggestion, people: List<Person>, childOnly: Boolean): String =
    "${s.title}. ${fills(s, people, childOnly, spoken = true)}"

/** The banner over the Starts date pane after "Use this" (9c §4.4). */
fun bannerLine(s: Suggestion, copied: Set<CopiedRow>, today: LocalDate): String {
    val names = CopiedRow.entries.filter { it in copied }.map { it.word }
    val what = if (names.isEmpty()) "" else ": ${spokenNames(names)}"
    return "Copied from ${s.title} (${s.lastOrNext(today)})$what. Tap any row to change it. The date is never copied."
}

/**
 * The next dates that fit a copied repeat, from [today] on (9c §4.4): three for a weekly, fortnightly or
 * monthly-on-a-weekday repeat, one for a yearly one, none otherwise. A fortnight counts from the series' first date.
 */
fun quickPicksFor(s: Suggestion, today: LocalDate): List<LocalDate> {
    val anchor = s.anchor()
    val firstWeekday = today.plusDays(Math.floorMod(anchor.dayOfWeek.value - today.dayOfWeek.value, DAYS_PER_WEEK).toLong())
    return when (val choice = s.choice()) {
        RepeatChoice.Weekly -> occurrences(choice, firstWeekday, PICKS)
        RepeatChoice.Fortnightly -> {
            val onFortnight = if (ChronoUnit.WEEKS.between(anchor, firstWeekday) % 2 == 0L) firstWeekday else firstWeekday.plusWeeks(1)
            occurrences(choice, onFortnight, PICKS)
        }
        // One more than wanted, since this month's date may be before today.
        RepeatChoice.MonthlyOnWeekday -> occurrences(choice, firstWeekday, PICKS + 1, s.nth()).filter { it >= today }.take(PICKS)
        RepeatChoice.Yearly -> listOfNotNull(
            (today.year..today.year + LEAP_GAP_YEARS).firstNotNullOfOrNull { year ->
                MonthDay.from(anchor).takeIf { it.isValidYear(year) }?.atYear(year)?.takeIf { it >= today }
            },
        )
        else -> emptyList()
    }
}
