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
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import uk.co.siland.culvery.core.access.Authorised
import uk.co.siland.culvery.core.access.test.FakeAccess
import uk.co.siland.culvery.core.house.GroupBefore
import uk.co.siland.culvery.core.house.HouseLights
import uk.co.siland.culvery.core.house.AddedPlug
import uk.co.siland.culvery.core.house.HouseModeSwitch
import uk.co.siland.culvery.core.house.HouseModes
import uk.co.siland.culvery.core.house.HouseMusic
import uk.co.siland.culvery.core.house.HousePlugs
import uk.co.siland.culvery.core.house.HouseRoom
import uk.co.siland.culvery.core.house.HouseStep
import uk.co.siland.culvery.core.house.LightChoice
import uk.co.siland.culvery.core.house.LightOptions
import uk.co.siland.culvery.core.house.MusicOptions
import uk.co.siland.culvery.core.house.MusicStart
import uk.co.siland.culvery.core.house.ModeGrant
import uk.co.siland.culvery.core.house.PartyGroup
import uk.co.siland.culvery.core.house.PlugChange
import uk.co.siland.culvery.core.house.PlugOption
import uk.co.siland.culvery.core.house.RoomBefore
import uk.co.siland.culvery.core.house.RoomChoice
import uk.co.siland.culvery.core.household.Person
import uk.co.siland.culvery.core.household.PersonId
import uk.co.siland.culvery.core.household.Role
import uk.co.siland.culvery.core.plugin.WallClock
import uk.co.siland.culvery.core.ui.Icons

/** What a fresh file holds once [PartyMove] has run: Party mode with nothing to do. */
internal val MOVED: Preferences = runBlocking { PartyMove.migrate(emptyPreferences()) }

/**
 * A settings store in memory, which runs no move, so it starts [MOVED]; null holds [data] back, as a file not yet read.
 * The write numbered [failOnWrite], counting from the next one, throws, and the others go through.
 */
