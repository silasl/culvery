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
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.hideFromAccessibility
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
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
