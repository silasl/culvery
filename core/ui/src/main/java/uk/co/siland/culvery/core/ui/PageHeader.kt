package uk.co.siland.culvery.core.ui

import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.hideFromAccessibility
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import java.util.Locale

/** The tabs' title block and the tray beside it, from the hand-off's page header at 1.5 px per dp. */
object PageHeaderTokens {
    val titleStyle = HhType.screenTitle.copy(fontSize = 45.sp, fontWeight = FontWeight.W700, letterSpacing = (-0.7).sp)
    val subtitleStyle = HhType.date.copy(fontSize = 15.sp)
    val subtitleGap = 4.dp

    /** A wizard step's or Settings page's title, with its 16 sp line 8 below (9 §4.11, §4.12). */
    val pageTitleStyle = titleStyle.copy(fontSize = 37.sp, letterSpacing = (-0.6).sp)
    val pageTitleLineStyle = HhType.body
    val pageTitleLineGap = 8.dp
    val below = 15.dp
    val trailingGap = 8.dp

    val trayRadius = 17.dp
    val trayEdge = 1.dp
    val trayPadding = 8.dp
    val trayGap = 8.dp
    val trayLabelIcon = 15.dp
    val trayLabelGap = 5.dp
    val trayLabelStart = 5.dp
    val trayLabelEnd = 7.dp
}

/**
 * A tab's title (a heading) with its [subtitle] under it, and [trailing] on the right with its foot level with the
 * block's last line (9 §4.4). [under] follows the subtitle, or stands in its place, for lines richer than text;
 * [footer] runs the full width below them both.
 */
@Composable
fun HhPageHeader(
    title: String,
    subtitle: String?,
    modifier: Modifier = Modifier,
    titleModifier: Modifier = Modifier,
    under: (@Composable () -> Unit)? = null,
    footer: (@Composable () -> Unit)? = null,
    trailing: @Composable RowScope.() -> Unit = {},
) {
    HhPageHeader(
        title = {
            Text(title, style = PageHeaderTokens.titleStyle, color = Culvery.colors.ink, modifier = titleModifier.semantics { heading() })
        },
        subtitle = subtitle,
        modifier = modifier,
        under = under,
        footer = footer,
        trailing = trailing,
    )
}

/** As above with a composable [title], for a title that is more than one run of text; it brings its own heading semantics. */
@Composable
fun HhPageHeader(
    title: @Composable () -> Unit,
    subtitle: String?,
    modifier: Modifier = Modifier,
    under: (@Composable () -> Unit)? = null,
    footer: (@Composable () -> Unit)? = null,
    trailing: @Composable RowScope.() -> Unit = {},
) {
    Column(modifier.fillMaxWidth().padding(bottom = PageHeaderTokens.below)) {
        Row(verticalAlignment = Alignment.Bottom, horizontalArrangement = Arrangement.spacedBy(PageHeaderTokens.trailingGap)) {
            Column(Modifier.weight(1f).align(Alignment.Top)) {
                title()
                if (subtitle != null || under != null) Spacer(Modifier.height(PageHeaderTokens.subtitleGap))
                if (subtitle != null) Text(subtitle, style = PageHeaderTokens.subtitleStyle, color = Culvery.colors.mute)
                under?.invoke()
            }
            trailing()
        }
        footer?.invoke()
    }
}

/** A wizard step's or Settings page's title (a heading) with its [line] under it, in the one page-title look (9 §4.11, §4.12). */
@Composable
fun HhPageTitle(title: String, line: String? = null, modifier: Modifier = Modifier) {
    Column(modifier.fillMaxWidth().padding(bottom = PageHeaderTokens.below)) {
        Text(title, style = PageHeaderTokens.pageTitleStyle, color = Culvery.colors.ink, modifier = Modifier.semantics { heading() })
        if (line != null) {
            Spacer(Modifier.height(PageHeaderTokens.pageTitleLineGap))
            Text(line, style = PageHeaderTokens.pageTitleLineStyle, color = Culvery.colors.mute)
        }
    }
}

/**
 * An outlined row of buttons: a `surf3` edge and no fill, so it reads as a group on `bg`. A [label] is drawn first,
 * uppercase with the touch icon, and not spoken: the buttons say what they do.
 */
@Composable
fun HhTray(modifier: Modifier = Modifier, label: String? = null, content: @Composable RowScope.() -> Unit) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(PageHeaderTokens.trayGap),
        modifier = modifier
            .border(PageHeaderTokens.trayEdge, Culvery.colors.surf3, RoundedCornerShape(PageHeaderTokens.trayRadius))
            .padding(PageHeaderTokens.trayPadding),
    ) {
        if (label != null) TrayLabel(label)
        content()
    }
}

/** The One-tap tray on Home (11 §4.4): the same edge and colour as [HhTray], with the label on top. */
object TrayTokens {
    val radius = 24.dp
    val padding = 11.dp
    val gap = 8.dp
}

/** One cell of [HhTrayGrid]: a key for its `tray_row_{key}` tag and what it draws. */
typealias TrayCell = Pair<String, @Composable () -> Unit>

/** How many rows [HhTrayGrid] always lays out, so a tray with few buttons keeps its rows' height (15a §2.1). */
const val TRAY_ROWS = 3

/**
 * [HhTray]'s Home form: the [label] on top, not spoken, then [TRAY_ROWS] rows of equal height. A row holds one cell at
 * full width or two side by side; a row with no cell, or past `rows.size`, is an empty spacer. Each cell is tagged `tray_row_{key}`.
 */
