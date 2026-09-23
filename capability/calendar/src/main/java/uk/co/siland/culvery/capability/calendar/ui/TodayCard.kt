package uk.co.siland.culvery.capability.calendar.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.style.TextOverflow
import uk.co.siland.culvery.capability.calendar.CALENDAR_TAB_ID
import uk.co.siland.culvery.capability.calendar.EventUi
import uk.co.siland.culvery.core.plugin.LocalShellNavigator
import uk.co.siland.culvery.core.ui.Culvery
import uk.co.siland.culvery.core.ui.HhCard
import uk.co.siland.culvery.core.ui.HhIcon
import uk.co.siland.culvery.core.ui.HhType

/** Hand-off Home "Today" card. [events] null while loading: shows nothing rather than a false "Nothing on today". */
@Composable
fun TodayCard(events: List<EventUi>?, modifier: Modifier = Modifier) {
    val c = Culvery.colors
    val navigator = LocalShellNavigator.current
    HhCard(modifier = modifier.fillMaxSize().testTag("calendar_today"), radius = CalendarDimens.cardRadius) {
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
            Text("Today", style = HhType.cardTitle, color = c.ink, modifier = Modifier.weight(1f))
            HeaderChip("Week", onClick = { navigator.openTab(CALENDAR_TAB_ID) })
        }
        Spacer(Modifier.height(CalendarDimens.todayHeaderGap))
        when {
            events == null -> {}
            events.isEmpty() -> Text("Nothing on today", style = HhType.body, color = c.mute)
            else -> LazyColumn(
                verticalArrangement = Arrangement.spacedBy(CalendarDimens.todayRowGap),
                modifier = Modifier.fillMaxWidth().weight(1f),
            ) {
                items(events, key = { it.key }) { TodayRow(it) }
            }
        }
    }
}

@Composable
private fun TodayRow(event: EventUi) {
    val c = Culvery.colors
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(IntrinsicSize.Min)
            .clip(RoundedCornerShape(CalendarDimens.todayRowRadius))
            .background(c.surf2)
            .padding(horizontal = CalendarDimens.todayRowPaddingH, vertical = CalendarDimens.todayRowPaddingV),
    ) {
        ColourBar(Color(event.person.color), width = CalendarDimens.todayBarWidth)
        Spacer(Modifier.width(CalendarDimens.todayBarGap))
        Column(Modifier.weight(1f)) {
            Text(event.title, style = HhType.rowTitle, color = c.ink, maxLines = 2, overflow = TextOverflow.Ellipsis)
            Spacer(Modifier.height(CalendarDimens.todayTimeTop))
            Text(
                "${event.timeLabel} · ${event.person.name}",
                style = HhType.secondary,
                color = c.mute,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        if (event.recurring) {
            HhIcon(
                "repeat",
                size = CalendarDimens.todayBadge,
                tint = c.mute,
                modifier = Modifier.align(Alignment.CenterVertically),
                contentDescription = "Repeats",
            )
        }
    }
}
