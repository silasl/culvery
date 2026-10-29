package uk.co.siland.culvery.capability.party

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.emptyPreferences
import java.io.IOException
import java.util.concurrent.CopyOnWriteArrayList
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
import uk.co.siland.culvery.core.house.GroupBefore
import uk.co.siland.culvery.core.house.HouseRoom
import uk.co.siland.culvery.core.house.PartyGroup
import uk.co.siland.culvery.core.house.RoomBefore
import uk.co.siland.culvery.core.house.SceneChoice
import uk.co.siland.culvery.core.household.Person
import uk.co.siland.culvery.core.household.PersonId
import uk.co.siland.culvery.core.household.Role

/**
 * A settings store in memory; null holds [data] back, as a file not yet read. The write numbered [failOnWrite], counting
 * from the next one, throws, and the others go through.
 */
internal class MemoryStore(initial: Preferences? = emptyPreferences()) : DataStore<Preferences> {
    val state = MutableStateFlow(initial)
    @Volatile var failOnWrite: Int? = null
    private val mutex = Mutex()
    override val data: Flow<Preferences> = state.filterNotNull()

    override suspend fun updateData(transform: suspend (t: Preferences) -> Preferences): Preferences = mutex.withLock {
        failOnWrite?.let {
            failOnWrite = if (it > 1) it - 1 else null
            if (it == 1) throw IOException("No space left")
        }
        transform(state.value ?: emptyPreferences()).also { state.value = it }
    }
}

/** A PIN pad answered by someone with [role] (null: cancelled); [asked] holds each call's permissions in order. */
internal class TestAccess(var role: Role?) : AccessControl {
    private val registry = PermissionRegistry(setOf(CorePermissionSource(), PartyPermissionSource()))
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

private val person = Person(PersonId("p"), "P", 0)

internal val KITCHEN = HouseRoom("Kitchen-id", "Kitchen")
internal val LOUNGE = HouseRoom("Lounge-id", "Lounge")
internal val DINING = HouseRoom("Dining-id", "Dining")

internal val PARTY_MIX = MusicChoice("FV:2/3", "Party mix")
internal val KITCHEN_BRIGHT = SceneChoice("r1", "Kitchen", "s1", "Bright")
internal val LOUNGE_RELAX = SceneChoice("r2", "Lounge", "s3", "Relax")

internal val GROUPS_BEFORE = listOf(
    GroupBefore("Kitchen-id", listOf("Kitchen-id")),
    GroupBefore("Lounge-id", listOf("Lounge-id", "Dining-id")),
)
internal val MADE = PartyGroup("Lounge-id", setOf("Kitchen-id", "Lounge-id", "Dining-id"))
internal val LIGHTS_BEFORE = listOf(
    RoomBefore("r1", "Kitchen", "s1", true, 40, "s2"),
    RoomBefore("r2", "Lounge", "s3", false, 0, null),
)

internal val FULL_PREFS = PartyPrefs(music = PARTY_MIX, rooms = listOf(KITCHEN, LOUNGE), scenes = listOf(KITCHEN_BRIGHT, LOUNGE_RELAX))
internal val FULL_RECORD = PartyRecord(
    on = true,
    since = 1_000,
    musicBefore = GROUPS_BEFORE,
    made = MADE,
    lights = LIGHTS_BEFORE,
)
