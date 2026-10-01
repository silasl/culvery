package uk.co.siland.culvery.capability.weather.db

import androidx.room.Dao
import androidx.room.Database
import androidx.room.Entity
import androidx.room.Insert
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.RoomDatabase
import kotlinx.coroutines.flow.Flow

/** The one fetch the tables hold (4b design §3.5). */
@Entity(tableName = "fetch")
data class FetchEntity(
    @PrimaryKey val id: Int = 0,
    val latitude: Double,
    val longitude: Double,
    val zoneId: String,
    val fetchedAtMillis: Long,
)

@Entity(tableName = "day")
data class DayEntity(
    /** ISO local date. */
    @PrimaryKey val date: String,
    /** A `Condition` name. */
    val condition: String,
    val high: Double,
    val low: Double,
    /** ISO local times; null when the sun doesn't rise or set that day. */
    val sunrise: String?,
    val sunset: String?,
)

@Entity(tableName = "hour")
data class HourEntity(
    /** ISO local date-time. */
    @PrimaryKey val start: String,
    val condition: String,
    val temperature: Double,
)

@Dao
interface WeatherDao {
    @Query("SELECT * FROM fetch LIMIT 1")
    fun fetchChanges(): Flow<FetchEntity?>

    @Query("SELECT * FROM fetch LIMIT 1")
    suspend fun fetch(): FetchEntity?

    @Query("SELECT * FROM day ORDER BY date")
    suspend fun days(): List<DayEntity>

    @Query("SELECT * FROM hour ORDER BY start")
    suspend fun hours(): List<HourEntity>

    @Query("DELETE FROM fetch")
    suspend fun clearFetch()

    @Query("DELETE FROM day")
    suspend fun clearDays()

    @Query("DELETE FROM hour")
    suspend fun clearHours()

    @Insert
    suspend fun insertFetch(row: FetchEntity)

    @Insert
    suspend fun insertDays(rows: List<DayEntity>)

    @Insert
    suspend fun insertHours(rows: List<HourEntity>)
}

/** v1. A cache, but a later version still ships a hand-written Migration, as the calendar's do. */
@Database(entities = [FetchEntity::class, DayEntity::class, HourEntity::class], version = 1, exportSchema = true)
abstract class WeatherDatabase : RoomDatabase() {
    abstract fun weatherDao(): WeatherDao
}
