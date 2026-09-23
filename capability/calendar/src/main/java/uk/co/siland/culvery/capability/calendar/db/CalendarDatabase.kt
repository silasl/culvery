package uk.co.siland.culvery.capability.calendar.db

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

    @Query("SELECT * FROM sync_state WHERE connectionId = :connectionId AND sourceId = :sourceId")
    suspend fun syncState(connectionId: String, sourceId: String): SyncStateEntity?

    @Upsert
    suspend fun upsertSyncState(state: SyncStateEntity)
}

/**
 * Holds user configuration (connections, source mappings), not just a cache: every version bump ships a
 * Migration or AutoMigration, never a destructive fallback. The schema JSON under schemas/ is committed.
 */
@Database(
    entities = [ConnectionEntity::class, SourceEntity::class, EventEntity::class, SyncStateEntity::class],
    version = 1,
    exportSchema = true,
)
abstract class CalendarDatabase : RoomDatabase() {
    abstract fun calendarDao(): CalendarDao
}
