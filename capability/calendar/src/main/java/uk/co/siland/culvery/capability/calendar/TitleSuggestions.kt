package uk.co.siland.culvery.capability.calendar

import java.time.Duration
import java.time.LocalDate
import java.time.LocalTime
import uk.co.siland.culvery.capability.calendar.db.RememberedTitleEntity

private const val MIN_TYPED = 2
private const val MAX_SUGGESTIONS = 3

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
    /** [LoadedTitles.matching] against the titles as they stand now. */
    suspend fun matching(typed: String, max: Int = MAX_SUGGESTIONS): List<Suggestion> =
        if (titleKey(typed).length < MIN_TYPED) emptyList() else LoadedTitles(store.rememberedTitles()).matching(typed, max)

    /** One editor's lookup: the titles are read on its first lookup of two characters or more, then matched in memory. */
    fun forEditor(): suspend (String) -> List<Suggestion> {
        var loaded: LoadedTitles? = null
        return { typed ->
            if (titleKey(typed).length < MIN_TYPED) {
                emptyList()
            } else {
                (loaded ?: LoadedTitles(store.rememberedTitles()).also { loaded = it }).matching(typed)
            }
        }
    }
}

/** Remembered titles read once: one per title across connections, the latest date first. */
internal class LoadedTitles(rows: List<RememberedTitleEntity>) {
    private class Candidate(val row: RememberedTitleEntity) {
        val spaced = " ${row.titleKey}"
    }

    private val latest: List<Candidate> = rows.groupBy { it.titleKey }.values
        .map { same -> same.maxBy { it.rememberedDate } }
        .sortedWith(compareByDescending<RememberedTitleEntity> { it.rememberedDate }.thenBy { it.titleKey })
        .map(::Candidate)

    /**
     * Up to [max] titles whose key starts with what is typed, then those with a word that does; the latest date first
     * in each. Nothing for under two characters.
     */
    fun matching(typed: String, max: Int = MAX_SUGGESTIONS): List<Suggestion> {
        val wanted = titleKey(typed)
        if (wanted.length < MIN_TYPED) return emptyList()
        val (starts, words) = latest.filter { it.spaced.contains(" $wanted") }.partition { it.row.titleKey.startsWith(wanted) }
        return (starts + words).take(max).map { it.row.toSuggestion() }
    }
}

private fun RememberedTitleEntity.toSuggestion() = Suggestion(
    title = title,
    forPerson = forPerson,
    allDay = allDay,
    start = startMinute?.let { clockAt(it.toLong()) },
    length = lengthMinutes?.let { Duration.ofMinutes(it.toLong()) },
    days = days,
    rule = rule,
    seriesStart = seriesStart?.let(LocalDate::parse),
    rememberedDate = LocalDate.parse(rememberedDate),
)
