package uk.co.siland.culvery.capability.weather

import androidx.test.ext.junit.runners.AndroidJUnit4
import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Test
import org.junit.runner.RunWith
import uk.co.siland.culvery.core.household.HomeLocation
import uk.co.siland.culvery.core.plugin.HomeCardSize
import uk.co.siland.culvery.core.plugin.WallClock

// Robolectric for android.util.Log (the repository's retries).
@RunWith(AndroidJUnit4::class)
class WeatherCapabilityTest {
    private val location = MutableStateFlow<HomeLocation?>(LONDON)
    private val stored = MutableStateFlow<StoredWeather?>(stored())
    private val capability = WeatherCapability(WeatherRepository(location, stored, MutableStateFlow(THU.atTime(10, 30))), WallClock { 0L })

    @Test
    fun itHasNoTabAndSitsAfterTheRailCapabilities() = runTest {
        assertThat(capability.id).isEqualTo("weather")
        assertThat(capability.order).isEqualTo(60)
        assertThat(capability.hasTab.first()).isFalse()
    }

    @Test
    fun itAlwaysOffersTheForecastCardAsARegularCardAfterTheCalendars() = runTest {
        location.value = null
        val card = capability.cards().first().single()
        assertThat(card.id).isEqualTo("weather.forecast")
        assertThat(card.size).isEqualTo(HomeCardSize.REGULAR)
        assertThat(card.priority).isEqualTo(40)
    }

    @Test
    fun theHeaderItemIsThereOnlyWhileTheWeatherShows() = runTest {
        val item = capability.headerItems().first().single()
        assertThat(item.id).isEqualTo("weather")
        assertThat(item.order).isEqualTo(10)
        stored.value = null
        assertThat(capability.headerItems().first()).isEmpty()
        location.value = null
        assertThat(capability.headerItems().first()).isEmpty()
    }
}
