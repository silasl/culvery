package uk.co.siland.culvery.capability.plugs

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.emptyPreferences
import java.io.IOException
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.ConcurrentLinkedQueue
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.atomic.AtomicInteger
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
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
import uk.co.siland.culvery.core.plugin.ToastAction
import uk.co.siland.culvery.core.plugin.Toaster

/**
 * A settings store in memory; null holds [data] back, as a file not yet read. [writeGate] holds the next write until it
 * completes, [failOnWrite] makes that numbered write (from 1) throw, and [failReads] makes [data] throw; [writeCount] is the writes tried so far.
 */
internal class MemoryStore(initial: Preferences? = emptyPreferences()) : DataStore<Preferences> {
    val state = MutableStateFlow(initial)
    @Volatile var writeGate: CompletableDeferred<Unit>? = null
    @Volatile var failOnWrite: Int? = null
    @Volatile var failReads = false
    private val writes = AtomicInteger()
    val writeCount: Int get() = writes.get()
    private val mutex = Mutex()

    override val data: Flow<Preferences> = flow {
        if (failReads) throw IOException("Unreadable")
        emitAll(state.filterNotNull())
    }

    override suspend fun updateData(transform: suspend (t: Preferences) -> Preferences): Preferences = mutex.withLock {
        val number = writes.incrementAndGet()
        writeGate?.let {
            writeGate = null
            it.await()
        }
        if (failOnWrite == number) throw IOException("No space left")
        transform(state.value ?: emptyPreferences()).also { state.value = it }
    }
}

/**
 * A plug system answering from [states]. [readGates] and [switchGates] hold a call after it is recorded; [results]
 * answer `switch` in turn, and a `Done` moves the plug to what was asked.
 */
internal class FakePlugSystem(initial: List<PlugState> = emptyList()) : PlugSystem {
    val states = MutableStateFlow(initial.associateBy { it.entityId })
    val added = MutableStateFlow<List<PlugState>?>(initial)
    val readGates = ConcurrentHashMap<String, CompletableDeferred<Unit>>()
    val switchGates = ConcurrentHashMap<String, CompletableDeferred<Unit>>()
    val results = ConcurrentLinkedQueue<PlugResult>()
    val reads = CopyOnWriteArrayList<String>()
    val switches = CopyOnWriteArrayList<Pair<String, Boolean>>()
    @Volatile var throwOnSwitch = false
    @Volatile var found: PlugCandidates = PlugCandidates.Found(emptyList())
    private val watching = AtomicInteger()
    val watchers: Int get() = watching.get()

    override val plugs: Flow<List<PlugState>?> = added
    override val problem: Flow<String?> = MutableStateFlow(null)

    override fun watch(): () -> Unit {
        watching.incrementAndGet()
        return { watching.decrementAndGet() }
    }

    override suspend fun candidates() = found

    override suspend fun read(entityId: String): PlugState {
        reads += entityId
        readGates[entityId]?.await()
        return states.value[entityId] ?: PlugState(entityId, PlugsWords.A_PLUG, PlugPower.GONE, null)
    }

    override suspend fun switch(entityId: String, on: Boolean): PlugResult {
        switches += entityId to on
        switchGates[entityId]?.await()
        if (throwOnSwitch) throw IOException("Unreachable")
        val result = results.poll() ?: PlugResult.Done
        if (result == PlugResult.Done) {
            states.value[entityId]?.let { states.value += entityId to it.copy(power = if (on) PlugPower.ON else PlugPower.OFF) }
        }
        return result
    }
}

private val person = Person(PersonId("p"), "P", 0)

/** A grant that holds [permissions] only. */
internal fun grant(vararg permissions: String) = Authorised(person, Role.ADULT, permissions.toSet())

/** A PIN pad answered by someone with [role] (null: cancelled); [asked] holds each call's permissions in order. */
internal class TestAccess(var role: Role?) : AccessControl {
    private val registry = PermissionRegistry(setOf(CorePermissionSource(), PlugsPermissionSource()))
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

/** A toaster that keeps what it was asked to show, in order. */
internal class RecordingToaster : Toaster {
    val shown: MutableList<String> = CopyOnWriteArrayList()

    override fun show(message: String, icon: String, urgent: Boolean, action: ToastAction?) {
        shown += message
    }
}
