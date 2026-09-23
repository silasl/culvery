package uk.co.siland.househub.core.household

import androidx.room.withTransaction
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import uk.co.siland.househub.core.household.db.HouseholdDatabase
import uk.co.siland.househub.core.household.db.LocationEntity
import uk.co.siland.househub.core.household.db.PersonEntity

@Singleton
class HouseholdRepository @Inject constructor(private val db: HouseholdDatabase) {
    private val dao = db.householdDao()

    /** Real people in display order; never includes Family. */
    val people: Flow<List<Person>> = dao.people().map { rows -> rows.map { it.toPerson() } }

    val peopleWithFamily: Flow<List<Person>> = people.map { listOf(Person.Family) + it }

    val location: Flow<HomeLocation?> =
        dao.location().map { it?.let { l -> HomeLocation(l.name, l.latitude, l.longitude, l.timeZoneId) } }

    suspend fun person(id: PersonId): Person? =
        if (id == PersonId.FAMILY) Person.Family else dao.person(id.value)?.toPerson()

    suspend fun addPerson(name: String, color: Long, role: Role): Person {
        val clean = cleanName(name)
        val id = PersonId.new()
        dao.upsertPerson(PersonEntity(id.value, clean, color, dao.maxSortOrder() + 1, role, null, null))
        return Person(id, clean, color)
    }

    suspend fun updatePerson(person: Person) {
        require(!person.isFamily) { "Family cannot be edited" }
        val existing = requireNotNull(dao.person(person.id.value)) { "Unknown person ${person.id.value}" }
        dao.upsertPerson(existing.copy(name = cleanName(person.name), color = person.color))
    }

    suspend fun removePerson(id: PersonId) {
        require(id != PersonId.FAMILY) { "Family cannot be removed" }
        db.withTransaction {
            val current = dao.person(id.value) ?: return@withTransaction
            guardLastAdmin(current, next = null)
            dao.deletePerson(id.value)
        }
    }

    suspend fun credential(id: PersonId): Credential? = dao.person(id.value)?.toCredential()

    suspend fun credentials(): List<Credential> = dao.all().map { it.toCredential() }

    suspend fun setRole(id: PersonId, role: Role) = change(id) { it.copy(role = role) }

    suspend fun setPinHash(id: PersonId, hash: String, salt: String) = change(id) { it.copy(pinHash = hash, salt = salt) }

    suspend fun clearPin(id: PersonId) = change(id) { it.copy(pinHash = null, salt = null) }

    suspend fun setLocation(location: HomeLocation) {
        dao.upsertLocation(
            LocationEntity(
                name = location.name,
                latitude = location.latitude,
                longitude = location.longitude,
                timeZoneId = location.timeZoneId,
            ),
        )
    }

    private suspend fun change(id: PersonId, edit: (PersonEntity) -> PersonEntity) = db.withTransaction {
        val current = requireNotNull(dao.person(id.value)) { "Unknown person ${id.value}" }
        val next = edit(current)
        guardLastAdmin(current, next)
        dao.upsertPerson(next)
    }

    private suspend fun guardLastAdmin(current: PersonEntity, next: PersonEntity?) {
        if (!current.isActiveAdmin() || next?.isActiveAdmin() == true) return
        if (dao.all().count { it.isActiveAdmin() } <= 1) throw LastAdminException()
    }

    private fun cleanName(name: String): String =
        name.trim().also { require(it.isNotEmpty()) { "Name must not be blank" } }

    private fun PersonEntity.isActiveAdmin() = role == Role.ADMIN && pinHash != null
    private fun PersonEntity.toPerson() = Person(PersonId(id), name, color)
    private fun PersonEntity.toCredential() = Credential(PersonId(id), role, pinHash, salt)
}
