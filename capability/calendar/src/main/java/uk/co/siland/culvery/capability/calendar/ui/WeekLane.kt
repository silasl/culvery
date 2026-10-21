package uk.co.siland.culvery.capability.calendar.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import java.time.LocalDate
import java.time.temporal.ChronoUnit
import uk.co.siland.culvery.capability.calendar.EventRef
import uk.co.siland.culvery.capability.calendar.SpanUi
import uk.co.siland.culvery.core.ui.Culvery
import uk.co.siland.culvery.core.ui.HhAlpha
import uk.co.siland.culvery.core.ui.HhIcon
import uk.co.siland.culvery.core.ui.Icons
import uk.co.siland.culvery.core.ui.hhClickable
import uk.co.siland.culvery.core.ui.shownNotSpoken

/** How tall the lane is with [rows] rows of bars: nothing with none. */
internal fun laneHeight(rows: Int): Dp =
    if (rows == 0) 0.dp else CalendarDimens.laneRow * rows + CalendarDimens.laneRowGap * (rows - 1)

/**
 * The multi-day bars over the week's seven equal columns (9 §2.1, note 31): row by row, each bar from [barInset] inside
 * its first column to [barInset] inside its last, [start] being the first column's date.
 */
@Composable
internal fun WeekLane(
    rows: List<List<SpanUi>>,
    start: LocalDate,
    columnWidth: Dp,
    columnGap: Dp,
    barInset: Dp,
    onOpen: (EventRef) -> Unit,
    modifier: Modifier = Modifier,
) {
    val placed = remember(rows) { rows.flatMapIndexed { row, spans -> spans.map { row to it } } }
    Layout(
        content = { placed.forEach { (_, span) -> LaneBar(span, onOpen) } },
        modifier = modifier,
    ) { measurables, constraints ->
        val column = columnWidth.toPx()
        val step = column + columnGap.toPx()
        val inset = barInset.toPx()
        val rowStep = (CalendarDimens.laneRow + CalendarDimens.laneRowGap).toPx()
        val placeables = measurables.mapIndexed { i, bar ->
            val span = placed[i].second
            val days = ChronoUnit.DAYS.between(span.first, span.last).toInt()
            val width = (column + step * days - 2 * inset).toInt().coerceAtLeast(0)
            bar.measure(Constraints.fixed(width, CalendarDimens.laneRow.roundToPx()))
        }
        layout(constraints.maxWidth, laneHeight(rows.size).roundToPx()) {
            placeables.forEachIndexed { i, bar ->
                val (row, span) = placed[i]
                val x = step * ChronoUnit.DAYS.between(start, span.first) + inset
                bar.place(x.toInt(), (rowStep * row).toInt())
            }
        }
    }
}

/** One bar: the person's tint and colour edge, date_range, the title and its range; square where it runs past the week. */
@Composable
private fun LaneBar(span: SpanUi, onOpen: (EventRef) -> Unit) {
    val c = Culvery.colors
    val colour = Color(span.people.first().color)
    val radius = CalendarDimens.laneBarRadius
    val shape = RoundedCornerShape(
        topStart = if (span.startsBefore) 0.dp else radius,
        bottomStart = if (span.startsBefore) 0.dp else radius,
        topEnd = if (span.endsAfter) 0.dp else radius,
        bottomEnd = if (span.endsAfter) 0.dp else radius,
    )
    Box(
        Modifier
            .testTag("week_span_${span.ref.listKey}")
            .clip(shape)
            .background(colour.copy(alpha = HhAlpha.bar(c)))
            .hhClickable({ onOpen(span.ref) }, label = OPEN_EVENT_DETAILS, spoken = span.spoken),
    ) {
        StripedBar(span.people, width = CalendarDimens.laneEdge, radius = 0.dp)
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(CalendarDimens.laneIconGap),
            modifier = Modifier
                .fillMaxHeight()
                .padding(start = CalendarDimens.lanePaddingStart, end = CalendarDimens.lanePaddingEnd)
                .shownNotSpoken(),
        ) {
            HhIcon(Icons.DATE_RANGE, size = CalendarDimens.laneIcon, tint = colour)
            Row(horizontalArrangement = Arrangement.spacedBy(CalendarDimens.laneTextGap)) {
                Text(
                    span.title, style = CalendarType.laneTitle, color = c.ink, maxLines = 1, overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f, fill = false).alignByBaseline(),
                )
                Text(span.label, style = CalendarType.laneRange, color = c.ink, maxLines = 1, softWrap = false, modifier = Modifier.alignByBaseline())
            }
        }
    }
}
