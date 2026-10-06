package uk.co.siland.culvery.core.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import java.util.Locale

@Composable
fun HhCard(
    modifier: Modifier = Modifier,
    radius: Dp = 24.dp,
    color: Color = Culvery.colors.surf,
    padding: PaddingValues = PaddingValues(22.dp),
    content: @Composable ColumnScope.() -> Unit,
) {
    Column(
        modifier = modifier
            .clip(RoundedCornerShape(radius))
            .background(color)
            .padding(padding),
        content = content,
    )
}

/** List rows: people, towns, connections, calendars (4d-1 §4.2). Lists put 10 between rows. */
object ListRowTokens {
    val radius = 18.dp
    val paddingV = 16.dp
    val paddingH = 20.dp
    val leadingGap = 16.dp
    val lineGap = 2.dp
    val trailingGap = 12.dp
    val belowGap = 12.dp
    val icon = 24.dp
}

/**
 * One list row: [title] and [subtitle] one line each, cut with "…"; [trailing] keeps its size and the text takes what's
 * left. With [onClick] the whole row is a button. [below] holds anything under the top line (a calendar's Master line,
 * its read problem, its person picker).
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
    below: (@Composable ColumnScope.() -> Unit)? = null,
) {
    val c = Culvery.colors
    Column(
        verticalArrangement = Arrangement.spacedBy(ListRowTokens.belowGap),
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(ListRowTokens.radius))
            .background(c.surf)
            .then(if (onClick != null) Modifier.clickable(role = Role.Button, onClick = onClick) else Modifier)
            .padding(horizontal = ListRowTokens.paddingH, vertical = ListRowTokens.paddingV),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            if (leading != null) {
                leading()
                Spacer(Modifier.width(ListRowTokens.leadingGap))
            }
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(ListRowTokens.lineGap)) {
                Text(
                    title, style = HhType.rowTitle, color = c.ink, maxLines = 1, overflow = TextOverflow.Ellipsis,
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
