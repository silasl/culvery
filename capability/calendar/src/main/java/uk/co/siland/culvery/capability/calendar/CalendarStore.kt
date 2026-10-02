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
import uk.co.siland.culvery.capability.calendar.db.CalendarDao
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
class CalendarStore internal constructor(private val db: CalendarDatabase, private val dao: CalendarDao) : StoredSeries {
    @Inject constructor(db: CalendarDatabase) : this(db, db.calendarDao())

    fun connections(): Flow<List<StoredConnection>> = dao.connections().map { rows -> rows.map { it.toStored() } }

    fun connectionIds(): Flow<List<String>> = dao.connectionIds().distinctUntilChanged()

    suspend fun connectionsNow(): List<StoredConnection> = dao.allConnections().map { it.toStored() }

    /**
     * One transaction, so the sync loop never sees a connection without its sources. A source missing from
     * [mapping] shows as Family. [masterSourceId] makes that source the master, clearing any other (3a design D4).
     */
    suspend fun addConnection(
        connection: Connection,
        sources: List<CalendarSource>,
        mapping: Map<String, SourceMapping>,
        masterSourceId: String? = null,
    ) = db.withTransaction {
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
                SourceEntity(connection.id, s.id, s.name, s.writable, m.visible, m.person.value, shownInService = s.visibleOnTablet)
            },
        )
        if (masterSourceId != null) {
            dao.clearMaster()
            // Fixed words (P8): a Google calendar id is often the account's email, and the message gets logged.
            require(dao.markMaster(connection.id, masterSourceId) == 1) { "The master calendar isn't among the connection's calendars" }
        }
    }

    /** The connection with its sources, events, cursors and queued changes, in one transaction (3a design D5). */
    suspend fun removeConnection(connectionId: String) = db.withTransaction {
        dao.deleteOutboxOf(connectionId)
        dao.deleteSyncStatesOf(connectionId)
        dao.deleteEventsOf(connectionId)
        dao.deleteSourcesOf(connectionId)
        dao.deleteConnection(connectionId)
    }

    /**
     * Follows the provider's list of [sources] (3a design §3.4, 4a design §3.10), in one transaction: a new source is
     * added with [mappingForNew]; an existing one keeps its person, takes the listed name and writability, and takes the
     * service's tick as its visibility only when that tick has changed since it was last seen (ruling 12: the tick is
     * `shown || primary`), so a calendar hidden or shown on the tablet stays so; the master is always shown. One no
     * longer listed goes with its events, cursor and queued changes. Returns true when the master went, or became
     * read-only, and was cleared. Writes nothing once the connection is gone.
     */
    suspend fun refreshSources(
        connectionId: String,
        sources: List<CalendarSource>,
        nowMillis: Long,
        mappingForNew: (CalendarSource) -> SourceMapping,
    ): Boolean = db.withTransaction {
        // Removed while the provider was being read: no orphan sources.
        dao.connection(connectionId) ?: return@withTransaction false
        val stored = dao.sources(connectionId).associateBy { it.sourceId }
        // A repeated id would break the insert's primary key on every pass.
        val listed = sources.distinctBy { it.id }.associateBy { it.id }
        var masterCleared = false
        stored.values.filter { it.sourceId !in listed }.forEach { gone ->
            if (gone.isMaster) masterCleared = true
            removeSourceRows(connectionId, gone.sourceId)
        }
        listed.values.forEach { s ->
            val existing = stored[s.id]
            val tick = s.visibleOnTablet
            if (existing == null) {
                val m = mappingForNew(s)
                dao.insertSources(listOf(SourceEntity(connectionId, s.id, s.name, s.writable, m.visible, m.person.value, shownInService = tick)))
            } else {
                val visible = when {
                    existing.isMaster -> true
                    existing.shownInService == tick -> existing.visible
                    else -> tick
                }
                dao.updateSource(connectionId, s.id, s.name, s.writable, visible, tick)
                if (existing.isMaster && !s.writable) {
                    dao.clearMaster()
                    masterCleared = true
                }
            }
        }
        dao.markSourcesChecked(connectionId, nowMillis)
        masterCleared
    }

    private suspend fun removeSourceRows(connectionId: String, sourceId: String) {
        dao.deleteOutboxOfSource(connectionId, sourceId)
        dao.deleteSyncState(connectionId, sourceId)
        dao.deleteEventsForSource(connectionId, sourceId)
        dao.deleteSource(connectionId, sourceId)
    }

    suspend fun visibleSourcesFor(connectionId: String): List<StoredSource> =
        dao.sources(connectionId).filter { it.visible }.map { it.toStored() }

    /** Every source of every connection, hidden ones included. */
    fun sources(): Flow<List<StoredSource>> = dao.allSources().map { rows -> rows.map { it.toStored() } }

    suspend fun source(connectionId: String, sourceId: String): StoredSource? = dao.source(connectionId, sourceId)?.toStored()

    fun master(): Flow<StoredSource?> = dao.master().map { it?.toStored() }

    /**
     * Makes this source the one master calendar, and shows it, clearing any other master, in one transaction. It also records the
     * source as writable: only CalendarSetup.setMaster calls this, after checking the provider says it is.
     */
    suspend fun setMaster(connectionId: String, sourceId: String) = db.withTransaction {
        dao.clearMaster()
        require(dao.markMaster(connectionId, sourceId) == 1) { "The master calendar isn't among the connection's calendars" }
    }

    /**
     * Who a calendar is for and whether it shows, chosen on the tablet (4a design §3.9, D13). The master is always shown:
     * hiding it is refused.
     */
    suspend fun setMapping(connectionId: String, sourceId: String, person: PersonId, visible: Boolean) = db.withTransaction {
        val row = requireNotNull(dao.source(connectionId, sourceId)) { "No such calendar" }
        require(visible || !row.isMaster) { "The master calendar is always shown" }
        dao.setMapping(connectionId, sourceId, person.value, visible)
    }

    /** Every calendar mapped to someone not in [existing] (removed from the household) now shows as Family (4a design §3.9). */
    suspend fun remapMissingPeople(existing: Set<PersonId>) = db.withTransaction {
        dao.allSourcesNow()
            .filter { it.personId != PersonId.FAMILY.value && PersonId(it.personId) !in existing }
            .forEach { dao.setPerson(it.connectionId, it.sourceId, PersonId.FAMILY.value) }
    }

    /** The disconnect confirmation's count (4a design D14). */
    suspend fun queuedChanges(connectionId: String): Int = dao.countOutboxOf(connectionId)

    /**
     * Also keeps the outbox's sign-in pause (3a design D16): the first NeedsSignIn starts it; Ok folds it into each of
     * the connection's rows (moving its creation forward) and ends it; Unreachable and Error leave it running, since
     * neither says access is back.
     */
    suspend fun setHealth(connectionId: String, health: ConnectionHealth, nowMillis: Long) = db.withTransaction {
        val row = dao.connection(connectionId) ?: return@withTransaction
        when {
            health == ConnectionHealth.NeedsSignIn && row.needsSignInSinceMillis == null -> dao.setNeedsSignInSince(connectionId, nowMillis)
            health == ConnectionHealth.Ok -> endPause(row, nowMillis)
        }
        dao.setHealth(connectionId, health.code(), (health as? ConnectionHealth.Error)?.message)
    }

    suspend fun markSynced(connectionId: String, atMillis: Long) = db.withTransaction {
        dao.connection(connectionId)?.let { endPause(it, atMillis) }
        dao.markSynced(connectionId, atMillis)
    }

    private suspend fun endPause(row: ConnectionEntity, nowMillis: Long) {
        val since = row.needsSignInSinceMillis ?: return
        dao.foldPause(row.id, since, nowMillis)
        dao.setNeedsSignInSince(row.id, null)
    }

    /** Reconnect (3a design D6, follow-up m4): the connection's queued changes are tried at the next pass. */
    suspend fun makeDue(connectionId: String, nowMillis: Long) = dao.makeDue(connectionId, nowMillis)

    /**
     * Reconnect (3a design D6) in one transaction: health Ok, which ends the sign-in pause, and the queued changes due
     * now. False, writing nothing, when the connection is gone.
     */
    suspend fun reconnect(connectionId: String, nowMillis: Long): Boolean = db.withTransaction {
        dao.connection(connectionId) ?: return@withTransaction false
        setHealth(connectionId, ConnectionHealth.Ok, nowMillis)
        makeDue(connectionId, nowMillis)
        true
    }

    /**
     * The sync token while what its full sync read still covers [window], in the same zone (4c ruling 1): a window
     * that has moved past what was read, or before it (a clock set back), or a zone change, reads in full. A zone
     * change must: all-day events are stored at midnight in the zone they were synced in.
     */
    suspend fun cursor(connectionId: String, sourceId: String, window: DateRange): SyncCursor? {
        val state = dao.syncState(connectionId, sourceId) ?: return null
        val key = CursorKey.parse(state.rangeStart) ?: return null
        val covers = !key.readStart.isAfter(window.start) && !key.readEnd.isBefore(window.endExclusive) && key.zoneId == window.zone.id
        return state.cursor?.takeIf { covers }?.let(::SyncCursor)
    }

    /**
     * Touches only the event mirror and the cursor; queued changes in the outbox are never affected. Writes nothing
     * once the source is gone.
     */
    suspend fun applySync(connectionId: String, sourceId: String, range: DateRange, result: SyncResult) =
        db.withTransaction {
            // A source removed while this sync was in flight: no orphan events or stale cursor.
            if (dao.source(connectionId, sourceId) == null) return@withTransaction
            if (result.fullReplace) {
                dao.deleteEventsForSource(connectionId, sourceId)
            } else {
                result.removedIds.chunked(REMOVE_CHUNK).forEach { dao.deleteEvents(connectionId, sourceId, it) }
            }
            dao.upsertEvents(result.upserts.map { it.toEntity(connectionId, sourceId, range.zone) })
            // An incremental result keeps what the full sync read: only a full sync reads a new range (4c ruling 1).
            val key = if (result.fullReplace) range.cursorKey() else dao.syncState(connectionId, sourceId)?.rangeStart ?: range.cursorKey()
            dao.upsertSyncState(SyncStateEntity(connectionId, sourceId, result.cursor?.value, key))
        }

    /**
     * Drops [sourceId]'s events that end before [window] starts, or start at or after the end of what its full sync read
     * (4c design §6.2, ruling 1): an incremental result can carry any date, and nothing else removes them. What was read
     * past the window stays, as no incremental result will bring it back. A row a queued change targets stays. The
     * stored key's start moves up to the window's. Returns how many went.
     */
    suspend fun prune(connectionId: String, sourceId: String, window: DateRange): Int = db.withTransaction {
        val state = dao.syncState(connectionId, sourceId)
        val key = state?.let { CursorKey.parse(it.rangeStart) }
        val removed = dao.pruneEvents(
            connectionId,
            sourceId,
            window.startInstant.toEpochMilli(),
            (key?.readEnd ?: window.endExclusive).atStartOfDay(window.zone).toInstant().toEpochMilli(),
        )
        // The key must describe what the store holds: days before the window are gone now, so a window set back to
        // them (a clock moved back) reads in full instead of keeping a token that cannot restore them.
        if (state != null && key != null && key.readStart.isBefore(window.start)) {
            dao.upsertSyncState(state.copy(rangeStart = CursorKey(window.start, key.readEnd, key.zoneId).toString()))
        }
        removed
    }

    override suspend fun instances(connectionId: String, sourceId: String): Map<String, String?> =
        dao.seriesInstances(connectionId, sourceId).associate { it.remoteId to it.recurrenceRule }

    fun eventsBetween(startMillis: Long, endMillis: Long): Flow<List<StoredEvent>> =
        dao.eventsBetween(startMillis, endMillis).map { rows -> rows.map { it.toStored() } }

    fun event(ref: EventRef): Flow<StoredEvent?> =
        dao.event(ref.connectionId, ref.sourceId, ref.remoteId).map { it?.toStored() }

    suspend fun eventNow(ref: EventRef): StoredEvent? =
        dao.eventNow(ref.connectionId, ref.sourceId, ref.remoteId)?.toStored()

    /**
     * Puts a write the provider accepted into the mirror and, if it came from the outbox, completes it. Writes nothing
     * once the source is gone: its queued changes went with it.
     */
    suspend fun applyAccepted(connectionId: String, sourceId: String, event: RemoteEvent, zone: ZoneId, completing: Long? = null) =
        db.withTransaction {
            if (dao.source(connectionId, sourceId) == null) return@withTransaction
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

    /**
     * Drops the create for [ref] and every change queued for that event, in one statement, so a change queued after
     * the caller read the queue goes too. Returns how many changes went.
     */
    suspend fun dropCreate(ref: EventRef): Int = dao.deleteOutboxFor(ref.connectionId, ref.sourceId, ref.remoteId)

    private fun OutboxEntity.readOrNull(): PendingChange? =
        runCatching { toPending() }
            .onFailure { Log.w(TAG, "Dropping unreadable outbox row ${this.id} (kind ${this.kind}, ${it::class.simpleName})") }
            .getOrNull()

    private companion object {
        const val TAG = "CalendarStore"

        // SQLite allows 999 bound variables per statement on older Android versions.
        const val REMOVE_CHUNK = 500
    }
}

