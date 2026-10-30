package uk.co.siland.culvery.capability.calendar.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.Placeable
import androidx.compose.ui.layout.SubcomposeLayout
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Constraints
import java.time.LocalDate
import uk.co.siland.culvery.capability.calendar.CALENDAR_TAB_ID
import uk.co.siland.culvery.capability.calendar.DayUi
import uk.co.siland.culvery.capability.calendar.EventRef
import uk.co.siland.culvery.capability.calendar.EventUi
import uk.co.siland.culvery.capability.calendar.dayOf
import uk.co.siland.culvery.capability.calendar.whoLine
import uk.co.siland.culvery.core.plugin.LocalShellNavigator
import uk.co.siland.culvery.core.ui.ButtonSurface
import uk.co.siland.culvery.core.ui.ButtonTokens
import uk.co.siland.culvery.core.ui.ButtonTone
import uk.co.siland.culvery.core.ui.Culvery
import uk.co.siland.culvery.core.ui.HhBottomFade
import uk.co.siland.culvery.core.ui.HhButton
import uk.co.siland.culvery.core.ui.HhCard
import uk.co.siland.culvery.core.ui.HhRoundButton
import uk.co.siland.culvery.core.ui.HhType
import uk.co.siland.culvery.core.ui.Icons
import uk.co.siland.culvery.core.ui.hhClickable
import uk.co.siland.culvery.core.ui.shownNotSpoken

/**
 * Hand-off Home "Today" card, always five rows (9 §4.2): fewer of [events] top up from [tomorrow] under a TOMORROW
 * divider, more scroll behind an "N more today · Open calendar" pill, and rows are measured so a long title or a large
 * font scrolls rather than cuts a row. [events] null while loading: shows nothing rather than a false "Nothing on today".
 * Each row is a button (hand-off §7) that opens its event through [onOpen]. The **+** left of Week adds an event
 * today through [onAdd]; it is absent when there is nowhere to add to (no writable master calendar).
 */
@Composable
fun TodayCard(
    events: List<EventUi>?,
    modifier: Modifier = Modifier,
    onOpen: (EventRef) -> Unit = {},
    onAdd: (() -> Unit)? = null,
    tomorrow: DayUi? = null,
) {
    val todays = events.orEmpty()
    val refs = todays.mapTo(HashSet()) { it.ref }
    // An event spanning midnight is already one of today's rows.
    val candidates = if (events != null && todays.size < TODAY_ROWS) {
        tomorrow?.events.orEmpty().filter { it.ref !in refs }.take(TODAY_ROWS - todays.size)
    } else {
        emptyList()
    }
    HhCard(
        modifier = modifier.fillMaxSize().testTag("calendar_today"),
        radius = CalendarDimens.cardRadius,
        // The foot is the list's: the scroll runs to the card's edge under the fade.
        padding = PaddingValues(start = CalendarDimens.todayPaddingH, end = CalendarDimens.todayPaddingH, top = CalendarDimens.todayPaddingV),
        decoration = Culvery.look?.cardCorner,
    ) {
        TodayBody(events, tomorrow?.copy(events = candidates), onOpen, onAdd)
    }
}

/** The heights fitToday needs, and what they were measured for. */
private class MeasuredParts(val key: Any, val header: Int, val today: List<Int>, val tomorrow: List<Int>, val nothing: Int, val divider: Int)

/**
 * The header and the list, laid out once the rows are measured, because the fit decides whether "See the week" shows.
 * [topUp] holds tomorrow's candidates. Each part is measured in a silent copy and the heights kept; the next pass then
 * runs without the copies, which drops them: a copy measured but never placed is still in the tree TalkBack and tests read.
 */
