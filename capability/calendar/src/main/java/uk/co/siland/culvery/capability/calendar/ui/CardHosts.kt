package uk.co.siland.culvery.capability.calendar.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import java.time.LocalDate
import uk.co.siland.culvery.capability.calendar.CalendarEditor
import uk.co.siland.culvery.capability.calendar.CalendarRepository
import uk.co.siland.culvery.capability.calendar.DayUi
import uk.co.siland.culvery.capability.calendar.EventUi
import uk.co.siland.culvery.capability.calendar.SyncStatusUi
import uk.co.siland.culvery.capability.calendar.WeekUi
import uk.co.siland.culvery.core.plugin.Connection

/** Today's rows open their event; its + adds one today, when there is a writable master calendar to add to. */
@Composable
internal fun TodayCardHost(repo: CalendarRepository, editor: CalendarEditor, today: LocalDate) {
    val open = rememberEventOpener(repo, editor, today)
    val add = rememberEventAdder(repo, editor, today)
    val events: List<EventUi>? by remember(today) { repo.day(today) }.collectAsState(initial = null)
    TodayCard(events, onOpen = open, onAdd = add?.let { a -> { a(null) } })
}

@Composable
internal fun ComingUpCardHost(repo: CalendarRepository, today: LocalDate) {
    val days: List<DayUi>? by remember(today) { repo.days(today.plusDays(1), 3) }.collectAsState(initial = null)
    ComingUpCard(days)
}

/**
 * The week shown is today plus six days, or a later one the family stepped to (4c D10); [today] moves at midnight, so
 * the week rolls with it and is back on this week. Shows nothing until both flows load.
 * Add event and the column taps show only when there is a writable master calendar to add to. The reconnect chip
 * reconnects the first connection that needs it ([onReconnect]).
 */
@Composable
internal fun WeekViewHost(
    repo: CalendarRepository,
    editor: CalendarEditor,
    today: LocalDate,
    nowMillis: Long,
    onReconnect: (Connection) -> Unit,
) {
    val open = rememberEventOpener(repo, editor, today)
    val add = rememberEventAdder(repo, editor, today)
    val shown = rememberWeekShown(today)
    val start = today.plusWeeks(shown.weeks.toLong())
    // produceState keeps the week on screen while the next one loads, so a step doesn't blank the view.
    val week: WeekUi? by produceState<WeekUi?>(null, repo, start) { repo.week(start).collect { value = it } }
    val sync: SyncStatusUi? by repo.syncStatus.collectAsState(initial = null)
    val w = week ?: return
    val s = sync ?: return
    WeekView(
        WeekViewState(w, today, s, nowMillis, shown.weeks),
        modifier = Modifier.onEveryTouch(shown::touched),
        onOpen = open,
        onAdd = add,
        onWeeksAhead = shown::show,
        onReconnect = { s.reconnect?.let(onReconnect) },
    )
}
