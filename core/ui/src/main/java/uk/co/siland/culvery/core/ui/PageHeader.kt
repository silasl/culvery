package uk.co.siland.culvery.core.ui

import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/** The tabs' title block and the tray beside it, from the hand-off's page header at 1.5 px per dp. */
object PageHeaderTokens {
    val titleStyle = HhType.screenTitle.copy(fontSize = 45.sp, fontWeight = FontWeight.W700, letterSpacing = (-0.7).sp)
    val subtitleStyle = HhType.date.copy(fontSize = 15.sp)
    val subtitleGap = 4.dp
    val below = 15.dp
    val trailingGap = 8.dp

    val trayRadius = 17.dp
    val trayEdge = 1.dp
    val trayPadding = 8.dp
    val trayGap = 8.dp
}

/**
 * A tab's title (a heading), its [subtitle] under it and [trailing] on the title's row, right-aligned (9 §4.4). [under]
 * follows the subtitle, or stands in its place, for lines richer than text.
 */
@Composable
fun HhPageHeader(
    title: String,
    subtitle: String?,
    modifier: Modifier = Modifier,
    titleModifier: Modifier = Modifier,
    under: (@Composable () -> Unit)? = null,
    trailing: @Composable RowScope.() -> Unit = {},
) {
    Column(modifier.fillMaxWidth().padding(bottom = PageHeaderTokens.below)) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(PageHeaderTokens.trailingGap)) {
            Text(
                title,
                style = PageHeaderTokens.titleStyle,
                color = Culvery.colors.ink,
                modifier = titleModifier.weight(1f).semantics { heading() },
            )
            trailing()
        }
        if (subtitle != null || under != null) Spacer(Modifier.height(PageHeaderTokens.subtitleGap))
        if (subtitle != null) Text(subtitle, style = PageHeaderTokens.subtitleStyle, color = Culvery.colors.mute)
        under?.invoke()
    }
}

/** An outlined row of buttons: a `surf3` edge and no fill, so it reads as a group on `bg`. */
@Composable
fun HhTray(modifier: Modifier = Modifier, content: @Composable RowScope.() -> Unit) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(PageHeaderTokens.trayGap),
        modifier = modifier
            .border(PageHeaderTokens.trayEdge, Culvery.colors.surf3, RoundedCornerShape(PageHeaderTokens.trayRadius))
            .padding(PageHeaderTokens.trayPadding),
        content = content,
    )
}
