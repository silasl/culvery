package uk.co.siland.culvery.provider.weather_openmeteo.di

import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import java.time.Duration
import javax.inject.Singleton
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import uk.co.siland.culvery.core.setup.LocationSearch
import uk.co.siland.culvery.provider.weather_openmeteo.OPEN_METEO_GEOCODING_URL
import uk.co.siland.culvery.provider.weather_openmeteo.OpenMeteoLocationSearch

// A search is typed live: give up sooner than the calendar's sync does.
private val CONNECT_TIMEOUT: Duration = Duration.ofSeconds(10)
private val READ_TIMEOUT: Duration = Duration.ofSeconds(15)

@Module
@InstallIn(SingletonComponent::class)
object OpenMeteoModule {
    // Its own client, inside the binding, as Google's is (ruling 3): no OkHttpClient in the graph to collide with.
    @Provides
    @Singleton
    fun locationSearch(): LocationSearch = OpenMeteoLocationSearch(
        OPEN_METEO_GEOCODING_URL.toHttpUrl(),
        OkHttpClient.Builder().connectTimeout(CONNECT_TIMEOUT).readTimeout(READ_TIMEOUT).build(),
    )
}
