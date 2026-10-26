package uk.co.siland.culvery.provider.heating_homeassistant

import android.util.Log
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import uk.co.siland.culvery.capability.heating.HeatingPrefs
import uk.co.siland.culvery.capability.heating.HeatingResult
import uk.co.siland.culvery.capability.heating.HeatingSettings
import uk.co.siland.culvery.capability.heating.HeatingState
import uk.co.siland.culvery.capability.heating.HeatingSystem
import uk.co.siland.culvery.core.homeassistant.CONFIRM_FOR_MS
import uk.co.siland.culvery.core.homeassistant.EntityState
import uk.co.siland.culvery.core.homeassistant.HaProblem
import uk.co.siland.culvery.core.homeassistant.HomeAssistant
import uk.co.siland.culvery.core.homeassistant.HomeAssistantException
import uk.co.siland.culvery.core.homeassistant.NOT_LINKED
import uk.co.siland.culvery.core.homeassistant.entityTrouble
import uk.co.siland.culvery.core.homeassistant.problemLine

private const val TAG = "HaHeatingSystem"

private const val ECO = "eco"

private fun EntityState.preset() = texts["preset_mode"]

private fun problemState(line: String) = HeatingState(THERMOSTAT, false, null, null, false, line)

/** The thermostat's state; null with no link or no thermostat chosen (11 §4.5). */
internal fun heatingState(
    prefs: HeatingPrefs,
    linked: Boolean,
    entities: Map<String, EntityState?>,
    problem: HaProblem?,
): HeatingState? {
    val device = prefs.device?.takeIf { linked } ?: return null
    entityTrouble(device, entities, problem, GONE_THERMOSTAT, THERMOSTAT_UNREACHABLE)?.let { return problemState(it) }
    val climate = entities[device]
    return HeatingState(
        name = climate?.friendlyName ?: THERMOSTAT,
        eco = climate?.preset() == ECO,
        currentC = climate?.numbers?.get("current_temperature"),
        targetC = climate?.numbers?.get("temperature"),
        heating = climate?.texts?.get("hvac_action") == "heating",
        problem = null,
    )
}

internal fun HomeAssistant.heatingStates(prefs: Flow<HeatingPrefs>): Flow<HeatingState?> =
    combine(prefs, link, entities, problem) { p, link, entities, problem -> heatingState(p, link != null, entities, problem) }
        .distinctUntilChanged()

/** The thermostat through Home Assistant (11 §4.2, §4.5, §6). */
@Singleton
class HaHeatingSystem internal constructor(
    private val ha: HomeAssistant,
    private val prefs: Flow<HeatingPrefs>,
) : HeatingSystem {
    @Inject
    constructor(ha: HomeAssistant, settings: HeatingSettings) : this(ha, settings.prefs)

    private val changing = Mutex()

    override val state: Flow<HeatingState?> = ha.heatingStates(prefs)

    /** One change at a time, each checked for 30 s; the lock goes when the check ends or is cancelled. */
    override suspend fun setEco(on: Boolean): HeatingResult = changing.withLock {
        if (ha.link.value == null) return HeatingResult.Failed(NOT_LINKED)
        val device = prefs.first().device ?: return HeatingResult.Failed(NO_THERMOSTAT_CHOSEN)
        try {
            ha.call("climate", "set_preset_mode", device, mapOf("preset_mode" to if (on) ECO else "none"))
            val shown = ha.awaitEntity(device, CONFIRM_FOR_MS) { (it.preset() == ECO) == on }
            if (shown != null) return HeatingResult.Done
            Log.w(TAG, "The preset did not change")
            HeatingResult.NotConfirmed
        } catch (e: HomeAssistantException) {
            if (e.problem != HaProblem.REFUSED) return HeatingResult.Failed(problemLine(e.problem))
            Log.w(TAG, "Home Assistant refused the preset")
            HeatingResult.Failed(THERMOSTAT_REFUSED)
        }
    }
}
