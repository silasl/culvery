package uk.co.siland.culvery.capability.calendar.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
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
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.style.TextOverflow
import java.time.LocalDate
import kotlinx.coroutines.delay
import uk.co.siland.culvery.capability.calendar.DayUi
import uk.co.siland.culvery.capability.calendar.EventRef
import uk.co.siland.culvery.capability.calendar.EventUi
import uk.co.siland.culvery.capability.calendar.MAX_WEEKS_AHEAD
import uk.co.siland.culvery.capability.calendar.SyncStatusUi
import uk.co.siland.culvery.capability.calendar.WEEKDAY
import uk.co.siland.culvery.capability.calendar.WeekUi
import uk.co.siland.culvery.capability.calendar.isStaleAt
import uk.co.siland.culvery.capability.calendar.weekTitle
import uk.co.siland.culvery.capability.calendar.weekSubtitle
import uk.co.siland.culvery.core.household.Person
import uk.co.siland.culvery.core.ui.Culvery
import uk.co.siland.culvery.core.ui.HhCard
import uk.co.siland.culvery.core.ui.HhIcon
import uk.co.siland.culvery.core.ui.HhType
import uk.co.siland.culvery.core.ui.Icons

/** [weeksAhead]: how many weeks after this one [week] is (4c D10). */
data class WeekViewState(
    val week: WeekUi,
    val today: LocalDate,
    val sync: SyncStatusUi,
    val nowMillis: Long,
    val weeksAhead: Int = 0,
)

internal fun reconnectLabel(labels: List<String>): String =
    if (labels.size == 1) "${labels.single()} needs reconnecting" else "${labels.size} calendars need reconnecting"

/**
 * Hand-off §2 and §7: seven days, person-coloured chips and the sync state. ‹ › step a week at a time, from this week
 * (today and six days) to [MAX_WEEKS_AHEAD] weeks on, inside the synced window (4c D10), through [onWeeksAhead]; This
 * week jumps back. With [onAdd] (there is a writable master calendar), Add event sits right of the legend and adds on
 * the first day shown, and a tap on the space below a column's chips, or on its hint, adds on that column's day; chip
 * taps still open their event. Without it, the legend keeps a 24 dp gap to the right edge. The reconnect chip runs
 * [onReconnect] (3a design §4.3).
 */
@Composable
fun WeekView(
    state: WeekViewState,
    modifier: Modifier = Modifier,
    onOpen: (EventRef) -> Unit = {},
    onAdd: ((LocalDate) -> Unit)? = null,
    onWeeksAhead: (Int) -> Unit = {},
    onReconnect: () -> Unit = {},
) {
    val c = Culvery.colors
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
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Row(horizontalArrangement = Arrangement.spacedBy(CalendarDimens.weekStepGap)) {
                            RoundButton(Icons.CHEVRON_LEFT, "Earlier week", "week_earlier", enabled = state.weeksAhead > 0) {
                                onWeeksAhead(state.weeksAhead - 1)
                            }
                            RoundButton(Icons.CHEVRON_RIGHT, "Later week", "week_later", enabled = state.weeksAhead < MAX_WEEKS_AHEAD) {
                                onWeeksAhead(state.weeksAhead + 1)
                            }
                        }
                        Spacer(Modifier.width(CalendarDimens.weekTitleGap))
                        Text(weekTitle(state.weeksAhead), style = CalendarType.weekTitle, color = c.ink, maxLines = 1)
                        if (state.weeksAhead > 0) {
                            Spacer(Modifier.width(CalendarDimens.weekTitleGap))
                            ThisWeekChip { onWeeksAhead(0) }
                        }
                    }
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
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(CalendarDimens.headerTrailingGap),
                ) {
                    Legend(
                        state.week.people,
                        Modifier
                            .testTag("week_legend")
                            .then(if (onAdd == null) Modifier.padding(end = CalendarDimens.headerTrailingGap) else Modifier),
                    )
                    if (onAdd != null) AddButton("Add event", "week_add_event") { onAdd(state.week.start) }
                }
            }
            if (state.sync.needsSignIn.isNotEmpty()) {
                Spacer(Modifier.height(CalendarDimens.reconnectTop))
                ReconnectChip(reconnectLabel(state.sync.needsSignIn), onClick = onReconnect)
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(CalendarDimens.weekColumnGap), modifier = Modifier.fillMaxWidth().weight(1f)) {
            state.week.days.forEach { day ->
                DayColumn(
                    day,
                    isToday = day.date == state.today,
                    onOpen = onOpen,
                    onAdd = onAdd?.let { add -> { add(day.date) } },
                    modifier = Modifier.weight(1f).fillMaxHeight(),
                )
            }
        }
    }
}

