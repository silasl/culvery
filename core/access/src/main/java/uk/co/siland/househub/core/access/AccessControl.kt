package uk.co.siland.househub.core.access

import kotlinx.coroutines.flow.StateFlow
import uk.co.siland.househub.core.household.Person
import uk.co.siland.househub.core.household.Role

data class Authorised(val person: Person, val role: Role, val granted: Set<String>)

const val SESSION_TIMEOUT_MS = 60_000L

interface AccessControl {
    /** The person currently identified, or null once the session has timed out or been locked. */
    val session: StateFlow<Identified?>

    /**
     * Succeeds if the identified person (or whoever enters a PIN) holds at least one of [anyOf].
     * Shows the PIN pad when needed, and always for fresh-PIN permissions. Returns null if cancelled.
     */
    suspend fun authorise(vararg anyOf: String): Authorised?

    /** Call on each touch-down; extends an active session. */
    fun touch()

    fun lock()
}
