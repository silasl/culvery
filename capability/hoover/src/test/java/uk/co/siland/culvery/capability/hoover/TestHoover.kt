package uk.co.siland.culvery.capability.hoover

import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.atomic.AtomicInteger
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import uk.co.siland.culvery.core.access.AccessControl
import uk.co.siland.culvery.core.access.Authorised
import uk.co.siland.culvery.core.access.CorePermissionSource
import uk.co.siland.culvery.core.access.Identified
import uk.co.siland.culvery.core.access.PermissionRegistry
import uk.co.siland.culvery.core.access.PinReason
import uk.co.siland.culvery.core.access.Refusal
import uk.co.siland.culvery.core.household.Person
import uk.co.siland.culvery.core.household.PersonId
import uk.co.siland.culvery.core.household.Role

internal fun docked(battery: Int? = 86) = HooverState(HooverActivity.DOCKED, battery, null)

/** A hoover a test drives: its state, what start() answers, a gate to hold it, and counts of starts, cancels and watchers. */
internal class FakeHooverSystem(initial: HooverState? = docked()) : HooverSystem {
    val current = MutableStateFlow(initial)
    override val state: Flow<HooverState?> = current

    @Volatile var next: HooverResult = HooverResult.Started

    /** While set, start() waits for it. */
    @Volatile var gate: CompletableDeferred<Unit>? = null

    private val startCount = AtomicInteger()
    private val watcherCount = AtomicInteger()
    private val cancelCount = AtomicInteger()
    val starts get() = startCount.get()
    val watchers get() = watcherCount.get()
    val cancelled get() = cancelCount.get()

    override suspend fun start(): HooverResult {
        startCount.incrementAndGet()
        try {
            gate?.await()
        } catch (e: CancellationException) {
            cancelCount.incrementAndGet()
            throw e
        }
        return next
    }

    override fun watch(): () -> Unit {
        watcherCount.incrementAndGet()
        return { watcherCount.decrementAndGet() }
    }
}

private val person = Person(PersonId("p"), "P", 0)

/** A PIN pad answered by someone with [role] (null: cancelled); [asked] holds each call's permissions in order. */
internal class TestAccess(var role: Role?) : AccessControl {
    private val registry = PermissionRegistry(setOf(CorePermissionSource(), HooverPermissionSource()))
    val asked: MutableList<List<String>> = CopyOnWriteArrayList()

    override val session: StateFlow<Identified?> = MutableStateFlow(null)
    override val expiresAt: StateFlow<Long?> = MutableStateFlow(null)

    override suspend fun authorise(
        vararg anyOf: String,
        reason: PinReason,
        allow: (Identified, Set<String>) -> Boolean,
        refusal: Refusal,
        label: String?,
    ): Authorised? {
        asked += anyOf.toList()
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
