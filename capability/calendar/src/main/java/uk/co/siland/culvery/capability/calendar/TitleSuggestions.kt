package uk.co.siland.culvery.capability.calendar

import java.time.Duration
import java.time.LocalDate
import java.time.LocalTime
import uk.co.siland.culvery.capability.calendar.db.RememberedTitleEntity

private const val MIN_TYPED = 2
private const val MINUTES_PER_HOUR = 60

/** A title remembered from a calendar and what the editor fills in with it (9c §4.4); never a date to use. */
data class Suggestion(
    val title: String,
    val forPerson: String?,
    val allDay: Boolean,
    val start: LocalTime?,
    val length: Duration?,
    val days: Int?,
    val rule: String?,
    val seriesStart: LocalDate?,
    val rememberedDate: LocalDate,
)

/** Matches what is typed in a new event's title against the titles of visible calendars (9c §4.2). */
class TitleSuggestions(private val store: CalendarStore) {
    /**
     * Up to [max] titles whose key starts with what is typed, then those with a word that does; the latest date first
     * in each, one per title across connections. Nothing for under two characters.
     */
    suspend fun matching(typed: String, max: Int = 3): List<Suggestion> {
        val wanted = titleKey(typed)
        if (wanted.length < MIN_TYPED) return emptyList()
        val latest = store.rememberedTitles().groupBy { it.titleKey }.values.map { rows -> rows.maxBy { it.rememberedDate } }
        val (starts, words) = latest.filter { " ${it.titleKey}".contains(" $wanted") }.partition { it.titleKey.startsWith(wanted) }
        val byDate = compareByDescending<RememberedTitleEntity> { it.rememberedDate }.thenBy { it.titleKey }
        return (starts.sortedWith(byDate) + words.sortedWith(byDate)).take(max).map { it.toSuggestion() }
    }
}

private fun RememberedTitleEntity.toSuggestion() = Suggestion(
    title = title,
    forPerson = forPerson,
    allDay = allDay,
    start = startMinute?.let { LocalTime.of(it / MINUTES_PER_HOUR, it % MINUTES_PER_HOUR) },
    length = lengthMinutes?.let { Duration.ofMinutes(it.toLong()) },
    days = days,
    rule = rule,
    seriesStart = seriesStart?.let(LocalDate::parse),
    rememberedDate = LocalDate.parse(rememberedDate),
)
