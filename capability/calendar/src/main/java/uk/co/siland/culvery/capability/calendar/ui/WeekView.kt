package uk.co.siland.culvery.capability.calendar.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.layout.SubcomposeLayout
import androidx.compose.ui.layout.layout
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.isTraversalGroup
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.testTag
import androidx.compose.ui.semantics.traversalIndex
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import java.time.LocalDate
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collectLatest
import uk.co.siland.culvery.capability.calendar.DayUi
import uk.co.siland.culvery.capability.calendar.EventRef
import uk.co.siland.culvery.capability.calendar.EventUi
import uk.co.siland.culvery.capability.calendar.MAX_WEEKS_AHEAD
import uk.co.siland.culvery.capability.calendar.MONTH_SHORT
import uk.co.siland.culvery.capability.calendar.SyncStatusUi
import uk.co.siland.culvery.capability.calendar.WEEKDAY
import uk.co.siland.culvery.capability.calendar.WeekUi
import uk.co.siland.culvery.capability.calendar.whoLineForms
import uk.co.siland.culvery.core.plugin.LocalUserActivity
import uk.co.siland.culvery.core.ui.Culvery
import uk.co.siland.culvery.core.ui.HhAlpha
import uk.co.siland.culvery.core.ui.HhCard
import uk.co.siland.culvery.core.ui.HhIcon
import uk.co.siland.culvery.core.ui.Icons
import uk.co.siland.culvery.core.ui.hhClickable
import uk.co.siland.culvery.core.ui.shownNotSpoken

/** [weeksAhead]: how many weeks after this one [week] is (4c D10). */
data class WeekViewState(
    val week: WeekUi,
    val today: LocalDate,
    val sync: SyncStatusUi,
    val nowMillis: Long,
    val weeksAhead: Int = 0,
)

/** One chip for every connection needing sign-in: the service by name, or how many accounts (4d-2 C20). */
internal fun reconnectLabel(services: List<String>): String =
    if (services.size == 1) "${services.single()} needs reconnecting" else "${services.size} accounts need reconnecting"

/**
 * Seven days with person-tinted chips and the sync state (9 §4.7). ‹ › step a week at a time, from this week (today and
 * six days) to [MAX_WEEKS_AHEAD] weeks on, inside the synced window (4c D10), through [onWeeksAhead]; This week jumps
 * back. With [onAdd] (there is a writable master calendar), Add event adds with no day chosen (9 §5 call 9), and a tap on
 * the space below a column's chips adds on that column's day, with a ghost while pressed; chip taps still open their event. The
 * reconnect chip runs [onReconnect] (3a design §4.3) and Update now runs [onUpdateNow] ([WeekHeader]).
 */
@Composable
fun WeekView(
    state: WeekViewState,
    modifier: Modifier = Modifier,
    onOpen: (EventRef) -> Unit = {},
    onAdd: ((LocalDate?) -> Unit)? = null,
    onWeeksAhead: (Int) -> Unit = {},
    onReconnect: () -> Unit = {},
    onUpdateNow: () -> Unit = {},
) {
    // The title block ends with its own gap above the columns.
    Column(modifier.fillMaxSize()) {
        WeekHeader(state, onWeeksAhead, onAdd, onReconnect, onUpdateNow)
        WeekColumns(state, onOpen, onAdd, Modifier.fillMaxWidth().weight(1f))
    }
}

/**
 * The seven columns with the lane of multi-day bars over them (9 §2.1): the lane sits where the chips start, below one
 * header measured at the current font scale, and every column's chips start below the lane. TalkBack reads the lane
 * before the columns, each of which it reads as one.
 */
