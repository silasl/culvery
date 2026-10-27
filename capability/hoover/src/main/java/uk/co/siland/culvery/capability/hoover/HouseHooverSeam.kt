package uk.co.siland.culvery.capability.hoover

import javax.inject.Inject
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import uk.co.siland.culvery.core.house.HouseHoover
import uk.co.siland.culvery.core.house.HouseStep

/** Holiday mode's way to the hoover (11 §5.1). */
class HouseHooverSeam @Inject constructor(systems: Set<@JvmSuppressWildcards HooverSystem>) : HouseHoover {
    private val system = systems.firstOrNull()

    override val ready: Flow<Boolean> = system?.state?.map { it != null } ?: flowOf(false)

    override suspend fun start(): HouseStep {
        val system = system ?: return DIDNT_RUN
        return when (val result = system.start()) {
            HooverResult.Started, HooverResult.AlreadyRunning -> HouseStep.Done
            HooverResult.NotStarted, HooverResult.Busy -> DIDNT_RUN
            is HooverResult.Failed -> HouseStep.Failed(result.line)
        }
    }

    private companion object {
        val DIDNT_RUN = HouseStep.Failed("The hoover didn’t run.")
    }
}
