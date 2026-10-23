package uk.co.siland.culvery.capability.shopping.db

import androidx.room.Dao
import androidx.room.Database
import androidx.room.Entity
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.RoomDatabase
import kotlinx.coroutines.flow.Flow

/** The one connection (10 §4.5); [slot] is always 0. [health] is a `ShoppingHealth` name. */
@Entity(tableName = "shopping_connection")
data class ConnectionEntity(
    @PrimaryKey val slot: Int = 0,
    val id: String,
    val providerId: String,
    val label: String,
    val configJson: String,
    val health: String,
    val lastReadMillis: Long?,
    val lastHistoryMillis: Long?,
    /** Whether the first history read has been counted for suggestions. */
    val seeded: Boolean,
) {
    // The config holds the account's email, which shows on Settings only.
    override fun toString() = "ConnectionEntity(id=$id, providerId=$providerId, health=$health)"
}

/** An open item as last read; [position] is the order the provider gave, [changedAtMillis] when it last changed. */
@Entity(tableName = "shopping_item")
data class ItemEntity(
    @PrimaryKey val id: String,
    val text: String,
    val position: Int,
    val changedAtMillis: Long,
) {
    override fun toString() = "ItemEntity(id=$id, position=$position)"
}

/**
 * An unsent change, oldest first by [seq]. [kind] is a `QueuedKind` name. [itemId] is null for an add until it is
 * sent. [excludeIds] holds the ids (as text) an ADD must never match when it is looked for after an uncertain send.
 */
@Entity(tableName = "shopping_queue")
data class QueuedEntity(
    @PrimaryKey(autoGenerate = true) val seq: Long = 0,
    val kind: String,
    val clientKey: String,
    val itemId: String?,
    val text: String,
    val madeAtMillis: Long,
    val attempts: Int,
    val nextAtMillis: Long,
    val excludeIds: String? = null,
) {
    override fun toString() = "QueuedEntity(seq=$seq, kind=$kind, attempts=$attempts)"
}

/** What has been added before, by text key; kept when sharing stops. */
@Entity(tableName = "shopping_learned")
data class LearnedEntity(
    @PrimaryKey val key: String,
    val text: String,
    val count: Int,
    val lastMillis: Long,
    val countedMillis: Long,
) {
    override fun toString() = "LearnedEntity(count=$count, lastMillis=$lastMillis)"
}

/** An item id already counted for suggestions. */
@Entity(tableName = "shopping_seen")
data class SeenEntity(
    @PrimaryKey val id: String,
    val atMillis: Long,
)

@Dao
interface ShoppingDao {
    @Query("SELECT * FROM shopping_connection WHERE slot = 0")
    fun connection(): Flow<ConnectionEntity?>

    @Query("SELECT * FROM shopping_connection WHERE slot = 0")
    suspend fun connectionNow(): ConnectionEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun putConnection(row: ConnectionEntity)

    @Query("DELETE FROM shopping_connection")
    suspend fun deleteConnection()

    @Query("UPDATE shopping_connection SET providerId = :providerId, label = :label, configJson = :configJson WHERE id = :id")
    suspend fun updateConnection(id: String, providerId: String, label: String, configJson: String): Int

    @Query("UPDATE shopping_connection SET health = :health")
    suspend fun setHealth(health: String)

    @Query("UPDATE shopping_connection SET health = :health, lastReadMillis = :atMillis")
    suspend fun setRead(health: String, atMillis: Long)

    @Query("UPDATE shopping_connection SET lastHistoryMillis = :atMillis")
    suspend fun setHistoryRead(atMillis: Long)

    @Query("UPDATE shopping_connection SET seeded = 1")
    suspend fun markSeeded()

    @Query("SELECT * FROM shopping_item ORDER BY position")
    fun items(): Flow<List<ItemEntity>>

    @Query("DELETE FROM shopping_item")
    suspend fun deleteItems()

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertItems(rows: List<ItemEntity>)

    @Query("SELECT * FROM shopping_queue ORDER BY seq")
    fun queue(): Flow<List<QueuedEntity>>

    @Query("SELECT * FROM shopping_queue ORDER BY seq")
    suspend fun queueNow(): List<QueuedEntity>

    @Query("SELECT * FROM shopping_queue WHERE seq = :seq")
    suspend fun queued(seq: Long): QueuedEntity?

    @Insert
    suspend fun insertQueued(row: QueuedEntity): Long

    @Query("DELETE FROM shopping_queue WHERE seq = :seq")
    suspend fun deleteQueued(seq: Long)

    @Query("DELETE FROM shopping_queue")
    suspend fun deleteQueue()

    @Query("UPDATE shopping_queue SET itemId = :itemId WHERE clientKey = :clientKey AND seq > :after")
    suspend fun setItemIdAfter(clientKey: String, after: Long, itemId: String)

    @Query("UPDATE shopping_queue SET excludeIds = :excludeIds WHERE seq = :seq")
    suspend fun setExcluded(seq: Long, excludeIds: String?)

    @Query("UPDATE shopping_queue SET nextAtMillis = 0")
    suspend fun makeAllDue()

    @Query("UPDATE shopping_queue SET attempts = attempts + 1, nextAtMillis = :nextAtMillis WHERE seq = :seq")
    suspend fun markFailed(seq: Long, nextAtMillis: Long)

    @Query("SELECT * FROM shopping_learned")
    suspend fun allLearned(): List<LearnedEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertLearned(row: LearnedEntity)

    @Query("SELECT * FROM shopping_learned")
    fun learned(): Flow<List<LearnedEntity>>

    @Query("SELECT * FROM shopping_learned WHERE `key` = :key")
    suspend fun learnedRow(key: String): LearnedEntity?

    @Query("UPDATE shopping_learned SET lastMillis = MAX(lastMillis, :atMillis) WHERE `key` = :key")
    suspend fun touchLearned(key: String, atMillis: Long)

    @Query("DELETE FROM shopping_learned WHERE `key` = :key")
    suspend fun deleteLearned(key: String)

    @Query("DELETE FROM shopping_learned")
    suspend fun deleteAllLearned()

    /** Keeps the [max] best rows: highest count, then latest use, then key. */
    @Query(
        "DELETE FROM shopping_learned WHERE `key` NOT IN " +
            "(SELECT `key` FROM shopping_learned ORDER BY `count` DESC, lastMillis DESC, `key` ASC LIMIT :max)",
    )
    suspend fun trimLearned(max: Int)

    @Query("DELETE FROM shopping_learned WHERE lastMillis < :cutoffMillis")
    suspend fun pruneLearned(cutoffMillis: Long)

    @Query("DELETE FROM shopping_seen WHERE atMillis < :cutoffMillis")
    suspend fun pruneSeen(cutoffMillis: Long)

    /** Returns -1 when the id was already seen. */
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertSeenIfNew(row: SeenEntity): Long

    @Query("SELECT * FROM shopping_seen")
    suspend fun allSeen(): List<SeenEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertSeen(row: SeenEntity)
}

/** v1. A later version ships a hand-written Migration, as the calendar's do. */
@Database(
    entities = [ConnectionEntity::class, ItemEntity::class, QueuedEntity::class, LearnedEntity::class, SeenEntity::class],
    version = 1,
    exportSchema = true,
)
abstract class ShoppingDatabase : RoomDatabase() {
    abstract fun shoppingDao(): ShoppingDao
}
