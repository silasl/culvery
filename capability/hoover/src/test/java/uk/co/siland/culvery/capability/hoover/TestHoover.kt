package uk.co.siland.culvery.capability.hoover

import java.util.concurrent.atomic.AtomicInteger
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import uk.co.siland.culvery.core.access.test.FakeAccess
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

/** A PIN pad answered by someone with [role] (null: cancelled) over the Hoover permissions. */
internal fun TestAccess(role: Role?) = FakeAccess(role, HooverPermissionSource())
