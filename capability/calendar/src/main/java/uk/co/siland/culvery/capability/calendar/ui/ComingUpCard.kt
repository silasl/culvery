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
import androidx.compose.foundation.layout.heightIn
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
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale
import uk.co.siland.culvery.capability.calendar.DayUi
import uk.co.siland.culvery.capability.calendar.EventUi
import uk.co.siland.culvery.capability.calendar.whoLine
import uk.co.siland.culvery.core.ui.ButtonTokens
import uk.co.siland.culvery.core.ui.Culvery
import uk.co.siland.culvery.core.ui.HhCard
import uk.co.siland.culvery.core.ui.HhType
import uk.co.siland.culvery.core.ui.shownNotSpoken

/**
 * Rows per day before "+N more". The WIDE card is 279 dp tall: 279 − 2 × 20 padding − 48 header − 6 gap = 185 dp
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
    HhCard(
        modifier = modifier.fillMaxSize().testTag("calendar_coming_up"),
        radius = CalendarDimens.cardRadius,
        padding = PaddingValues(horizontal = CalendarDimens.comingUpPaddingH, vertical = CalendarDimens.comingUpPaddingV),
    ) {
        // The row is Today's header height (its Week button), so the two cards' headers line up.
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.fillMaxWidth().heightIn(min = ButtonTokens.regularHeight),
        ) {
            Text("Coming up", style = HhType.cardTitle, color = c.ink, modifier = Modifier.semantics { heading() })
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
                        spoken = dayHeading(day.date, if (i == 0) "Tomorrow" else null),
                        date = day.date,
                        events = day.events,
                        modifier = Modifier.weight(1f),
                    )
                }
            }
        }
    }
}

@Composable
private fun DayColumn(label: String, spoken: String, date: LocalDate, events: List<EventUi>, modifier: Modifier) {
    val c = Culvery.colors
    // Scrolls only if a tall font scale makes MAX_ROWS rows overflow the card.
    Column(
        verticalArrangement = Arrangement.spacedBy(CalendarDimens.comingUpRowGap),
        modifier = modifier.verticalScroll(rememberScrollState()),
    ) {
        Text(label, style = CalendarType.strong14, color = c.ink, modifier = Modifier.semantics { heading(); contentDescription = spoken })
        if (events.isEmpty()) Text("Nothing on", style = HhType.secondary, color = c.mute)
        events.take(MAX_ROWS).forEach { CompactRow(it, date) }
        if (events.size > MAX_ROWS) {
            Text("+${events.size - MAX_ROWS} more in Calendar", style = CalendarType.small12, color = c.mute)
        }
    }
}

@Composable
private fun CompactRow(event: EventUi, day: LocalDate) {
    val c = Culvery.colors
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .semantics(mergeDescendants = true) { contentDescription = eventOnDay(event, day) }
            .height(IntrinsicSize.Min)
            .clip(RoundedCornerShape(CalendarDimens.compactRowRadius))
            .background(c.surf2)
            .padding(horizontal = CalendarDimens.compactRowPaddingH, vertical = CalendarDimens.compactRowPaddingV),
    ) {
        StripedBar(event.people, width = CalendarDimens.compactBarWidth)
        Spacer(Modifier.width(CalendarDimens.compactBarGap))
        Column(Modifier.shownNotSpoken()) {
            Text(event.title, style = CalendarType.strong14, color = c.ink, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(
                "${event.timeLabel} · ${whoLine(event.people)}",
                style = CalendarType.small12,
                color = c.mute,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}
