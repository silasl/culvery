package uk.co.siland.culvery.core.access

import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import uk.co.siland.culvery.core.household.Credential
import uk.co.siland.culvery.core.household.HouseholdRepository
import uk.co.siland.culvery.core.household.Person
import uk.co.siland.culvery.core.household.PersonId
import uk.co.siland.culvery.core.household.Role

data class Identified(val person: Person, val role: Role)

class PinInUseException : Exception("That PIN is already used by someone else")

@Singleton
class PinManager @Inject constructor(
    private val household: HouseholdRepository,
    private val hasher: PinHasher,
) {
    /** A hash and salt for [pin]; PinInUseException when anyone but [owner] has it (PINs identify people). */
    suspend fun hashNew(pin: String, owner: PersonId?): Pair<String, String> {
        hasher.validate(pin)
        return withContext(Dispatchers.Default) {
            val others = household.credentials().filter { it.personId != owner }
            if (others.any { it.matches(pin) }) throw PinInUseException()
            val salt = hasher.newSalt()
            hasher.hash(pin, salt) to salt
        }
    }

    suspend fun setPin(id: PersonId, pin: String) {
        val (hash, salt) = hashNew(pin, id)
        household.setPinHash(id, hash, salt)
    }

    /** Adds a person with [pin] (or none) in one step, so a PIN in use leaves nobody added. */
    suspend fun addPerson(name: String, color: Long, role: Role, pin: String?): Person {
        val hashed = pin?.let { hashNew(it, owner = null) }
        return household.addPerson(name, color, role, hashed?.first, hashed?.second)
    }

    suspend fun identify(pin: String): Identified? {
        if (!hasher.isWellFormed(pin)) return null
        val match = withContext(Dispatchers.Default) {
            household.credentials().firstOrNull { it.matches(pin) }
        } ?: return null
        val person = household.person(match.personId) ?: return null
        return Identified(person, match.role)
    }

    private fun Credential.matches(pin: String): Boolean {
        val h = pinHash ?: return false
        val s = salt ?: return false
        return hasher.matches(pin, s, h)
    }
}
