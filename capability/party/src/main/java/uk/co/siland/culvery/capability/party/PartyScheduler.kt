package uk.co.siland.culvery.capability.party

import javax.inject.Inject
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import uk.co.siland.culvery.core.plugin.ApplicationScope
import uk.co.siland.culvery.core.plugin.HouseholdClock
import uk.co.siland.culvery.core.plugin.Startable

/** A tick at start and each minute, in Culvery's own process: no alarms, no new permissions (13 §2.9). */
class PartyScheduler @Inject constructor(
    private val house: PartyHouse,
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
