package uk.co.siland.culvery.capability.party

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.emptyPreferences
import java.io.IOException
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.ConcurrentLinkedQueue
import java.util.concurrent.CopyOnWriteArrayList
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
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
import uk.co.siland.culvery.core.house.HouseLights
import uk.co.siland.culvery.core.house.HouseModeSwitch
import uk.co.siland.culvery.core.house.HouseMusic
import uk.co.siland.culvery.core.house.HousePlugs
import uk.co.siland.culvery.core.house.HouseRoom
import uk.co.siland.culvery.core.house.HouseStep
import uk.co.siland.culvery.core.house.LightOptions
import uk.co.siland.culvery.core.house.MusicOptions
import uk.co.siland.culvery.core.house.MusicStart
import uk.co.siland.culvery.core.house.PlugChange
import uk.co.siland.culvery.core.house.PartyGroup
import uk.co.siland.culvery.core.house.RoomBefore
import uk.co.siland.culvery.core.house.SceneChoice
import uk.co.siland.culvery.core.household.Person
import uk.co.siland.culvery.core.household.PersonId
import uk.co.siland.culvery.core.household.Role
import uk.co.siland.culvery.core.plugin.WallClock

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

/** A grant that holds [permissions] only. */
internal fun grant(vararg permissions: String) = Authorised(person, Role.ADULT, permissions.toSet())

/**
 * What the three fake seams share: [calls] in the order made, a [gates] entry that holds each call named by its key
 * (such as "music.start") until completed, and [throws] naming the calls that throw after any gate.
 */
internal class Trace {
    val calls = CopyOnWriteArrayList<String>()
    val gates = ConcurrentHashMap<String, CompletableDeferred<Unit>>()
    val throws: MutableSet<String> = ConcurrentHashMap.newKeySet()

    suspend fun hit(key: String, detail: String = "") {
        calls += if (detail.isEmpty()) key else "$key $detail"
        gates[key]?.await()
        check(key !in throws) { "$key threw" }
    }
}

/** A plug seam that behaves as the real one does about runs: [enter] of a mode never begun is null. */
internal class FakePlugs(private val trace: Trace = Trace()) : HousePlugs {
    val readyNow = MutableStateFlow(true)
    @Volatile var changes: List<PlugChange> = listOf(PlugChange("Lamp", false))
    val begun: MutableSet<String> = ConcurrentHashMap.newKeySet()
    val begins = CopyOnWriteArrayList<Authorised>()
    val enters = ConcurrentLinkedQueue<HouseStep?>()
    val leaves = ConcurrentLinkedQueue<HouseStep>()
    @Volatile var enterOtherwise: HouseStep? = HouseStep.Done
    @Volatile var leaveOtherwise: HouseStep = HouseStep.Done

    override fun ready(modeId: String): Flow<Boolean> = readyNow

    override suspend fun preview(modeId: String): List<PlugChange> = changes

    override suspend fun begin(modeId: String, by: Authorised) {
        trace.hit("plugs.begin", modeId)
        begun += modeId
        begins += by
    }

    override suspend fun enter(modeId: String): HouseStep? {
        trace.hit("plugs.enter", modeId)
        if (modeId !in begun) return null
        return enters.poll() ?: enterOtherwise
    }

    override suspend fun leave(modeId: String): HouseStep {
        trace.hit("plugs.leave", modeId)
        begun -= modeId
        return leaves.poll() ?: leaveOtherwise
    }
}

internal class FakeMusic(private val trace: Trace = Trace()) : HouseMusic {
    val readyNow = MutableStateFlow(true)
    @Volatile var snapshot: List<GroupBefore> = GROUPS_BEFORE
    @Volatile var started: MusicStart = MusicStart(MADE, HouseStep.Done)
    @Volatile var stopped: HouseStep = HouseStep.Done
    @Volatile var putBackAnswer: HouseStep = HouseStep.Done
    val startedWith = CopyOnWriteArrayList<Triple<String, Set<String>?, List<GroupBefore>>>()
    val stops = CopyOnWriteArrayList<Pair<PartyGroup, List<GroupBefore>>>()
    val putBacks = CopyOnWriteArrayList<List<GroupBefore>>()

    override val ready: Flow<Boolean> = readyNow

    override suspend fun options() = MusicOptions(emptyList(), emptyList(), null)

    override suspend fun before(): List<GroupBefore> {
        trace.hit("music.before")
        return snapshot
    }

    override suspend fun start(pickId: String, roomIds: Set<String>?, before: List<GroupBefore>): MusicStart {
        trace.hit("music.start", "$pickId $roomIds")
        startedWith += Triple(pickId, roomIds, before)
        return started
    }

    override suspend fun stop(made: PartyGroup, before: List<GroupBefore>): HouseStep {
        trace.hit("music.stop")
        stops += made to before
        return stopped
    }

    override suspend fun putBack(before: List<GroupBefore>): HouseStep {
        trace.hit("music.putBack")
        putBacks += before
        return putBackAnswer
    }
}

internal class FakeLights(private val trace: Trace = Trace()) : HouseLights {
    val readyNow = MutableStateFlow(true)
    @Volatile var snapshot: List<RoomBefore> = LIGHTS_BEFORE
    @Volatile var notChanged: List<String> = emptyList()
    @Volatile var notBack: List<String> = emptyList()
    val backs = CopyOnWriteArrayList<List<RoomBefore>>()

    override val ready: Flow<Boolean> = readyNow

    override suspend fun setHoliday(on: Boolean, by: Authorised): HouseStep = HouseStep.Done

    override suspend fun partyOptions() = LightOptions(emptyList(), null)

    override suspend fun partyBefore(choices: List<SceneChoice>): List<RoomBefore> {
        trace.hit("lights.partyBefore")
        return snapshot
    }

    override suspend fun partyOn(rooms: List<RoomBefore>): List<String> {
        trace.hit("lights.partyOn")
        return notChanged
    }

    override suspend fun partyBack(rooms: List<RoomBefore>): List<String> {
        trace.hit("lights.partyBack")
        backs += rooms
        return notBack
    }
}

/** Another mode's switch, turned on and off by the test. */
internal class TestSwitch(
    override val modeId: String,
    override val modeName: String,
    override val isOn: MutableStateFlow<Boolean?>,
) : HouseModeSwitch

/** A real [PartyHouse] over an in-memory store and the fake seams. */
internal class HouseRig(scope: CoroutineScope) {
    val trace = Trace()
    val plugs = FakePlugs(trace)
    val music = FakeMusic(trace)
    val lights = FakeLights(trace)
    val settings = PartySettings(MemoryStore(), scope)
    val house = PartyHouse(settings, plugs, music, lights, WallClock { 1_000_000L })

    /** Reads the stored record, as the scheduler's first tick does; the plugs put back on that first read are not counted. */
    suspend fun loaded(): PartyHouse = house.also {
        it.tick()
        trace.calls.clear()
    }

    /** Party mix on every speaker, Bright in Kitchen and Relax in Lounge. */
    suspend fun choose(prefs: PartyPrefs = PartyPrefs(music = PARTY_MIX, scenes = listOf(KITCHEN_BRIGHT, LOUNGE_RELAX))) =
        settings.update { prefs }
}
