package uk.co.siland.culvery.core.homeassistant

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.emptyPreferences
import java.util.concurrent.CopyOnWriteArrayList
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import uk.co.siland.culvery.core.access.test.FakeAccess
import uk.co.siland.culvery.core.household.Person
import uk.co.siland.culvery.core.household.PersonId
import uk.co.siland.culvery.core.household.Role

/** A DataStore in memory, so a test's virtual time never waits on a real disk. */
internal class MemoryStore : DataStore<Preferences> {
    private val state = MutableStateFlow(emptyPreferences())
    override val data: Flow<Preferences> = state

    override suspend fun updateData(transform: suspend (t: Preferences) -> Preferences): Preferences =
        transform(state.value).also { state.value = it }
}

private val person = Person(PersonId("p"), "P", 0)

/** A PIN pad answered by someone with [role] (null: cancelled). */
internal fun TestAccess(role: Role?) = FakeAccess(role)

/** A kind of device with a picker: what it chose is [chosen], what it was told is [picks]. */
internal class FakeDevice(
    override val domain: String,
    override val title: String,
    override val order: Int,
    override val noneFound: String,
    chosen: String? = null,
) : HomeAssistantDevice {
    val picked = MutableStateFlow(chosen)
    val picks = CopyOnWriteArrayList<String?>()
    val watchedIds = MutableStateFlow<Set<String>>(emptySet())
    val lineText = MutableStateFlow<String?>(null)

    override val chosen: Flow<String?> = picked
    override val watched: Flow<Set<String>> = watchedIds
    override val line: Flow<String?> = lineText

    /** Thrown by [choose] when set. */
    var failure: java.io.IOException? = null

    override suspend fun choose(entityId: String?) {
        failure?.let { throw it }
        picks += entityId
        picked.value = entityId
    }
}

internal fun hoover(chosen: String? = null) = FakeDevice("vacuum", "Hoover", 10, "Home Assistant has no hoover.", chosen)

internal fun thermostat(chosen: String? = null) = FakeDevice("climate", "Thermostat", 20, "Home Assistant has no thermostat.", chosen)

internal val SAVED = HomeAssistantLink(checkNotNull(homeAssistantUrl("192.168.1.20")), "test-token", pin = null)
