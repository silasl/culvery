package uk.co.siland.culvery.capability.calendar.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import java.time.LocalDate
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collectLatest
import uk.co.siland.culvery.capability.calendar.DayUi
import uk.co.siland.culvery.capability.calendar.EventRef
import uk.co.siland.culvery.capability.calendar.EventUi
import uk.co.siland.culvery.capability.calendar.MAX_WEEKS_AHEAD
import uk.co.siland.culvery.capability.calendar.SyncStatusUi
import uk.co.siland.culvery.capability.calendar.WEEKDAY
import uk.co.siland.culvery.capability.calendar.WeekUi
import uk.co.siland.culvery.capability.calendar.isStaleAt
import uk.co.siland.culvery.capability.calendar.weekLine
import uk.co.siland.culvery.capability.calendar.weekTitle
import uk.co.siland.culvery.core.household.Person
import uk.co.siland.culvery.core.plugin.LocalUserActivity
import uk.co.siland.culvery.core.ui.ButtonSurface
import uk.co.siland.culvery.core.ui.ButtonTone
import uk.co.siland.culvery.core.ui.Culvery
import uk.co.siland.culvery.core.ui.HhAlpha
import uk.co.siland.culvery.core.ui.HhButton
import uk.co.siland.culvery.core.ui.HhCard
import uk.co.siland.culvery.core.ui.HhIcon
import uk.co.siland.culvery.core.ui.HhType
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
 * Hand-off §2 and §7: seven days, person-coloured chips and the sync state. ‹ › step a week at a time, from this week
 * (today and six days) to [MAX_WEEKS_AHEAD] weeks on, inside the synced window (4c D10), through [onWeeksAhead]; This
 * week jumps back. With [onAdd] (there is a writable master calendar), Add event sits right of the legend and adds on
 * the first day shown, and a tap on the space below a column's chips, or on its hint, adds on that column's day; chip
 * taps still open their event. Without it, the legend keeps a 24 dp gap to the right edge. The reconnect chip runs
 * [onReconnect] (3a design §4.3). While the week is out of date, Update now runs [onUpdateNow] and the line says
 * "Updating…" until the sync lands or [UPDATING_SHOWN_MS] passes (4e).
 */
@Composable
fun WeekView(
    state: WeekViewState,
    modifier: Modifier = Modifier,
    onOpen: (EventRef) -> Unit = {},
    onAdd: ((LocalDate) -> Unit)? = null,
    onWeeksAhead: (Int) -> Unit = {},
    onReconnect: () -> Unit = {},
    onUpdateNow: () -> Unit = {},
) {
    val c = Culvery.colors
    // Reset by a sync landing (a new age), or after a while if it failed and the age stayed the same.
    var updating by remember(state.sync.lastSyncMillis) { mutableStateOf(false) }
    if (updating) LaunchedEffect(Unit) {
        delay(UPDATING_SHOWN_MS)
        updating = false
    }
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
                            RoundButton(Icons.CHEVRON_LEFT, "Earlier week", "week_earlier", on = ButtonSurface.Bg, enabled = state.weeksAhead > 0) {
                                onWeeksAhead(state.weeksAhead - 1)
                            }
                            RoundButton(Icons.CHEVRON_RIGHT, "Later week", "week_later", on = ButtonSurface.Bg, enabled = state.weeksAhead < MAX_WEEKS_AHEAD) {
                                onWeeksAhead(state.weeksAhead + 1)
                            }
                        }
                        Spacer(Modifier.width(CalendarDimens.weekTitleGap))
                        Text(
                            weekTitle(state.weeksAhead),
                            style = CalendarType.weekTitle,
                            color = c.ink,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f, fill = false).testTag("week_title").semantics {
                                heading()
                                liveRegion = LiveRegionMode.Polite
                            },
                        )
                        if (state.weeksAhead > 0) {
                            Spacer(Modifier.width(CalendarDimens.weekTitleGap))
                            HhButton("This week", { onWeeksAhead(0) }, Modifier.testTag("week_this_week"), on = ButtonSurface.Bg)
                        }
                    }
                    Spacer(Modifier.height(CalendarDimens.subtitleTop))
                    val stale = state.sync.isStaleAt(state.nowMillis)
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(CalendarDimens.staleIconGap)) {
                        if (stale && !updating) HhIcon(Icons.SYNC_PROBLEM, size = CalendarDimens.staleIcon, tint = c.danger, modifier = Modifier.testTag("week_out_of_date"))
                        Text(
                            if (updating) UPDATING else weekLine(state.sync, state.nowMillis),
                            style = CalendarType.subtitle,
                            color = if (stale && !updating) c.danger else c.mute,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f, fill = false).testTag("week_subtitle").semantics { liveRegion = LiveRegionMode.Polite },
                        )
                        if (stale && !updating) {
                            HhButton(
                                UPDATE_NOW,
                                {
                                    updating = true
                                    onUpdateNow()
                                },
                                Modifier.testTag("week_update_now"),
                                on = ButtonSurface.Bg,
                            )
                        }
                    }
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
                    if (onAdd != null) {
                        HhButton(
                            "Add event", { onAdd(state.week.start) }, Modifier.testTag("week_add_event"),
                            tone = ButtonTone.Primary, icon = Icons.ADD, on = ButtonSurface.Bg,
                        )
                    }
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
        Row(
            Modifier
                .padding(start = inset, end = inset, bottom = inset)
                .clearAndSetSemantics {
                    heading()
                    contentDescription = dayHeading(day.date, if (isToday) "Today" else null)
                },
        ) {
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
                items(day.events, key = { it.ref.listKey }) { EventChip(it, day.date, onOpen) }
            }
        }
        if (onAdd != null) AddHint(day.date, onAdd)
    }
}

