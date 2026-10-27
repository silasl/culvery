package uk.co.siland.culvery.capability.holiday

import javax.inject.Inject
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import uk.co.siland.culvery.core.plugin.ApplicationScope
import uk.co.siland.culvery.core.plugin.HouseholdClock
import uk.co.siland.culvery.core.plugin.Startable

/** A check at start and each minute, in Culvery's own process: no alarms, no new permissions (11 §5.3). */
class HolidayScheduler @Inject constructor(
    private val house: HolidayHouse,
    private val clock: HouseholdClock,
    @ApplicationScope private val scope: CoroutineScope,
) : Startable {
    override fun start() {
        scope.launch {
            house.tick()
            clock.minutes.collect { house.tick() }
        }
    }
}
