package uk.co.siland.culvery.capability.party

import android.util.Log
import java.util.Optional
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import uk.co.siland.culvery.core.house.GroupBefore
import uk.co.siland.culvery.core.house.HouseLights
import uk.co.siland.culvery.core.house.HouseLines
import uk.co.siland.culvery.core.house.HouseModeSwitch
import uk.co.siland.culvery.core.house.HouseModes
import uk.co.siland.culvery.core.house.HouseMusic
import uk.co.siland.culvery.core.house.HousePlugs
import uk.co.siland.culvery.core.house.HouseStep
import uk.co.siland.culvery.core.house.HouseTimings
import uk.co.siland.culvery.core.house.ModeGrant
import uk.co.siland.culvery.core.house.ModePlugs
import uk.co.siland.culvery.core.house.PartyGroup
import uk.co.siland.culvery.core.house.RoomBefore
import uk.co.siland.culvery.core.house.allows
import uk.co.siland.culvery.core.house.isReady
import uk.co.siland.culvery.core.house.line
import uk.co.siland.culvery.core.plugin.WallClock
import uk.co.siland.culvery.core.plugin.attempt
import uk.co.siland.culvery.core.plugin.within

private const val TAG = "StoredModesHouse"

private enum class ModeStep { MUSIC, LIGHTS }

/** The stored mode a change is turning on or off. [toString] leaves out the id. */
data class ModeRunning(val modeId: String, val change: PartyRun) {
    override fun toString() = "ModeRunning($change)"
}

/** What [modeId]'s tile says under it. */
private class ModeLine(val modeId: String, val text: String)

/**
 * Every stored mode, one at a time (13 §2.2–§2.9, §4; 15b §4.3): turning on and off, and the plugs retry run by [tick].
 * The mode is read as it turns on, and its record keeps what to put back, so changes made while it is on apply next
 * time. Each step runs whatever the others answer, and saves what it will have to put back before it changes anything.
 * Retries and the off line's ten minutes are wall millis.
 */