/** A sync cursor's key (4c ruling 1): the days its full sync read, end exclusive, and the zone it read them in. */
private class CursorKey(val readStart: LocalDate, val readEnd: LocalDate, val zoneId: String) {
    override fun toString(): String = "$readStart|$readEnd|$zoneId"

    companion object {
        /** Null for a key from before v6 (cleared by the migration) or anything unreadable: a full sync. */
        fun parse(text: String): CursorKey? {
            val parts = text.split('|')
            if (parts.size != 3) return null
            return runCatching { CursorKey(LocalDate.parse(parts[0]), LocalDate.parse(parts[1]), parts[2]) }.getOrNull()
        }
    }
}

private fun DateRange.cursorKey(): String = CursorKey(start, endExclusive, zone.id).toString()

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
    .put("forPersonColor", d.forPersonColor ?: JSONObject.NULL)
    .toString()

private fun decodeDraft(json: String): EventDraft {
    val o = JSONObject(json)
    return EventDraft(
        title = o.getString("title"),
        start = decodeTime(o.getJSONObject("start")),
        end = decodeTime(o.getJSONObject("end")),
        forPerson = o.stringOrNull("forPerson"),
        createdBy = o.stringOrNull("createdBy"),
        // A row from before v4 has no colour.
        forPersonColor = if (o.has("forPersonColor") && !o.isNull("forPersonColor")) o.getLong("forPersonColor") else null,
    )
}

private fun encodeFields(fields: Set<EventField>?): String? = fields?.joinToString(",") { it.name }

private fun decodeFields(text: String?): Set<EventField>? =
    text?.split(",")?.filter { it.isNotEmpty() }?.map(EventField::valueOf)?.toSet()

private fun ConnectionEntity.toStored() = StoredConnection(
    Connection(id, providerId, label, decodeConfig(configJson)),
    healthOf(health, healthMessage),
    lastSyncMillis,
    sourcesCheckedMillis,
    needsSignInSinceMillis,
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
    recurrenceRule = recurrenceRule,
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
    recurrenceRule = event.recurrenceRule,
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
    clientKey = clientKey,
    fields = encodeFields(fields),
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
    clientKey = clientKey,
    fields = decodeFields(fields),
)
