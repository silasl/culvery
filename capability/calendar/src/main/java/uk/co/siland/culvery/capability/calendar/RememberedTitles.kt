package uk.co.siland.culvery.capability.calendar

import java.text.Normalizer
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.temporal.ChronoUnit
import java.util.Locale
import uk.co.siland.culvery.capability.calendar.db.EventRow
import uk.co.siland.culvery.capability.calendar.db.RememberedTitleEntity

private val WHITESPACE = Regex("\\s+")
private val MARKS = Regex("\\p{Mn}+")
private const val MILLIS_PER_MINUTE = 60_000L
private const val KEPT_MONTHS = 12L

/** What two titles must share to be one: trimmed, single-spaced, lower case, accents dropped. */
fun titleKey(title: String): String =
    Normalizer.normalize(title.trim().replace(WHITESPACE, " "), Normalizer.Form.NFD).replace(MARKS, "").lowercase(Locale.ROOT)

/** [titleKey], worked out once per distinct title: for one sync's many reads of the same titles. */
internal fun titleKeys(): (String) -> String {
    val keys = HashMap<String, String>()
    return { title -> keys.getOrPut(title) { titleKey(title) } }
}

/** The date a title is remembered by: the later of two on or before [today], else the earlier of two after it. */
fun better(a: LocalDate, b: LocalDate, today: LocalDate): LocalDate = when {
    a <= today && b <= today -> maxOf(a, b)
    a <= today -> a
    b <= today -> b
    else -> minOf(a, b)
}

/** Whether [new] would replace [old] as a title's date: only if strictly better. */
internal fun isBetter(new: LocalDate, old: LocalDate, today: LocalDate): Boolean = new != old && better(old, new, today) == new

/** Titles remembered for 12 months after their date. */
internal fun rememberedCutoff(today: LocalDate): LocalDate = today.minusMonths(KEPT_MONTHS)

private data class Supplier(val connectionId: String, val sourceId: String, val sourceEventId: String, val titleKey: String)

/**
 * One remembered title per connection and title key from stored events (spec 9c §4.1): a series counts once, from its
 * occurrence with the best date; where several events share a title the best date wins; a blank title, an event that
 * can't be read and a date before the 12 months are left out. An unreadable repeat is kept as none.
 */
internal fun rememberedFrom(rows: List<EventRow>, zone: ZoneId, today: LocalDate, keyOf: (String) -> String = ::titleKey): List<RememberedTitleEntity> {
    val cutoff = rememberedCutoff(today).toString()
    return rows.mapNotNull { runCatching { it.remembered(zone, keyOf) }.getOrNull() }
        .groupBy { Supplier(it.connectionId, it.sourceId, it.sourceEventId, it.titleKey) }
        .values
        .map { supplied -> supplied.reduce { a, b -> if (isBetter(b.date(), a.date(), today)) b else a } }
        .groupBy { it.connectionId to it.titleKey }
        .values
        .map { named -> named.reduce { a, b -> if (isBetter(b.date(), a.date(), today)) b else a } }
        .filter { it.rememberedDate >= cutoff }
}

private fun RememberedTitleEntity.date(): LocalDate = LocalDate.parse(rememberedDate)

/** Null for a blank title; throws for an event whose times can't be read. */
private fun EventRow.remembered(zone: ZoneId, keyOf: (String) -> String): RememberedTitleEntity? {
    val key = keyOf(event.title)
    if (key.isEmpty()) return null
    val allDay = event.startInstant == null
    val startDate: LocalDate
    var startMinute: Int? = null
    var length: Int? = null
    var days: Int? = null
    if (allDay) {
        startDate = LocalDate.parse(requireNotNull(event.startDate))
        days = ChronoUnit.DAYS.between(startDate, LocalDate.parse(requireNotNull(event.endDate))).toInt().coerceAtLeast(1)
    } else {
        val startMillis = requireNotNull(event.startInstant)
        val start = Instant.ofEpochMilli(startMillis).atZone(zone)
        startDate = start.toLocalDate()
        startMinute = start.toLocalTime().minuteOfDay()
        length = ((requireNotNull(event.endInstant) - startMillis) / MILLIS_PER_MINUTE).toInt().coerceAtLeast(0)
    }
    val series = event.recurrenceRule?.let(::seriesStart)
    return RememberedTitleEntity(
        connectionId = event.connectionId,
        titleKey = key,
        title = event.title.trim(),
        sourceId = event.sourceId,
        sourceEventId = event.seriesId ?: event.remoteId,
        forPerson = event.forPerson ?: sourcePersonId,
        allDay = allDay,
        startMinute = startMinute,
        lengthMinutes = length,
        days = days,
        rule = event.recurrenceRule?.let { keptRule(it, series ?: startDate) },
        seriesStart = series?.toString(),
        rememberedDate = startDate.toString(),
    )
}

/** The repeat the editor offers, written with no end and no first date; null for any other, or one that can't be read. */
private fun keptRule(rule: String, start: LocalDate): String? {
    val choice = runCatching { choiceOf(rule, start) }.getOrNull()?.takeIf { it != RepeatChoice.Never } ?: return null
    return recurrenceRule(choice, RepeatEnd.Never, start, nthOf(rule, start))
}

/**
 * What reading [new] makes of the row [old] (9c §4.1); null leaves [old] as it is. A row of a hidden calendar
 * ([oldHidden]) is never offered, so any visible title replaces it.
 */
internal fun mergedTitle(old: RememberedTitleEntity?, new: RememberedTitleEntity, today: LocalDate, oldHidden: Boolean): RememberedTitleEntity? = when {
    old == null || oldHidden -> new
    old.sourceId == new.sourceId && old.sourceEventId == new.sourceEventId ->
        new.copy(rememberedDate = better(old.date(), new.date(), today).toString())
    isBetter(new.date(), old.date(), today) -> new
    else -> null
}
