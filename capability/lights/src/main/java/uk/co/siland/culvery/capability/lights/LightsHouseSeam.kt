package uk.co.siland.culvery.capability.lights

import javax.inject.Inject
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import uk.co.siland.culvery.core.access.Authorised
import uk.co.siland.culvery.core.house.HouseLights
import uk.co.siland.culvery.core.house.HouseStep
import uk.co.siland.culvery.core.house.LightOptions
import uk.co.siland.culvery.core.house.RoomBefore
import uk.co.siland.culvery.core.house.RoomChoice

/** Holiday mode's way to Holiday lights (11 §5.1). */
class LightsHouseSeam @Inject constructor(
    private val house: LightsHouse,
    private val settings: LightsSettings,
    private val modes: LightsModes,
) : HouseLights {
    override val ready: Flow<Boolean> = settings.prefs.map { it.link != null }.distinctUntilChanged()

    override suspend fun setHoliday(on: Boolean, by: Authorised): HouseStep {
        if (on && settings.prefs.first().holidayOn) return HouseStep.Done
        return when (val result = house.setHoliday(on, by)) {
            HolidayResult.Done, is HolidayResult.Partly -> HouseStep.Done
            is HolidayResult.CantReach -> HouseStep.Failed(problemLine(result.problem))
            HolidayResult.Busy -> HouseStep.Failed(LIGHTS_BUSY)
            HolidayResult.Refused -> HouseStep.Failed(houseLine(result, house::nameOf) ?: HUE_REFUSED)
        }
    }

    override suspend fun holidayAutomationNames(): List<String>? = house.holidayAutomationNames()

    override suspend fun lightOptions(): LightOptions = modes.options()

    override suspend fun modeBefore(choices: List<RoomChoice>): List<RoomBefore> = modes.before(choices)

    override suspend fun modeOn(rooms: List<RoomBefore>): List<String> = modes.on(rooms)

    override suspend fun modeBack(rooms: List<RoomBefore>): List<String> = modes.back(rooms)
}
