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
