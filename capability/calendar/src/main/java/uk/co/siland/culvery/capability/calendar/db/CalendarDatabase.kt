package uk.co.siland.culvery.capability.calendar.db

import androidx.room.ColumnInfo
import androidx.room.Dao
import androidx.room.Database
import androidx.room.Embedded
import androidx.room.Entity
import androidx.room.Index
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.RoomDatabase
import androidx.room.Upsert
import kotlinx.coroutines.flow.Flow

@Entity(tableName = "connection")
data class ConnectionEntity(
    @PrimaryKey val id: String,
    val providerId: String,
    val label: String,
    val configJson: String,
    /** OK, UNREACHABLE, NEEDS_SIGN_IN or ERROR. */
    val health: String,
    val healthMessage: String?,
    val lastSyncMillis: Long?,
    /** v4: when the sources were last refreshed from the provider. */
    val sourcesCheckedMillis: Long? = null,
    /** v4 (D16): when the running sign-in pause of the outbox age clock began; null when none runs. */
    val needsSignInSinceMillis: Long? = null,
)

@Entity(tableName = "source", primaryKeys = ["connectionId", "sourceId"])
data class SourceEntity(
    val connectionId: String,
    val sourceId: String,
    val name: String,
    val writable: Boolean,
    val visible: Boolean,
    /** A household PersonId value, or "family". */
    val personId: String,
    /** At most one row is 1 (CalendarStore.setMaster clears the others in the same transaction). */
    @ColumnInfo(defaultValue = "0") val isMaster: Boolean = false,
    /**
     * v5 (4a design §3.10): the service's tick when it was last seen, so a refresh changes `visible` only when the tick
     * changes; null until first seen.
     */
    val shownInService: Boolean? = null,
    /**
     * v6 (4c design §6.4): "REFUSED" while the service refuses this calendar's events although it still lists it; null
     * otherwise.
     */
    val readProblem: String? = null,
)

/** Timed events set the *Instant columns; all-day events set the *Date columns (ISO dates, end exclusive). */
@Entity(
    tableName = "event",
    primaryKeys = ["connectionId", "sourceId", "remoteId"],
    indices = [Index("startSort")],
)
data class EventEntity(
    val connectionId: String,
    val sourceId: String,
    val remoteId: String,
    val title: String,
    val startInstant: Long?,
    val startDate: String?,
    val endInstant: Long?,
    val endDate: String?,
    val recurring: Boolean,
    val forPerson: String?,
    val createdBy: String?,
    /** Epoch millis; for all-day events, midnight of the date in the household zone at sync time. */
    val startSort: Long,
    val endSort: Long,
    /** v4: the series' RRULE line; null when unknown. */
    val recurrenceRule: String? = null,
)

@Entity(tableName = "sync_state", primaryKeys = ["connectionId", "sourceId"])
data class SyncStateEntity(
    val connectionId: String,
    val sourceId: String,
    val cursor: String?,
    /** The cursor's key, "<first day its full sync read>|<first day it didn't>|<zone id>" (ISO dates; 4c ruling 1); the column keeps its v1 name. */
    val rangeStart: String,
)

/** Writes waiting for the provider. The event table stays a pure mirror of the provider. */
@Entity(tableName = "outbox")
data class OutboxEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val connectionId: String,
    val sourceId: String,
    /** Null for CREATE. */
    val remoteId: String?,
    /** CREATE, UPDATE, DELETE or ASSIGN. */
    val kind: String,
    /** The EventDraft as JSON; null for DELETE. */
    val draftJson: String?,
    val attempts: Int,
    val nextAttemptMillis: Long,
    val createdMillis: Long,
    /** CREATE only (v3): the key the provider uses as the event's id, so a retried create can't duplicate it. */
    val clientKey: String? = null,
    /** v4, UPDATE only: the EventField names it changes, comma-separated; null on an older row (every field). */
    val fields: String? = null,
)

data class EventRow(
    @Embedded val event: EventEntity,
    val sourcePersonId: String,
)

data class SeriesInstance(val remoteId: String, val recurrenceRule: String?)

@Dao
interface CalendarDao {
    @Query("SELECT remoteId, recurrenceRule FROM event WHERE connectionId = :connectionId AND sourceId = :sourceId AND recurring = 1")
    suspend fun seriesInstances(connectionId: String, sourceId: String): List<SeriesInstance>

