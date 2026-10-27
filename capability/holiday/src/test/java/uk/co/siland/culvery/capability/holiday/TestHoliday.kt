package uk.co.siland.culvery.capability.holiday

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.emptyPreferences
import java.io.IOException
import java.util.concurrent.ConcurrentLinkedQueue
import java.util.concurrent.CopyOnWriteArrayList
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import uk.co.siland.culvery.core.access.AccessControl
import uk.co.siland.culvery.core.access.Authorised
import uk.co.siland.culvery.core.access.CorePermissionSource
import uk.co.siland.culvery.core.access.Identified
import uk.co.siland.culvery.core.access.PermissionRegistry
import uk.co.siland.culvery.core.access.PinReason
import uk.co.siland.culvery.core.access.Refusal
import uk.co.siland.culvery.core.house.HouseHeating
import uk.co.siland.culvery.core.house.HouseHoover
import uk.co.siland.culvery.core.house.HouseLights
import uk.co.siland.culvery.core.house.HouseStep
import uk.co.siland.culvery.core.household.Person
import uk.co.siland.culvery.core.household.PersonId
import uk.co.siland.culvery.core.household.Role

/** A settings store in memory; null holds [data] back, as a file not yet read. The next [failingWrites] writes throw. */
internal class MemoryStore(initial: Preferences? = emptyPreferences()) : DataStore<Preferences> {
    val state = MutableStateFlow(initial)
    @Volatile var failingWrites = 0
    private val mutex = Mutex()
    override val data: Flow<Preferences> = state.filterNotNull()

    override suspend fun updateData(transform: suspend (t: Preferences) -> Preferences): Preferences = mutex.withLock {
        if (failingWrites > 0) {
            failingWrites--
            throw IOException("No space left")
        }
        transform(state.value ?: emptyPreferences()).also { state.value = it }
    }
}

/** What a fake seam answers next: [results] in turn, then [otherwise]; [gate] holds each call; [calls] counts them. */
internal open class Script {
    val readyNow = MutableStateFlow(true)
    val results = ConcurrentLinkedQueue<HouseStep>()
    @Volatile var otherwise: HouseStep = HouseStep.Done
    @Volatile var gate: CompletableDeferred<Unit>? = null
    @Volatile var onCall: suspend () -> Unit = {}

    protected suspend fun answer(): HouseStep {
        onCall()
        gate?.await()
        return results.poll() ?: otherwise
    }
}

internal class FakeLights : Script(), HouseLights {
    val calls = CopyOnWriteArrayList<Boolean>()
    override val ready: Flow<Boolean> = readyNow

    override suspend fun setHoliday(on: Boolean, by: Authorised): HouseStep {
        calls += on
        return answer()
    }
}

internal class FakeHeating : Script(), HouseHeating {
    val calls = CopyOnWriteArrayList<Boolean>()
    override val ready: Flow<Boolean> = readyNow

    override suspend fun setEco(on: Boolean): HouseStep {
        calls += on
        return answer()
    }
}

internal class FakeHoover : Script(), HouseHoover {
    val calls = CopyOnWriteArrayList<Unit>()
    override val ready: Flow<Boolean> = readyNow

    override suspend fun start(): HouseStep {
        calls += Unit
        return answer()
    }
}

private val person = Person(PersonId("p"), "P", 0)

/** A grant that holds [permissions] only. */
internal fun grant(vararg permissions: String) = Authorised(person, Role.ADULT, permissions.toSet())

/** A PIN pad answered by someone with [role] (null: cancelled); [asked] holds each call's permissions in order. */
internal class TestAccess(var role: Role?) : AccessControl {
    private val registry = PermissionRegistry(setOf(CorePermissionSource(), HolidayPermissionSource()))
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
