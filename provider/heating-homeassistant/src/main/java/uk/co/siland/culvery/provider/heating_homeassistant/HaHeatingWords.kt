package uk.co.siland.culvery.provider.heating_homeassistant

import java.util.Locale
import uk.co.siland.culvery.capability.heating.HeatingState
import uk.co.siland.culvery.core.plugin.CHECKING

const val GONE_THERMOSTAT = "Home Assistant has no thermostat called that any more. Choose it again in Settings › Other integrations."
const val THERMOSTAT_UNREACHABLE = "Home Assistant can’t reach the thermostat. Check the Nest integration."
const val NO_THERMOSTAT = "Home Assistant has no thermostat."
const val NO_THERMOSTAT_CHOSEN = "No thermostat is chosen. Choose one in Settings › Other integrations."
const val THERMOSTAT_REFUSED = "The thermostat didn’t change. Check the Nest integration."
const val THERMOSTAT = "Thermostat"

private fun celsius(value: Double) = String.format(Locale.UK, "%.1f °C", value)

/** The thermostat's line in Settings: `Hall · 20.8 °C · heating to 21.2 °C`, `… · Eco`, `… · set to 21.2 °C`, or Checking (11 §4.1). */
fun heatingLine(state: HeatingState): String {
    state.problem?.let { return it }
    val eco = state.eco ?: return CHECKING
    val target = state.targetC
    val tail = when {
        eco -> "Eco"
        target == null -> null
        state.heating -> "heating to ${celsius(target)}"
        else -> "set to ${celsius(target)}"
    }
    return listOfNotNull(state.name, state.currentC?.let(::celsius), tail).joinToString(" · ")
}
