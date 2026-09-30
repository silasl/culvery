package uk.co.siland.culvery.core.access

import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import uk.co.siland.culvery.core.household.Credential
import uk.co.siland.culvery.core.household.HouseholdRepository
import uk.co.siland.culvery.core.household.Person
import uk.co.siland.culvery.core.household.PinChange
import uk.co.siland.culvery.core.household.PinGuard
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
    private suspend fun hashNew(pin: String, owner: PersonId?): Pair<String, String> {
        hasher.validate(pin)
        return withContext(Dispatchers.Default) {
            guardFor(pin).check(household.credentials().filter { it.personId != owner })
            val salt = hasher.newSalt()
            hasher.hash(pin, salt) to salt
        }
    }

    /** A [PinChange.Set] for [pin] whose clash check runs again inside the write's transaction. */
    suspend fun changeTo(pin: String, owner: PersonId?): PinChange.Set {
        val (hash, salt) = hashNew(pin, owner)
        return PinChange.Set(hash, salt, guardFor(pin))
    }

    /** Adds a person with [pin] (or none) in one step, so a PIN in use leaves nobody added. */
    suspend fun addPerson(name: String, color: Long, role: Role, pin: String?): Person {
        val hashed = pin?.let { hashNew(it, owner = null) }
        return household.addPerson(name, color, role, hashed?.first, hashed?.second, pin?.let(::guardFor))
    }

    suspend fun identify(pin: String): Identified? {
        if (!hasher.isWellFormed(pin)) return null
        val match = withContext(Dispatchers.Default) {
            household.credentials().firstOrNull { it.matches(pin) }
        } ?: return null
        val person = household.person(match.personId) ?: return null
        return Identified(person, match.role)
    }

    private fun guardFor(pin: String) = PinGuard { others ->
        if (others.any { it.matches(pin) }) throw PinInUseException()
    }

    private fun Credential.matches(pin: String): Boolean {
        val h = pinHash ?: return false
        val s = salt ?: return false
        return hasher.matches(pin, s, h)
    }
}