    /** Events outside [start, end) (spanOverlaps' opposite), except those a queued change targets (4c ruling 11). */
    @Query(
        """
        DELETE FROM event WHERE connectionId = :connectionId AND sourceId = :sourceId
        AND (startSort >= :end OR (endSort <= :start AND startSort < :start))
        AND remoteId NOT IN (SELECT remoteId FROM outbox WHERE connectionId = :connectionId AND sourceId = :sourceId AND remoteId IS NOT NULL)
        AND remoteId NOT IN (SELECT clientKey FROM outbox WHERE connectionId = :connectionId AND sourceId = :sourceId AND clientKey IS NOT NULL)
        """,
    )
    suspend fun pruneEvents(connectionId: String, sourceId: String, start: Long, end: Long): Int

    @Query("SELECT * FROM connection ORDER BY label, id")
    fun connections(): Flow<List<ConnectionEntity>>

    @Query("SELECT id FROM connection ORDER BY id")
    fun connectionIds(): Flow<List<String>>

    @Query("SELECT * FROM connection ORDER BY label, id")
    suspend fun allConnections(): List<ConnectionEntity>

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insertConnection(connection: ConnectionEntity)

    @Query("UPDATE connection SET health = :health, healthMessage = :message WHERE id = :id")
    suspend fun setHealth(id: String, health: String, message: String?)

    @Query("UPDATE connection SET health = 'OK', healthMessage = NULL, lastSyncMillis = :at WHERE id = :id")
    suspend fun markSynced(id: String, at: Long)

    @Query("SELECT * FROM connection WHERE id = :id")
    suspend fun connection(id: String): ConnectionEntity?

    @Query("DELETE FROM connection WHERE id = :id")
    suspend fun deleteConnection(id: String)

    @Query("UPDATE connection SET sourcesCheckedMillis = :at WHERE id = :id")
    suspend fun markSourcesChecked(id: String, at: Long)

    @Query("UPDATE connection SET needsSignInSinceMillis = :since WHERE id = :id")
    suspend fun setNeedsSignInSince(id: String, since: Long?)

    /**
     * D16: moves each of the connection's rows' creation forward by the pause since [since], counted from when each
     * was made, so the pause never counts towards its age. The outbox is read in id order, so order is kept.
     */
    @Query(
        "UPDATE outbox SET createdMillis = createdMillis + MAX(0, :now - MAX(:since, createdMillis)) " +
            "WHERE connectionId = :connectionId",
    )
    suspend fun foldPause(connectionId: String, since: Long, now: Long)

    @Query("UPDATE outbox SET nextAttemptMillis = :now WHERE connectionId = :connectionId")
    suspend fun makeDue(connectionId: String, now: Long)

    @Query("DELETE FROM source WHERE connectionId = :connectionId")
    suspend fun deleteSourcesOf(connectionId: String)

    @Query("DELETE FROM event WHERE connectionId = :connectionId")
    suspend fun deleteEventsOf(connectionId: String)

    @Query("DELETE FROM sync_state WHERE connectionId = :connectionId")
    suspend fun deleteSyncStatesOf(connectionId: String)

    @Query("DELETE FROM outbox WHERE connectionId = :connectionId")
    suspend fun deleteOutboxOf(connectionId: String)

    @Query("DELETE FROM source WHERE connectionId = :connectionId AND sourceId = :sourceId")
    suspend fun deleteSource(connectionId: String, sourceId: String)

    @Query("DELETE FROM sync_state WHERE connectionId = :connectionId AND sourceId = :sourceId")
    suspend fun deleteSyncState(connectionId: String, sourceId: String)

    @Query("DELETE FROM outbox WHERE connectionId = :connectionId AND sourceId = :sourceId")
    suspend fun deleteOutboxOfSource(connectionId: String, sourceId: String)

    @Query(
        "UPDATE source SET name = :name, writable = :writable, visible = :visible, shownInService = :shownInService " +
            "WHERE connectionId = :connectionId AND sourceId = :sourceId",
    )
    suspend fun updateSource(connectionId: String, sourceId: String, name: String, writable: Boolean, visible: Boolean, shownInService: Boolean)

    @Query("SELECT * FROM source WHERE connectionId = :connectionId ORDER BY name")
    suspend fun sources(connectionId: String): List<SourceEntity>

    @Query("SELECT * FROM source ORDER BY connectionId, name")
    fun allSources(): Flow<List<SourceEntity>>

    @Query("SELECT * FROM source WHERE connectionId = :connectionId AND sourceId = :sourceId")
    suspend fun source(connectionId: String, sourceId: String): SourceEntity?

    @Query("SELECT * FROM source WHERE isMaster = 1 LIMIT 1")
    fun master(): Flow<SourceEntity?>

    @Query("UPDATE source SET isMaster = 0 WHERE isMaster = 1")
    suspend fun clearMaster()

