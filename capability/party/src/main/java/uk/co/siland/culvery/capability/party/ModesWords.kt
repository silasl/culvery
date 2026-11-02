package uk.co.siland.culvery.capability.party

/** The change a mode's tile is making, while neither "on" nor "off" is yet true. */
enum class PartyRun { TURNING_ON, TURNING_OFF }

/** The stored modes' words (13 §5, 15b §7). */
object ModesWords {
    const val LABEL = "Party mode"
    const val NOTHING_TO_DO = "Nothing to do yet. Choose music, lights or plugs in Settings › Modes."

    const val SETTINGS_TITLE = "Party mode"
    const val MUSIC_SECTION = "Music"
    const val SPEAKERS = "Speakers"
    const val NONE = "None"
    const val MUSIC_SHEET_TITLE = "Music for Party mode"
    const val CHOOSE_MUSIC = "Choose the music for Party mode"
    const val LIGHTS_SECTION = "Lights"
    const val LEAVE_ALONE = "Leave alone"
    const val PLUGS_SECTION = "Plugs"
    const val PLUGS_LINE = "Party mode keeps the plug choices it has. A later update lets you change them here."
    const val SET_UP_SONOS = "Set up Sonos in Settings › Music to choose the music."
    const val SET_UP_HUE = "Set up Hue in Settings › Lights to choose scenes."

    /** What a tap on a light room's row does. */
    fun chooseScene(room: String) = "Choose a scene for $room"

    /** What the PIN pad says the PIN is for (15b Ruling 2). */
    fun turnLabel(name: String, on: Boolean) = if (on) "Turn $name on" else "Turn $name off"

    /** The tile's line when the wall was asked to turn off a mode that has come to need a PIN (plan). */
    fun turnOffWithPin(name: String) = "Turn $name off with a PIN."

    fun tileSpoken(name: String, on: Boolean) = if (on) "$name, on." else "$name, off."

    fun runningSpoken(name: String, run: PartyRun): String = when (run) {
        PartyRun.TURNING_ON -> "$name, turning on."
        PartyRun.TURNING_OFF -> "$name, turning off."
    }
}
