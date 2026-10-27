package uk.co.siland.culvery.capability.heating

import javax.inject.Inject
import kotlinx.coroutines.flow.Flow
import uk.co.siland.culvery.core.house.HouseHeating
import uk.co.siland.culvery.core.house.HouseLines
import uk.co.siland.culvery.core.house.HouseStep
import uk.co.siland.culvery.core.house.chosen

/** Holiday mode's way to the thermostat (11 §5.1). */
class HouseHeatingSeam @Inject constructor(systems: Set<@JvmSuppressWildcards HeatingSystem>) : HouseHeating {
    private val system = systems.firstOrNull()

    override val ready: Flow<Boolean> = system?.state.chosen()

    override suspend fun setEco(on: Boolean): HouseStep {
        val notDone = HouseStep.Failed(if (on) HouseLines.ECO_ON_FAILED else HouseLines.ECO_OFF_FAILED)
        val system = system ?: return notDone
        return when (val result = system.setEco(on)) {
            HeatingResult.Done -> HouseStep.Done
            HeatingResult.NotConfirmed -> notDone
            is HeatingResult.Failed -> HouseStep.Failed(result.line)
        }
    }
}
