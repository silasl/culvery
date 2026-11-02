package uk.co.siland.culvery.core.house

import java.time.LocalDate
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import uk.co.siland.culvery.core.access.Authorised

/** What a device step did: [Failed.line] is the line to show. */
sealed interface HouseStep {
    data object Done : HouseStep

    /** [line] can hold plug names, so it is shown and never logged. */
    data class Failed(val line: String) : HouseStep
}

/** The line to show for a step's answer: none when it was done, its own when it failed, [threw] when there was none. */
fun HouseStep?.line(threw: String): String? = when (this) {
    HouseStep.Done -> null
    is HouseStep.Failed -> line
    null -> threw
}

/** The failure lines the seams and Holiday mode's tile both show (11 §6). */
object HouseLines {
    const val ECO_ON_FAILED = "The heating didn’t go to Eco. Check the Nest app."
    const val ECO_OFF_FAILED = "The heating didn’t come off Eco. Turn it off in the Nest app."
    const val HOOVER_DIDNT_RUN = "The hoover didn’t run."
    const val MUSIC_DIDNT_START = "The music didn’t start. Check the Sonos app."
    const val MUSIC_DIDNT_STOP = "The music didn’t stop. Stop it in the Sonos app."
    const val SPEAKERS_NOT_BACK = "The speakers didn’t go back to their groups. Regroup them in the Sonos app."
    const val PLUGS_THREW = "The plugs didn’t switch. They’ll keep trying."
    const val PLUGS_MISSED = "The plugs didn’t switch. Switch them on the Lights tab."
    const val PLUGS_LEAVE_THREW = "Some plugs may not have gone back. Check them on the Lights tab."
    const val PLUGS_BACK = "Plugs go back as they were when you turn it off."
    const val CANT_REACH_SONOS = "Culvery can’t reach Sonos. Check the speakers are on and the tablet is on the home Wi-Fi."
    const val SONOS_REFUSED = "Sonos didn’t do that. Try again."
    const val HUE_REFUSED = "Hue didn’t do that. Try again."

    /** "Kitchen", "Kitchen and Lounge", "Kitchen, Lounge and Dining". */
    fun roomList(names: List<String>): String =
        if (names.size < 2) names.joinToString() else "${names.dropLast(1).joinToString(", ")} and ${names.last()}"

    fun lightsDidntChange(names: List<String>) = "Some lights didn’t change: ${roomList(names)}."

    fun lightsDidntGoBack(names: List<String>) = "Some lights didn’t go back: ${roomList(names)}."

    fun turnOffFirst(modeName: String) = "Turn $modeName off first."
}

/** Whether a seam's device is chosen: false with no system, else true while its state is known. */
fun Flow<*>?.chosen(): Flow<Boolean> = this?.map { it != null } ?: flowOf(false)

/** A seam's [HouseLights.ready] and the like as it is now; false with no seam. */
suspend fun Flow<Boolean>?.isReady(): Boolean = this?.first() == true

/** The modes' wall-clock spans: a failed step is tried again after [RETRY_MS], and the off line shows for [OFF_LINE_MS]. */
object HouseTimings {
    const val RETRY_MS = 300_000L
    const val OFF_LINE_MS = 600_000L
}

/** What Holiday mode reaches in the house; each is optional, and one that isn't [ready] is no step (11 §5.1). */
interface HouseLights {
    /** True once a Hue link is saved. */
    val ready: Flow<Boolean>
    suspend fun setHoliday(on: Boolean, by: Authorised): HouseStep

    /** Every room and its scenes, read afresh; [LightOptions.line] says why they couldn't be. */
    suspend fun lightOptions(): LightOptions

    /** The chosen rooms read afresh; a scene the bridge no longer has, or a room it no longer has, is left out (13 §2.13). Throws when the bridge can't be read. */
    suspend fun modeBefore(choices: List<RoomChoice>): List<RoomBefore>

    /** Sets each room as chosen; the names of the rooms that didn't change. Never throws. */
    suspend fun modeOn(rooms: List<RoomBefore>): List<String>

    /** Puts back each room that is still as the mode left it, read afresh (13 §2.12, 15b §4.3); the names not put back. Never throws. */
    suspend fun modeBack(rooms: List<RoomBefore>): List<String>
}

interface HouseHeating {
    /** True once a thermostat is chosen. */
    val ready: Flow<Boolean>
    suspend fun setEco(on: Boolean): HouseStep
}

interface HouseHoover {
    /** True once a hoover is chosen. */
    val ready: Flow<Boolean>
    suspend fun start(): HouseStep
}

interface HouseMode {
    /** Null until it has been read. */
    val holiday: StateFlow<Boolean?>
}

object HouseModes {
    const val HOLIDAY = "holiday"
    const val PARTY = "party"