@Composable
private fun WeekColumns(state: WeekViewState, onOpen: (EventRef) -> Unit, onAdd: ((LocalDate) -> Unit)?, modifier: Modifier) {
    val rows = state.week.spans
    val spanRefs = remember(rows) { rows.flatten().mapTo(HashSet()) { it.ref } }
    // The lane draws the multi-day events, so the columns leave them out.
    val columnDays = remember(state.week, spanRefs) { state.week.days.map { day -> day.copy(events = day.events.filter { it.ref !in spanRefs }) } }
    val laneHeight = laneHeight(rows.size)
    SubcomposeLayout(modifier.semantics { isTraversalGroup = true }) { constraints ->
        val gap = CalendarDimens.weekColumnGap.toPx()
        val days = columnDays.size
        val columnWidth = (constraints.maxWidth - gap * (days - 1)) / days
        val columns = subcompose("columns") {
            Row(horizontalArrangement = Arrangement.spacedBy(CalendarDimens.weekColumnGap)) {
                columnDays.forEach { day ->
                    DayColumn(
                        day,
                        isToday = day.date == state.today,
                        monthLabel = day.date.takeIf { it.dayOfMonth == 1 && it != state.week.start }?.format(MONTH_SHORT),
                        laneHeight = laneHeight,
                        onOpen = onOpen,
                        onAdd = onAdd?.let { add -> { add(day.date) } },
                        modifier = Modifier.weight(1f).fillMaxHeight(),
                    )
                }
            }
        }.single().measure(constraints)
        val lane = if (rows.isEmpty()) {
            null
        } else {
            val inner = (columnWidth - 2 * CalendarDimens.weekColumnPaddingSide.toPx()).toInt().coerceAtLeast(0)
            val header = subcompose("header") { Box(Modifier.clearAndSetSemantics {}) { DayHeader(state.week.start, isToday = false, onAdd = null) } }
                .single().measure(Constraints(maxWidth = inner))
            val top = CalendarDimens.weekColumnPaddingTop.roundToPx() + header.height + CalendarDimens.columnGap.roundToPx()
            top to subcompose("lane") {
                WeekLane(
                    rows, state.week.start, columnWidth.toDp(), CalendarDimens.weekColumnGap, CalendarDimens.laneBarInset, onOpen,
                    Modifier.semantics {
                        isTraversalGroup = true
                        traversalIndex = -1f
                    },
                )
            }.single().measure(constraints.copy(minHeight = 0))
        }
        layout(columns.width, columns.height) {
            columns.place(0, 0)
            lane?.let { (top, bars) -> bars.place(0, top) }
        }
    }
}

/**
 * A column's day and date: one heading for TalkBack, which adds from it with [onAdd]. A [monthLabel] ("Oct", on the 1st
 * of a new month) hangs from [CalendarDimens.monthLabelTop] below the card's top edge, whatever its own height, and takes no header height, so
 * every column's chips still start level (9 §3.2).
 */
@Composable
private fun DayHeader(date: LocalDate, isToday: Boolean, onAdd: (() -> Unit)?, monthLabel: String? = null) {
    val c = Culvery.colors
    val inset = CalendarDimens.columnHeaderInset
    Row(
        Modifier
            .padding(start = inset, end = inset, bottom = inset)
            .clearAndSetSemantics {
                heading()
                contentDescription = dayHeading(date, if (isToday) "Today" else null)
                // A full column has no empty space to reach, so TalkBack adds from here.
                if (onAdd != null) customActions = listOf(CustomAccessibilityAction(addEventOn(date)) { onAdd(); true })
            },
    ) {
        Text(
            if (isToday) "Today" else date.format(WEEKDAY),
            style = CalendarType.strong14,
            color = if (isToday) c.accent else c.mute,
            modifier = Modifier.alignByBaseline(),
        )
        Spacer(Modifier.width(CalendarDimens.weekDayDateGap))
        if (monthLabel != null) {
            Text(
                monthLabel,
                style = CalendarType.monthLabel,
                color = c.accent,
                maxLines = 1,
                softWrap = false,
                modifier = Modifier
                    .layout { measurable, constraints ->
                        val label = measurable.measure(constraints.copy(minWidth = 0, maxWidth = Constraints.Infinity))
                        layout(0, 0) { label.place(0, -(CalendarDimens.weekColumnPaddingTop - CalendarDimens.monthLabelTop).roundToPx()) }
                    }
                    .testTag("week_month_label"),
            )
        }
        Text(date.dayOfMonth.toString(), style = CalendarType.weekDate, color = c.ink, modifier = Modifier.alignByBaseline())
    }
}

