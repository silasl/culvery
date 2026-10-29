package uk.co.siland.culvery.capability.party

import android.util.Log
import java.util.Optional
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import uk.co.siland.culvery.core.access.Authorised
import uk.co.siland.culvery.core.house.GroupBefore
import uk.co.siland.culvery.core.house.HouseLights
import uk.co.siland.culvery.core.house.HouseLines
import uk.co.siland.culvery.core.house.HouseModeSwitch
import uk.co.siland.culvery.core.house.HouseModes
import uk.co.siland.culvery.core.house.HouseMusic
import uk.co.siland.culvery.core.house.HousePermissions
import uk.co.siland.culvery.core.house.HousePlugs
import uk.co.siland.culvery.core.house.HouseStep
import uk.co.siland.culvery.core.house.HouseTimings
import uk.co.siland.culvery.core.house.ModePlugs
import uk.co.siland.culvery.core.house.PartyGroup
import uk.co.siland.culvery.core.house.RoomBefore
import uk.co.siland.culvery.core.house.isReady
import uk.co.siland.culvery.core.house.line
import uk.co.siland.culvery.core.plugin.WallClock
import uk.co.siland.culvery.core.plugin.attempt
import uk.co.siland.culvery.core.plugin.within

private const val TAG = "PartyHouse"

private enum class PartyStep { MUSIC, LIGHTS }

/**
 * The house's Party mode (13 §2.2–§2.9, §4): turning on and off, and the plugs retry run by [tick]. Each step runs
 * whatever the others answer, and saves what it will have to put back before it changes anything. Retries and the off
 * line's ten minutes are wall millis.
 */
