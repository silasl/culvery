package uk.co.siland.culvery.capability.heating

import javax.inject.Inject
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import uk.co.siland.culvery.core.house.HouseHeating
import uk.co.siland.culvery.core.house.HouseStep

/** Holiday mode's way to the thermostat (11 §5.1). */
class HouseHeatingSeam @Inject constructor(systems: Set<@JvmSuppressWildcards HeatingSystem>) : HouseHeating {
    private val system = systems.firstOrNull()

    override val ready: Flow<Boolean> = system?.state?.map { it != null } ?: flowOf(false)

    override suspend fun setEco(on: Boolean): HouseStep {
        val system = system ?: return HouseStep.Failed(if (on) ECO_ON_LINE else ECO_OFF_LINE)
        return when (val result = system.setEco(on)) {
            HeatingResult.Done -> HouseStep.Done
            HeatingResult.NotConfirmed -> HouseStep.Failed(if (on) ECO_ON_LINE else ECO_OFF_LINE)
            is HeatingResult.Failed -> HouseStep.Failed(result.line)
        }
    }

    private companion object {
        const val ECO_ON_LINE = "The heating didn’t go to Eco. Check the Nest app."
        const val ECO_OFF_LINE = "The heating didn’t come off Eco. Turn it off in the Nest app."
    }
}
