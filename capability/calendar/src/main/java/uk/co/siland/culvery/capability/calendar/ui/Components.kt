package uk.co.siland.culvery.capability.calendar.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.Dp
import uk.co.siland.culvery.core.ui.Culvery
import uk.co.siland.culvery.core.ui.HhIcon

/** Card-header pill ("Week"): hand-off §7, 44 dp tall, radius 22, `surf2` on the whole 44 dp box. */
@Composable
internal fun HeaderChip(text: String, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val c = Culvery.colors
    Box(
        contentAlignment = Alignment.Center,
        modifier = modifier
            .height(CalendarDimens.touchTarget)
            .clip(RoundedCornerShape(CalendarDimens.pillRadius))
            .background(c.surf2)
            .clickable(onClick = onClick)
            .padding(horizontal = CalendarDimens.pillPaddingH),
    ) {
        Text(text, style = CalendarType.pill, color = c.ink, maxLines = 1)
    }
}

/** The person-colour bar at the start of an event row; fills the row's intrinsic height. */
@Composable
internal fun ColourBar(color: Color, width: Dp) {
    Box(
        Modifier
            .width(width)
            .fillMaxHeight()
            .clip(RoundedCornerShape(width / 2))
            .background(color),
    )
}

/** The sheets' Delete (hand-off §7): 60 dp, padding 0 26, radius 30, `surf2`, `danger` 17 sp / 700 with `delete`. */
@Composable
internal fun DeleteButton(enabled: Boolean, tag: String, onClick: () -> Unit) {
    val c = Culvery.colors
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(CalendarDimens.footerIconGap),
        modifier = Modifier
            .testTag(tag)
            .height(CalendarDimens.footerButtonHeight)
            .clip(RoundedCornerShape(CalendarDimens.footerButtonRadius))
            .background(c.surf2)
            .clickable(enabled = enabled, onClickLabel = "Delete", onClick = onClick)
            .padding(horizontal = CalendarDimens.deleteButtonPaddingH),
    ) {
        HhIcon("delete", size = CalendarDimens.footerIcon, tint = c.danger)
        Text("Delete", style = CalendarType.footerButton, color = c.danger)
    }
}

/**
 * A sheet's main action (hand-off §7 Save, Edit): 60 dp, radius 30, `accent`, a 24 dp icon 10 from its 18 sp / 700
 * label. Disabled, it is `surf2` with `mute` text and does nothing.
 */
@Composable
internal fun PrimaryButton(
    text: String,
    icon: String,
    enabled: Boolean,
    tag: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val c = Culvery.colors
    val content = if (enabled) c.accentInk else c.mute
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(CalendarDimens.primaryIconGap, Alignment.CenterHorizontally),
        modifier = modifier
            .testTag(tag)
            .height(CalendarDimens.footerButtonHeight)
            .clip(RoundedCornerShape(CalendarDimens.footerButtonRadius))
            .background(if (enabled) c.accent else c.surf2)
            .clickable(enabled = enabled, onClick = onClick),
    ) {
        HhIcon(icon, size = CalendarDimens.footerIcon, tint = content)
        Text(text, style = CalendarType.primaryButton, color = content, maxLines = 1)
    }
}

/**
 * An `accent` pill: hand-off §7's Add event, and Settings' Connect and Reconnect (3a design §4.1). 48 dp, radius 24,
 * a 15 sp / 700 label; with an [icon], a 24 dp one 6 from the label and padding 0 20 0 14, without one padding 0 20.
 */
@Composable
internal fun AddButton(text: String, tag: String, icon: String? = "add", onClick: () -> Unit) {
    val c = Culvery.colors
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(CalendarDimens.addEventIconGap),
        modifier = Modifier
            .testTag(tag)
            .height(CalendarDimens.addEventHeight)
            .clip(RoundedCornerShape(CalendarDimens.addEventRadius))
            .background(c.accent)
            .clickable(onClick = onClick)
            .padding(
                start = if (icon != null) CalendarDimens.addEventPaddingStart else CalendarDimens.addEventPaddingEnd,
                end = CalendarDimens.addEventPaddingEnd,
            ),
    ) {
        if (icon != null) HhIcon(icon, size = CalendarDimens.addEventIcon, tint = c.accentInk)
        Text(text, style = CalendarType.addEventButton, color = c.accentInk, maxLines = 1)
    }
}