/** [laneHeight]: how far the lane over the columns reaches into this one; its chips start a gap below that. */
@Composable
private fun DayColumn(day: DayUi, isToday: Boolean, monthLabel: String?, laneHeight: Dp, onOpen: (EventRef) -> Unit, onAdd: (() -> Unit)?, modifier: Modifier) {
    val c = Culvery.colors
    val chips = rememberLazyListState()
    val currentOnAdd = rememberUpdatedState(onAdd)
    HhCard(
        modifier = modifier
            .testTag("week_day_${day.date}")
            .then(if (isToday) Modifier.todayRing(c.accent) else Modifier),
        radius = CalendarDimens.weekColumnRadius,
        padding = PaddingValues(
            start = CalendarDimens.weekColumnPaddingSide,
            top = CalendarDimens.weekColumnPaddingTop,
            end = CalendarDimens.weekColumnPaddingSide,
            bottom = CalendarDimens.weekColumnPaddingSide,
        ),
    ) {
        DayHeader(day.date, isToday, onAdd, monthLabel)
        Spacer(Modifier.height(CalendarDimens.columnGap))
        var pressed by remember { mutableStateOf(false) }
        val band = if (laneHeight > 0.dp) laneHeight + CalendarDimens.columnGap else 0.dp
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
                            detectTapGestures(
                                onPress = { press ->
                                    if (chips.isBelowChips(press.y)) {
                                        pressed = true
                                        try {
                                            tryAwaitRelease()
                                        } finally {
                                            pressed = false
                                        }
                                    }
                                },
                                onTap = { tap -> if (chips.isBelowChips(tap.y)) currentOnAdd.value?.invoke() },
                            )
                        }
                    },
                ),
        ) {
            LazyColumn(
                state = chips,
                contentPadding = PaddingValues(top = band),
                verticalArrangement = Arrangement.spacedBy(CalendarDimens.columnGap),
                // Chips scrolled up into the lane's band aren't drawn behind its see-through bars.
                modifier = Modifier.fillMaxSize().drawWithContent { clipRect(top = band.toPx()) { this@drawWithContent.drawContent() } },
            ) {
                items(day.events, key = { it.ref.listKey }) { EventChip(it, day.date, onOpen) }
            }
            if (onAdd != null) EmptySpace(day.date, chips, pressed, onAdd)
            if (band > 0.dp) LaneBand(band)
        }
    }
}

/**
 * Takes the lane band's touches from the chips scrolled up under it, without consuming them, so the column's add
 * gesture still sees a tap there.
 */
@Composable
private fun LaneBand(height: Dp) {
    Box(
        Modifier.fillMaxWidth().height(height).pointerInput(Unit) {
            awaitPointerEventScope { while (true) awaitPointerEvent() }
        },
    )
}

/** Whether [y], in the list's coordinates, is past the last visible chip; anywhere is, with no chips (the lane's room too). */
private fun LazyListState.isBelowChips(y: Float): Boolean = chipsBottom()?.let { y > it } ?: true

/** The last visible chip's bottom edge in the list's coordinates, past its top padding; null with no chips. */
private fun LazyListState.chipsBottom(): Int? =
    layoutInfo.visibleItemsInfo.lastOrNull()?.let { it.offset + it.size - layoutInfo.viewportStartOffset }

/**
 * The column's empty space as one add target (9 §4.7), from the last chip's bottom to the foot: the dashed ghost while
 * [pressed], and TalkBack's node. It takes no touches, so the column's gesture sees them.
 */
