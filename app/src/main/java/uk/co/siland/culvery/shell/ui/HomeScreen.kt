package uk.co.siland.culvery.shell.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.key
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.FirstBaseline
import androidx.compose.ui.layout.LastBaseline
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.constrainHeight
import androidx.compose.ui.unit.constrainWidth
import androidx.compose.ui.unit.dp
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.util.Locale
import uk.co.siland.culvery.core.plugin.HeaderItem
import uk.co.siland.culvery.core.plugin.HomePlacement
import uk.co.siland.culvery.core.ui.Culvery
import uk.co.siland.culvery.core.ui.HhType
import uk.co.siland.culvery.core.ui.lineBox

private val CLOCK = DateTimeFormatter.ofPattern("HH:mm")
private val DATE = DateTimeFormatter.ofPattern("EEEE d MMMM", Locale.UK)

// Hand-off CSS: clock line-height 0.9 (11 px below its baseline) + 12 px margin + the date's 21 px ascent.
private val CLOCK_TO_DATE_BASELINES = 44.dp

// Hand-off §1: header items 28 apart, a 1 × 48 `line` divider between two, their row 4 above the date's bottom.
private val HEADER_ITEM_GAP = 28.dp
private val HEADER_DIVIDER_WIDTH = 1.dp
private val HEADER_DIVIDER_HEIGHT = 48.dp
private val HEADER_ITEMS_BOTTOM = 4.dp

@Composable
fun HomeScreen(now: () -> LocalDateTime, placements: List<HomePlacement>, headerItems: List<HeaderItem>) {
    Column(verticalArrangement = Arrangement.spacedBy(18.dp), modifier = Modifier.fillMaxSize()) {
        HomeHeader(now, headerItems, Modifier.fillMaxWidth())
        HomeGrid(placements, Modifier.fillMaxWidth().weight(1f))
    }
}

/**
 * Clock and date, the date's baseline [CLOCK_TO_DATE_BASELINES] below the clock's, as the hand-off's CSS places it; the
 * capabilities' items at the right, in the width the clock and date leave less [HEADER_ITEM_GAP], their bottom
 * [HEADER_ITEMS_BOTTOM] above the date's (4b design §4.1, 4d-1 §6.2).
 */
@Composable
fun HomeHeader(now: () -> LocalDateTime, items: List<HeaderItem>, modifier: Modifier = Modifier) {
    val c = Culvery.colors
    Layout(
        modifier = modifier,
        content = {
            Text(now().format(CLOCK), style = HhType.clock, color = c.ink, modifier = Modifier.testTag("home_clock").lineBox(HhType.clock.lineHeight))
            Text(now().format(DATE), style = HhType.date, color = c.mute, modifier = Modifier.testTag("home_date"))
            HeaderItems(items)
        },
    ) { measurables, constraints ->
        val loose = constraints.copy(minWidth = 0, minHeight = 0)
        val clock = measurables[0].measure(loose)
        val date = measurables[1].measure(loose)
        val budget = constraints.maxWidth - maxOf(clock.width, date.width) - HEADER_ITEM_GAP.roundToPx()
        val right = measurables[2].measure(loose.copy(maxWidth = budget.coerceAtLeast(0)))
        val dateY = clock[LastBaseline] + CLOCK_TO_DATE_BASELINES.roundToPx() - date[FirstBaseline]
        val dateBottom = dateY + date.height
        val width = constraints.constrainWidth(maxOf(clock.width, date.width))
        val height = constraints.constrainHeight(maxOf(dateBottom, right.height))
        layout(width, height) {
            clock.place(0, 0)
            date.place(0, dateY)
            right.place(width - right.width, maxOf(0, dateBottom - HEADER_ITEMS_BOTTOM.roundToPx() - right.height))
        }
    }
}

@Composable
private fun HeaderItems(items: List<HeaderItem>) {
    val c = Culvery.colors
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(HEADER_ITEM_GAP),
        modifier = Modifier.testTag("home_header_items"),
    ) {
        items.forEachIndexed { i, item ->
            key(item.id) {
                if (i > 0) {
                    Box(Modifier.testTag("home_header_divider").size(HEADER_DIVIDER_WIDTH, HEADER_DIVIDER_HEIGHT).background(c.line))
                }
                Box(Modifier.testTag("home_header_${item.id}")) { item.content() }
            }
        }
    }
}
