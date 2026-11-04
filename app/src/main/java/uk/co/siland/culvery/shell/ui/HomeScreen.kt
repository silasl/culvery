package uk.co.siland.culvery.shell.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.key
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.text
import androidx.compose.ui.semantics.traversalIndex
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.constrainHeight
import androidx.compose.ui.unit.constrainWidth
import androidx.compose.ui.unit.dp
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.util.Locale
import uk.co.siland.culvery.core.house.HouseLines
import uk.co.siland.culvery.core.plugin.HeaderItem
import uk.co.siland.culvery.core.plugin.HomeLayout
import uk.co.siland.culvery.core.ui.CakeMark
import uk.co.siland.culvery.core.ui.Culvery
import uk.co.siland.culvery.core.ui.HhType
import uk.co.siland.culvery.core.ui.HomeSkyBand
import uk.co.siland.culvery.core.ui.LocalHomeSkyBand
import uk.co.siland.culvery.core.ui.SlotClock
import uk.co.siland.culvery.core.ui.lineBox
import uk.co.siland.culvery.core.ui.readAsOne

private val CLOCK = DateTimeFormatter.ofPattern("HH:mm")
private val DATE = DateTimeFormatter.ofPattern("EEEE d MMMM", Locale.UK)

// Header items 28 apart, a 1 × 48 `line` divider between two, their row 4 above the date's bottom (4b design §4.1).
private val HEADER_ITEM_GAP = 28.dp
private val HEADER_DIVIDER_WIDTH = 1.dp
private val HEADER_DIVIDER_HEIGHT = 48.dp
private val HEADER_ITEMS_BOTTOM = 4.dp

@Composable
fun HomeScreen(now: () -> LocalDateTime, layout: HomeLayout, headerItems: List<HeaderItem>) {
    Column(verticalArrangement = Arrangement.spacedBy(18.dp), modifier = Modifier.fillMaxSize()) {
        HomeHeader(now, headerItems, Modifier.fillMaxWidth())
        HomeGrid(layout, Modifier.fillMaxWidth().weight(1f))
    }
}

/** Tells the sky how big a part of the header is, where the theme has a sky to place (14 §4.3). */
private fun Modifier.measuring(band: HomeSkyBand?, set: HomeSkyBand.(Int, Int) -> Unit): Modifier =
    if (band == null) this else onSizeChanged { band.set(it.width, it.height) }

/**
 * Clock and date, the date [ShellDimens.clockToDate] below the clock's line box (9 §4.2); the
 * capabilities' items at the right, in the width the clock and date leave less [HEADER_ITEM_GAP], their bottom
 * [HEADER_ITEMS_BOTTOM] above the date's (4b design §4.1, 4d-1 §6.2).
 */
@Composable
fun HomeHeader(now: () -> LocalDateTime, items: List<HeaderItem>, modifier: Modifier = Modifier) {
    val c = Culvery.colors
    val band = LocalHomeSkyBand.current
    // Read clock, date, then the items: the items sit level with the clock, so by position they'd come between.
    Layout(
        modifier = modifier.readAsOne().measuring(band) { w, h -> headerWidth = w; headerHeight = h },
        content = {
            val birthday = Culvery.birthday
            val time = now().format(CLOCK)
            val clockModifier = Modifier.testTag("home_clock").lineBox(HhType.clock.lineHeight).semantics { traversalIndex = 0f }
                .measuring(band) { w, _ -> clockWidth = w }
            val face = Culvery.look?.clockFace
            if (face != null) {
                SlotClock(time, face, c.accent, clockModifier)
            } else {
                Text(time, style = HhType.clock, color = c.ink, modifier = clockModifier)
            }
            Text(now().format(DATE), style = HhType.date, color = c.mute, modifier = Modifier.testTag("home_date").semantics { traversalIndex = 1f }.measuring(band) { w, _ -> dateWidth = w })
            HeaderItems(items, Modifier.measuring(band) { w, _ -> itemsWidth = w })
            if (birthday != null) GreetingPill("Happy birthday " + HouseLines.roomList(birthday.names), band)
        },
    ) { measurables, constraints ->
        val loose = constraints.copy(minWidth = 0, minHeight = 0)
        val clock = measurables[0].measure(loose)
        val date = measurables[1].measure(loose)
        val budget = constraints.maxWidth - maxOf(clock.width, date.width) - HEADER_ITEM_GAP.roundToPx()
        val right = measurables[2].measure(loose.copy(maxWidth = budget.coerceAtLeast(0)))
        val dateY = clock.height + ShellDimens.clockToDate.roundToPx()
        val dateBottom = dateY + date.height
        val width = constraints.constrainWidth(maxOf(clock.width, date.width))
        val pill = measurables.getOrNull(3)?.measure(loose.copy(maxWidth = (constraints.maxWidth - right.width - HEADER_ITEM_GAP.roundToPx()).coerceAtLeast(0)))
        val pillY = dateBottom + ShellDimens.greetingGap.roundToPx()
        val height = constraints.constrainHeight(maxOf(dateBottom, right.height, pill?.let { pillY + it.height } ?: 0))
        layout(width, height) {
            clock.place(0, 0)
            date.place(0, dateY)
            pill?.place(0, pillY)
            right.place(width - right.width, maxOf(0, dateBottom - HEADER_ITEMS_BOTTOM.roundToPx() - right.height))
        }
    }
}

/** The drawn cake leads the line; the pill is read as the one line of text (16 §3.2). */
@Composable
private fun GreetingPill(greeting: String, band: HomeSkyBand?) {
    val c = Culvery.colors
    if (band != null) DisposableEffect(band) { onDispose { band.greetingWidth = 0 } }
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.testTag("home_greeting")
            .clearAndSetSemantics { text = AnnotatedString(greeting); traversalIndex = 1.5f }
            .measuring(band) { w, _ -> greetingWidth = w }
            .clip(CircleShape)
            .background(c.accentSoft)
            .padding(horizontal = ShellDimens.greetingPaddingH, vertical = ShellDimens.greetingPaddingV),
    ) {
        CakeMark(ShellDimens.greetingCake)
        Spacer(Modifier.width(ShellDimens.greetingCakeGap))
        Text(greeting, style = HhType.rowTitle, color = c.ink, maxLines = 2, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f, fill = false))
    }
}

@Composable
private fun HeaderItems(items: List<HeaderItem>, measuring: Modifier) {
    val c = Culvery.colors
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(HEADER_ITEM_GAP),
        // A group, or its index wouldn't reach the items inside it.
        modifier = measuring.testTag("home_header_items").readAsOne().semantics { traversalIndex = 2f },
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