@Singleton
class PartyHouse internal constructor(
    private val settings: PartySettings,
    plugs: HousePlugs?,
    private val music: HouseMusic?,
    private val lights: HouseLights?,
    private val wall: WallClock,
) : HouseModeSwitch {
    @Inject
    constructor(
        settings: PartySettings,
        plugs: Optional<HousePlugs>,
        music: Optional<HouseMusic>,
        lights: Optional<HouseLights>,
        wall: WallClock,
    ) : this(settings, plugs.orElse(null), music.orElse(null), lights.orElse(null), wall)

    private val current = MutableStateFlow<PartyRecord?>(null)
    private val on = MutableStateFlow<Boolean?>(null)
    private val run = MutableStateFlow<PartyRun?>(null)
    private val shown = MutableStateFlow<String?>(null)

    /** Null until the store has answered. */
    val record: StateFlow<PartyRecord?> = current.asStateFlow()
    override val isOn: StateFlow<Boolean?> = on.asStateFlow()
    val running: StateFlow<PartyRun?> = run.asStateFlow()

    /** The tile's line: what didn't happen. */
    val line: StateFlow<String?> = shown.asStateFlow()

    override val modeId = HouseModes.PARTY
    override val modeName = PartyWords.LABEL

    private val plugs = plugs?.let { ModePlugs(it, HouseModes.PARTY) }
    private val mutex = Mutex()

    // The rest is in memory and changed under the mutex: a restart forgets the lines and the last try.
    private var plugsPending = false
    private var lastTry: Long? = null

    // True while a restart's saved speaker groups wait for the music system to open.
    private var groupsToPutBack = false
    private var lines = Lines()
    private var offLine: String? = null
    private var offAt = 0L

    // True once Party has loaded off, or been turned on, so a leftover plugs run is put back only once.
    private var orphansChecked = false

    // Memory is the truth; true while the store holds an older record than it.
    private var unsaved = false

    /** What turning on would do, for the sheet. Speaker rooms saved as an empty list count as no speaker chosen. */
    suspend fun plan(): PartyPlan {
        val prefs = settings.prefs.first()
        val musicReady = music?.ready.isReady() && prefs.rooms?.isEmpty() != true
        return PartyPlan(
            plugs = readyPlugs()?.preview().orEmpty(),
            music = prefs.music.takeIf { musicReady },
            scenes = if (lights?.ready.isReady()) prefs.scenes else emptyList(),
            rooms = prefs.rooms,
        )
    }

    /** The off sheet's list, from what the record says Party changed. */
    suspend fun backLines(): List<String> =
        PartyWords.backLines(current.value ?: PartyRecord(), readyPlugs() != null)

    suspend fun turnOn(by: Authorised) {
        require(HousePermissions.PARTY_MODE in by.granted) { "Turning Party mode on needs its own grant" }
        exclusive(PartyRun.TURNING_ON) {
            if (load().on) return@exclusive
            orphansChecked = true
            val prefs = settings.prefs.first()
            val plugs = readyPlugs()
            resetRun()
            offLine = null
            set(PartyRecord(on = true, since = wall.nowMillis()))
            if (plugs != null) {
                plugsPending = true
                plugs.begin(by)
                enterPlugs()
            }
            startMusic(prefs)
            showLine()
            startLights(prefs)
            showLine()
        }
    }

    /** Every part is tried and Party mode ends whatever they answer (13 §4.2). */
    suspend fun turnOff(by: Authorised) {
        require(HousePermissions.PARTY_MODE in by.granted) { "Turning Party mode off needs its own grant" }
        exclusive(PartyRun.TURNING_OFF) {
            val record = load()
            if (!record.on) return@exclusive
            val musicLine = record.made?.let { stopMusic(it, record.musicBefore.orEmpty()) }
            val lightsLine = record.lights?.takeIf { it.isNotEmpty() }?.let { putLightsBack(it) }
            val plugsLine = plugs?.leave()
            resetRun()
            offLine = Lines(musicLine, plugsLine, lightsLine).joined()
            offAt = wall.nowMillis()
            set(PartyRecord())
        }
    }

    /** Runs the plugs retry; a tick that finds another change running leaves it to the next minute. */
    override suspend fun tick() {
        if (!mutex.tryLock()) return
        try {
            val record = load()
            if (!orphansChecked) {
                orphansChecked = true
                if (!record.on) plugs?.leaveQuietly()
            }
            putGroupsBack()
            if (unsaved) persist()
            if (current.value?.on == true) {
                if (plugsPending && !wall.within(lastTry, HouseTimings.RETRY_MS)) enterPlugs()
            } else if (offLine != null && !wall.within(offAt, HouseTimings.OFF_LINE_MS)) {
                offLine = null
                showLine()
            }
        } finally {
            mutex.unlock()
        }
    }

    private suspend fun exclusive(change: PartyRun, block: suspend () -> Unit) {
        if (!run.compareAndSet(null, change)) return
        try {
            mutex.withLock { block() }
        } finally {
            run.value = null
        }
    }

    /**
     * The first read. A party that was on gets its plugs retry back, and speakers whose groups were saved but whose
     * party group never was are put back, once, with nothing paused (13 §4.3).
     */
    private suspend fun load(): PartyRecord = current.value ?: settings.record.filterNotNull().first().also { record ->
        publish(record)
        if (record.on) resume(record)
    }

    private suspend fun resume(record: PartyRecord) {
        plugsPending = readyPlugs() != null
        groupsToPutBack = record.made == null && record.musicBefore != null
        putGroupsBack()
    }

    /** Waits for the music system to open: a put-back sent before then would fail and lose the snapshot. */
    private suspend fun putGroupsBack() {
        val before = current.value?.musicBefore
        if (!groupsToPutBack || before == null || music == null || !music.ready.isReady()) return
        groupsToPutBack = false
        lines.music = answer(PartyStep.MUSIC) { music.putBack(before) }.line(threw = HouseLines.SPEAKERS_NOT_BACK)
        set(current.value!!.copy(musicBefore = null))
    }

    private suspend fun startMusic(prefs: PartyPrefs) {
        val choice = prefs.music ?: return
        if (music == null || !music.ready.isReady()) return
        val rooms = prefs.rooms
        if (rooms?.isEmpty() == true) {
            Log.w(TAG, "Party mode's MUSIC step has no speaker chosen")
            lines.music = HouseLines.MUSIC_DIDNT_START
            return
        }
        val before = answer(PartyStep.MUSIC) { music.before() }
        if (before == null) {
            lines.music = HouseLines.MUSIC_DIDNT_START
            return
        }
        set(current.value!!.copy(musicBefore = before))
        val started = answer(PartyStep.MUSIC) { music.start(choice.pickId, rooms?.map { it.id }?.toSet(), before) }
        // With no group left standing there is nothing to put back, and a restart must not undo hand changes.
        set(current.value!!.copy(made = started?.made, musicBefore = before.takeIf { started?.made != null }))
        lines.music = started?.step.line(threw = HouseLines.MUSIC_DIDNT_START)
        if (lines.music != null) Log.w(TAG, "Party mode's MUSIC step didn't work")
    }

    private suspend fun startLights(prefs: PartyPrefs) {
        val choices = prefs.scenes
        if (choices.isEmpty() || lights == null || !lights.ready.isReady()) return
        val rooms = answer(PartyStep.LIGHTS) { lights.partyBefore(choices) }
        if (rooms == null) {
            lines.lights = HouseLines.lightsDidntChange(choices.map { it.roomName })
            return
        }
        if (rooms.isEmpty()) return
        set(current.value!!.copy(lights = rooms))
        val left = answer(PartyStep.LIGHTS) { lights.partyOn(rooms) } ?: rooms.map { it.name }
        lines.lights = left.takeIf { it.isNotEmpty() }?.let(HouseLines::lightsDidntChange)
        if (lines.lights != null) Log.w(TAG, "Party mode's LIGHTS step didn't change every room")
    }

    /** No snapshot means the begin never landed, so there is nothing to retry (12 §5.2); a throw is retried. */
    private suspend fun enterPlugs() {
        val plugs = plugs ?: return
        lastTry = wall.nowMillis()
        when (val step = plugs.enter()) {
            null -> {
                Log.w(TAG, "Party mode's PLUGS step was missed")
                plugsPending = false
                lines.plugs = HouseLines.PLUGS_MISSED
            }
            HouseStep.Done -> {
                plugsPending = false
                lines.plugs = null
            }
            is HouseStep.Failed -> {
                Log.w(TAG, "Party mode's PLUGS step didn't work; trying again in five minutes")
                lines.plugs = step.line
            }
        }
        showLine()
    }

    /** The line for music that didn't stop or the groups that didn't come back, or null when they did. */
    private suspend fun stopMusic(made: PartyGroup, before: List<GroupBefore>): String? {
        val line = music?.let { answer(PartyStep.MUSIC) { it.stop(made, before) } }.line(threw = HouseLines.MUSIC_DIDNT_STOP)
        if (line != null) Log.w(TAG, "Party mode's MUSIC step didn't put back")
        return line
    }

    private suspend fun putLightsBack(rooms: List<RoomBefore>): String? {
        val left = lights?.let { answer(PartyStep.LIGHTS) { it.partyBack(rooms) } } ?: rooms.map { it.name }
        if (left.isEmpty()) return null
        Log.w(TAG, "Party mode's LIGHTS step didn't put every room back")
        return HouseLines.lightsDidntGoBack(left)
    }

    /** [block]'s answer, or null when it threw, which is logged by step and exception type only. */
    private inline fun <T> answer(step: PartyStep, block: () -> T): T? = attempt(block) { e ->
        Log.w(TAG, "Party mode's ${step.name} step threw ${e::class.simpleName}")
        null
    }

    private fun resetRun() {
        plugsPending = false
        groupsToPutBack = false
        lastTry = null
        lines = Lines()
    }

    /** Shows [record] and saves it; one that can't be saved still runs, and is saved again at each tick until it lands. */
    private suspend fun set(record: PartyRecord) {
        publish(record)
        persist()
    }

    private suspend fun persist() {
        val record = current.value ?: return
        unsaved = attempt({
            if (record.on) settings.save(record) else settings.clearRecord()
            false
        }) { e ->
            Log.w(TAG, "Couldn't save the Party mode record (${e::class.simpleName}); trying again at the next tick")
            true
        }
    }

    private fun publish(record: PartyRecord) {
        current.value = record
        on.value = record.on
        showLine()
    }

    private fun showLine() {
        shown.value = if (current.value?.on == true) {
            lines.joined()
        } else {
            offLine
        }
    }

    /** The plugs, while Party's list switches any. */
    private suspend fun readyPlugs(): ModePlugs? = plugs?.takeIf { it.ready() }

    /** What didn't happen, one line per step, in the order the tile shows them. */
    private class Lines(var music: String? = null, var plugs: String? = null, var lights: String? = null) {
        fun joined() = listOfNotNull(music, plugs, lights).joinToString("\n").ifEmpty { null }
    }
}
