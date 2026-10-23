package uk.co.siland.culvery.capability.shopping

import java.util.concurrent.CopyOnWriteArrayList
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import uk.co.siland.culvery.core.access.AccessControl
import uk.co.siland.culvery.core.access.Authorised
import uk.co.siland.culvery.core.access.Identified
import uk.co.siland.culvery.core.access.PinReason
import uk.co.siland.culvery.core.access.Refusal
import uk.co.siland.culvery.core.household.Person
import uk.co.siland.culvery.core.household.PersonId
import uk.co.siland.culvery.core.household.Role

/** Access that answers every PIN pad the same way: an Admin ([admin]) or a cancel. [asked] holds each call's permissions. */
internal class FakeAccess(var admin: Boolean = true) : AccessControl {
    val asked: MutableList<List<String>> = CopyOnWriteArrayList()
    private val person = Person(PersonId("alex"), "Alex", 0)

    override val session: StateFlow<Identified?> = MutableStateFlow(null)
    override val expiresAt: StateFlow<Long?> = MutableStateFlow(null)

    override suspend fun authorise(
        vararg anyOf: String,
        reason: PinReason,
        allow: (Identified, Set<String>) -> Boolean,
        refusal: Refusal,
    ): Authorised? {
        asked += anyOf.toList()
        return if (admin) Authorised(person, Role.ADMIN, anyOf.toSet()) else null
    }

    override fun beginSetupSession(person: Identified) = Unit

    override fun endSetupSession() = Unit

    override fun touch(holdForEdits: Boolean) = Unit

    override fun checkExpiry() = Unit

    override fun lock() = Unit
}

/** [ShoppingConnections] over the rig's real store, sync and lock, with a drain loop that has not been started. */
internal class ConnectionsRig(
    val rig: ShoppingRig,
    scope: CoroutineScope,
    val access: FakeAccess = FakeAccess(),
    val toasts: ToastLog = ToastLog(),
    providers: Set<ShoppingProvider> = setOf(rig.provider),
) {
    val loop = ShoppingDrainLoop(rig.sync::drain, { rig.clock.nowMillis() }, scope)
    val connections = ShoppingConnections(rig.store, providers, rig.sync, rig.lock, loop, rig.learning, access, toasts, scope)
}
