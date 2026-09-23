package uk.co.siland.culvery.capability.calendar.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.style.TextOverflow
import java.time.format.DateTimeFormatter
import java.util.Locale
import uk.co.siland.culvery.capability.calendar.CALENDAR_TAB_ID
import uk.co.siland.culvery.capability.calendar.DayUi
import uk.co.siland.culvery.capability.calendar.EventUi
import uk.co.siland.culvery.core.plugin.LocalShellNavigator
import uk.co.siland.culvery.core.ui.Culvery
import uk.co.siland.culvery.core.ui.HhCard
import uk.co.siland.culvery.core.ui.HhType

/**
 * Rows per day before "+N more". The WIDE card is 279 dp tall: 279 − 2 × 20 padding − 44 header − 6 gap = 189 dp
 * for a day column. The day label is about 19 dp, each compact row about 50 dp (8 + 18 + 16 + 8) plus a 6 dp gap,
 * and "+N more" about 16 dp plus its gap. Three rows need 19 + 3 × 56 + 22 = 209 dp and do not fit; two need
 * 19 + 2 × 56 + 22 = 153 dp, which leaves room for font rounding. So 2.
 */
private const val MAX_ROWS = 2
private val WEEKDAY = DateTimeFormatter.ofPattern("EEEE", Locale.UK)

/** The next three days, [days] starting with tomorrow; null while loading. */
@Composable
fun ComingUpCard(days: List<DayUi>?, modifier: Modifier = Modifier) {
    val c = Culvery.colors
    val navigator = LocalShellNavigator.current
    HhCard(
        modifier = modifier.fillMaxSize().testTag("calendar_coming_up"),
        radius = CalendarDimens.cardRadius,
        padding = PaddingValues(horizontal = CalendarDimens.comingUpPaddingH, vertical = CalendarDimens.comingUpPaddingV),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
            Text("Coming up", style = HhType.cardTitle, color = c.ink, modifier = Modifier.weight(1f))
            HeaderLink("Week ›", onClick = { navigator.openTab(CALENDAR_TAB_ID) })
        }
        Spacer(Modifier.height(CalendarDimens.comingUpHeaderGap))
        if (days != null) {
            Row(
                horizontalArrangement = Arrangement.spacedBy(CalendarDimens.comingUpColumnGap),
                modifier = Modifier.fillMaxWidth().weight(1f),
            ) {
                days.forEachIndexed { i, day ->
                    DayColumn(
                        label = if (i == 0) "Tomorrow" else day.date.format(WEEKDAY),
                        events = day.events,
                        modifier = Modifier.weight(1f),
                    )
                }
            }
        }
    }
}

@Composable
private fun DayColumn(label: String, events: List<EventUi>, modifier: Modifier) {
    val c = Culvery.colors
    // Scrolls only if a tall font scale makes MAX_ROWS rows overflow the card.
    Column(
        verticalArrangement = Arrangement.spacedBy(CalendarDimens.comingUpRowGap),
        modifier = modifier.verticalScroll(rememberScrollState()),
    ) {
        Text(label, style = CalendarType.strong14, color = c.ink)
        if (events.isEmpty()) Text("Free", style = HhType.secondary, color = c.mute)
        events.take(MAX_ROWS).forEach { CompactRow(it) }
        if (events.size > MAX_ROWS) {
            Text("+${events.size - MAX_ROWS} more", style = CalendarType.small12, color = c.mute)
        }
    }
}

@Composable
private fun CompactRow(event: EventUi) {
    val c = Culvery.colors
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(IntrinsicSize.Min)
            .clip(RoundedCornerShape(CalendarDimens.compactRowRadius))
            .background(c.surf2)
            .padding(horizontal = CalendarDimens.compactRowPaddingH, vertical = CalendarDimens.compactRowPaddingV),
    ) {
        ColourBar(Color(event.person.color), width = CalendarDimens.compactBarWidth)
        Spacer(Modifier.width(CalendarDimens.compactBarGap))
        Column {
            Text(event.title, style = CalendarType.strong14, color = c.ink, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(event.timeLabel, style = CalendarType.small12, color = c.mute, maxLines = 1)
        }
    }
}
