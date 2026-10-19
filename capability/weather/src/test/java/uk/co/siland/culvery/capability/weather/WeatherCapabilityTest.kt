package uk.co.siland.culvery.capability.weather

import androidx.test.ext.junit.runners.AndroidJUnit4
import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Test
import org.junit.runner.RunWith
import uk.co.siland.culvery.core.household.HomeLocation

// Robolectric for android.util.Log (the repository's retries).
@RunWith(AndroidJUnit4::class)
class WeatherCapabilityTest {
    private val location = MutableStateFlow<HomeLocation?>(LONDON)
    private val stored = MutableStateFlow<StoredWeather?>(stored())
    private val capability = WeatherCapability(WeatherRepository(location, stored, MutableStateFlow(THU.atTime(10, 30))))

    @Test
    fun itHasNoTabAndSitsAfterTheRailCapabilities() = runTest {
        assertThat(capability.id).isEqualTo("weather")
        assertThat(capability.order).isEqualTo(60)
        assertThat(capability.hasTab.first()).isFalse()
    }

    @Test
    fun weatherHasNoHomeCard() = runTest {
        location.value = null
        assertThat(capability.cards().first()).isEmpty()
    }

    @Test
    fun theHeaderItemShowsWithoutALocation() = runTest {
        location.value = null
        assertThat(capability.headerItems().first().single().id).isEqualTo("weather")
    }

    @Test
    fun theHeaderItemIsThereOnlyWhileThereIsSomethingToSay() = runTest {
        val item = capability.headerItems().first().single()
        assertThat(item.id).isEqualTo("weather")
        assertThat(item.order).isEqualTo(10)
        stored.value = null
        assertThat(capability.headerItems().first()).isEmpty()
    }
}
