package uk.co.siland.culvery.core.house

import javax.inject.Inject
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import uk.co.siland.culvery.core.plugin.ApplicationScope
import uk.co.siland.culvery.core.plugin.HouseholdClock
import uk.co.siland.culvery.core.plugin.Startable

/** Each mode's tick at start and each minute, in Culvery's own process: no alarms, no new permissions (11 §5.3, 13 §2.9). */
class HouseModeScheduler @Inject constructor(
    private val switches: Set<@JvmSuppressWildcards HouseModeSwitch>,
    private val clock: HouseholdClock,
    @ApplicationScope private val scope: CoroutineScope,
) : Startable {
    // One coroutine per mode, so a slow tick in one never holds up another's.
    override fun start() {
        for (mode in switches) {
            scope.launch {
                mode.tick()
                clock.minutes.collect { mode.tick() }
            }
        }
    }
}
