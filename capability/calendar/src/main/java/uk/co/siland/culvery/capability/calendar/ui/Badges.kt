package uk.co.siland.culvery.capability.calendar.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.Dp
import uk.co.siland.culvery.capability.calendar.EventUi
import uk.co.siland.culvery.capability.calendar.badges
import uk.co.siland.culvery.core.ui.Culvery
import uk.co.siland.culvery.core.ui.HhIcon

/** Hand-off §7 badges, muted: 15 dp on week chips, 20 dp on Today rows. Nothing when there are none. */
@Composable
internal fun EventBadges(event: EventUi, size: Dp, modifier: Modifier = Modifier) {
    val badges = event.badges()
    if (badges.isEmpty()) return
    val c = Culvery.colors
    Row(
        horizontalArrangement = Arrangement.spacedBy(CalendarDimens.badgeGap),
        verticalAlignment = Alignment.CenterVertically,
        modifier = modifier,
    ) {
        badges.forEach { HhIcon(it.icon, size = size, tint = c.mute, contentDescription = it.description) }
    }
}
