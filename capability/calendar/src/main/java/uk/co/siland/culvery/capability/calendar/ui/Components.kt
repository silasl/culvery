package uk.co.siland.culvery.capability.calendar.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import uk.co.siland.culvery.core.ui.Culvery

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

/** Card-header text link ("Week ›"): muted text, as the hand-off's "3 lights on ›", with a 44 dp touch target. */
@Composable
internal fun HeaderLink(text: String, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val c = Culvery.colors
    Box(
        contentAlignment = Alignment.CenterEnd,
        modifier = modifier.heightIn(min = CalendarDimens.touchTarget).clickable(onClick = onClick),
    ) {
        Text(text, style = CalendarType.link, color = c.mute, maxLines = 1)
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