@Composable
private fun TodayBody(events: List<EventUi>?, topUp: DayUi?, onOpen: (EventRef) -> Unit, onAdd: (() -> Unit)?) {
    val todays = events.orEmpty()
    var kept by remember { mutableStateOf<MeasuredParts?>(null) }
    SubcomposeLayout(Modifier.fillMaxSize()) { constraints ->
        val width = constraints.maxWidth
        val height = constraints.maxHeight
        val row = Constraints.fixedWidth(width)
        fun measure(slot: Any, content: @Composable () -> Unit): Placeable = subcompose(slot, content).single().measure(row)
        fun copy(slot: Any, content: @Composable () -> Unit): Int =
            measure("copy $slot") { Box(Modifier.clearAndSetSemantics {}) { content() } }.height

        val key = listOf(events, topUp, onAdd != null, width, density, fontScale)
        val parts = kept?.takeIf { it.key == key } ?: MeasuredParts(
            key,
            // With "See the week" showing, the taller header, so hiding it never pushes the list down.
            header = copy("header") { TodayHeader(onAdd, seeWeek = true) },
            today = todays.map { e -> copy("today ${e.ref.listKey}") { TodayRow(e, onOpen) } },
            tomorrow = topUp?.let { day -> day.events.map { e -> copy("tomorrow ${e.ref.listKey}") { TomorrowRow(e, day.date, onOpen) } } }.orEmpty(),
            nothing = if (events?.isEmpty() == true) copy("nothing") { NothingOnToday() } else 0,
            divider = if (topUp?.events.isNullOrEmpty()) 0 else copy("divider") { TomorrowDivider() },
        )
        val bodyTop = parts.header + CalendarDimens.todayHeaderGap.roundToPx()
        val fit = fitToday(
            parts.today, parts.tomorrow, parts.nothing, parts.divider,
            gap = CalendarDimens.todayRowGap.roundToPx(),
            available = height - bodyTop - CalendarDimens.todayPaddingV.roundToPx(),
            pillSpace = ButtonTokens.regularHeight.roundToPx(),
        )
        val header = measure("header") { TodayHeader(onAdd, seeWeek = !fit.scroll) }
        val list = if (fit.scroll) {
            val listHeight = (height - bodyTop).coerceAtLeast(0)
            subcompose("scroll") { TodayScroll(todays, fit.more, onOpen) }.single().measure(Constraints.fixed(width, listHeight))
        } else {
            measure("stack") { TodayStack(events, topUp?.copy(events = topUp.events.take(fit.tomorrowShown)), onOpen) }
        }
        layout(width, height) {
            header.place(0, 0)
            list.place(0, bodyTop)
            // Written while placing: a write during the measure that read it runs no second pass.
            if (parts !== kept) kept = parts
        }
    }
}

/** Today's rows, or "Nothing on today", then tomorrow's that fit under TOMORROW. */
@Composable
private fun TodayStack(events: List<EventUi>?, topUp: DayUi?, onOpen: (EventRef) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(CalendarDimens.todayRowGap), modifier = Modifier.fillMaxWidth()) {
        if (events?.isEmpty() == true) NothingOnToday()
        events.orEmpty().forEach { key(it.ref.listKey) { TodayRow(it, onOpen) } }
        if (topUp != null && topUp.events.isNotEmpty()) {
            TomorrowDivider()
            topUp.events.forEach { key(it.ref.listKey) { TomorrowRow(it, topUp.date, onOpen) } }
        }
    }
}

@Composable
private fun TodayHeader(onAdd: (() -> Unit)?, seeWeek: Boolean) {
    val navigator = LocalShellNavigator.current
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(CalendarDimens.todayHeaderButtonGap),
        modifier = Modifier.fillMaxWidth().heightIn(min = ButtonTokens.regularHeight),
    ) {
        Text("Today", style = CalendarType.todayHeader, color = Culvery.colors.ink, modifier = Modifier.weight(1f).semantics { heading() })
        if (onAdd != null) {
            HhRoundButton(
                Icons.ADD, "Add event", onAdd, Modifier.testTag("today_add"),
                tone = ButtonTone.Primary, on = ButtonSurface.Surf, iconSize = CalendarDimens.todayAddIcon,
            )
        }
        if (seeWeek) HhButton("See the week", { navigator.openTab(CALENDAR_TAB_ID) }, on = ButtonSurface.Surf)
    }
}

