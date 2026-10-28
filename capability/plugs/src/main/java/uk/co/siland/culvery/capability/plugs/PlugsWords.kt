package uk.co.siland.culvery.capability.plugs

import uk.co.siland.culvery.core.house.PlugChange
import uk.co.siland.culvery.core.plugin.CHECKING

/** The Plugs tab's words (12 §2.5, §2.14). */
object PlugsWords {
    const val PLUGS = "Plugs"
    private const val HOME_ASSISTANT = "Home Assistant"
    const val A_PLUG = "A plug"
    const val SWITCHING = "Switching…"
    const val SWITCH_ON = "Switch on"
    const val SWITCH_OFF = "Switch off"
    const val CANT_REACH_HA = "Can’t reach Home Assistant."
    const val CANT_REACH_THIS_PLUG = "Can’t reach this plug."
    const val GONE_PLUG = "Home Assistant has no plug called that any more. Turn it off in Settings."
    const val NEEDS_A_PIN = "Needs a PIN to switch"
    const val SHOW_ON_TAB = "Show on the Plugs tab"
    const val PLUGS_HELP = "Plugs from Home Assistant’s TP-Link integration. Turn one on to show it on the Plugs tab."
    const val NO_TP_LINK_PLUGS = "Home Assistant has no TP-Link plugs. Add them in Home Assistant first."
    const val CANT_LIST_PLUGS = "Can’t list the plugs. Check Home Assistant is up to date."
    const val GONE_SHOWN = "Home Assistant has no plug called that any more."
    const val IGNORE = "Ignore"
    const val ON = "On"
    const val OFF = "Off"
    const val EDIT = "Edit"
    const val DONE = "Done"

    /** Counts the plugs known to be On or Off; with none known, just "Home Assistant". */
    fun plugsOn(plugs: List<PlugState>): String {
        val known = plugs.filter { it.isOn != null }
        return if (known.isEmpty()) HOME_ASSISTANT else "${known.count { it.power == PlugPower.ON }} of ${known.size} on · $HOME_ASSISTANT"
    }

    fun showSpoken(name: String) = "$name, ${SHOW_ON_TAB.replaceFirstChar(Char::lowercase)}"

    fun needsPinSpoken(name: String) = "$name, ${NEEDS_A_PIN.replaceFirstChar(Char::lowercase)}"

    fun plugStatus(state: PlugState) = when (state.power) {
        PlugPower.ON -> state.watts?.let { "$ON · $it W" } ?: ON
        PlugPower.OFF -> OFF
        PlugPower.UNREACHABLE -> CANT_REACH_THIS_PLUG
        PlugPower.GONE -> GONE_PLUG
        PlugPower.UNKNOWN -> CHECKING
        PlugPower.NO_LINK -> CANT_REACH_HA
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

    fun word(choice: ModeChoice) = when (choice) {
        ModeChoice.IGNORE -> IGNORE
        ModeChoice.ON -> ON
        ModeChoice.OFF -> OFF
    }

    fun modeRowSpoken(mode: String, name: String, choice: ModeChoice) = "$mode, $name, ${word(choice).lowercase()}"

    fun modeCellSpoken(mode: String, choice: ModeChoice) = "$mode, ${word(choice).lowercase()}"

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