internal class MemoryStore(initial: Preferences? = MOVED) : DataStore<Preferences> {
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

/** A PIN pad answered by someone with [role] (null: cancelled) over the Party permissions. */
internal fun TestAccess(role: Role?) = FakeAccess(role, PartyPermissionSource())

private val person = Person(PersonId("p"), "P", 0)

internal val KITCHEN = HouseRoom("Kitchen-id", "Kitchen")
internal val LOUNGE = HouseRoom("Lounge-id", "Lounge")
internal val DINING = HouseRoom("Dining-id", "Dining")

internal val PARTY_MIX = MusicChoice("FV:2/3", "Party mix")
internal val KITCHEN_BRIGHT = RoomChoice("r1", "Kitchen", LightChoice.Scene("s1", "Bright"))
internal val LOUNGE_RELAX = RoomChoice("r2", "Lounge", LightChoice.Scene("s3", "Relax"))

internal val GROUPS_BEFORE = listOf(
    GroupBefore("Kitchen-id", listOf("Kitchen-id")),
    GroupBefore("Lounge-id", listOf("Lounge-id", "Dining-id")),
)
internal val MADE = PartyGroup("Lounge-id", setOf("Kitchen-id", "Lounge-id", "Dining-id"))
internal val LIGHTS_BEFORE = listOf(
    RoomBefore("r1", "Kitchen", LightChoice.Scene("s1", ""), true, 40, "s2"),
    RoomBefore("r2", "Lounge", LightChoice.Scene("s3", ""), false, 0, null),
)

internal val PARTY_MODE = StoredMode(HouseModes.PARTY, "Party mode", PARTY_ICON, showOnHome = true, needsPin = true)
internal val FULL_MODE = PARTY_MODE.copy(music = PARTY_MIX, speakers = listOf(KITCHEN, LOUNGE), lights = listOf(KITCHEN_BRIGHT, LOUNGE_RELAX))
internal val FULL_PREFS = ModesPrefs(modes = listOf(FULL_MODE))
internal val FULL_RECORD = ModeRecord(
    modeId = HouseModes.PARTY,
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
    val grants = CopyOnWriteArrayList<ModeGrant>()
    val addedNow = MutableStateFlow<List<AddedPlug>>(emptyList())
    @Volatile var addedThrows = false
    @Volatile var holdsPin = false

    /** The people whose grants began a run; a wall's begin adds none. */
    val begins: List<Authorised> get() = grants.filterIsInstance<ModeGrant.Person>().map { it.by }
    val enters = ConcurrentLinkedQueue<HouseStep?>()
    val leaves = ConcurrentLinkedQueue<HouseStep>()
    @Volatile var enterOtherwise: HouseStep? = HouseStep.Done
    @Volatile var leaveOtherwise: HouseStep = HouseStep.Done

    override fun ready(modeId: String): Flow<Boolean> = readyNow

    override suspend fun preview(modeId: String): List<PlugChange> = changes

    override suspend fun begin(modeId: String, grant: ModeGrant) {
        trace.hit("plugs.begin", modeId)
        begun += modeId
        grants += grant
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

    override suspend fun options() = emptyList<PlugOption>()

    override fun added(modeId: String): Flow<List<AddedPlug>> = addedNow.onEach { check(!addedThrows) { "added threw" } }

    override suspend fun choose(modeId: String, plugId: String, on: Boolean?) = Unit

    override suspend fun forget(modeId: String) = Unit

    override suspend fun holdsPinPlug(modeId: String) = holdsPin
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

    @Volatile var offered = MusicOptions(emptyList(), emptyList(), null)

    /** Holds [options] until completed; [optionsThrow] makes it throw after that. */
    @Volatile var optionsGate: CompletableDeferred<Unit>? = null
    @Volatile var optionsThrow = false

    override suspend fun options(): MusicOptions {
        optionsGate?.await()
        check(!optionsThrow) { "options threw" }
        return offered
    }

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
    val asked = CopyOnWriteArrayList<List<RoomChoice>>()

    override val ready: Flow<Boolean> = readyNow

    override suspend fun setHoliday(on: Boolean, by: Authorised): HouseStep = HouseStep.Done

    @Volatile var offered = LightOptions(emptyList(), null)

    @Volatile var optionsGate: CompletableDeferred<Unit>? = null
    @Volatile var optionsThrow = false

    override suspend fun lightOptions(): LightOptions {
        optionsGate?.await()
        check(!optionsThrow) { "lightOptions threw" }
        return offered
    }

    override suspend fun modeBefore(choices: List<RoomChoice>): List<RoomBefore> {
        trace.hit("lights.modeBefore")
        asked += choices
        return snapshot
    }

    override suspend fun modeOn(rooms: List<RoomBefore>): List<String> {
        trace.hit("lights.modeOn")
        return notChanged
    }

    override suspend fun modeBack(rooms: List<RoomBefore>): List<String> {
        trace.hit("lights.modeBack")
        backs += rooms
        return notBack
    }
}

/** Another mode's switch, turned on and off by the test. */
internal class TestSwitch(
    override val modeId: String,
    override val modeName: String,
    override val isOn: MutableStateFlow<Boolean?>,
    override val logName: String = modeName,
) : HouseModeSwitch {
    override suspend fun tick() = Unit
}

/** Film night, a mode the family added: no PIN, Party mix on every speaker and Bright in Kitchen. */
internal const val FILM_ID = "modeabcdef"
internal val FILM_NIGHT = StoredMode(FILM_ID, "Film night", Icons.BEDTIME, showOnHome = true, needsPin = false, music = PARTY_MIX, lights = listOf(KITCHEN_BRIGHT))

/** A plug a mode added, On, that needs a PIN. */
internal val LOCKED_HEATER = AddedPlug("heater", on = true, needsPin = true)

/** A real [StoredModesHouse] over an in-memory store and the fake seams. */
internal class HouseRig(scope: CoroutineScope) {
    val trace = Trace()
    val plugs = FakePlugs(trace)
    val music = FakeMusic(trace)
    val lights = FakeLights(trace)
    val settings = ModesStore(MemoryStore(), scope)
    val house = StoredModesHouse(settings, plugs, music, lights, WallClock { 1_000_000L })

    /** Reads the stored record, as the scheduler's first tick does; the plugs put back on that first read are not counted. */
    suspend fun loaded(): StoredModesHouse = house.also {
        it.tick()
        trace.calls.clear()
    }

    /** Party mix on every speaker, Bright in Kitchen and Relax in Lounge. */
    suspend fun choose(vararg modes: StoredMode = arrayOf(PARTY_MODE.copy(music = PARTY_MIX, lights = listOf(KITCHEN_BRIGHT, LOUNGE_RELAX)))) =
        settings.update { ModesPrefs(modes = modes.toList()) }
}
