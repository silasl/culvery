package uk.co.siland.culvery.capability.party

import uk.co.siland.culvery.core.house.HouseLines
import uk.co.siland.culvery.core.house.HouseRoom
import uk.co.siland.culvery.core.house.PlugChange
import uk.co.siland.culvery.core.house.SceneChoice

/** What Party will do, as the sheet lists it. [toString] leaves out the choices. */
data class PartyPlan(
    val plugs: List<PlugChange>,
    val music: MusicChoice?,
    val scenes: List<SceneChoice>,
    val rooms: List<HouseRoom>? = null,
) {
    val nothing: Boolean get() = plugs.isEmpty() && music == null && scenes.isEmpty()

    override fun toString() = "PartyPlan"
}

/** The change the tile is making, while neither "on" nor "off" is yet true. */
enum class PartyRun { TURNING_ON, TURNING_OFF }

/** Party mode's words (13 §5). */
object PartyWords {
    const val LABEL = "Party mode"
    const val ON = "On"
    const val OFF = "Off"
    const val WILL = "Party mode will:"
    const val WHEN_ENDS = "When Party mode ends:"
    const val TURN_ON_BUTTON = "Turn Party mode on"
    const val TURN_OFF_BUTTON = "Turn Party mode off"
    const val CANCEL = "Cancel"
    const val TURNING_ON = "Turning on…"
    const val TURNING_OFF = "Turning off…"
    const val CLICK_ON = "Turn on"
    const val CLICK_OFF = "Turn off"
    const val NOTHING_TO_DO = "Party mode has nothing to do yet. Choose plugs, music and lights in Settings › Party mode."
    const val NOTHING_TO_PUT_BACK = "Party mode changed nothing, so there’s nothing to put back."
    const val STOP_MUSIC = "Stop the music and put the speakers back in their groups."
    const val PLUGS_BACK_NOW = "Put the plugs back as they were."
    const val BY_HAND = "Anything changed by hand during the party stays as it is."

    const val SETTINGS_TITLE = "Party mode"
    const val MUSIC_SECTION = "Music"
    const val SPEAKERS = "Speakers"
    const val NONE = "None"
    const val MUSIC_SHEET_TITLE = "Music for Party mode"
    const val CHOOSE_MUSIC = "Choose the music for Party mode"
    const val LIGHTS_SECTION = "Lights"
    const val LEAVE_ALONE = "Leave alone"
    const val PLUGS_SECTION = "Plugs"
    const val PLUGS_LINE = "Choose what each plug does in Party mode on the Plugs tab."
    const val SET_UP_SONOS = "Set up Sonos in Settings › Music to choose the music."
    const val CANT_REACH_SONOS = "Culvery can’t reach Sonos. Check the speakers are on and the tablet is on the home Wi-Fi."
    const val HUE_REFUSED = "Hue didn’t do that. Try again."
    const val SET_UP_HUE = "Set up Hue in Settings › Lights to choose scenes."

    /** What a tap on a light room's row does. */
    fun chooseScene(room: String) = "Choose a scene for $room"

    /** "Play {playlist} on every speaker." or on the [rooms] chosen. */
    fun musicLine(choice: MusicChoice, rooms: List<HouseRoom>? = null): String =
        "Play ${choice.pickName} on ${rooms?.let { HouseLines.roomList(it.map(HouseRoom::name)) } ?: "every speaker"}."

    /** The on sheet's list, in the order the steps run. */
    fun willLines(plan: PartyPlan): List<String> {
        if (plan.nothing) return listOf(NOTHING_TO_DO)
        return buildList {
            if (plan.plugs.isNotEmpty()) {
                plan.plugs.forEach { add("Switch ${it.name} ${if (it.on) "on" else "off"}.") }
                add(HouseLines.PLUGS_BACK)
            }
            plan.music?.let { add(musicLine(it, plan.rooms)) }
            plan.scenes.forEach { add("${it.sceneName} in ${it.roomName}.") }
        }
    }

    /** The off sheet's list: only what the record says Party changed. */
    fun backLines(record: PartyRecord, plugsListed: Boolean): List<String> {
        val lightRooms = record.lights.orEmpty().map { it.name }
        val lines = buildList {
            if (record.made != null) add(STOP_MUSIC)
            if (plugsListed) add(PLUGS_BACK_NOW)
            if (lightRooms.isNotEmpty()) add("Put the lights back in ${HouseLines.roomList(lightRooms)}.")
        }
        return if (lines.isEmpty()) listOf(NOTHING_TO_PUT_BACK) else lines + BY_HAND
    }

    fun tileSpoken(on: Boolean) = if (on) "Party mode, on." else "Party mode, off."

    fun runningSpoken(run: PartyRun): String = when (run) {
        PartyRun.TURNING_ON -> "Party mode, turning on."
        PartyRun.TURNING_OFF -> "Party mode, turning off."
    }
}
