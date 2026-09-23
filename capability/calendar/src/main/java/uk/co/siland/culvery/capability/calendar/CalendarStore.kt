package uk.co.siland.culvery.capability.calendar

import androidx.room.withTransaction
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import org.json.JSONObject
import uk.co.siland.culvery.capability.calendar.db.CalendarDatabase
import uk.co.siland.culvery.capability.calendar.db.ConnectionEntity
import uk.co.siland.culvery.capability.calendar.db.EventEntity
import uk.co.siland.culvery.capability.calendar.db.EventRow
import uk.co.siland.culvery.capability.calendar.db.SourceEntity
import uk.co.siland.culvery.capability.calendar.db.SyncStateEntity
import uk.co.siland.culvery.core.household.PersonId
import uk.co.siland.culvery.core.plugin.Connection
import uk.co.siland.culvery.core.plugin.ConnectionHealth

/** The only writer of calendar.db. UI reads go through CalendarRepository. */
@Singleton
class CalendarStore @Inject constructor(private val db: CalendarDatabase) {
    private val dao = db.calendarDao()

    fun connections(): Flow<List<StoredConnection>> = dao.connections().map { rows -> rows.map { it.toStored() } }

    fun connectionIds(): Flow<List<String>> = dao.connectionIds().distinctUntilChanged()

    suspend fun connectionsNow(): List<StoredConnection> = dao.allConnections().map { it.toStored() }

    /**
     * One transaction, so the sync loop never sees a connection without its sources. A source missing from
     * [mapping] shows as Family. Source editing and pruning come with Plan 4's Connections settings.
     */
    suspend fun addConnection(connection: Connection, sources: List<CalendarSource>, mapping: Map<String, SourceMapping>) =
        db.withTransaction {
            dao.insertConnection(
                ConnectionEntity(
                    id = connection.id,
                    providerId = connection.providerId,
                    label = connection.label,
                    configJson = encodeConfig(connection.config),
                    health = ConnectionHealth.Ok.code(),
                    healthMessage = null,
                    lastSyncMillis = null,
                ),
            )
            dao.insertSources(
                sources.map { s ->
                    val m = mapping[s.id] ?: SourceMapping.Default
                    SourceEntity(connection.id, s.id, s.name, s.writable, m.visible, m.person.value)
                },
            )
        }

    suspend fun visibleSourcesFor(connectionId: String): List<StoredSource> =
        dao.sources(connectionId).filter { it.visible }.map { it.toStored() }

    suspend fun setHealth(connectionId: String, health: ConnectionHealth) =
        dao.setHealth(connectionId, health.code(), (health as? ConnectionHealth.Error)?.message)

    suspend fun markSynced(connectionId: String, atMillis: Long) = dao.markSynced(connectionId, atMillis)

    /**
     * Null when nothing is stored or the cursor belongs to a different window or zone, forcing a full resync.
     * A zone change must resync: all-day events are stored at midnight in the zone they were synced in.
     */
    suspend fun cursor(connectionId: String, sourceId: String, range: DateRange): SyncCursor? =
        dao.syncState(connectionId, sourceId)
            ?.takeIf { it.rangeStart == range.cursorKey() }
            ?.cursor
            ?.let(::SyncCursor)

    suspend fun applySync(connectionId: String, sourceId: String, range: DateRange, result: SyncResult) =
        db.withTransaction {
            if (result.fullReplace) {
                dao.deleteEventsForSource(connectionId, sourceId)
            } else {
                result.removedIds.chunked(REMOVE_CHUNK).forEach { dao.deleteEvents(connectionId, sourceId, it) }
            }
            dao.upsertEvents(result.upserts.map { it.toEntity(connectionId, sourceId, range.zone) })
            dao.upsertSyncState(SyncStateEntity(connectionId, sourceId, result.cursor?.value, range.cursorKey()))
        }

    fun eventsBetween(startMillis: Long, endMillis: Long): Flow<List<StoredEvent>> =
        dao.eventsBetween(startMillis, endMillis).map { rows -> rows.map { it.toStored() } }

    private companion object {
        // SQLite allows 999 bound variables per statement on older Android versions.
        const val REMOVE_CHUNK = 500
    }
}

private fun DateRange.cursorKey(): String = "$start|${zone.id}"

internal fun ConnectionHealth.code(): String = when (this) {
    ConnectionHealth.Ok -> "OK"
    ConnectionHealth.Unreachable -> "UNREACHABLE"
    ConnectionHealth.NeedsSignIn -> "NEEDS_SIGN_IN"
    is ConnectionHealth.Error -> "ERROR"
}

internal fun healthOf(code: String, message: String?): ConnectionHealth = when (code) {
    "UNREACHABLE" -> ConnectionHealth.Unreachable
    "NEEDS_SIGN_IN" -> ConnectionHealth.NeedsSignIn
    "ERROR" -> ConnectionHealth.Error(message ?: "Unknown error")
    else -> ConnectionHealth.Ok
}

private fun encodeConfig(config: Map<String, String>): String = JSONObject(config).toString()

private fun decodeConfig(json: String): Map<String, String> {
    val o = JSONObject(json)
    return o.keys().asSequence().associateWith { o.getString(it) }
}

private fun ConnectionEntity.toStored() = StoredConnection(
    Connection(id, providerId, label, decodeConfig(configJson)),
    healthOf(health, healthMessage),
    lastSyncMillis,
)

private fun SourceEntity.toStored() = StoredSource(
    connectionId,
    CalendarSource(sourceId, name, writable),
    SourceMapping(PersonId(personId), visible),
)

private fun RemoteEvent.toEntity(connectionId: String, sourceId: String, zone: ZoneId) = EventEntity(
    connectionId = connectionId,
    sourceId = sourceId,
    remoteId = remoteId,
    title = title,
    startInstant = (start as? EventTime.Timed)?.instant?.toEpochMilli(),
    startDate = (start as? EventTime.AllDay)?.date?.toString(),
    endInstant = (end as? EventTime.Timed)?.instant?.toEpochMilli(),
    endDate = (end as? EventTime.AllDay)?.date?.toString(),
    recurring = recurring,
    forPerson = forPerson,
    createdBy = createdBy,
    startSort = start.instantIn(zone).toEpochMilli(),
    endSort = end.instantIn(zone).toEpochMilli(),
)

private fun timeOf(instant: Long?, date: String?): EventTime =
    if (instant != null) EventTime.Timed(Instant.ofEpochMilli(instant)) else EventTime.AllDay(LocalDate.parse(requireNotNull(date)))

private fun EventRow.toStored() = StoredEvent(
    connectionId = event.connectionId,
    sourceId = event.sourceId,
    remoteId = event.remoteId,
    title = event.title,
    start = timeOf(event.startInstant, event.startDate),
    end = timeOf(event.endInstant, event.endDate),
    recurring = event.recurring,
    forPerson = event.forPerson,
    createdBy = event.createdBy,
    sourcePerson = PersonId(sourcePersonId),
    startSort = event.startSort,
    endSort = event.endSort,
)
