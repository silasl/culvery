package uk.co.siland.culvery.provider.calendar_fake

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.runtime.Composable
import androidx.compose.ui.unit.dp
import java.time.Clock
import java.time.LocalDate
import java.time.ZoneId
import java.time.ZoneOffset
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton
import uk.co.siland.culvery.capability.calendar.CalendarProvider
import uk.co.siland.culvery.capability.calendar.CalendarSource
import uk.co.siland.culvery.capability.calendar.CalendarWriter
import uk.co.siland.culvery.capability.calendar.DateRange
import uk.co.siland.culvery.capability.calendar.EVENT_GONE
import uk.co.siland.culvery.capability.calendar.EventDraft
import uk.co.siland.culvery.capability.calendar.EventField
import uk.co.siland.culvery.capability.calendar.RemoteEvent
import uk.co.siland.culvery.capability.calendar.SyncCursor
import uk.co.siland.culvery.capability.calendar.SyncResult
import uk.co.siland.culvery.capability.calendar.UnreachableException
import uk.co.siland.culvery.capability.calendar.WriteRejectedException
import uk.co.siland.culvery.core.plugin.Connection
import uk.co.siland.culvery.core.plugin.Feature
import uk.co.siland.culvery.core.plugin.ProviderDescriptor
import uk.co.siland.culvery.core.ui.HhPillButton
import uk.co.siland.culvery.core.ui.Icons

/** Gap between the ConnectScreen's buttons. */
private val ConnectScreenButtonGap = 12.dp

private const val OFFLINE_MESSAGE = "Sample calendar is offline"

/**
 * Debug-only sample data matching the design hand-off, generated relative to today so it never goes stale.
 * Writes to the "Family calendar" are kept in memory, so the fake forgets them when the app restarts.
 */
@Singleton
class FakeCalendarProvider(private val clock: Clock) : CalendarProvider, CalendarWriter {
    @Inject constructor() : this(Clock.systemUTC())

    override val descriptor =
        ProviderDescriptor(ID, "Sample calendar (debug)", Icons.EVENT, setOf(Feature.READ, Feature.WRITE), userConnectable = false)
    override val providerId = ID

    @Volatile private var failNext: Throwable? = null
    @Volatile private var offline = false
    // The last sync's zone, so an update can rebuild the sample it changes.
    @Volatile private var zone: ZoneId = ZoneOffset.UTC

    private val lock = Any()
    // Everything below is guarded by lock. Every change bumps version, so the next sync is a full replace.
    private var version = 0
    private var idsByName: Map<String, String> = emptyMap()
    private val created = linkedMapOf<String, RemoteEvent>()
    private val changed = mutableMapOf<String, RemoteEvent>()
    private val deleted = mutableSetOf<String>()
    private var rejectNext: String? = null
    private var unreachableNext = false

    /** For contract tests: the next sources() or sync() call throws [error]. */
    fun failNextWith(error: Throwable) {
        failNext = error
    }

    /** While offline, every read and write throws UnreachableException (the walkthrough's DebugOfflineReceiver). */
    fun setOffline(value: Boolean) {
        offline = value
    }

    /** The next create, update or delete throws WriteRejectedException([message]). */
    fun rejectNextWrite(message: String) = synchronized(lock) { rejectNext = message }

    /** The next create, update or delete throws UnreachableException, as if the tablet were offline. */
    fun unreachableNextWrite() = synchronized(lock) { unreachableNext = true }

    /** Debug seed only: the household's ids for the sample people, by name ("Alex", "Sam", "Mia"). */
    fun tagSamples(idsByName: Map<String, String>) = synchronized(lock) {
        if (idsByName != this.idsByName) {
            this.idsByName = idsByName
            version++
        }
    }

