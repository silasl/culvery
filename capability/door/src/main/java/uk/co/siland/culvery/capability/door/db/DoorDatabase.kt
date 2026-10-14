package uk.co.siland.culvery.capability.door.db

import androidx.room.Dao
import androidx.room.Database
import androidx.room.Entity
import androidx.room.Index
import androidx.room.Insert
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.RoomDatabase
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

/** One ring or motion (5 §3.1); its picture, when [hasSnapshot], is `files/door/<id>.jpg`. */
@Entity(tableName = "door_event", indices = [Index("noticeKey")])
data class DoorEventEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val noticeKey: String,
    /** The notification's own time: with the key, which notification this is. */
    val noticeWhen: Long,
    val atMillis: Long,
    /** A `DoorKind` name. */
    val kind: String,
    val device: String,
    val unrecognised: Boolean,
    val hasSnapshot: Boolean,
    val seen: Boolean,
)

data class UnseenRow(val rings: Int, val motion: Int)

@Dao
interface DoorDao {
    @Query("SELECT * FROM door_event ORDER BY atMillis DESC, id DESC")
    fun events(): Flow<List<DoorEventEntity>>

    @Query("SELECT * FROM door_event WHERE noticeKey = :key AND noticeWhen = :noticeWhen AND atMillis >= :since ORDER BY atMillis DESC LIMIT 1")
    suspend fun sameNotice(key: String, noticeWhen: Long, since: Long): DoorEventEntity?

    @Insert
    suspend fun insert(row: DoorEventEntity): Long

    @Update
    suspend fun update(row: DoorEventEntity)

    @Query("DELETE FROM door_event WHERE atMillis < :before")
    suspend fun deleteBefore(before: Long)

    @Query("SELECT id FROM door_event WHERE hasSnapshot = 1")
    suspend fun withSnapshots(): List<Long>

    @Query("UPDATE door_event SET seen = 1 WHERE seen = 0")
    suspend fun markAllSeen()

    @Query("UPDATE door_event SET seen = 1 WHERE id = :id")
    suspend fun markSeen(id: Long)

    @Query(
        "SELECT COALESCE(SUM(kind = 'RING'), 0) AS rings, COALESCE(SUM(kind = 'MOTION'), 0) AS motion " +
            "FROM door_event WHERE seen = 0 AND atMillis >= :since",
    )
    fun unseen(since: Long): Flow<UnseenRow>
}

/** v1. A later version ships a hand-written Migration, as the calendar's do. */
@Database(entities = [DoorEventEntity::class], version = 1, exportSchema = true)
abstract class DoorDatabase : RoomDatabase() {
    abstract fun doorDao(): DoorDao
}
