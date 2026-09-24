package uk.co.siland.culvery.capability.calendar

import android.util.Log
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
import uk.co.siland.culvery.capability.calendar.db.OutboxEntity
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

    /** Every source of every connection, hidden ones included. */
    fun sources(): Flow<List<StoredSource>> = dao.allSources().map { rows -> rows.map { it.toStored() } }

    suspend fun source(connectionId: String, sourceId: String): StoredSource? = dao.source(connectionId, sourceId)?.toStored()

    fun master(): Flow<StoredSource?> = dao.master().map { it?.toStored() }

    /**
     * Makes this source the one master calendar, clearing any other, in one transaction. It also records the
     * source as writable: only CalendarSetup.setMaster calls this, after checking the provider says it is.
     */
    suspend fun setMaster(connectionId: String, sourceId: String) = db.withTransaction {
        dao.clearMaster()
        require(dao.markMaster(connectionId, sourceId) == 1) { "No source $sourceId in connection $connectionId" }
    }

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

    /** Touches only the event mirror and the cursor; queued changes in the outbox are never affected. */
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

    fun event(ref: EventRef): Flow<StoredEvent?> =
        dao.event(ref.connectionId, ref.sourceId, ref.remoteId).map { it?.toStored() }

    suspend fun eventNow(ref: EventRef): StoredEvent? =
        dao.eventNow(ref.connectionId, ref.sourceId, ref.remoteId)?.toStored()

    /** Puts a write the provider accepted into the mirror and, if it came from the outbox, completes it. */
    suspend fun applyAccepted(connectionId: String, sourceId: String, event: RemoteEvent, zone: ZoneId, completing: Long? = null) =
        db.withTransaction {
            dao.upsertEvents(listOf(event.toEntity(connectionId, sourceId, zone)))
            completing?.let { dao.deleteOutbox(it) }
        }

    /** Removes an accepted delete from the mirror and, if it came from the outbox, completes it. */
    suspend fun applyDeleted(ref: EventRef, completing: Long? = null) = db.withTransaction {
        dao.deleteEvents(ref.connectionId, ref.sourceId, listOf(ref.remoteId))
        completing?.let { dao.deleteOutbox(it) }
    }

    suspend fun enqueue(change: PendingChange): Long = dao.insertOutbox(change.toEntity())

    /** Skips a row this version can't read; [pendingNow] deletes it. */
    fun pending(): Flow<List<PendingChange>> = dao.outbox().map { rows -> rows.mapNotNull { it.readOrNull() } }

    /** In queue order. A row this version can't read is logged and deleted, so one bad row can't stall the queue. */
    suspend fun pendingNow(): List<PendingChange> = dao.outboxNow().mapNotNull { row ->
        val change = row.readOrNull()
        if (change == null) dao.deleteOutbox(row.id)
        change
    }

    suspend fun nextAttemptMillis(): Long? = dao.nextAttemptMillis()

    suspend fun reschedule(id: Long, attempts: Int, nextAttemptMillis: Long) = dao.rescheduleOutbox(id, attempts, nextAttemptMillis)

    suspend fun dropChange(id: Long) = dao.deleteOutbox(id)

    private fun OutboxEntity.readOrNull(): PendingChange? =
        runCatching { toPending() }
            .onFailure { Log.w(TAG, "Dropping unreadable outbox row $id (kind $kind)", it) }
            .getOrNull()

    private companion object {
        const val TAG = "CalendarStore"

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

/** An unknown code (a newer app's value, or corruption) must not read as healthy. */
internal fun healthOf(code: String, message: String?): ConnectionHealth = when (code) {
    "OK" -> ConnectionHealth.Ok
    "UNREACHABLE" -> ConnectionHealth.Unreachable
    "NEEDS_SIGN_IN" -> ConnectionHealth.NeedsSignIn
    "ERROR" -> ConnectionHealth.Error(message ?: "Unknown error")
    else -> ConnectionHealth.Error("Unknown health code $code")
}

private fun encodeConfig(config: Map<String, String>): String = JSONObject(config).toString()

private fun decodeConfig(json: String): Map<String, String> {
    val o = JSONObject(json)
    return o.keys().asSequence().associateWith { o.getString(it) }
}

private fun encodeTime(t: EventTime): JSONObject = when (t) {
    is EventTime.Timed -> JSONObject().put("instant", t.instant.toEpochMilli())
    is EventTime.AllDay -> JSONObject().put("date", t.date.toString())
}

private fun decodeTime(o: JSONObject): EventTime =
    if (o.has("instant")) EventTime.Timed(Instant.ofEpochMilli(o.getLong("instant"))) else EventTime.AllDay(LocalDate.parse(o.getString("date")))

private fun JSONObject.stringOrNull(key: String): String? = if (isNull(key)) null else getString(key)

private fun encodeDraft(d: EventDraft): String = JSONObject()
    .put("title", d.title)
    .put("start", encodeTime(d.start))
    .put("end", encodeTime(d.end))
    .put("forPerson", d.forPerson ?: JSONObject.NULL)
    .put("createdBy", d.createdBy ?: JSONObject.NULL)
    .toString()

private fun decodeDraft(json: String): EventDraft {
    val o = JSONObject(json)
    return EventDraft(
        title = o.getString("title"),
        start = decodeTime(o.getJSONObject("start")),
        end = decodeTime(o.getJSONObject("end")),
        forPerson = o.stringOrNull("forPerson"),
        createdBy = o.stringOrNull("createdBy"),
    )
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
    isMaster,
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

private fun PendingChange.toEntity() = OutboxEntity(
    id = id,
    connectionId = connectionId,
    sourceId = sourceId,
    remoteId = remoteId,
    kind = kind.name,
    draftJson = draft?.let(::encodeDraft),
    attempts = attempts,
    nextAttemptMillis = nextAttemptMillis,
    createdMillis = createdMillis,
)

private fun OutboxEntity.toPending() = PendingChange(
    id = id,
    connectionId = connectionId,
    sourceId = sourceId,
    remoteId = remoteId,
    kind = ChangeKind.valueOf(kind),
    draft = draftJson?.let(::decodeDraft),
    attempts = attempts,
    nextAttemptMillis = nextAttemptMillis,
    createdMillis = createdMillis,
)
