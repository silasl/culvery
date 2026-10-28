package uk.co.siland.culvery.capability.plugs

import uk.co.siland.culvery.core.house.PlugChange
import uk.co.siland.culvery.core.plugin.CHECKING

/** The Plugs tab's words (12 §2.5, §2.14). */
object PlugsWords {
    const val PLUGS = "Plugs"
    const val A_PLUG = "A plug"
    const val ADD_A_PLUG = "Add a plug"
    const val NO_PLUGS = "No plugs yet. Add one from Home Assistant."
    const val NOTHING_TO_ADD = "Home Assistant has no other switches."
    const val SWITCHING = "Switching…"
    const val SWITCH_ON = "Switch on"
    const val SWITCH_OFF = "Switch off"
    const val CANT_REACH_HOME_ASSISTANT = "Can’t reach Home Assistant."
    const val CANT_REACH_THIS_PLUG = "Can’t reach this plug."
    const val GONE_PLUG = "Home Assistant has no plug called that any more. Remove it or add it again."
    const val NEEDS_A_PIN = "Needs a PIN to switch"
    const val REMOVE = "Remove"
    const val LEAVE = "Leave"
    const val ON = "On"
    const val OFF = "Off"
    const val EDIT = "Edit"
    const val DONE = "Done"

    fun settingsFor(name: String) = "Settings for $name"

    fun plugsOn(plugs: List<PlugState>) = "${plugs.count { it.power == PlugPower.ON }} of ${plugs.size} on · Home Assistant"

    fun plugStatus(state: PlugState) = when (state.power) {
        PlugPower.ON -> state.watts?.let { "$ON · $it W" } ?: ON
        PlugPower.OFF -> OFF
        PlugPower.UNREACHABLE -> CANT_REACH_THIS_PLUG
        PlugPower.GONE -> GONE_PLUG
        PlugPower.UNKNOWN -> CHECKING
        PlugPower.NO_LINK -> CANT_REACH_HOME_ASSISTANT
    }

    /** What TalkBack reads for a card. */
    fun plugSpoken(state: PlugState): String {
        val name = state.name
        return when (state.power) {
            PlugPower.ON -> "Plug $name, on${state.watts?.let { ", $it watts" }.orEmpty()}."
            PlugPower.OFF -> "Plug $name, off."
            PlugPower.UNREACHABLE -> "Plug $name, can’t reach this plug."
            PlugPower.GONE -> "Plug $name. $GONE_PLUG"
            PlugPower.UNKNOWN -> "Plug $name, checking."
            PlugPower.NO_LINK -> "Plug $name, can’t reach Home Assistant."
        }
    }

    /** The card button: the action for the plug's state, with the PIN setting read out. */
    fun switchSpoken(state: PlugState, needsPin: Boolean): String {
        val action = if (state.power == PlugPower.ON) SWITCH_OFF else SWITCH_ON
        return if (needsPin) "$action, needs a PIN" else action
    }

    fun modeRowSpoken(mode: String, name: String, choice: ModeChoice): String {
        val word = when (choice) {
            ModeChoice.LEAVE -> "leave alone"
            ModeChoice.ON -> "on"
            ModeChoice.OFF -> "off"
        }
        return "$mode, $name, $word"
    }

    fun cantReach(name: String) = "Can’t reach $name. Check it’s plugged in and the Tapo app can see it."

    fun didntSwitch(name: String) = "$name didn’t switch. Try again, or use the Tapo app."

    /** The tile line for plugs a mode didn't switch; the mode keeps trying (12 §5.3). */
    fun enterLine(missed: List<PlugChange>): String =
        missed.singleOrNull()?.let { "${it.name} didn’t switch ${if (it.on) "on" else "off"}. It’ll keep trying." }
            ?: "${missed.size} plugs didn’t switch. They’ll keep trying."

    /** The tile line for plugs a mode didn't put back; nothing retries them. */
    fun leaveLine(missed: List<PlugChange>): String =
        missed.singleOrNull()?.let { "${it.name} didn’t switch back ${if (it.on) "on. Switch it on" else "off. Switch it off on"} the Plugs tab." }
            ?: "${missed.size} plugs didn’t switch back. Switch them on the Plugs tab."
}
