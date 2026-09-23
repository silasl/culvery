package uk.co.siland.culvery.capability.calendar.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import java.time.LocalDate
import uk.co.siland.culvery.capability.calendar.CalendarRepository
import uk.co.siland.culvery.capability.calendar.DayUi
import uk.co.siland.culvery.capability.calendar.EventUi
import uk.co.siland.culvery.capability.calendar.SyncStatusUi
import uk.co.siland.culvery.capability.calendar.WeekUi

@Composable
internal fun TodayCardHost(repo: CalendarRepository, today: LocalDate) {
    val events: List<EventUi>? by remember(today) { repo.day(today) }.collectAsState(initial = null)
    TodayCard(events)
}

@Composable
internal fun ComingUpCardHost(repo: CalendarRepository, today: LocalDate) {
    val days: List<DayUi>? by remember(today) { repo.days(today.plusDays(1), 3) }.collectAsState(initial = null)
    ComingUpCard(days)
}

/** Today plus six days; [today] moves at midnight, so the week rolls with it. Shows nothing until both flows load. */
@Composable
internal fun WeekViewHost(repo: CalendarRepository, today: LocalDate, nowMillis: Long) {
    val week: WeekUi? by remember(today) { repo.week(today) }.collectAsState(initial = null)
    val sync: SyncStatusUi? by repo.syncStatus.collectAsState(initial = null)
    val w = week ?: return
    val s = sync ?: return
    WeekView(WeekViewState(w, today, s, nowMillis))
}
