package uk.co.siland.culvery.capability.weather

import android.util.Log
import java.time.LocalDateTime
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import uk.co.siland.culvery.core.household.HomeLocation
import uk.co.siland.culvery.core.household.HouseholdRepository
import uk.co.siland.culvery.core.plugin.Daylight
import uk.co.siland.culvery.core.plugin.HouseholdClock
import uk.co.siland.culvery.core.plugin.SunTimes
import uk.co.siland.culvery.core.plugin.retryWithBackoff

/**
 * What the weather UI and the theme read (4b design §3.7): the home location, the stored fetch, where the last fetch
 * failed and the household's wall time, by the minute. The rules are the pure [weatherView], [headerWeather] and [sunTimesOn].
 */
@Singleton
class WeatherRepository internal constructor(
    location: Flow<HomeLocation?>,
    stored: Flow<StoredWeather?>,
    now: Flow<LocalDateTime>,
    failures: Flow<WeatherPlace?> = flowOf(null),
) : Daylight {
    @Inject
    constructor(household: HouseholdRepository, store: WeatherStore, clock: HouseholdClock, syncLoop: WeatherSyncLoop) :
        this(household.location, store.stored, clock.minutes, syncLoop.lastFailure)

    val view: Flow<WeatherView> = combine(location, stored, now, failures) { l, s, n, f -> weatherView(l, s, n, f) }
        .distinctUntilChanged()
        .retryWithBackoff { Log.w(TAG, "Couldn't read the weather (${it::class.simpleName}); retrying") }

    /** The header's weather, or null while it is hidden (§4.1). */
    val header: Flow<HeaderWeather?> = combine(view, now) { v, n -> headerWeather(v, n) }.distinctUntilChanged()

    /** Today's sun times in the household's zone, from matching data however old (§3.7). The shell retries it, as [header]. */
    override val today: Flow<SunTimes?> =
        combine(location, stored, now.map { it.toLocalDate() }.distinctUntilChanged()) { l, s, date -> sunTimesOn(l, s, date) }
            .distinctUntilChanged()
}
