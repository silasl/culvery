package uk.co.siland.culvery.capability.weather

import javax.inject.Inject
import uk.co.siland.culvery.core.plugin.WallClock

/** One fetch: the forecast for a place, stored under that place (4b design §3.5, ruling 7). */
class WeatherFetcher @Inject constructor(
    providers: Set<@JvmSuppressWildcards WeatherProvider>,
    private val store: WeatherStore,
    private val clock: WallClock,
) {
    // With more than one bound, the first by id (§3.1).
    private val provider: WeatherProvider? = providers.minByOrNull { it.descriptor.id }

    /**
     * Asks for [place]'s forecast in its zone and stores it under [place], not the location now: a fetch that lands after
     * a move then never matches. Does nothing without a provider. Throws on any failure, leaving the store as it was.
     */
    suspend fun fetch(place: WeatherPlace) {
        val provider = provider ?: return
        val forecast = provider.forecast(place.latitude, place.longitude, place.zone)
        store.replace(place, forecast, clock.nowMillis())
    }
}