    @Query("UPDATE source SET isMaster = 1, writable = 1, visible = 1 WHERE connectionId = :connectionId AND sourceId = :sourceId")
    suspend fun markMaster(connectionId: String, sourceId: String): Int

    @Query("UPDATE source SET personId = :personId, visible = :visible WHERE connectionId = :connectionId AND sourceId = :sourceId")
    suspend fun setMapping(connectionId: String, sourceId: String, personId: String, visible: Boolean): Int

    @Query("UPDATE source SET personId = :personId WHERE connectionId = :connectionId AND sourceId = :sourceId")
    suspend fun setPerson(connectionId: String, sourceId: String, personId: String)

    @Query("SELECT * FROM source ORDER BY connectionId, name")
    suspend fun allSourcesNow(): List<SourceEntity>

    @Query("SELECT COUNT(*) FROM outbox WHERE connectionId = :connectionId")
    suspend fun countOutboxOf(connectionId: String): Int

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insertSources(sources: List<SourceEntity>)

    @Query("DELETE FROM event WHERE connectionId = :connectionId AND sourceId = :sourceId")
    suspend fun deleteEventsForSource(connectionId: String, sourceId: String)

    @Query("DELETE FROM event WHERE connectionId = :connectionId AND sourceId = :sourceId AND remoteId IN (:ids)")
    suspend fun deleteEvents(connectionId: String, sourceId: String, ids: List<String>)

    @Upsert
    suspend fun upsertEvents(events: List<EventEntity>)

    @Query(
        """
        SELECT e.*, s.personId AS sourcePersonId FROM event e
        JOIN source s ON s.connectionId = e.connectionId AND s.sourceId = e.sourceId
        WHERE s.visible = 1 AND e.startSort < :end AND (e.endSort > :start OR e.startSort >= :start)
        ORDER BY e.startSort, e.title
        """,
    )
    fun eventsBetween(start: Long, end: Long): Flow<List<EventRow>>

    @Query(
        """
        SELECT e.*, s.personId AS sourcePersonId FROM event e
        JOIN source s ON s.connectionId = e.connectionId AND s.sourceId = e.sourceId
        WHERE e.connectionId = :connectionId AND e.sourceId = :sourceId AND e.remoteId = :remoteId
        """,
    )
    fun event(connectionId: String, sourceId: String, remoteId: String): Flow<EventRow?>

    @Query(
        """
        SELECT e.*, s.personId AS sourcePersonId FROM event e
        JOIN source s ON s.connectionId = e.connectionId AND s.sourceId = e.sourceId
        WHERE e.connectionId = :connectionId AND e.sourceId = :sourceId AND e.remoteId = :remoteId
        """,
    )
    suspend fun eventNow(connectionId: String, sourceId: String, remoteId: String): EventRow?

    @Query("SELECT * FROM sync_state WHERE connectionId = :connectionId AND sourceId = :sourceId")
    suspend fun syncState(connectionId: String, sourceId: String): SyncStateEntity?

    @Upsert
    suspend fun upsertSyncState(state: SyncStateEntity)

    @Insert
    suspend fun insertOutbox(change: OutboxEntity): Long

    @Query("SELECT * FROM outbox ORDER BY id")
    fun outbox(): Flow<List<OutboxEntity>>

    @Query("SELECT * FROM outbox ORDER BY id")
    suspend fun outboxNow(): List<OutboxEntity>

    @Query("SELECT MIN(nextAttemptMillis) FROM outbox")
    suspend fun nextAttemptMillis(): Long?

    @Query("UPDATE outbox SET attempts = :attempts, nextAttemptMillis = :next WHERE id = :id")
    suspend fun rescheduleOutbox(id: Long, attempts: Int, next: Long)

    @Query("DELETE FROM outbox WHERE id = :id")
    suspend fun deleteOutbox(id: Long)

    /** A create and every change queued for its event: the create carries the key, the others target it as remoteId. */
    @Query(
        "DELETE FROM outbox WHERE connectionId = :connectionId AND sourceId = :sourceId " +
            "AND (clientKey = :key OR remoteId = :key)",
    )
    suspend fun deleteOutboxFor(connectionId: String, sourceId: String, key: String): Int
}

/**
 * Holds user configuration (connections, source mappings, the master flag, queued writes), not just a cache:
 * every version bump ships a Migration (see Migrations.kt) with a MigrationTestHelper test, never a destructive
 * fallback. The schema JSON under schemas/ is committed.
 */
@Database(
    entities = [ConnectionEntity::class, SourceEntity::class, EventEntity::class, SyncStateEntity::class, OutboxEntity::class],
    version = 6,
    exportSchema = true,
)
abstract class CalendarDatabase : RoomDatabase() {
    abstract fun calendarDao(): CalendarDao
}
