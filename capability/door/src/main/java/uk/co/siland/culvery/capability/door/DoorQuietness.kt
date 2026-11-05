package uk.co.siland.culvery.capability.door

import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import uk.co.siland.culvery.core.house.DoorQuiet
import uk.co.siland.culvery.core.household.HouseholdZone
import uk.co.siland.culvery.core.plugin.WallClock

/** What the sound pads see of the door: the ring card, quiet hours and the doorbell's volume. */
@Singleton
class DoorQuietness @Inject constructor(
    private val alerts: DoorAlerts,
    private val settings: DoorSettings,
    private val zone: HouseholdZone,
    private val clock: WallClock,
) : DoorQuiet {
    override val ringShowing: Flow<Boolean> = alerts.showing.map { it != null }

    override suspend fun quietNow() = quietAt(clock.nowMillis(), zone.current(), settings.current.value)

    override val volume get() = settings.current.value.volume
}
