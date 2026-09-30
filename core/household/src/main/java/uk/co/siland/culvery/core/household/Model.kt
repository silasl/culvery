package uk.co.siland.culvery.core.household

import java.util.UUID

@JvmInline
value class PersonId(val value: String) {
    companion object {
        val FAMILY = PersonId("family")
        fun new() = PersonId(UUID.randomUUID().toString())
    }
}

const val FAMILY_COLOR: Long = 0xFFE0A85B

data class Person(val id: PersonId, val name: String, val color: Long) {
    val isFamily: Boolean get() = id == PersonId.FAMILY

    companion object {
        val Family = Person(PersonId.FAMILY, "Family", FAMILY_COLOR)
    }
}

enum class Role { ADMIN, ADULT, CHILD }

/** Role and PIN material for one person. [pinHash] and [salt] are Base64. */
data class Credential(
    val personId: PersonId,
    val role: Role,
    val pinHash: String?,
    val salt: String?,
) {
    val hasPin: Boolean get() = pinHash != null
    val isActiveAdmin: Boolean get() = role == Role.ADMIN && pinHash != null
}

class LastAdminException : Exception("At least one Admin with a PIN must remain")

data class HomeLocation(
    val name: String,
    val latitude: Double,
    val longitude: Double,
    val timeZoneId: String,
)

/** At most eight people (4a design D12): one for each person colour. */
const val MAX_PEOPLE = 8

/** A person with their role and whether they have a PIN: the people list and the editor (4a design §4.4). */
data class Member(val person: Person, val role: Role, val hasPin: Boolean) {
    val isActiveAdmin: Boolean get() = role == Role.ADMIN && hasPin
}

/**
 * Decides, inside the write's own transaction, whether a new PIN clashes with anyone else's; it throws to refuse.
 * [others] are everyone's credentials but the person being written.
 */
fun interface PinGuard {
    fun check(others: List<Credential>)
}

/** What a member edit does to the PIN. [Set] carries PinManager's hash and salt, both Base64. */
sealed interface PinChange {
    data object Keep : PinChange

    data object Remove : PinChange

    class Set(val hash: String, val salt: String, val guard: PinGuard? = null) : PinChange
}

/** [name] is for the editor's message; the exception's own text holds no name, as it may be logged. */
class DuplicateNameException(val name: String) : Exception("Someone already has that name")

class ColourInUseException : Exception("That colour is already someone's")

/** A ninth person. Still an IllegalArgumentException, but distinct from a blank name. */
class HouseholdFullException : IllegalArgumentException("At most $MAX_PEOPLE people")