@Composable
private fun EmptySpace(date: LocalDate, chips: LazyListState, pressed: Boolean, onAdd: () -> Unit) {
    val chipsBottom by remember(chips) { derivedStateOf { chips.chipsBottom() } }
    // With no chips the space starts below the list's top padding, the lane's room.
    val top by remember(chips) { derivedStateOf { chipsBottom ?: -chips.layoutInfo.viewportStartOffset } }
    Box(
        Modifier
            .fillMaxSize()
            .layout { measurable, constraints ->
                val free = (constraints.maxHeight - top).coerceAtLeast(0)
                val placeable = measurable.measure(constraints.copy(minHeight = free, maxHeight = free))
                layout(constraints.maxWidth, constraints.maxHeight) { placeable.place(0, top) }
            }
            .semantics {
                testTag = "week_add_$date"
                role = Role.Button
                contentDescription = addEventOn(date)
                onClick { onAdd(); true }
            },
    ) {
        if (pressed) PressedGhost(date, hasChipsAbove = chipsBottom != null)
    }
}

@Composable
private fun PressedGhost(date: LocalDate, hasChipsAbove: Boolean) {
    val accent = Culvery.colors.accent
    Box(
        contentAlignment = Alignment.Center,
        modifier = Modifier
            .fillMaxSize()
            .padding(top = if (hasChipsAbove) CalendarDimens.columnGap else 0.dp)
            .drawBehind {
                val stroke = CalendarDimens.ghostStroke.toPx()
                val dashes = floatArrayOf(CalendarDimens.ghostDash.toPx(), CalendarDimens.ghostGap.toPx())
                drawRoundRect(
                    accent,
                    topLeft = Offset(stroke / 2, stroke / 2),
                    size = Size(size.width - stroke, size.height - stroke),
                    cornerRadius = CornerRadius(CalendarDimens.ghostRadius.toPx()),
                    style = Stroke(stroke, pathEffect = PathEffect.dashPathEffect(dashes)),
                )
            },
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(CalendarDimens.ghostIconGap)) {
            HhIcon(Icons.ADD, size = CalendarDimens.ghostIcon, tint = accent)
            Text(addOn(date), style = CalendarType.strong14, color = accent)
        }
    }
}

/** A ring drawn over the column, inside its edge, so the column keeps its size (9 §4.7). */
private fun Modifier.todayRing(color: Color): Modifier = drawWithContent {
    drawContent()
    val width = CalendarDimens.todayRingWidth.toPx()
    val inset = CalendarDimens.todayRingInset.toPx() + width / 2
    drawRoundRect(
        color,
        topLeft = Offset(inset, inset),
        size = Size(size.width - 2 * inset, size.height - 2 * inset),
        cornerRadius = CornerRadius(CalendarDimens.weekColumnRadius.toPx() - inset),
        style = Stroke(width),
    )
}

