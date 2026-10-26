package uk.co.siland.culvery.provider.heating_homeassistant

import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import uk.co.siland.culvery.capability.heating.HeatingSettings
import uk.co.siland.culvery.core.homeassistant.HomeAssistant
import uk.co.siland.culvery.core.homeassistant.HomeAssistantDevice

/** The Thermostat picker in Settings › Other integrations (11 §4.1). */
@Singleton
class HaThermostatDevice @Inject constructor(
    ha: HomeAssistant,
    private val settings: HeatingSettings,
) : HomeAssistantDevice {
    override val order = 20
    override val title = THERMOSTAT
    override val domain = "climate"
    override val noneFound = NO_THERMOSTAT
    override val chosen: Flow<String?> = settings.prefs.map { it.device }
    override val watched: Flow<Set<String>> = settings.prefs.map { setOfNotNull(it.device) }
    override val line: Flow<String?> = ha.heatingStates(settings.prefs).map { state -> state?.let(::heatingLine) }

    override suspend fun choose(entityId: String?) = settings.choose(entityId)
}
