package uk.co.siland.culvery.capability.party

/** The change a mode's tile is making, while neither "on" nor "off" is yet true. */
enum class PartyRun { TURNING_ON, TURNING_OFF }

/** The stored modes' words (13 §5, 15b §7). */
object ModesWords {
    const val PAGE_TITLE = "Modes"
    const val PAGE_LINE = "One tap sets the house up. Each mode can sit on Home and can need a PIN."
    const val HOLIDAY_NAME = "Holiday mode"
    const val ADD_MODE = "Add a mode"
    const val BACK_TO_MODES = "Modes"
    const val NAME_LABEL = "Name of the mode"
    const val NAME_EXAMPLE = "Film night"
    const val NAME_SHEET_TITLE = "Name the mode"
    const val RENAME_SHEET_TITLE = "Rename the mode"
    const val ICON_SHEET_TITLE = "Choose an icon"
    const val SAVE_NAME = "Save name"
    const val SAVE_ICON = "Save icon"
    const val BACK = "Back"
    const val NAME_LIMIT = 24
    const val NOTHING_CHOSEN = "Nothing chosen yet"
    const val HOLIDAY_DOES = "Lights, heating, hoover, plugs, door chime"

    private const val SEPARATOR = " · "

    fun openMode(name: String) = "open $name"

    fun changeIcon(name: String) = "change the icon of $name"

    fun renameMode(name: String) = "rename $name"

    /** A mode's row line (15b §4.1): its place, its PIN, then what it does. [plugs] is how many it switches. */
    fun summary(mode: StoredMode, on: Boolean, plugs: Int): String =
        pieces(on, builtIn = false, onHome = mode.showOnHome, pin = mode.needsPin, does = whatItDoes(mode, plugs))

    fun holidaySummary(on: Boolean, onHome: Boolean): String = pieces(on, builtIn = true, onHome = onHome, pin = true, does = HOLIDAY_DOES)

    private fun pieces(on: Boolean, builtIn: Boolean, onHome: Boolean, pin: Boolean, does: String) = listOfNotNull(
        "On".takeIf { on },
        "Built in".takeIf { builtIn },
        if (onHome) "On Home" else "Not on Home",
        if (pin) "PIN" else "No PIN",
        does,
    ).joinToString(SEPARATOR)

    private fun whatItDoes(mode: StoredMode, plugs: Int): String = listOfNotNull(
        mode.music?.let { music ->
            val speakers = mode.speakers
            if (speakers == null) "${music.pickName} on every speaker" else "${music.pickName} on ${counted(speakers.size, "speaker")}"
        },
        mode.lights.size.takeIf { it > 0 }?.let { counted(it, "room") },
        plugs.takeIf { it > 0 }?.let { counted(it, "plug") },
    ).ifEmpty { listOf(NOTHING_CHOSEN) }.joinToString(SEPARATOR)

    private fun counted(n: Int, noun: String) = if (n == 1) "1 $noun" else "$n ${noun}s"

    /** Why [typed], trimmed, can't be a mode's name, or null (plan copy). [taken] is the other modes' names; Holiday's is always taken. */
    fun nameProblem(typed: String, taken: List<String>): String? {
        val name = typed.trim()
        val same = (taken + HOLIDAY_NAME).firstOrNull { it.equals(name, ignoreCase = true) }
        return when {
            name.isEmpty() -> "Type a name for the mode."
            name.length > NAME_LIMIT -> "Keep the name to $NAME_LIMIT characters or fewer."
            same != null -> "There’s already a mode called $same."
            name.any { it.isISOControl() } -> "Use letters, numbers and spaces in the name."
            else -> null
        }
    }

    const val LABEL = "Party mode"
    const val NOTHING_TO_DO = "Nothing to do yet. Choose music, lights or plugs in Settings › Modes."

    const val MUSIC_SECTION = "Music"
    const val SPEAKERS = "Speakers"
    const val NONE = "None"
    const val LIGHTS_SECTION = "Lights · every other light is left alone"
    const val PLUGS_SECTION = "Plugs · every other plug is left alone"
    const val SET_UP_SONOS = "Set up Sonos in Settings › Music to choose the music."
    const val SET_UP_HUE = "Set up Hue in Settings › Lights to choose rooms."
    const val ADD_LIGHT = "Add a light"
    const val ADD_PLUG = "Add a plug"
    const val ADD_LIGHT_TITLE = "Choose a room"
    const val ADD_PLUG_TITLE = "Choose a plug"
    const val SAVE_LIGHT = "Save light"
    const val LIGHT_ON = "On"
    const val LIGHT_OFF = "Off"
    const val SHOW_ON_HOME = "Show on Home"
    const val NEEDS_A_PIN = "Needs a PIN"
    const val HOLIDAY_PIN = "Holiday mode always needs a PIN."
    const val PLUG_NEEDS_PIN = "A plug in this mode needs a PIN."
    const val DELETE_MODE = "Delete mode"
    const val MISSING_PLUG = "A plug that is no longer on the Lights tab"

    fun turnNow(on: Boolean) = if (on) "Turn off now" else "Turn on now"

    fun appliesNext(name: String) = "Changes apply the next time $name turns on."

    fun homeHasRoom(room: Int) = "Home has room for $room ${if (room == 1) "mode" else "modes"}."

    fun deleteQuestion(name: String) = "Delete $name? Its music, lights and plugs choices go too."

    fun musicSheetTitle(name: String) = "Music for $name"

    fun chooseMusic(name: String) = "Choose the music for $name"

    fun removeLight(room: String) = "Remove $room"

    fun removePlug(plug: String) = "Remove $plug"

    fun changeLight(room: String) = "change what $room does"

    fun plugSpoken(plug: String, on: Boolean) = "$plug, ${if (on) "on" else "off"}"

    fun switchPlug(plug: String, on: Boolean) = "switch $plug ${if (on) "off" else "on"}"

    fun lower(room: String) = "Lower the brightness of $room by 10 percent"

    fun raise(room: String) = "Raise the brightness of $room by 10 percent"

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