@Composable
fun HhTrayGrid(label: String, rows: List<List<TrayCell>>, modifier: Modifier = Modifier) {
    Column(
        verticalArrangement = Arrangement.spacedBy(TrayTokens.gap),
        modifier = modifier
            .testTag("tray_column")
            .border(PageHeaderTokens.trayEdge, Culvery.colors.surf3, RoundedCornerShape(TrayTokens.radius))
            .padding(TrayTokens.padding),
    ) {
        TrayLabel(label)
        TrayRows(rows, Modifier.fillMaxWidth().weight(1f))
    }
}

/** Where a tile in [HhTrayGrid] puts its line, as `(tag, line)`: under the tile's whole row. Null outside the tray. */
internal val LocalTrayLine = compositionLocalOf<((String, String?) -> Unit)?> { null }

/**
 * [TRAY_ROWS] rows, each at least a third of the height. The tiles' lines go under their row, stacked in tile order, and
 * the row grows by them so its tiles keep their size; a row whose tiles need more grows too. The height comes from the
 * empty rows first and then from rows with room to spare, so no row is drawn over another.
 */
@Composable
private fun TrayRows(rows: List<List<TrayCell>>, modifier: Modifier) {
    val slots = maxOf(TRAY_ROWS, rows.size)
    val lines = remember { mutableStateMapOf<String, Pair<String, String>>() }
    Layout(
        content = {
            repeat(slots) { i ->
                val cells = rows.getOrNull(i)
                if (cells.isNullOrEmpty()) {
                    Spacer(Modifier)
                } else {
                    Row(horizontalArrangement = Arrangement.spacedBy(TrayTokens.gap)) {
                        cells.forEach { (id, content) ->
                            key(id) {
                                val toRow: (String, String?) -> Unit = remember(id) {
                                    { tag, line -> if (line == null) lines.remove(id) else lines[id] = tag to line }
                                }
                                CompositionLocalProvider(LocalTrayLine provides toRow) {
                                    Box(Modifier.weight(1f).fillMaxHeight().testTag("tray_row_$id")) { content() }
                                }
                            }
                        }
                    }
                }
                Column(Modifier.fillMaxWidth()) {
                    cells.orEmpty().mapNotNull { lines[it.first] }.forEach { (tag, line) -> HouseModeLine(line, tag) }
                }
            }
        },
        modifier = modifier,
    ) { measurables, constraints ->
        val width = constraints.maxWidth
        val gap = TrayTokens.gap.roundToPx()
        val tiles = measurables.filterIndexed { i, _ -> i % 2 == 0 }
        val under = measurables.filterIndexed { i, _ -> i % 2 == 1 }
        val filled = List(slots) { i -> !rows.getOrNull(i).isNullOrEmpty() }
        val lineHeights = under.map { it.minIntrinsicHeight(width) }
        val tileHeights = tiles.mapIndexed { i, m -> if (filled[i]) m.minIntrinsicHeight(width) else 0 }
        val heights = if (constraints.hasBoundedHeight) {
            val third = (constraints.maxHeight - gap * (slots - 1)) / slots
            val natural = List(slots) { i -> if (lineHeights[i] > 0) maxOf(tileHeights[i], third) + lineHeights[i] else tileHeights[i] }
            trayRowHeights(natural, filled, constraints.maxHeight, gap)
        } else {
            List(slots) { i -> tileHeights[i] + lineHeights[i] }
        }
        val placed = List(slots) { i ->
            val tile = maxOf(0, heights[i] - lineHeights[i])
            tiles[i].measure(Constraints.fixed(width, tile)) to under[i].measure(Constraints.fixed(width, heights[i] - tile))
        }
        val height = if (constraints.hasBoundedHeight) constraints.maxHeight else heights.sum() + gap * (slots - 1)
        layout(width, height) {
            var y = 0
            placed.forEach { (tile, line) ->
                tile.placeRelative(0, y)
                line.placeRelative(0, y + tile.height)
                y += tile.height + line.height + gap
            }
        }
    }
}

/** Each row's height: a third of [total] less the gaps, grown to its [natural] height where that is more and the others can give, empty rows first. */
private fun trayRowHeights(natural: List<Int>, filled: List<Boolean>, total: Int, gap: Int): List<Int> {
    val third = (total - gap * (natural.size - 1)) / natural.size
    val heights = MutableList(natural.size) { third }
    val demand = natural.mapIndexed { i, n -> if (filled[i]) maxOf(0, n - third) else 0 }
    var need = demand.sum()
    var donated = 0
    fun give(i: Int, room: Int) {
        val g = minOf(room, need)
        heights[i] -= g
        need -= g
        donated += g
    }
    for (i in natural.indices) if (!filled[i]) give(i, third)
    for (i in natural.indices) if (filled[i]) give(i, maxOf(0, third - natural[i]))
    for (i in natural.indices) {
        val g = minOf(demand[i], donated)
        heights[i] += g
        donated -= g
    }
    return heights
}

@Composable
private fun TrayLabel(label: String) {
    val mute = Culvery.colors.mute
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(PageHeaderTokens.trayLabelGap),
        modifier = Modifier
            .testTag("tray_label")
            .semantics(mergeDescendants = true) { hideFromAccessibility() }
            .padding(start = PageHeaderTokens.trayLabelStart, end = PageHeaderTokens.trayLabelEnd),
    ) {
        HhIcon(Icons.TOUCH_APP, size = PageHeaderTokens.trayLabelIcon, filled = true, tint = mute, modifier = Modifier.testTag("tray_label_icon"))
        Text(label.uppercase(Locale.UK), style = HhType.sectionLabel, color = mute, maxLines = 1)
    }
}
