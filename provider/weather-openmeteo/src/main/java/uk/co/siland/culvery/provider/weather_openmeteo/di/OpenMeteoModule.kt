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
import uk.co.siland.culvery.core.net.internetHttpClient
import uk.co.siland.culvery.core.plugin.WallClock
import uk.co.siland.culvery.core.setup.LocationSearch
import uk.co.siland.culvery.provider.weather_openmeteo.OPEN_METEO_FORECAST_URL
import uk.co.siland.culvery.provider.weather_openmeteo.OPEN_METEO_GEOCODING_URL
import uk.co.siland.culvery.provider.weather_openmeteo.OpenMeteoForecast
import uk.co.siland.culvery.provider.weather_openmeteo.OpenMeteoLocationSearch

// A search is typed live: give up sooner than the calendar's sync does.
private val SEARCH_CONNECT_TIMEOUT: Duration = Duration.ofSeconds(10)
private val SEARCH_READ_TIMEOUT: Duration = Duration.ofSeconds(15)

// The forecast runs in the background on the shared client's timeouts, whole-call limit included (plan review 2).
internal fun forecastClient(): OkHttpClient = internetHttpClient().build()

internal fun searchClient(): OkHttpClient =
    internetHttpClient().connectTimeout(SEARCH_CONNECT_TIMEOUT).readTimeout(SEARCH_READ_TIMEOUT).build()

@Module
@InstallIn(SingletonComponent::class)
object OpenMeteoModule {
    // Each client stays inside its binding, as Google's does (4a ruling 3): no OkHttpClient in the graph to collide with.
    @Provides
    @Singleton
    fun locationSearch(): LocationSearch = OpenMeteoLocationSearch(
        OPEN_METEO_GEOCODING_URL.toHttpUrl(),
        searchClient(),
    )

    @Provides
    @Singleton
    @IntoSet
    fun forecast(clock: WallClock): WeatherProvider = OpenMeteoForecast(OPEN_METEO_FORECAST_URL.toHttpUrl(), forecastClient(), clock)
}
