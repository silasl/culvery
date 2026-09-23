package uk.co.siland.househub.core.access

import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import uk.co.siland.househub.core.household.Credential
import uk.co.siland.househub.core.household.HouseholdRepository
import uk.co.siland.househub.core.household.Person
import uk.co.siland.househub.core.household.PersonId
import uk.co.siland.househub.core.household.Role

data class Identified(val person: Person, val role: Role)

class PinInUseException : Exception("That PIN is already used by someone else")

@Singleton
class PinManager @Inject constructor(
    private val household: HouseholdRepository,
    private val hasher: PinHasher,
) {
    suspend fun setPin(id: PersonId, pin: String) {
        hasher.validate(pin)
        val (hash, salt) = withContext(Dispatchers.Default) {
            val others = household.credentials().filter { it.personId != id }
            if (others.any { it.matches(pin) }) throw PinInUseException()
            val salt = hasher.newSalt()
            hasher.hash(pin, salt) to salt
        }
        household.setPinHash(id, hash, salt)
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
