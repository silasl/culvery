package uk.co.siland.culvery.capability.plugs

import kotlinx.coroutines.flow.first
import uk.co.siland.culvery.core.house.HouseModeInfo
import uk.co.siland.culvery.core.house.HouseStep

internal const val LAMP = "switch.lamp"
internal const val FAN = "switch.fan"
internal const val DESK = "switch.desk"
internal const val HOLIDAY = "holiday"
internal const val PARTY = "party"

internal val MODES = setOf(
    HouseModeInfo(HOLIDAY, "Holiday mode", 0, "holiday.mode"),
    HouseModeInfo(PARTY, "Party mode", 1, "party.mode"),
)

internal fun lamp(power: PlugPower) = PlugState(LAMP, "Lamp", power, null)

internal fun fan(power: PlugPower) = PlugState(FAN, "Fan", power, null)

internal fun desk(power: PlugPower) = PlugState(DESK, "Desk", power, null)

/** A store and a fake system holding [states], and the houses made over them; a new [house] is a restart. */
internal class PlugsRig(vararg states: PlugState) {
    val store = MemoryStore()
    val settings = PlugSettings(store)
    val system = FakePlugSystem(states.toList())

    fun house() = PlugsHouse(settings, system, MODES)

    suspend fun prefs() = settings.prefs.first()

    suspend fun configure(change: (PlugPrefs) -> PlugPrefs) = settings.update(change)

    /** Lamp and Fan added, holiday setting the plugs given. */
    suspend fun holiday(lamp: ModeChoice = ModeChoice.IGNORE, fan: ModeChoice = ModeChoice.IGNORE) = configure {
        it.showing(LAMP).showing(FAN).with(HOLIDAY, LAMP, lamp).with(HOLIDAY, FAN, fan)
    }

    fun power(id: String, power: PlugPower) {
        system.states.value += id to system.states.value.getValue(id).copy(power = power)
    }

    fun forgetCalls() {
        system.reads.clear()
        system.switches.clear()
    }
}

/** [mode]'s own grant, as its sheet would collect. */
internal suspend fun PlugsHouse.begin(mode: String) = begin(mode, grant("$mode.mode"))

/** begin, then enter. */
internal suspend fun PlugsHouse.start(mode: String): HouseStep? {
    begin(mode)
    return enter(mode)
}