/** Hand-off §7: an `add` in `mute` at the foot of each column, in a tap area at least 48 dp tall, named with its day. */
@Composable
private fun AddHint(date: LocalDate, onAdd: () -> Unit) {
    Box(
        contentAlignment = Alignment.BottomCenter,
        modifier = Modifier
            .testTag("week_add_$date")
            .fillMaxWidth()
            .heightIn(min = CalendarDimens.addHintMinHeight)
            .semantics { contentDescription = addEventOn(date) }
            .hhClickable(onAdd),
    ) {
        HhIcon(Icons.ADD, size = CalendarDimens.addHintIcon, tint = Culvery.colors.mute)
    }
}

@Composable
private fun EventChip(event: EventUi, day: LocalDate, onOpen: (EventRef) -> Unit) {
    val c = Culvery.colors
    Column(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(CalendarDimens.chipRadius))
            .background(Color(event.person.color).copy(alpha = HhAlpha.tint(c)))
            .semantics(mergeDescendants = true) { contentDescription = eventOnDay(event, day) }
            .hhClickable({ onOpen(event.ref) }, label = OPEN_EVENT_DETAILS)
            .padding(horizontal = CalendarDimens.chipPaddingH, vertical = CalendarDimens.chipPaddingV),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.shownNotSpoken()) {
            // One line, so a long name is cut before the time (4d-1 Decision 2).
            Text(
                "${event.startLabel} · ${event.person.name}",
                style = CalendarType.chipTime,
                color = c.mute,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f),
            )
            EventBadges(event, CalendarDimens.chipBadge)
        }
        Spacer(Modifier.height(CalendarDimens.chipTitleTop))
        Text(
            event.title, style = CalendarType.chipTitle, color = c.ink, maxLines = 3, overflow = TextOverflow.Ellipsis,
            modifier = Modifier.shownNotSpoken(),
        )
    }
}

@Composable
private fun Legend(people: List<Person>, modifier: Modifier = Modifier) {
    val c = Culvery.colors
    Row(
        horizontalArrangement = Arrangement.spacedBy(CalendarDimens.legendGap),
        verticalAlignment = Alignment.CenterVertically,
        modifier = modifier.clearAndSetSemantics { contentDescription = legendName(people) },
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

/** One chip for every connection that needs signing in again: a `dangerSoft` pill, 48 dp, radius 24. */
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
            .hhClickable(onClick, label = RECONNECT_ACTION)
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

/** How long "Updating…" stands in for an out-of-date line before a failed sync shows it again. */
internal const val UPDATING_SHOWN_MS = 30_000L
private const val UPDATE_NOW = "Update now"
private const val UPDATING = "Updating…"
