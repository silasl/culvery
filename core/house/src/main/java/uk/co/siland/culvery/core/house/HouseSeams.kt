package uk.co.siland.culvery.core.house

import java.time.LocalDate
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.StateFlow
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
    const val PLUGS_MISSED = "The plugs didn’t switch. Switch them on the Plugs tab."
    const val PLUGS_LEAVE_THREW = "Some plugs may not have gone back. Check them on the Plugs tab."
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

/** What Holiday mode reaches in the house; each is optional, and one that isn't [ready] is no step (11 §5.1). */
interface HouseLights {
    /** True once a Hue link is saved. */
    val ready: Flow<Boolean>
    suspend fun setHoliday(on: Boolean, by: Authorised): HouseStep

    /** Every room and its scenes, read afresh; [LightOptions.line] says why they couldn't be. */
    suspend fun partyOptions(): LightOptions

    /** The chosen rooms read afresh; a room or scene the bridge no longer has is left out (13 §2.13). Throws when the bridge can't be read. */
    suspend fun partyBefore(choices: List<SceneChoice>): List<RoomBefore>

    /** Recalls each room's scene; the names of the rooms that didn't change. Never throws. */
    suspend fun partyOn(rooms: List<RoomBefore>): List<String>

    /** Puts back each room whose active scene, read afresh, is still Party's (13 §2.12); the names not put back. Never throws. */
    suspend fun partyBack(rooms: List<RoomBefore>): List<String>
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

/** A mode that can list plugs (12 §5.1): [order] sorts its section on the Plugs tab, [permission] is what turning it on takes. */
data class HouseModeInfo(val id: String, val name: String, val order: Int, val permission: String)

object HouseModes {
    const val HOLIDAY = "holiday"
    const val PARTY = "party"
}

/** A mode's on state, for the rule that two modes are never on together (13 §2.6). */
interface HouseModeSwitch {
    val modeId: String
    val modeName: String

    /** Null until it has been read. */
    val isOn: StateFlow<Boolean?>
}

/** The first mode other than [modeId] that is on. */
fun Set<HouseModeSwitch>.blockedBy(modeId: String): HouseModeSwitch? = firstOrNull { it.modeId != modeId && it.isOn.value == true }

/** A plug a mode will switch, or put back, to [on]. [toString] leaves out the name. */
data class PlugChange(val name: String, val on: Boolean) {
    override fun toString() = "PlugChange"
}

/** What a mode reaches in the plugs; absent when no plug system is bound (12 §5.1). */
interface HousePlugs {
    /** True while [modeId]'s list has an On or Off for an added plug and a plug system is bound. */
    fun ready(modeId: String): Flow<Boolean>
    suspend fun preview(modeId: String): List<PlugChange>

    /** Takes the mode's snapshot (12 §5.2); [by] must hold the mode's permission (12 §2.13). Switches nothing. */
    suspend fun begin(modeId: String, by: Authorised)

    /** Switches a begun mode's pending plugs, with no grant; null with no snapshot, and nothing switched. */
    suspend fun enter(modeId: String): HouseStep?
    suspend fun leave(modeId: String): HouseStep
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
    const val PARTY_MODE = "party.mode"
}

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

/** A Hue room and its scenes. [toString] leaves out the id, name and scenes. */
data class LightRoomOption(val id: String, val name: String, val scenes: List<HouseScene>) {
    override fun toString() = "LightRoomOption"
}

/** What Settings offers for the lights: [line] says why [rooms] couldn't be read. */
data class LightOptions(val rooms: List<LightRoomOption>, val line: String?)

/** The scene chosen for a room, with the names the sheet shows. [toString] leaves out the ids and names. */
data class SceneChoice(val roomId: String, val roomName: String, val sceneId: String, val sceneName: String) {
    override fun toString() = "SceneChoice"
}

/** A room as it was before Party; [sceneId] is Party's. [toString] leaves out the ids and name. */
data class RoomBefore(
    val roomId: String,
    val name: String,
    val sceneId: String,
    val on: Boolean,
    val brightness: Int,
    val activeSceneId: String?,
) {
    override fun toString() = "RoomBefore"
}
