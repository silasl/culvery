package uk.co.siland.househub.core.household.db

import androidx.room.Dao
import androidx.room.Database
import androidx.room.Entity
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.RoomDatabase
import androidx.room.Upsert
import kotlinx.coroutines.flow.Flow
import uk.co.siland.househub.core.household.Role

@Entity(tableName = "person")
data class PersonEntity(
    @PrimaryKey val id: String,
    val name: String,
    val color: Long,
    val sortOrder: Int,
    val role: Role,
    val pinHash: String?,
    val salt: String?,
)

@Entity(tableName = "location")
data class LocationEntity(
    @PrimaryKey val id: Int = 0,
    val name: String,
    val latitude: Double,
    val longitude: Double,
    val timeZoneId: String,
)

@Dao
interface HouseholdDao {
    @Query("SELECT * FROM person ORDER BY sortOrder")
    fun people(): Flow<List<PersonEntity>>

    @Query("SELECT * FROM person ORDER BY sortOrder")
    suspend fun all(): List<PersonEntity>

    @Query("SELECT * FROM person WHERE id = :id")
    suspend fun person(id: String): PersonEntity?

    @Query("SELECT COALESCE(MAX(sortOrder), -1) FROM person")
    suspend fun maxSortOrder(): Int

    @Upsert
    suspend fun upsertPerson(person: PersonEntity)

    @Query("DELETE FROM person WHERE id = :id")
    suspend fun deletePerson(id: String)

    @Query("SELECT * FROM location WHERE id = 0")
    fun location(): Flow<LocationEntity?>

    @Upsert
    suspend fun upsertLocation(location: LocationEntity)
}

@Database(entities = [PersonEntity::class, LocationEntity::class], version = 1)
abstract class HouseholdDatabase : RoomDatabase() {
    abstract fun householdDao(): HouseholdDao
}
