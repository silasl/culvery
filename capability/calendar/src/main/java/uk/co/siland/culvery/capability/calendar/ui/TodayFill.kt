package uk.co.siland.culvery.capability.calendar.ui

import kotlin.math.min

/** Today's card shows five rows, today's first (9 §4.2). */
const val TODAY_ROWS = 5

/** The handoff's scroll shows four of today's rows above the pill, so its count starts at n − 4. */
const val PILL_ROWS_SHOWN = 4

const val NOTHING_ON_TODAY = "Nothing on today"
const val TOMORROW = "Tomorrow"

fun moreToday(count: Int): String = "$count more today · Open calendar"

/** How many of tomorrow's candidates show, or that today's rows scroll behind a pill reading [more]. */
data class TodayFit(val tomorrowShown: Int, val scroll: Boolean, val more: Int)

/**
 * Which rows fit Today's list, all heights in px (9 §4.2): [today]'s rows, or the [nothing] line when there are none, then
 * the [divider] and as many of [tomorrow]'s candidates as fit [available], [gap] apart. Today's rows that don't fit alone
 * scroll, and the pill counts those not wholly above the [pillSpace] it covers at the list's foot.
 */
internal fun fitToday(today: List<Int>, tomorrow: List<Int>, nothing: Int, divider: Int, gap: Int, available: Int, pillSpace: Int): TodayFit {
    if (today.size <= TODAY_ROWS) {
        val head = today.ifEmpty { listOf(nothing) }
        val shown = (tomorrow.size downTo 0).firstOrNull { t ->
            stackHeight(if (t == 0) head else head + divider + tomorrow.take(t), gap) <= available
        }
        if (shown != null) return TodayFit(shown, scroll = false, more = 0)
        if (today.isEmpty()) return TodayFit(0, scroll = false, more = 0)
    }
    var bottom = -gap
    val abovePill = today.takeWhile { bottom += gap + it; bottom <= available - pillSpace }.size
    return TodayFit(0, scroll = true, more = today.size - min(abovePill, PILL_ROWS_SHOWN))
}

private fun stackHeight(items: List<Int>, gap: Int): Int = items.sum() + gap * (items.size - 1)