@Singleton
class StoredModesHouse internal constructor(
    private val settings: ModesStore,
    private val plugSeam: HousePlugs?,
    private val music: HouseMusic?,
    private val lights: HouseLights?,
    private val wall: WallClock,
) : HouseModeSwitch {
    @Inject
    constructor(
        settings: ModesStore,
        plugs: Optional<HousePlugs>,
        music: Optional<HouseMusic>,
        lights: Optional<HouseLights>,
        wall: WallClock,
    ) : this(settings, plugs.orElse(null), music.orElse(null), lights.orElse(null), wall)

    private val current = MutableStateFlow<ModeRecord?>(null)
    private val on = MutableStateFlow<Boolean?>(null)
    private val onId = MutableStateFlow<String?>(null)
    private val run = MutableStateFlow<ModeRunning?>(null)
    private val shown = MutableStateFlow<ModeLine?>(null)

    /** Null until the store has answered. */
    val record: StateFlow<ModeRecord?> = current.asStateFlow()

    /** True while any stored mode is on; null until the store has answered. */
    override val isOn: StateFlow<Boolean?> = on.asStateFlow()

    /** The id of the stored mode that is on, or null. */
    val onMode: StateFlow<String?> = onId.asStateFlow()
    val running: StateFlow<ModeRunning?> = run.asStateFlow()

    override val modeId = HouseModes.STORED
    override val modeName: String get() = onId.value?.let { settings.prefsNow.value?.mode(it)?.name } ?: A_MODE
    override val logName = "a stored mode"

    private val mutex = Mutex()

    // The rest is in memory and changed under the mutex: a restart forgets the lines and the last try.
    private var plugsPending = false
    private var lastTry: Long? = null

    private var lines = Lines()
    private var offLine: String? = null
    private var offAt = 0L
    private var offModeId: String? = null

    // True once the record has loaded off, or a mode been turned on, so leftover plugs runs are put back only once.
    private var orphansChecked = false

    // Memory is the truth; true while the store holds an older record than it.
    private var unsaved = false

    /** [modeId]'s tile line: what didn't happen while it is on, or for ten minutes after it went off. */
    fun line(modeId: String): Flow<String?> = shown.map { it?.takeIf { line -> line.modeId == modeId }?.text }.distinctUntilChanged()

    /** Ruling 14: the mode's own flag, a locked plug it adds, or a locked plug its run still holds; true when any can't be read. */
    suspend fun needsPin(modeId: String): Boolean = attempt({
        val mode = settings.prefs.first().mode(modeId)
        mode == null || mode.needsPin ||
            plugSeam != null && (plugSeam.added(modeId).first().any { it.needsPin } || plugSeam.holdsPinPlug(modeId))
    }) { e ->
        Log.w(TAG, "Couldn't read whether a stored mode needs a PIN (${e::class.simpleName}); taking it that it does")
        true
    }

    /** Turns [modeId] on unless a stored mode is on already. The wall may turn on only a mode whose own flag needs no PIN. */
    suspend fun turnOn(modeId: String, grant: ModeGrant) {
        require(grant.allows(modeId)) { "Turning a stored mode on needs its own grant" }
        exclusive(ModeRunning(modeId, PartyRun.TURNING_ON)) {
            if (load().on) return@exclusive
            val mode = settings.prefs.first().mode(modeId) ?: return@exclusive
            // A plug locked since the tap is left out by the plug store instead (15b Ruling 4).
            require(grant is ModeGrant.Person || !mode.needsPin) { "A stored mode that needs a PIN can't turn on with none" }
            orphansChecked = true
            val plugs = readyPlugs(modeId)
            resetRun()
            offLine = null
            if (grant is ModeGrant.Wall) Log.i(TAG, "A stored mode turned on with no PIN")
            set(ModeRecord(modeId = modeId, on = true, since = wall.nowMillis(), musicBefore = groupsWaiting()))
            if (mode.music == null && mode.lights.isEmpty() && plugs == null) lines.nothing = ModesWords.NOTHING_TO_DO
            if (plugs != null) {
                plugsPending = true
                plugs.begin(grant)
                enterPlugs()
            }
            startMusic(mode)
            showLine()
            startLights(mode)
            showLine()
        }
    }

    /**
     * Every part is tried and the mode ends whatever they answer (13 §4.2). The wall's grant is for the mode that is on
     * only, and turns nothing off once that mode needs a PIN: its tile then says to use one (15b Ruling 14, S1).
     */
    suspend fun turnOff(grant: ModeGrant) {
        val seen = current.value ?: settings.record.filterNotNull().first()
        if (!seen.on) return
        exclusive(ModeRunning(seen.onId(), PartyRun.TURNING_OFF)) {
            val record = load()
            if (!record.on) return@exclusive
            val id = record.onId()
            require(grant.allows(id)) { "Turning a stored mode off needs its own grant" }
            if (grant is ModeGrant.Wall && needsPin(id)) {
                Log.w(TAG, "A stored mode wasn't turned off with no PIN, as it needs one now")
                lines.pin = ModesWords.turnOffWithPin(settings.prefs.first().mode(id)?.name ?: A_MODE)
                showLine()
                return@exclusive
            }
            val musicLine = if (record.made != null) stopMusic(record.made, record.musicBefore.orEmpty()) else putGroupsBack()
            val lightsLine = record.lights?.takeIf { it.isNotEmpty() }?.let { putLightsBack(it) }
            val plugsLine = plugsFor(id)?.leave()
            resetRun()
            offLine = Lines(music = musicLine, plugs = plugsLine, lights = lightsLine).joined()
            offAt = wall.nowMillis()
            offModeId = id
            // The mode stays named while its speakers wait to go back, so a restart still shows their line on its tile.
            val waiting = groupsWaiting()
            set(ModeRecord(modeId = id.takeIf { waiting != null }, musicBefore = waiting))
        }
    }

    /** Runs the plugs retry; a tick that finds another change running leaves it to the next minute. */
    override suspend fun tick() {
        if (!mutex.tryLock()) return
        try {
            val record = load()
            if (!orphansChecked) {
                orphansChecked = true
                if (!record.on && plugSeam != null) settings.prefs.first().modes.forEach { ModePlugs(plugSeam, it.id, logName).leaveQuietly() }
            }
            putGroupsBack()?.let(::showGroupsLine)
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

    private suspend fun exclusive(change: ModeRunning, block: suspend () -> Unit) {
        if (!run.compareAndSet(null, change)) return
        try {
            mutex.withLock { block() }
        } finally {
            run.value = null
        }
    }

    /** The first read. A mode that was on gets its plugs retry back. */
    private suspend fun load(): ModeRecord = current.value ?: settings.record.filterNotNull().first().also { record ->
        publish(record)
        if (record.on) {
            plugsPending = readyPlugs(record.onId()) != null
        } else {
            offModeId = record.modeId
        }
    }

    /** Speaker groups saved for a mode's group that was never made: a restart or turn-off put them back (13 §4.3). */
    private fun groupsWaiting(): List<GroupBefore>? = current.value?.let { record -> record.musicBefore.takeIf { record.made == null } }

    /**
     * Puts the waiting groups back once, with nothing paused, and returns the line when they didn't go back. Waits for
     * the music system to open: a put-back sent before then would fail and lose the snapshot.
     */
    private suspend fun putGroupsBack(): String? {
        val record = current.value ?: return null
        val before = groupsWaiting() ?: return null
        if (music == null || !music.ready.isReady()) return null
        val line = answer(ModeStep.MUSIC) { music.putBack(before) }.line(threw = HouseLines.SPEAKERS_NOT_BACK)
        set(record.copy(musicBefore = null, modeId = record.modeId.takeIf { record.on }))
        return line
    }

    private fun showGroupsLine(line: String) {
        if (current.value?.on == true) {
            lines.music = line
        } else {
            offLine = listOfNotNull(offLine, line).joinToString("\n")
            offAt = wall.nowMillis()
        }
        showLine()
    }

    private suspend fun startMusic(mode: StoredMode) {
        // Before the snapshot below, so it is taken of the groups as they went back.
        lines.music = putGroupsBack()
        val choice = mode.music ?: return
        if (music == null || !music.ready.isReady()) return
        val rooms = mode.speakers
        if (rooms?.isEmpty() == true) {
            Log.w(TAG, "A stored mode's MUSIC step has no speaker chosen")
            lines.music = HouseLines.MUSIC_DIDNT_START
            return
        }
        val before = answer(ModeStep.MUSIC) { music.before() }
        if (before == null) {
            lines.music = HouseLines.MUSIC_DIDNT_START
            return
        }
        set(current.value!!.copy(musicBefore = before))
        val started = answer(ModeStep.MUSIC) { music.start(choice.pickId, rooms?.map { it.id }?.toSet(), before) }
        // With no group left standing there is nothing to put back, and a restart must not undo hand changes.
        set(current.value!!.copy(made = started?.made, musicBefore = before.takeIf { started?.made != null }))
        lines.music = started?.step.line(threw = HouseLines.MUSIC_DIDNT_START)
        if (lines.music != null) Log.w(TAG, "A stored mode's MUSIC step didn't work")
    }

    private suspend fun startLights(mode: StoredMode) {
        val choices = mode.lights
        if (choices.isEmpty() || lights == null || !lights.ready.isReady()) return
        val rooms = answer(ModeStep.LIGHTS) { lights.modeBefore(choices) }
        if (rooms == null) {
            lines.lights = HouseLines.lightsDidntChange(choices.map { it.roomName })
            return
        }
        if (rooms.isEmpty()) return
        set(current.value!!.copy(lights = rooms))
        val left = answer(ModeStep.LIGHTS) { lights.modeOn(rooms) } ?: rooms.map { it.name }
        lines.lights = left.takeIf { it.isNotEmpty() }?.let(HouseLines::lightsDidntChange)
        if (lines.lights != null) Log.w(TAG, "A stored mode's LIGHTS step didn't change every room")
    }

    /** No snapshot means the begin never landed, so there is nothing to retry (12 §5.2); a throw is retried. */
    private suspend fun enterPlugs() {
        val plugs = current.value?.takeIf { it.on }?.let { plugsFor(it.onId()) } ?: return
        lastTry = wall.nowMillis()
        when (val step = plugs.enter()) {
            null -> {
                Log.w(TAG, "A stored mode's PLUGS step was missed")
                plugsPending = false
                lines.plugs = HouseLines.PLUGS_MISSED
            }
            HouseStep.Done -> {
                plugsPending = false
                lines.plugs = null
            }
            is HouseStep.Failed -> {
                Log.w(TAG, "A stored mode's PLUGS step didn't work; trying again in five minutes")
                lines.plugs = step.line
            }
        }
        showLine()
    }

    /** The line for music that didn't stop or the groups that didn't come back, or null when they did. */
    private suspend fun stopMusic(made: PartyGroup, before: List<GroupBefore>): String? {
        val line = music?.let { answer(ModeStep.MUSIC) { it.stop(made, before) } }.line(threw = HouseLines.MUSIC_DIDNT_STOP)
        if (line != null) Log.w(TAG, "A stored mode's MUSIC step didn't put back")
        return line
    }

    private suspend fun putLightsBack(rooms: List<RoomBefore>): String? {
        val left = lights?.let { answer(ModeStep.LIGHTS) { it.modeBack(rooms) } } ?: rooms.map { it.name }
        if (left.isEmpty()) return null
        Log.w(TAG, "A stored mode's LIGHTS step didn't put every room back")
        return HouseLines.lightsDidntGoBack(left)
    }

    /** [block]'s answer, or null when it threw, which is logged by step and exception type only. */
    private inline fun <T> answer(step: ModeStep, block: () -> T): T? = attempt(block) { e ->
        Log.w(TAG, "A stored mode's ${step.name} step threw ${e::class.simpleName}")
        null
    }

    private fun resetRun() {
        plugsPending = false
        lastTry = null
        lines = Lines()
    }

    /** Shows [record] and saves it; one that can't be saved still runs, and is saved again at each tick until it lands. */
    private suspend fun set(record: ModeRecord) {
        publish(record)
        persist()
    }

    private suspend fun persist() {
        val record = current.value ?: return
        unsaved = attempt({
            if (record == ModeRecord()) settings.clearRecord() else settings.save(record)
            false
        }) { e ->
            Log.w(TAG, "Couldn't save the record of a stored mode (${e::class.simpleName}); trying again at the next tick")
            true
        }
    }

    private fun publish(record: ModeRecord) {
        current.value = record
        on.value = record.on
        onId.value = if (record.on) record.onId() else null
        showLine()
    }

    private fun showLine() {
        val record = current.value
        shown.value = if (record?.on == true) {
            lines.joined()?.let { ModeLine(record.onId(), it) }
        } else {
            offLine?.let { line -> offModeId?.let { ModeLine(it, line) } }
        }
    }

    private fun plugsFor(modeId: String): ModePlugs? = plugSeam?.let { ModePlugs(it, modeId, logName) }

    /** [modeId]'s plugs, while its list switches any. */
    private suspend fun readyPlugs(modeId: String): ModePlugs? = plugsFor(modeId)?.takeIf { it.ready() }

    /** What didn't happen, one line per kind, in the order the tile shows them. */
    private class Lines(
        var pin: String? = null,
        var nothing: String? = null,
        var music: String? = null,
        var plugs: String? = null,
        var lights: String? = null,
    ) {
        fun joined() = listOfNotNull(pin, nothing, music, plugs, lights).joinToString("\n").ifEmpty { null }
    }

    private companion object {
        /** How a mode is named when its name can't be read. */
        const val A_MODE = "a mode"
    }
}

/** The on mode's id; a record from before 15b holds none, and was Party mode's. */
private fun ModeRecord.onId(): String = modeId ?: HouseModes.PARTY
