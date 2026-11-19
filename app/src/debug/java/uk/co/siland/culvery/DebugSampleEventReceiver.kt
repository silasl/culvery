package uk.co.siland.culvery

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.android.EntryPointAccessors
import dagger.hilt.components.SingletonComponent
import java.time.Duration
import java.time.LocalDate
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import uk.co.siland.culvery.capability.calendar.CalendarSetup
import uk.co.siland.culvery.capability.calendar.DateRange
import uk.co.siland.culvery.capability.calendar.EventDraft
import uk.co.siland.culvery.capability.calendar.EventField
import uk.co.siland.culvery.capability.calendar.EventTime
import uk.co.siland.culvery.core.household.HouseholdZone
import uk.co.siland.culvery.core.plugin.Connection
import uk.co.siland.culvery.provider.calendar_fake.FakeCalendarProvider

// Debug only: delete or move (+60 min) a Family sample event by title, as a phone would: `adb shell "am broadcast -n uk.co.siland.culvery/.DebugSampleEventReceiver -a uk.co.siland.culvery.debug.SAMPLE_EVENT --es action delete --es title '<title>'"` (`--es action move` to move it).
class DebugSampleEventReceiver : BroadcastReceiver() {
    @EntryPoint
    @InstallIn(SingletonComponent::class)
    interface Dependencies {
        fun fake(): FakeCalendarProvider

        fun calendarSetup(): CalendarSetup

        fun zone(): HouseholdZone
    }

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != ACTION) return
        val action = intent.getStringExtra(EXTRA_ACTION)?.takeIf { it == DELETE || it == MOVE } ?: return
        val title = intent.getStringExtra(EXTRA_TITLE)?.takeIf { it.isNotBlank() } ?: return
        val deps = EntryPointAccessors.fromApplication(context.applicationContext, Dependencies::class.java)
        val pending = goAsync()
        CoroutineScope(Dispatchers.Default).launch {
            try {
                change(deps, action, title)
                deps.calendarSetup().syncSoon()
            } finally {
                pending.finish()
            }
        }
    }

    private suspend fun change(deps: Dependencies, action: String, title: String) {
        val fake = deps.fake()
        val conn = Connection(DEBUG_CONNECTION_ID, FakeCalendarProvider.ID, "Sample calendar", emptyMap())
        val family = FakeCalendarProvider.SOURCES.single { it.id == FakeCalendarProvider.SOURCE_FAMILY }
        val zone = deps.zone().current()
        val today = LocalDate.now(zone)
        val range = DateRange(today, today.plusDays(WINDOW_DAYS), zone)
        val event = fake.sync(conn, family, range, null).upserts.firstOrNull { it.title == title } ?: return
        if (action == DELETE) {
            fake.delete(conn, family, event.remoteId)
            return
        }
        val start = event.start as? EventTime.Timed ?: return
        val end = event.end as? EventTime.Timed ?: return
        val draft = EventDraft(
            event.title, EventTime.Timed(start.instant.plus(HOUR)), EventTime.Timed(end.instant.plus(HOUR)), event.forPerson, event.createdBy,
        )
        fake.update(conn, family, event.remoteId, draft, setOf(EventField.TIMES))
    }

    companion object {
        const val ACTION = "uk.co.siland.culvery.debug.SAMPLE_EVENT"
        const val EXTRA_ACTION = "action"
        const val EXTRA_TITLE = "title"
        const val DELETE = "delete"
        const val MOVE = "move"
        private const val WINDOW_DAYS = 28L
        private val HOUR = Duration.ofMinutes(60)
    }
}
