package uk.co.siland.culvery.capability.calendar.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale
import uk.co.siland.culvery.capability.calendar.DayUi
import uk.co.siland.culvery.capability.calendar.EventUi
import uk.co.siland.culvery.capability.calendar.SyncStatusUi
import uk.co.siland.culvery.capability.calendar.WeekUi
import uk.co.siland.culvery.capability.calendar.isStaleAt
import uk.co.siland.culvery.capability.calendar.weekSubtitle
import uk.co.siland.culvery.core.household.Person
import uk.co.siland.culvery.core.plugin.LocalShellNavigator
import uk.co.siland.culvery.core.ui.Culvery
import uk.co.siland.culvery.core.ui.HhCard
import uk.co.siland.culvery.core.ui.HhIcon
import uk.co.siland.culvery.core.ui.HhType

private val SHORT_WEEKDAY = DateTimeFormatter.ofPattern("EEE", Locale.UK)

data class WeekViewState(val week: WeekUi, val today: LocalDate, val sync: SyncStatusUi, val nowMillis: Long)

internal fun reconnectLabel(labels: List<String>): String =
    if (labels.size == 1) "${labels.single()} needs reconnecting" else "${labels.size} calendars need reconnecting"

/**
 * Hand-off §2 and §7: a rolling seven days from today, person-coloured chips and the sync state. There is no week
 * navigation, so the view never leaves the synced window. The legend keeps a 24 dp gap to the right edge, where
 * 2b's Add event button goes.
 */
@Composable
fun WeekView(state: WeekViewState, modifier: Modifier = Modifier) {
    val c = Culvery.colors
    val navigator = LocalShellNavigator.current
    Column(verticalArrangement = Arrangement.spacedBy(CalendarDimens.weekHeaderGap), modifier = modifier.fillMaxSize()) {
        Column {
            // The title+subtitle column and the legend share one bottom-aligned row, so the legend stays level
            // with the subtitle whether or not the reconnect chip below adds height to the title column.
            Row(
                verticalAlignment = Alignment.Bottom,
                horizontalArrangement = Arrangement.spacedBy(CalendarDimens.headerTrailingGap),
                modifier = Modifier.fillMaxWidth(),
            ) {
                Column(Modifier.weight(1f)) {
                    Text("This week", style = CalendarType.weekTitle, color = c.ink)
                    Spacer(Modifier.height(CalendarDimens.subtitleTop))
                    Text(
                        weekSubtitle(state.sync, state.nowMillis),
                        style = CalendarType.subtitle,
                        color = if (state.sync.isStaleAt(state.nowMillis)) c.danger else c.mute,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.testTag("week_subtitle"),
                    )
                }
                Legend(state.week.people, Modifier.testTag("week_legend").padding(end = CalendarDimens.headerTrailingGap))
            }
            if (state.sync.needsSignIn.isNotEmpty()) {
                Spacer(Modifier.height(CalendarDimens.reconnectTop))
                ReconnectChip(reconnectLabel(state.sync.needsSignIn), onClick = navigator::openSettings)
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(CalendarDimens.weekColumnGap), modifier = Modifier.fillMaxWidth().weight(1f)) {
            state.week.days.forEach { day ->
                DayColumn(day, isToday = day.date == state.today, modifier = Modifier.weight(1f).fillMaxHeight())
            }
        }
    }
}

@Composable
private fun DayColumn(day: DayUi, isToday: Boolean, modifier: Modifier) {
    val c = Culvery.colors
    val shape = RoundedCornerShape(CalendarDimens.weekColumnRadius)
    val inset = CalendarDimens.columnHeaderInset
    HhCard(
        modifier = modifier
            .testTag("week_day_${day.date}")
            .then(if (isToday) Modifier.border(CalendarDimens.todayRingWidth, c.accent, shape) else Modifier),
        radius = CalendarDimens.weekColumnRadius,
        padding = PaddingValues(horizontal = CalendarDimens.weekColumnPaddingH, vertical = CalendarDimens.weekColumnPaddingV),
    ) {
        Row(Modifier.padding(start = inset, end = inset, bottom = inset)) {
            Text(
                if (isToday) "Today" else day.date.format(SHORT_WEEKDAY),
                style = CalendarType.strong14,
                color = if (isToday) c.accent else c.mute,
                modifier = Modifier.alignByBaseline(),
            )
            Spacer(Modifier.width(CalendarDimens.weekDayDateGap))
            Text(day.date.dayOfMonth.toString(), style = HhType.dateNumber, color = c.ink, modifier = Modifier.alignByBaseline())
        }
        Spacer(Modifier.height(CalendarDimens.columnGap))
        LazyColumn(
            verticalArrangement = Arrangement.spacedBy(CalendarDimens.columnGap),
            modifier = Modifier.fillMaxWidth().weight(1f),
        ) {
            items(day.events, key = { it.ref.listKey }) { EventChip(it) }
        }
    }
}

@Composable
private fun EventChip(event: EventUi) {
    val c = Culvery.colors
    val colour = Color(event.person.color)
    val tint = if (c.bg.luminance() < 0.5f) CalendarDimens.CHIP_ALPHA_DARK else CalendarDimens.CHIP_ALPHA_LIGHT
    Column(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(CalendarDimens.chipRadius))
            .background(colour.copy(alpha = tint))
            .padding(horizontal = CalendarDimens.chipPaddingH, vertical = CalendarDimens.chipPaddingV),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(event.startLabel, style = CalendarType.chipTime, color = colour, maxLines = 1, modifier = Modifier.weight(1f))
            if (event.recurring) {
                HhIcon("repeat", size = CalendarDimens.chipBadge, tint = c.mute, contentDescription = "Repeats")
            }
        }
        Spacer(Modifier.height(CalendarDimens.chipTitleTop))
        Text(event.title, style = CalendarType.chipTitle, color = c.ink, maxLines = 3, overflow = TextOverflow.Ellipsis)
    }
}

@Composable
private fun Legend(people: List<Person>, modifier: Modifier = Modifier) {
    val c = Culvery.colors
    Row(
        horizontalArrangement = Arrangement.spacedBy(CalendarDimens.legendGap),
        verticalAlignment = Alignment.CenterVertically,
        modifier = modifier,
    ) {
        people.forEach { person ->
            Row(
                horizontalArrangement = Arrangement.spacedBy(CalendarDimens.legendDotGap),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Box(Modifier.size(CalendarDimens.legendDot).clip(CircleShape).background(Color(person.color)))
                Text(
                    person.name,
                    style = CalendarType.legend,
                    color = c.ink,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.widthIn(max = CalendarDimens.legendNameMax),
                )
            }
        }
    }
}

/** One chip for every connection that needs signing in again: hand-off `dangerSoft` pill, 44 dp, radius 22. */
@Composable
private fun ReconnectChip(label: String, onClick: () -> Unit) {
    val c = Culvery.colors
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(CalendarDimens.reconnectIconGap),
        modifier = Modifier
            .height(CalendarDimens.touchTarget)
            .clip(RoundedCornerShape(CalendarDimens.pillRadius))
            .background(c.dangerSoft)
            .clickable(onClick = onClick)
            .padding(horizontal = CalendarDimens.pillPaddingH),
    ) {
        HhIcon("sync_problem", size = CalendarDimens.reconnectIcon, tint = c.danger)
        Text(
            label,
            style = CalendarType.pill,
            color = c.ink,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f, fill = false),
        )
    }
}
