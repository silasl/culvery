package uk.co.siland.culvery.capability.holiday

import java.time.LocalDate
import uk.co.siland.culvery.core.house.MultiDayEvent

private const val FURTHEST_DAYS = 90L

/** The calendar's guess at the back date: [back] is the event's last day. [toString] leaves out both. */
data class BackDatePick(val title: String, val back: LocalDate) {
    override fun toString() = "BackDatePick"
}

/** An event on today, else one starting tomorrow; of several, the latest to start, then the latest to end (11 §5.2). */
fun backDatePick(events: List<MultiDayEvent>, today: LocalDate): BackDatePick? {
    val candidates = events.filter { it.last > today && it.last <= today.plusDays(FURTHEST_DAYS) }
    val latest = compareBy<MultiDayEvent>({ it.first }, { it.last })
    val chosen = candidates.filter { it.first <= today }.maxWithOrNull(latest)
        ?: candidates.filter { it.first == today.plusDays(1) }.maxWithOrNull(latest)
    return chosen?.let { BackDatePick(it.title, it.last) }
}