    /** The one switch every stored mode shares (15b Ruling 8). */
    const val STORED = "stored"

    /** Holiday mode's button in Home's tray. */
    const val HOLIDAY_TRAY_ID = "holiday.mode"

    private const val TRAY_PREFIX = "mode."

    /** A stored mode's button in Home's tray. */
    fun trayId(modeId: String) = "$TRAY_PREFIX$modeId"

    /** Whether a tray button is a mode's, Holiday's or a stored mode's. */
    fun isModeTrayId(id: String) = id.startsWith(TRAY_PREFIX) || id == HOLIDAY_TRAY_ID
}

/** What lets a mode's run change the house (15b Ruling 3). Neither [toString] prints a person or an id. */
sealed interface ModeGrant {
    class Person(val by: Authorised) : ModeGrant {
        override fun toString() = "ModeGrant.Person"
    }

    /** No PIN (15b decision 7): the wall vouches for [modeId]'s own choices only, and never for Holiday mode. */
    class Wall(val modeId: String) : ModeGrant {
        init {
            require(modeId != HouseModes.HOLIDAY) { "Holiday mode always needs a PIN" }
        }

        override fun toString() = "ModeGrant.Wall"
    }
}

/** A person needs [modeId]'s permission; the wall must be vouching for [modeId] itself. */
fun ModeGrant.allows(modeId: String): Boolean = when (this) {
    is ModeGrant.Person -> modePermission(modeId) in by.granted
    is ModeGrant.Wall -> this.modeId == modeId
}

/** A mode's on state, for the rule that two modes are never on together (13 §2.6). */
interface HouseModeSwitch {
    val modeId: String
    val modeName: String

    /** How a log line names the mode: "Holiday" or "a stored mode", never its name or id (15b §5). */
    val logName: String

    /** Null until it has been read. */
    val isOn: StateFlow<Boolean?>

    /** True from a turn-on's start until it ends, so one at a time counts the mode as on before its slow steps (15b I1). */
    val turningOn: StateFlow<Boolean>

    /** Runs what is due, at start and each minute; a tick that finds a change running leaves it to the next. */
    suspend fun tick()
}

/** Where the modes sit in Home's tray (15b Ruling 12): bound by the stored modes, read by Holiday mode. */
interface ModeShelf {
    /** Where [modeId] sits among the modes on Home, 0 first; null while it isn't on Home (15b decision 3). */
    fun place(modeId: String): Flow<Int?>
}

/** True while the mode is on or turning on. */
fun HouseModeSwitch.onOrTurningOn(): Boolean = isOn.value == true || turningOn.value

/** The first mode other than [modeId] that is on or turning on. */
fun Set<HouseModeSwitch>.blockedBy(modeId: String): HouseModeSwitch? = firstOrNull { it.modeId != modeId && it.onOrTurningOn() }

/** A plug a mode will switch, or put back, to [on]. [toString] leaves out the name. */
data class PlugChange(val name: String, val on: Boolean) {
    override fun toString() = "PlugChange"
}

/** What a mode reaches in the plugs; absent when no plug system is bound (12 §5.1). */
interface HousePlugs {
    /** True while [modeId]'s list has an On or Off for an added plug and a plug system is bound. */
    fun ready(modeId: String): Flow<Boolean>
    suspend fun preview(modeId: String): List<PlugChange>

    /** Takes the mode's snapshot (12 §5.2); [grant] must allow [modeId], and a wall's leaves out every plug that needs a PIN (15b Ruling 4). Switches nothing. */
    suspend fun begin(modeId: String, grant: ModeGrant)

    /** Switches a begun mode's pending plugs, with no grant; null with no snapshot, and nothing switched. */
    suspend fun enter(modeId: String): HouseStep?
    suspend fun leave(modeId: String): HouseStep

    /** The plugs on the Lights tab, named, in tab order; empty with no plug system (15b §4.2). */
    suspend fun options(): List<PlugOption>

    /** [modeId]'s added plugs, in tab order. */
    fun added(modeId: String): Flow<List<AddedPlug>>

    /** Adds the plug at [on], or takes it out with null; nothing for a plug not on the Lights tab. */
    suspend fun choose(modeId: String, plugId: String, on: Boolean?)

    /** Puts back anything [modeId]'s run still holds, then drops its choices and run (15b §4.2 Delete). Throws when the put-back can't be saved. */
    suspend fun forget(modeId: String)

    /** True while [modeId]'s run holds a plug that now needs a PIN, in its notes or pending (S1); true when the store can't be read. */
    suspend fun holdsPinPlug(modeId: String): Boolean
}

/** A plug a mode's page can add. [toString] leaves out the id and the name. */
data class PlugOption(val id: String, val name: String, val needsPin: Boolean) {
    override fun toString() = "PlugOption"
}