@Composable
private fun DayColumn(day: DayUi, isToday: Boolean, onOpen: (EventRef) -> Unit, onAdd: (() -> Unit)?, modifier: Modifier) {
    val c = Culvery.colors
    val shape = RoundedCornerShape(CalendarDimens.weekColumnRadius)
    val inset = CalendarDimens.columnHeaderInset
    val chips = rememberLazyListState()
    val currentOnAdd = rememberUpdatedState(onAdd)
    HhCard(
        modifier = modifier
            .testTag("week_day_${day.date}")
            .then(if (isToday) Modifier.border(CalendarDimens.todayRingWidth, c.accent, shape) else Modifier),
        radius = CalendarDimens.weekColumnRadius,
        padding = PaddingValues(horizontal = CalendarDimens.weekColumnPaddingH, vertical = CalendarDimens.weekColumnPaddingV),
    ) {
        Row(Modifier.padding(start = inset, end = inset, bottom = inset)) {
            Text(
                if (isToday) "Today" else day.date.format(WEEKDAY),
                style = CalendarType.strong14,
                color = if (isToday) c.accent else c.mute,
                modifier = Modifier.alignByBaseline(),
            )
            Spacer(Modifier.width(CalendarDimens.weekDayDateGap))
            Text(day.date.dayOfMonth.toString(), style = HhType.dateNumber, color = c.ink, modifier = Modifier.alignByBaseline())
        }
        Spacer(Modifier.height(CalendarDimens.columnGap))
        Box(
            Modifier
                .fillMaxWidth()
                .weight(1f)
                .then(
                    if (onAdd == null) {
                        Modifier
                    } else {
                        // The chips take their own taps. Only the space below the last one adds: a tap in the header or
                        // between two chips is a missed chip, not a new event (2b-2 design §4.1). Keyed on the day
                        // (not the onAdd lambda, rebuilt every recomposition as the header's clock ticks), so the
                        // gesture coroutine doesn't restart and drop a tap mid-gesture; it reads onAdd fresh via
                        // rememberUpdatedState.
                        Modifier.pointerInput(day.date) {
                            detectTapGestures { tap ->
                                val last = chips.layoutInfo.visibleItemsInfo.lastOrNull()
                                if (last == null || tap.y > last.offset + last.size) currentOnAdd.value?.invoke()
                            }
                        }
                    },
                ),
        ) {
            LazyColumn(
                state = chips,
                verticalArrangement = Arrangement.spacedBy(CalendarDimens.columnGap),
                modifier = Modifier.fillMaxSize(),
            ) {
                items(day.events, key = { it.ref.listKey }) { EventChip(it, onOpen) }
            }
        }
        if (onAdd != null) AddHint(day.date, onAdd)
    }
}

/** Hand-off §7: a faint `add` at the foot of each column, in a tap area at least 40 dp tall. */
@Composable
private fun AddHint(date: LocalDate, onAdd: () -> Unit) {
    Box(
        contentAlignment = Alignment.BottomCenter,
        modifier = Modifier
            .testTag("week_add_$date")
            .fillMaxWidth()
            .heightIn(min = CalendarDimens.addHintMinHeight)
            .clickable(onClickLabel = "Add event", onClick = onAdd),
    ) {
        HhIcon(Icons.ADD, size = CalendarDimens.addHintIcon, tint = Culvery.colors.mute.copy(alpha = CalendarDimens.ADD_HINT_ALPHA))
    }
}

@Composable
private fun EventChip(event: EventUi, onOpen: (EventRef) -> Unit) {
    val c = Culvery.colors
    val colour = Color(event.person.color)
    val tint = if (c.bg.luminance() < 0.5f) CalendarDimens.CHIP_ALPHA_DARK else CalendarDimens.CHIP_ALPHA_LIGHT
    Column(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(CalendarDimens.chipRadius))
            .background(colour.copy(alpha = tint))
            .clickable(onClickLabel = "Open") { onOpen(event.ref) }
            .padding(horizontal = CalendarDimens.chipPaddingH, vertical = CalendarDimens.chipPaddingV),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(event.startLabel, style = CalendarType.chipTime, color = colour, maxLines = 1, modifier = Modifier.weight(1f))
            EventBadges(event, CalendarDimens.chipBadge)
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
        HhIcon(Icons.SYNC_PROBLEM, size = CalendarDimens.reconnectIcon, tint = c.danger)
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

/** Back to this week (4c §6.6): a quiet 44 dp pill beside a later week's title. */
@Composable
private fun ThisWeekChip(onClick: () -> Unit) {
    val c = Culvery.colors
    Box(
        contentAlignment = Alignment.Center,
        modifier = Modifier
            .testTag("week_this_week")
            .height(CalendarDimens.touchTarget)
            .clip(RoundedCornerShape(CalendarDimens.pillRadius))
            .background(c.surf2)
            .clickable(onClick = onClick)
            .padding(horizontal = CalendarDimens.pillPaddingH),
    ) {
        Text("This week", style = CalendarType.pill, color = c.ink, maxLines = 1)
    }
}

/** After this long without a touch, a later week goes back to this week (4c §6.6). */
internal const val BACK_TO_THIS_WEEK_MS = 120_000L

/** Which week the Calendar tab shows (4c D10): [weeks] after this one, 0 to [MAX_WEEKS_AHEAD]. */
@Stable
internal class WeekShown {
    var weeks by mutableIntStateOf(0)
        private set
    var touches by mutableIntStateOf(0)
        private set

    fun show(weeks: Int) {
        this.weeks = weeks.coerceIn(0, MAX_WEEKS_AHEAD)
    }

    fun touched() {
        touches++
    }
}

/**
 * A new [WeekShown] each [today], so the view is back on this week at midnight; a later week goes back after
 * [BACK_TO_THIS_WEEK_MS] without a touch.
 */
@Composable
internal fun rememberWeekShown(today: LocalDate): WeekShown {
    val shown = remember(today) { WeekShown() }
    LaunchedEffect(shown, shown.weeks, shown.touches) {
        if (shown.weeks > 0) {
            delay(BACK_TO_THIS_WEEK_MS)
            shown.show(0)
        }
    }
    return shown
}

/** Calls [onTouch] for every finger that goes down inside, before the children see it, without taking it. */
internal fun Modifier.onEveryTouch(onTouch: () -> Unit): Modifier = pointerInput(onTouch) {
    awaitPointerEventScope {
        while (true) {
            if (awaitPointerEvent(PointerEventPass.Initial).type == PointerEventType.Press) onTouch()
        }
    }
}
