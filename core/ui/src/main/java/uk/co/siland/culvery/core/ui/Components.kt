package uk.co.siland.culvery.core.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import java.util.Locale

object CardTokens {
    /** The light theme's hairline edge, drawn in `border` (9 §4.1). */
    val edge = 1.dp
}

@Composable
fun HhCard(
    modifier: Modifier = Modifier,
    radius: Dp = 24.dp,
    color: Color = Culvery.colors.surf,
    padding: PaddingValues = PaddingValues(22.dp),
    decoration: (DrawScope.(HhColors) -> Unit)? = null,
    content: @Composable ColumnScope.() -> Unit,
) {
    val shape = RoundedCornerShape(radius)
    val colors = Culvery.colors
    Column(
        modifier = modifier
            .readAsOne()
            .clip(shape)
            .border(CardTokens.edge, colors.border, shape)
            .background(color)
            .then(if (decoration != null) Modifier.drawBehind { decoration(colors) } else Modifier)
            .padding(padding),
        content = content,
    )
}

/** List rows: people, towns, connections, calendars (9 §4.12). Lists put 10 between rows. */
object ListRowTokens {
    val radius = 19.dp
    val shape = RoundedCornerShape(radius)
    val paddingStart = 20.dp
    val paddingEnd = 16.dp
    val paddingV = 12.dp
    val minHeight = 69.dp
    val titleStyle = HhType.rowTitle.copy(fontSize = 18.sp)
    val leadingGap = 16.dp
    val lineGap = 2.dp
    val trailingGap = 12.dp
    val belowGap = 12.dp
    val icon = 24.dp
}

/** A list row's card: at least [minHeight] tall, clipped to [ListRowTokens.shape], with the hairline edge, filled `surf`. */
fun Modifier.listRowCard(colors: HhColors, minHeight: Dp = ListRowTokens.minHeight): Modifier = this
    .heightIn(min = minHeight)
    .clip(ListRowTokens.shape)
    .border(CardTokens.edge, colors.border, ListRowTokens.shape)
    .background(colors.surf)

/**
 * One list row: [title] and [subtitle] one line each, cut with "…"; [trailing] keeps its size and the text takes what's
 * left. With [onClick] the whole row is a button; [clickLabel] says what a tap does ("change Sam’s details"). [below]
 * holds anything under the top line (a calendar's Main line, its read problem, its person picker).
 */
@Composable
fun HhListRow(
    title: String,
    modifier: Modifier = Modifier,
    leading: (@Composable () -> Unit)? = null,
    subtitle: String? = null,
    subtitleColor: Color = Culvery.colors.mute,
    trailing: (@Composable RowScope.() -> Unit)? = null,
    onClick: (() -> Unit)? = null,
    clickLabel: String? = null,
    below: (@Composable ColumnScope.() -> Unit)? = null,
    minHeight: Dp = ListRowTokens.minHeight,
) {
    val c = Culvery.colors
    Column(
        verticalArrangement = Arrangement.spacedBy(ListRowTokens.belowGap, Alignment.CenterVertically),
        modifier = modifier
            .fillMaxWidth()
            .listRowCard(c, minHeight)
            .then(if (onClick != null) Modifier.hhClickable(onClick, label = clickLabel) else Modifier)
            .padding(start = ListRowTokens.paddingStart, end = ListRowTokens.paddingEnd, top = ListRowTokens.paddingV, bottom = ListRowTokens.paddingV),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            if (leading != null) {
                leading()
                Spacer(Modifier.width(ListRowTokens.leadingGap))
            }
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(ListRowTokens.lineGap)) {
                Text(
                    title, style = ListRowTokens.titleStyle, color = c.ink, maxLines = 1, overflow = TextOverflow.Ellipsis,
                )
                if (subtitle != null) {
                    Text(
                        subtitle, style = HhType.secondary, color = subtitleColor, maxLines = 1, overflow = TextOverflow.Ellipsis,
                    )
                }
            }
            if (trailing != null) {
                Spacer(Modifier.width(ListRowTokens.trailingGap))
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(ListRowTokens.trailingGap),
                    content = trailing,
                )
            }
        }
        below?.invoke(this)
    }
}

/** A section's label (4d-1 Decision 6): drawn in capitals, spoken as [text] is written ("Who"), and a heading. */
@Composable
fun HhSectionLabel(text: String, modifier: Modifier = Modifier) {
    Text(
        text.uppercase(Locale.UK),
        style = HhType.sectionLabel,
        color = Culvery.colors.mute,
        modifier = modifier.semantics {
            heading()
            contentDescription = text
        },
    )
}

/** 1–4 cards two to a row, more three (Lights' rooms). */
fun gridColumns(count: Int): Int = if (count <= 4) 2 else 3