/** A plug a mode switches to [on]. [toString] leaves out the id. */
data class AddedPlug(val id: String, val on: Boolean, val needsPin: Boolean) {
    override fun toString() = "AddedPlug"
}

interface MultiDayEvents {
    suspend fun around(today: LocalDate): List<MultiDayEvent>
}

/** An all-day event over two or more days, [last] being its last day. [toString] leaves out the title and the days. */
data class MultiDayEvent(val title: String, val first: LocalDate, val last: LocalDate) {
    override fun toString() = "MultiDayEvent"
}

object HousePermissions {
    const val HOLIDAY_MODE = "holiday.mode"

    /** Party mode's id kept, for every stored mode (15b Ruling 2). */
    const val STORED_MODE = "party.mode"
}

/** What turning [modeId] on or off takes: Holiday mode its own, every stored mode the one (15b Ruling 2). */
fun modePermission(modeId: String): String =
    if (modeId == HouseModes.HOLIDAY) HousePermissions.HOLIDAY_MODE else HousePermissions.STORED_MODE

/** A speaker room. [toString] leaves out the id and name. */
data class HouseRoom(val id: String, val name: String) {
    override fun toString() = "HouseRoom"
}

/** A favourite or playlist Party can play. [toString] leaves out the id and name. */
data class MusicPick(val id: String, val name: String) {
    override fun toString() = "MusicPick"
}

/** What Settings offers for the music: [line] says why [picks] or [rooms] couldn't be read. */
data class MusicOptions(val picks: List<MusicPick>, val rooms: List<HouseRoom>, val line: String?)

/** A speaker group as it was, [roomIds] in layout order. [toString] leaves out the ids. */
data class GroupBefore(val coordinatorId: String, val roomIds: List<String>) {
    override fun toString() = "GroupBefore"
}

/** The group Party made. [toString] leaves out the ids. */
data class PartyGroup(val coordinatorId: String, val roomIds: Set<String>) {
    override fun toString() = "PartyGroup"
}

/** [made] is the group Party made, or null when none was left standing. */
data class MusicStart(val made: PartyGroup?, val step: HouseStep)

/** What Party reaches in the speakers (13 §4.1, §4.2); absent with no music capability. */
interface HouseMusic {
    /** True while a music system is open. */
    val ready: Flow<Boolean>

    /** Favourites then playlists, and the rooms, read afresh; [MusicOptions.line] says why they couldn't be. */
    suspend fun options(): MusicOptions

    /** Every group as it is now, read afresh; throws when the speakers can't be read. */
    suspend fun before(): List<GroupBefore>

    /** Groups [roomIds] (null: every room) and plays [pickId]; a failed play puts [before] back. Never throws. */
    suspend fun start(pickId: String, roomIds: Set<String>?, before: List<GroupBefore>): MusicStart

    /** Pauses [made] and puts [before] back when [made] is still Party's (13 §2.11), else sends nothing. Never throws. */
    suspend fun stop(made: PartyGroup, before: List<GroupBefore>): HouseStep

    /** Puts [before] back without pausing anything, for a restart that finds the snapshot but no group Party made. Never throws. */
    suspend fun putBack(before: List<GroupBefore>): HouseStep
}

/** A Hue scene. [toString] leaves out the id and name. */
data class HouseScene(val id: String, val name: String) {
    override fun toString() = "HouseScene"
}

/** A Hue room and its scenes; [dimmable] false offers it On only, with no percent (15b Ruling 10). [toString] leaves out the id, name and scenes. */
data class LightRoomOption(val id: String, val name: String, val scenes: List<HouseScene>, val dimmable: Boolean = true) {
    override fun toString() = "LightRoomOption"
}

/** What Settings offers for the lights: [line] says why [rooms] couldn't be read. */
data class LightOptions(val rooms: List<LightRoomOption>, val line: String?)

/** What a mode does to a room (15b §4.2). */
sealed interface LightChoice {
    /** [toString] leaves out the ids and names. */
    data class Scene(val sceneId: String, val sceneName: String) : LightChoice {
        override fun toString() = "Scene"
    }

    /** [percent] is 10–100 in steps of 10 (15b §4.2). */
    data class On(val percent: Int) : LightChoice

    data object Off : LightChoice
}

/** The choice made for a room, with the names the sheet shows. [toString] leaves out the ids and names. */
data class RoomChoice(val roomId: String, val roomName: String, val choice: LightChoice) {
    override fun toString() = "RoomChoice"
}

/** A room as it was before the mode; [choice] is the mode's. [toString] leaves out the ids and name. */
data class RoomBefore(
    val roomId: String,
    val name: String,
    val choice: LightChoice,
    val on: Boolean,
    val brightness: Int,
    val activeSceneId: String?,
) {
    override fun toString() = "RoomBefore"
}