/** Today's rows scrolling under a fade, with the pill pinned over their foot (9 §4.2, §4.13). */
@Composable
private fun TodayScroll(events: List<EventUi>, more: Int, onOpen: (EventRef) -> Unit) {
    val navigator = LocalShellNavigator.current
    val c = Culvery.colors
    Box(Modifier.fillMaxSize()) {
        LazyColumn(
            verticalArrangement = Arrangement.spacedBy(CalendarDimens.todayRowGap),
            contentPadding = PaddingValues(bottom = CalendarDimens.todayScrollBottom),
            modifier = Modifier.fillMaxSize(),
        ) {
            items(events, key = { it.ref.listKey }) { TodayRow(it, onOpen) }
        }
        HhBottomFade(c.surf, CalendarDimens.todayFade, Modifier.align(Alignment.BottomCenter))
        HhButton(
            moreToday(more),
            { navigator.openTab(CALENDAR_TAB_ID) },
            Modifier
                .align(Alignment.BottomCenter)
                .padding(bottom = CalendarDimens.todayPaddingV)
                .fillMaxWidth()
                .shadow(CalendarDimens.todayPillShadow, RoundedCornerShape(percent = 50)),
            icon = Icons.CALENDAR_MONTH,
            trailingIcon = Icons.ARROW_FORWARD,
            on = ButtonSurface.Surf,
        )
    }
}

@Composable
private fun NothingOnToday() = Text(NOTHING_ON_TODAY, style = CalendarType.nothingToday, color = Culvery.colors.mute)

/** TOMORROW and a rule, spoken as the heading "Tomorrow". */
@Composable
private fun TomorrowDivider() {
    val c = Culvery.colors
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.fillMaxWidth().semantics(mergeDescendants = true) {
            heading()
            contentDescription = TOMORROW
        },
    ) {
        Text(TOMORROW.uppercase(), style = CalendarType.tomorrowDivider, color = c.mute, modifier = Modifier.shownNotSpoken())
        Spacer(Modifier.width(CalendarDimens.tomorrowRuleGap))
        Box(Modifier.weight(1f).height(CalendarDimens.tomorrowRule).background(c.surf3))
    }
}

@Composable
private fun TodayRow(event: EventUi, onOpen: (EventRef) -> Unit) = EventRow(event, todayRowName(event), outlined = false, showDayOf = true, onOpen)

@Composable
private fun TomorrowRow(event: EventUi, date: LocalDate, onOpen: (EventRef) -> Unit) =
    EventRow(event, eventOnDay(event, date), outlined = true, showDayOf = false, onOpen)

/** A today row (`surf2`, two-line title) or, [outlined], tomorrow's (a `surf2` edge, a softer bar, one line). */
@Composable
private fun EventRow(event: EventUi, spoken: String, outlined: Boolean, showDayOf: Boolean, onOpen: (EventRef) -> Unit) {
    val c = Culvery.colors
    val shape = RoundedCornerShape(CalendarDimens.todayRowRadius)
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(IntrinsicSize.Min)
            .clip(shape)
            .then(if (outlined) Modifier.border(CalendarDimens.tomorrowEdge, c.surf2, shape) else Modifier.background(c.surf2))
            .semantics(mergeDescendants = true) { contentDescription = spoken }
            .hhClickable({ onOpen(event.ref) }, label = OPEN_EVENT_DETAILS)
            .padding(
                horizontal = if (outlined) CalendarDimens.tomorrowRowPaddingH else CalendarDimens.todayRowPaddingH,
                vertical = if (outlined) CalendarDimens.tomorrowRowPaddingV else CalendarDimens.todayRowPaddingV,
            ),
    ) {
        val bars = event.people.map { Color(it.color).let { bar -> if (outlined) bar.copy(alpha = CalendarDimens.TOMORROW_BAR_ALPHA) else bar } }
        StripedBar(bars, width = CalendarDimens.todayBarWidth)
        Spacer(Modifier.width(CalendarDimens.todayBarGap))
        Column(Modifier.weight(1f).shownNotSpoken()) {
            Text(
                event.title,
                style = if (outlined) CalendarType.tomorrowTitle else CalendarType.todayTitle,
                color = c.ink,
                maxLines = if (outlined) 1 else 2,
                overflow = TextOverflow.Ellipsis,
            )
            Spacer(Modifier.height(CalendarDimens.todayTimeTop))
            val span = event.span
            val whenLabel = if (showDayOf && span != null) dayOf(span) else event.timeLabel
            Text(
                "$whenLabel · ${whoLine(event.people)}",
                style = if (outlined) CalendarType.tomorrowTime else CalendarType.todayTime,
                color = c.mute,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        EventBadges(event, CalendarDimens.todayBadge, Modifier.align(Alignment.CenterVertically))
    }
}
