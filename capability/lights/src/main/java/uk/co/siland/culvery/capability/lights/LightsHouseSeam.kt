package uk.co.siland.culvery.capability.lights

import javax.inject.Inject
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import uk.co.siland.culvery.core.access.Authorised
import uk.co.siland.culvery.core.house.HouseLights
import uk.co.siland.culvery.core.house.HouseStep

/** Holiday mode's way to Holiday lights (11 §5.1). */
class LightsHouseSeam @Inject constructor(
    private val house: LightsHouse,
    private val settings: LightsSettings,
) : HouseLights {
    override val ready: Flow<Boolean> = settings.prefs.map { it.link != null }.distinctUntilChanged()

    override suspend fun setHoliday(on: Boolean, by: Authorised): HouseStep {
        if (on && settings.prefs.first().holidayOn) return HouseStep.Done
        return when (val result = house.setHoliday(on, by)) {
            HolidayResult.Done, is HolidayResult.Partly -> HouseStep.Done
            is HolidayResult.CantReach -> HouseStep.Failed(problemLine(result.problem))
            HolidayResult.Busy, HolidayResult.Refused -> HouseStep.Failed(HUE_REFUSED)
        }
    }
}
