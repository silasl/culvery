package uk.co.siland.culvery.capability.reminders.db

import androidx.room.Dao
import androidx.room.Database
import androidx.room.Entity
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.RoomDatabase
import kotlinx.coroutines.flow.Flow

/** One reminder. [forPeople] is comma-joined person ids; [weekdays] is a bit mask, Monday 1 to Sunday 64, and 0 for a one-off on [onDate]. */
@Entity(tableName = "reminder")
data class ReminderEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val words: String,
    val forPeople: String,
    val madeBy: String,
    val minuteOfDay: Int,
    val onDate: String?,
    val weekdays: Int,
    val answeredAtMillis: Long,
    val snoozedUntilMillis: Long?,
    val chimedAtMillis: Long?,
) {
    override fun toString() = "ReminderEntity($id)"
}

/** The single row, [slot] always 0. [binsKeptBins] is each bin as its URL-encoded name and ink id, joined as `name:ink,name:ink`. */
@Entity(tableName = "reminder_state")
data class ReminderStateEntity(
    @PrimaryKey val slot: Int = 0,
    val binsOn: Boolean,
    val binsMinuteOfDay: Int,
    val binsDoneFor: String?,
    val binsSnoozedUntilMillis: Long?,
    val binsChimedAtMillis: Long?,
    val heldSeen: Boolean,
    val heldEndedAtMillis: Long?,
    val binsKeptDate: String?,
    val binsKeptBins: String?,
) {
    override fun toString() = "ReminderStateEntity"
}

@Dao
interface RemindersDao {
    @Query("SELECT * FROM reminder ORDER BY id")
    fun reminders(): Flow<List<ReminderEntity>>

    @Query("SELECT COUNT(*) FROM reminder")
    suspend fun count(): Int

    @Query("SELECT * FROM reminder WHERE id = :id")
    suspend fun byId(id: Long): ReminderEntity?

    @Insert
    suspend fun insert(row: ReminderEntity): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun put(row: ReminderEntity)

    @Query("DELETE FROM reminder WHERE id = :id")
    suspend fun delete(id: Long): Int

    @Query("UPDATE reminder SET chimedAtMillis = :nowMillis WHERE id IN (:ids)")
    suspend fun chimed(ids: Collection<Long>, nowMillis: Long)

    @Query("SELECT * FROM reminder_state WHERE slot = 0")
    fun state(): Flow<ReminderStateEntity?>

    @Query("SELECT * FROM reminder_state WHERE slot = 0")
    suspend fun stateNow(): ReminderStateEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun putState(row: ReminderStateEntity)
}

/** v1. A later version ships a hand-written Migration, as the calendar does. */
@Database(entities = [ReminderEntity::class, ReminderStateEntity::class], version = 1, exportSchema = true)
abstract class RemindersDatabase : RoomDatabase() {
    abstract fun remindersDao(): RemindersDao
}
