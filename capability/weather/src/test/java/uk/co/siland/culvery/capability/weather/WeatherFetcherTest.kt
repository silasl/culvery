package uk.co.siland.culvery.capability.weather

import androidx.test.ext.junit.runners.AndroidJUnit4
import com.google.common.truth.Truth.assertThat
import java.time.ZoneId
import java.util.TimeZone
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import uk.co.siland.culvery.capability.weather.db.WeatherDatabase
import uk.co.siland.culvery.core.plugin.WallClock

// Robolectric for Room.
@RunWith(AndroidJUnit4::class)
class WeatherFetcherTest {
    private lateinit var db: WeatherDatabase
    private lateinit var store: WeatherStore
    private val clock = WallClock { 5_000L }
    private val deviceZone = TimeZone.getDefault()

    @Before
    fun setUp() {
        // The tablet's own zone is not the household's.
        TimeZone.setDefault(TimeZone.getTimeZone("America/New_York"))
        db = weatherDb()
        store = WeatherStore(db)
    }

    @After
    fun tearDown() {
        db.close()
        TimeZone.setDefault(deviceZone)
    }

    @Test
    fun itStoresTheForecastUnderThePlaceItAskedFor() = runTest {
        val provider = ScriptedWeatherProvider()
        WeatherFetcher(setOf(provider), store, clock).fetch(WeatherPlace(LONDON))
        assertThat(provider.asked).containsExactly(Triple(51.5074, -0.1278, ZoneId.of("Europe/London")))
        val s = store.stored.first()!!
        assertThat(s.place).isEqualTo(WeatherPlace(LONDON))
        assertThat(s.fetchedAtMillis).isEqualTo(5_000L)
        assertThat(s.days).isEqualTo(forecast().days)
    }

    @Test
    fun itAsksInTheHouseholdZoneNotTheDevices() = runTest {
        val provider = ScriptedWeatherProvider()
        WeatherFetcher(setOf(provider), store, clock).fetch(WeatherPlace(WELLINGTON))
        assertThat(provider.asked.single().third).isEqualTo(ZoneId.of("Pacific/Auckland"))
    }

    @Test
    fun theFirstProviderByIdIsAsked() = runTest {
        val b = ScriptedWeatherProvider("weather.b")
        val a = ScriptedWeatherProvider("weather.a")
        WeatherFetcher(setOf(b, a), store, clock).fetch(WeatherPlace(LONDON))
        assertThat(a.asked).hasSize(1)
        assertThat(b.asked).isEmpty()
    }

    @Test
    fun withNoProviderNothingIsFetchedOrStored() = runTest {
        WeatherFetcher(emptySet(), store, clock).fetch(WeatherPlace(LONDON))
        assertThat(store.stored.first()).isNull()
    }

    @Test
    fun aFailedFetchThrowsAndLeavesTheStoreAlone() = runTest {
        store.replace(WeatherPlace(LONDON), forecast(), 1_000L)
        val offline = ScriptedWeatherProvider(answer = { throw WeatherUnavailableException("offline") })
        val thrown = runCatching { WeatherFetcher(setOf(offline), store, clock).fetch(WeatherPlace(LEEDS)) }.exceptionOrNull()
        assertThat(thrown).isInstanceOf(WeatherUnavailableException::class.java)
        val s = store.stored.first()!!
        assertThat(s.place).isEqualTo(WeatherPlace(LONDON))
        assertThat(s.fetchedAtMillis).isEqualTo(1_000L)
    }
}
