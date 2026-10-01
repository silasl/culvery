package uk.co.siland.culvery.provider.weather_openmeteo.di

import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import dagger.multibindings.IntoSet
import java.time.Duration
import javax.inject.Singleton
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import uk.co.siland.culvery.capability.weather.WeatherProvider
import uk.co.siland.culvery.core.plugin.WallClock
import uk.co.siland.culvery.core.setup.LocationSearch
import uk.co.siland.culvery.provider.weather_openmeteo.OPEN_METEO_FORECAST_URL
import uk.co.siland.culvery.provider.weather_openmeteo.OPEN_METEO_GEOCODING_URL
import uk.co.siland.culvery.provider.weather_openmeteo.OpenMeteoForecast
import uk.co.siland.culvery.provider.weather_openmeteo.OpenMeteoLocationSearch

// A search is typed live: give up sooner than the calendar's sync does.
private val SEARCH_CONNECT_TIMEOUT: Duration = Duration.ofSeconds(10)
private val SEARCH_READ_TIMEOUT: Duration = Duration.ofSeconds(15)

// The forecast runs in the background, as the calendar's sync does: its timeouts, and a whole-call limit, so a body
// that drips in inside the read timeout can't hold the loop (plan review 2).
private val FORECAST_CONNECT_TIMEOUT: Duration = Duration.ofSeconds(15)
private val FORECAST_READ_TIMEOUT: Duration = Duration.ofSeconds(30)
private val FORECAST_CALL_TIMEOUT: Duration = Duration.ofSeconds(60)

internal fun forecastClient(): OkHttpClient = OkHttpClient.Builder()
    .connectTimeout(FORECAST_CONNECT_TIMEOUT)
    .readTimeout(FORECAST_READ_TIMEOUT)
    .callTimeout(FORECAST_CALL_TIMEOUT)
    .build()

@Module
@InstallIn(SingletonComponent::class)
object OpenMeteoModule {
    // Each client stays inside its binding, as Google's does (4a ruling 3): no OkHttpClient in the graph to collide with.
    @Provides
    @Singleton
    fun locationSearch(): LocationSearch = OpenMeteoLocationSearch(
        OPEN_METEO_GEOCODING_URL.toHttpUrl(),
        OkHttpClient.Builder().connectTimeout(SEARCH_CONNECT_TIMEOUT).readTimeout(SEARCH_READ_TIMEOUT).build(),
    )

    @Provides
    @Singleton
    @IntoSet
    fun forecast(clock: WallClock): WeatherProvider = OpenMeteoForecast(OPEN_METEO_FORECAST_URL.toHttpUrl(), forecastClient(), clock)
}