/** A day's event chip: the person's tint, or for a shared event the neutral surface with a striped edge and both dots (9 §3.3). A null [onOpen] leaves it unclickable. */
@Composable
internal fun EventChip(event: EventUi, day: LocalDate, onOpen: ((EventRef) -> Unit)?) {
    val c = Culvery.colors
    val shared = event.shared
    Row(
        Modifier
            .fillMaxWidth()
            .height(IntrinsicSize.Min)
            .clip(RoundedCornerShape(CalendarDimens.chipRadius))
            .background(if (shared) c.surf2 else Color(event.people.first().color).copy(alpha = HhAlpha.tint(c)))
            .semantics(mergeDescendants = true) { contentDescription = eventOnDay(event, day) }
            .then(if (onOpen != null) Modifier.hhClickable({ onOpen(event.ref) }, label = OPEN_EVENT_DETAILS) else Modifier),
    ) {
        if (shared) StripedBar(event.people, width = CalendarDimens.sharedChipEdge, radius = 0.dp)
        Column(
            Modifier
                .weight(1f)
                .padding(
                    start = if (shared) CalendarDimens.sharedChipPaddingStart - CalendarDimens.sharedChipEdge else CalendarDimens.chipPaddingH,
                    end = CalendarDimens.chipPaddingH,
                    top = CalendarDimens.chipPaddingV,
                    bottom = CalendarDimens.chipPaddingV,
                ),
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(CalendarDimens.chipDotGap),
                modifier = Modifier.shownNotSpoken(),
            ) {
                if (shared) {
                    PersonDots(event.people, CalendarDimens.sharedDot, ring = c.surf2, dotTag = "chip_dot", modifier = Modifier.testTag("chip_dots"))
                } else {
                    Box(Modifier.testTag("chip_dot").size(CalendarDimens.chipDot).clip(CircleShape).background(Color(event.people.first().color)))
                }
                // One line: the who shortens first, then a long name is cut before the time (4d-1 Decision 2, 9c §5).
                ShorteningText(
                    whoLineForms(event.people).map { "${event.startLabel} · $it" },
                    style = CalendarType.chipTime,
                    color = c.mute,
                    modifier = Modifier.weight(1f).testTag("chip_who"),
                )
                EventBadges(event, CalendarDimens.chipBadge)
            }
            Spacer(Modifier.height(CalendarDimens.chipTitleTop))
            Text(
                event.title, style = CalendarType.chipTitle, color = c.ink, maxLines = 2, overflow = TextOverflow.Ellipsis,
                modifier = Modifier.shownNotSpoken(),
            )
        }
    }
}

/** After this long without a touch, a later week goes back to this week (4c §6.6). */
internal const val BACK_TO_THIS_WEEK_MS = 120_000L

/** Which week the Calendar tab shows (4c D10): [weeks] after this one, 0 to [MAX_WEEKS_AHEAD]. */
@Stable
internal class WeekShown {
    var weeks by mutableIntStateOf(0)
        private set

    fun show(weeks: Int) {
        this.weeks = weeks.coerceIn(0, MAX_WEEKS_AHEAD)
    }
}

/**
 * A new [WeekShown] each [today], so the view is back on this week at midnight. A later week goes back after
 * [BACK_TO_THIS_WEEK_MS] without a touch anywhere, a sheet's included; never while TalkBack
 * is on, where it would move the page under a reader (4d-2 §6.4).
 */
@Composable
internal fun rememberWeekShown(today: LocalDate): WeekShown {
    val shown = remember(today) { WeekShown() }
    val activity = LocalUserActivity.current
    val exploring by activity.exploring.collectAsState()
    // Collecting inside the effect keeps a touch from recomposing the host.
    LaunchedEffect(shown, shown.weeks, exploring) {
        if (shown.weeks > 0 && !exploring) {
            activity.touches.collectLatest {
                delay(activity.timeout(BACK_TO_THIS_WEEK_MS))
                shown.show(0)
            }
        }
    }
    return shown
}

/** One line showing the first of [forms] that fits the width it is given; only the last may end in an ellipsis. */
@Composable
private fun ShorteningText(forms: List<String>, style: TextStyle, color: Color, modifier: Modifier = Modifier) {
    Layout(
        content = {
            forms.forEachIndexed { i, form ->
                Text(
                    form, style = style, color = color, maxLines = 1,
                    overflow = if (i == forms.lastIndex) TextOverflow.Ellipsis else TextOverflow.Clip,
                )
            }
        },
        modifier = modifier,
    ) { measurables, constraints ->
        val fitting = measurables.firstOrNull { it.maxIntrinsicWidth(Constraints.Infinity) <= constraints.maxWidth }
        val placeable = (fitting ?: measurables.last()).measure(constraints.copy(minWidth = 0))
        layout(placeable.width.coerceAtLeast(constraints.minWidth), placeable.height) { placeable.placeRelative(0, 0) }
    }
}
