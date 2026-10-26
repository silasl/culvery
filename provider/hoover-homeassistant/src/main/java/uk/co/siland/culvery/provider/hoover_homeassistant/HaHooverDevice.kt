package uk.co.siland.culvery.provider.hoover_homeassistant

import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import uk.co.siland.culvery.capability.hoover.HOOVER
import uk.co.siland.culvery.capability.hoover.HooverSettings
import uk.co.siland.culvery.capability.hoover.hooverStatus
import uk.co.siland.culvery.core.homeassistant.HomeAssistant
import uk.co.siland.culvery.core.homeassistant.HomeAssistantDevice

/** The Hoover picker in Settings › Other integrations (11 §4.1). */
@Singleton
class HaHooverDevice @Inject constructor(
    ha: HomeAssistant,
    private val settings: HooverSettings,
) : HomeAssistantDevice {
    override val order = 10
    override val title = HOOVER
    override val domain = "vacuum"
    override val noneFound = NO_HOOVER
    override val chosen: Flow<String?> = settings.prefs.map { it.device }
    override val watched: Flow<Set<String>> = settings.prefs.map { it.watched() }
    override val line: Flow<String?> = ha.hooverStates(settings.prefs).map { state -> state?.let(::hooverStatus) }

    override suspend fun choose(entityId: String?) = settings.choose(entityId)
}