    @Composable
    override fun ConnectScreen(existing: Connection?, onConnected: (Connection) -> Unit, onCancel: () -> Unit) {
        Row(horizontalArrangement = Arrangement.spacedBy(ConnectScreenButtonGap)) {
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
        zone = range.zone
        val today = LocalDate.now(clock.withZone(range.zone))
        val (current, events) = synchronized(lock) {
            // The cursor carries the date and the write version: the sample data rolls over daily, and writes show.
            val current = SyncCursor("v2:$today:$version")
            if (cursor == current) return SyncResult(emptyList(), emptyList(), current, fullReplace = false)
            val samples = SampleEvents.forSource(source.id, today, range.zone, idsByName)
                .filterNot { it.remoteId in deleted }
                .map { changed[it.remoteId] ?: it }
            val written = if (source.id == SOURCE_FAMILY) created.values.toList() else emptyList()
            current to samples + written
        }
        return SyncResult(events.filter { range.overlaps(it.start, it.end) }, emptyList(), current, fullReplace = true)
    }

    override suspend fun create(conn: Connection, source: CalendarSource, draft: EventDraft, clientKey: String): RemoteEvent =
        write(source) {
            // A key whose event was deleted is refused, never made again (CalendarWriter contract).
            if (clientKey in deleted) throw WriteRejectedException(EVENT_GONE)
            // The key is the event's id, so a retried create returns the event it made (CalendarWriter contract).
            created.getOrPut(clientKey) {
                RemoteEvent(clientKey, draft.title, draft.start, draft.end, recurring = false, draft.forPerson, draft.createdBy)
            }
        }

    override suspend fun update(
        conn: Connection,
        source: CalendarSource,
        remoteId: String,
        draft: EventDraft,
        fields: Set<EventField>,
    ): RemoteEvent = write(source) {
        val current = current(remoteId) ?: throw WriteRejectedException(EVENT_GONE)
        if (current.recurring) throw WriteRejectedException("Repeating events can't be changed here")
        val event = current.copy(
            title = if (EventField.TITLE in fields) draft.title else current.title,
            start = if (EventField.TIMES in fields) draft.start else current.start,
            end = if (EventField.TIMES in fields) draft.end else current.end,
            forPerson = if (EventField.FOR_PERSON in fields) draft.forPerson else current.forPerson,
        )
        if (remoteId in created) created[remoteId] = event else changed[remoteId] = event
        event
    }

    override suspend fun delete(conn: Connection, source: CalendarSource, remoteId: String) = write(source) {
        // Deleting something already gone succeeds (CalendarWriter contract).
        created.remove(remoteId)
        changed.remove(remoteId)
        deleted += remoteId
        Unit
    }

    override suspend fun find(conn: Connection, source: CalendarSource, remoteId: String): RemoteEvent? = synchronized(lock) {
        if (offline) throw UnreachableException(OFFLINE_MESSAGE)
        if (unreachableNext) {
            unreachableNext = false
            throw UnreachableException(OFFLINE_MESSAGE)
        }
        if (source.id == SOURCE_FAMILY) current(remoteId) else null
    }

    /** The Family calendar's event as it is now; null once deleted. Called under [lock]. */
    private fun current(remoteId: String): RemoteEvent? = when (remoteId) {
        in deleted -> null
        in created -> created[remoteId]
        in changed -> changed[remoteId]
        else -> SampleEvents.forSource(SOURCE_FAMILY, LocalDate.now(clock.withZone(zone)), zone, idsByName)
            .firstOrNull { it.remoteId == remoteId }
    }

    private inline fun <T> write(source: CalendarSource, block: () -> T): T = synchronized(lock) {
        if (offline) throw UnreachableException(OFFLINE_MESSAGE)
        if (unreachableNext) {
            unreachableNext = false
            throw UnreachableException(OFFLINE_MESSAGE)
        }
        rejectNext?.let {
            rejectNext = null
            throw WriteRejectedException(it)
        }
        if (source.id != SOURCE_FAMILY) throw WriteRejectedException("${source.name} can't be changed here")
        block().also { version++ }
    }

    private fun throwIfFailing() {
        if (offline) throw UnreachableException(OFFLINE_MESSAGE)
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
            CalendarSource(SOURCE_FAMILY, "Family calendar", writable = true, primary = true),
            CalendarSource(SOURCE_SCHOOL, "School terms", writable = false),
        )
    }
}
