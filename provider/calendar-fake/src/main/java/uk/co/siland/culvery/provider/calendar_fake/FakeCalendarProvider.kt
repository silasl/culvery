package uk.co.siland.culvery.provider.calendar_fake

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.runtime.Composable
import androidx.compose.ui.unit.dp
import java.time.Clock
import java.time.LocalDate
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton
import uk.co.siland.culvery.capability.calendar.CalendarProvider
import uk.co.siland.culvery.capability.calendar.CalendarSource
import uk.co.siland.culvery.capability.calendar.DateRange
import uk.co.siland.culvery.capability.calendar.SyncCursor
import uk.co.siland.culvery.capability.calendar.SyncResult
import uk.co.siland.culvery.core.plugin.Connection
import uk.co.siland.culvery.core.plugin.Feature
import uk.co.siland.culvery.core.plugin.ProviderDescriptor
import uk.co.siland.culvery.core.ui.HhPillButton

/** Debug-only sample data matching the design hand-off, generated relative to today so it never goes stale. */
@Singleton
class FakeCalendarProvider(private val clock: Clock) : CalendarProvider {
    @Inject constructor() : this(Clock.systemUTC())

    override val descriptor = ProviderDescriptor(ID, "Sample calendar (debug)", "event", setOf(Feature.READ))

    @Volatile private var failNext: Throwable? = null

    /** For contract tests: the next sources() or sync() call throws [error]. */
    fun failNextWith(error: Throwable) {
        failNext = error
    }

    @Composable
    override fun ConnectScreen(existing: Connection?, onConnected: (Connection) -> Unit, onCancel: () -> Unit) {
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            HhPillButton(
                "Connect sample calendar",
                onClick = {
                    val id = existing?.id ?: UUID.randomUUID().toString()
                    onConnected(Connection(id, ID, "Sample calendar", emptyMap()))
                },
                primary = true,
            )
            HhPillButton("Cancel", onClick = onCancel)
        }
    }

    override suspend fun sources(conn: Connection): List<CalendarSource> {
        throwIfFailing()
        return SOURCES
    }

    override suspend fun sync(conn: Connection, source: CalendarSource, range: DateRange, cursor: SyncCursor?): SyncResult {
        throwIfFailing()
        val today = LocalDate.now(clock.withZone(range.zone))
        // The cursor carries the date, so the relative sample data is replaced once a day.
        val current = SyncCursor("v1:$today")
        if (cursor == current) return SyncResult(emptyList(), emptyList(), current, fullReplace = false)
        val events = SampleEvents.forSource(source.id, today, range.zone).filter { range.overlaps(it.start, it.end) }
        return SyncResult(events, emptyList(), current, fullReplace = true)
    }

    private fun throwIfFailing() {
        failNext?.let {
            failNext = null
            throw it
        }
    }

    companion object {
        const val ID = "calendar.fake"
        const val SOURCE_ALEX = "fake-alex"
        const val SOURCE_SAM = "fake-sam"
        const val SOURCE_MIA = "fake-mia"
        const val SOURCE_FAMILY = "fake-family"
        const val SOURCE_SCHOOL = "fake-school"

        val SOURCES = listOf(
            CalendarSource(SOURCE_ALEX, "Alex", writable = false),
            CalendarSource(SOURCE_SAM, "Sam", writable = false),
            CalendarSource(SOURCE_MIA, "Mia", writable = false),
            CalendarSource(SOURCE_FAMILY, "Family", writable = false),
            CalendarSource(SOURCE_SCHOOL, "School terms", writable = false),
        )
    }
}
