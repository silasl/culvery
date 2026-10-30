package uk.co.siland.culvery.core.access.test

import java.util.concurrent.CopyOnWriteArrayList
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import uk.co.siland.culvery.core.access.AccessControl
import uk.co.siland.culvery.core.access.Authorised
import uk.co.siland.culvery.core.access.CorePermissionSource
import uk.co.siland.culvery.core.access.Identified
import uk.co.siland.culvery.core.access.PermissionRegistry
import uk.co.siland.culvery.core.access.PermissionSource
import uk.co.siland.culvery.core.access.PinReason
import uk.co.siland.culvery.core.access.Refusal
import uk.co.siland.culvery.core.household.Person
import uk.co.siland.culvery.core.household.PersonId
import uk.co.siland.culvery.core.household.Role

private val person = Person(PersonId("p"), "P", 0)

/**
 * A PIN pad answered by someone with [role] (null: cancelled), over the real permission rules of [sources]. It asks the
 * pad for every permission, a fresh-PIN one or not, and [session] is settable but never consulted by [authorise].
 * [asked] holds each call's permissions in order, and [padGate] holds the answer back.
 */
class FakeAccess(var role: Role?, vararg sources: PermissionSource) : AccessControl {
    @Volatile var padGate: CompletableDeferred<Unit>? = null
    private val registry = PermissionRegistry(setOf(CorePermissionSource(), *sources))
    val asked: MutableList<List<String>> = CopyOnWriteArrayList()

    override val session = MutableStateFlow<Identified?>(null)
    override val expiresAt: StateFlow<Long?> = MutableStateFlow(null)

    override suspend fun authorise(
        vararg anyOf: String,
        reason: PinReason,
        allow: (Identified, Set<String>) -> Boolean,
        refusal: Refusal,
        label: String?,
    ): Authorised? {
        asked += anyOf.toList()
        padGate?.await()
        val role = role ?: return null
        val granted = anyOf.filter { registry.isGranted(role, it) }.toSet()
        return if (granted.isEmpty()) null else Authorised(person, role, granted)
    }

    override fun beginSetupSession(person: Identified) = Unit

    override fun endSetupSession() = Unit

    override fun touch(holdForEdits: Boolean) = Unit

    override fun checkExpiry() = Unit

    override fun lock() = Unit
}
