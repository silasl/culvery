package uk.co.siland.culvery.core.household

import androidx.room.withTransaction
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import uk.co.siland.culvery.core.household.db.HouseholdDatabase
import uk.co.siland.culvery.core.household.db.LocationEntity
import uk.co.siland.culvery.core.household.db.PersonEntity

@Singleton
class HouseholdRepository @Inject constructor(private val db: HouseholdDatabase) {
    private val dao = db.householdDao()

    /** Real people in display order; never includes Family. */
    val people: Flow<List<Person>> = dao.people().map { rows -> rows.map { it.toPerson() } }

    val peopleWithFamily: Flow<List<Person>> = people.map { listOf(Person.Family) + it }

    /** Real people with their roles and whether they have a PIN, in display order (4a design §4.4). */
    val members: Flow<List<Member>> = dao.people().map { rows -> rows.map { it.toMember() } }

    val hasActiveAdmin: Flow<Boolean> = members.map { list -> list.any { it.isActiveAdmin } }.distinctUntilChanged()

    val location: Flow<HomeLocation?> =
        dao.location().map { it?.let { l -> HomeLocation(l.name, l.latitude, l.longitude, l.timeZoneId) } }

    suspend fun person(id: PersonId): Person? =
        if (id == PersonId.FAMILY) Person.Family else dao.person(id.value)?.toPerson()

    suspend fun member(id: PersonId): Member? = dao.person(id.value)?.toMember()

    /**
     * 4a design §3.7: a name nobody else has (ignoring case and spaces; nobody is "Family"), a colour nobody else has, and
     * at most [MAX_PEOPLE]. [pinHash] and [salt] set the PIN in the same transaction (PinManager.addPerson), so a person
     * is never left half made.
     */
    suspend fun addPerson(name: String, color: Long, role: Role,
        pinHash: String? = null,
        salt: String? = null,
        pinGuard: PinGuard? = null,
    ): Person {
        require((pinHash == null) == (salt == null)) { "A PIN needs both its hash and its salt" }
        val clean = cleanName(name)
        val id = PersonId.new()
        db.withTransaction {
            val all = dao.all()
            if (all.size >= MAX_PEOPLE) throw HouseholdFullException()
            checkUnique(all, id, clean, color)
            pinGuard?.check(all.map { it.toCredential() })
            dao.upsertPerson(PersonEntity(id.value, clean, color, dao.maxSortOrder() + 1, role, pinHash, salt))
        }
        return Person(id, clean, color)
    }

    /**
     * Name, colour, role and PIN in one transaction (4a design §5: a refused edit changes nothing), under [addPerson]'s
     * rules and the last-Admin rule.
     */
    suspend fun updateMember(id: PersonId, name: String, color: Long, role: Role, pin: PinChange) {
        require(id != PersonId.FAMILY) { "Family cannot be edited" }
        val clean = cleanName(name)
        db.withTransaction {
            val current = requireNotNull(dao.person(id.value)) { "Unknown person ${id.value}" }
            val all = dao.all()
            checkUnique(all, id, clean, color)
            val renamed = current.copy(name = clean, color = color, role = role)
            val next = when (pin) {
                PinChange.Keep -> renamed
                PinChange.Remove -> renamed.copy(pinHash = null, salt = null)
                is PinChange.Set -> {
                    pin.guard?.check(all.filter { it.id != id.value }.map { it.toCredential() })
                    renamed.copy(pinHash = pin.hash, salt = pin.salt)
                }
            }
            guardLastAdmin(current, next)
            dao.upsertPerson(next)
        }
    }

    suspend fun removePerson(id: PersonId) {
        require(id != PersonId.FAMILY) { "Family cannot be removed" }
        db.withTransaction {
            val current = dao.person(id.value) ?: return@withTransaction
            guardLastAdmin(current, next = null)
            dao.deletePerson(id.value)
        }
    }

    /**
     * Swaps [id] with the person above it ([up]) or below it, in one transaction (4c design §7.1); nobody moves past
     * either end, and Family, never in the list, never moves. Returns whether anyone moved.
     */
    suspend fun move(id: PersonId, up: Boolean): Boolean = db.withTransaction {
        val all = dao.all()
        val index = all.indexOfFirst { it.id == id.value }
        val other = all.getOrNull(if (up) index - 1 else index + 1)
        if (index < 0 || other == null) return@withTransaction false
        val person = all[index]
        dao.upsertPerson(person.copy(sortOrder = other.sortOrder))
        dao.upsertPerson(other.copy(sortOrder = person.sortOrder))
        true
    }

    suspend fun credential(id: PersonId): Credential? = dao.person(id.value)?.toCredential()

    suspend fun credentials(): List<Credential> = dao.all().map { it.toCredential() }

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

    private suspend fun guardLastAdmin(current: PersonEntity, next: PersonEntity?) {
        if (!current.isActiveAdmin() || next?.isActiveAdmin() == true) return
        if (dao.all().count { it.isActiveAdmin() } <= 1) throw LastAdminException()
    }

    private fun checkUnique(all: List<PersonEntity>, id: PersonId, name: String, color: Long) {
        val others = all.filter { it.id != id.value }
        if (name.equals(Person.Family.name, ignoreCase = true) || others.any { it.name.equals(name, ignoreCase = true) }) {
            throw DuplicateNameException(name)
        }
        if (others.any { it.color == color }) throw ColourInUseException()
    }

    private fun cleanName(name: String): String =
        name.trim().also { require(it.isNotEmpty()) { "Name must not be blank" } }

    private fun PersonEntity.isActiveAdmin() = role == Role.ADMIN && pinHash != null
    private fun PersonEntity.toPerson() = Person(PersonId(id), name, color)
    private fun PersonEntity.toMember() = Member(toPerson(), role, pinHash != null)
    private fun PersonEntity.toCredential() = Credential(PersonId(id), role, pinHash, salt)
}
