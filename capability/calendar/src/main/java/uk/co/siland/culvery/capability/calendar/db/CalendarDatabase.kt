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
)

@Entity(tableName = "sync_state", primaryKeys = ["connectionId", "sourceId"])
data class SyncStateEntity(
    val connectionId: String,
    val sourceId: String,
    val cursor: String?,
    /** The window the cursor belongs to, as "<ISO start date>|<zone id>", e.g. "2026-09-22|Europe/London". */
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
)

data class EventRow(
    @Embedded val event: EventEntity,
    val sourcePersonId: String,
)

@Dao
interface CalendarDao {
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

    @Query("UPDATE source SET isMaster = 1, writable = 1 WHERE connectionId = :connectionId AND sourceId = :sourceId")
    suspend fun markMaster(connectionId: String, sourceId: String): Int

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
}

/**
 * Holds user configuration (connections, source mappings, the master flag, queued writes), not just a cache:
 * every version bump ships a Migration (see Migrations.kt) with a MigrationTestHelper test, never a destructive
 * fallback. The schema JSON under schemas/ is committed.
 */
@Database(
    entities = [ConnectionEntity::class, SourceEntity::class, EventEntity::class, SyncStateEntity::class, OutboxEntity::class],
    version = 3,
    exportSchema = true,
)
abstract class CalendarDatabase : RoomDatabase() {
    abstract fun calendarDao(): CalendarDao
}
