package uk.co.siland.culvery.core.plugin

import android.util.Log
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import uk.co.siland.culvery.core.household.HouseholdZone

/**
 * The household's wall time (4c design §4.3): one minute ticker for the whole app, in the household's zone. The shell's
 * clocks and theme, the weather and the calendar's "today" all read it. [ticks] are the epoch millis to show; by
 * default now, then each minute's start. Every zone in use is offset by whole minutes, so an epoch minute is a minute
 * on its clock.
 */
@Singleton
class HouseholdClock(
    zones: Flow<ZoneId>,
    private val wall: WallClock,
    scope: CoroutineScope,
    ticks: Flow<Long> = minuteTicks(wall),
) {
    @Inject
    constructor(zone: HouseholdZone, wall: WallClock, @ApplicationScope scope: CoroutineScope) : this(zone.zone, wall, scope)

    /** Now, then at each minute's start and at once when the zone changes; null until the zone is first read. */
    val now: StateFlow<LocalDateTime?> =
        combine(zones.retryWithBackoff { Log.w(TAG, "Couldn't read the household's time zone (${it::class.simpleName}); retrying") }, ticks) { zone, millis ->
            LocalDateTime.ofInstant(Instant.ofEpochMilli(millis), zone)
        }.stateIn(scope, SharingStarted.Eagerly, null)

    val minutes: Flow<LocalDateTime> = now.filterNotNull()

    /** Today's date, changing only at midnight. */
    val today: Flow<LocalDate> = minutes.map { it.toLocalDate() }.distinctUntilChanged()

    fun nowMillis(): Long = wall.nowMillis()

    private companion object {
        const val TAG = "HouseholdClock"
    }
}
