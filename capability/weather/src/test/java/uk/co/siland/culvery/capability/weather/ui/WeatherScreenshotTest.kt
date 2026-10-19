package uk.co.siland.culvery.capability.weather.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.github.takahirom.roborazzi.captureRoboImage
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.GraphicsMode
import uk.co.siland.culvery.capability.weather.Condition
import uk.co.siland.culvery.capability.weather.HeaderWeather
import uk.co.siland.culvery.capability.weather.READY
import uk.co.siland.culvery.core.plugin.LocalShellNavigator
import uk.co.siland.culvery.core.plugin.test.RecordingNavigator
import uk.co.siland.culvery.core.ui.Culvery
import uk.co.siland.culvery.core.ui.CulveryTheme

@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class WeatherScreenshotTest {
    @get:Rule val compose = createComposeRule()

    private val day = HeaderWeather.Now(Condition.PARTLY_CLOUDY, night = false, temperature = 17.0, high = 19.0, low = 11.0, days = READY.days)
    private val night = day.copy(night = true)
    private val clearNight = night.copy(condition = Condition.CLEAR)
    private val rain = day.copy(condition = Condition.RAIN, temperature = 12.0)

    private fun snap(name: String, dark: Boolean, content: @Composable () -> Unit) {
        compose.setContent {
            CompositionLocalProvider(LocalShellNavigator provides RecordingNavigator()) {
                CulveryTheme(dark = dark) {
                    Box(Modifier.testTag("shot").background(Culvery.colors.bg).padding(16.dp)) { content() }
                }
            }
        }
        compose.onNodeWithTag("shot").captureRoboImage("src/test/screenshots/$name.png")
    }

    @Test fun headerDayDark() = snap("header_day_dark", true) { WeatherHeaderItem(day) }
    @Test fun headerDayLight() = snap("header_day_light", false) { WeatherHeaderItem(day) }
    @Test fun headerNightDark() = snap("header_night_dark", true) { WeatherHeaderItem(night) }
    @Test fun headerNightLight() = snap("header_night_light", false) { WeatherHeaderItem(night) }
    @Test fun headerClearNightDark() = snap("header_clear_night_dark", true) { WeatherHeaderItem(clearNight) }
    @Test fun headerClearNightLight() = snap("header_clear_night_light", false) { WeatherHeaderItem(clearNight) }
    @Test fun headerRainDark() = snap("header_rain_dark", true) { WeatherHeaderItem(rain) }
    @Test fun headerRainLight() = snap("header_rain_light", false) { WeatherHeaderItem(rain) }
    @Test fun headerNoLocationDark() = snap("header_no_location_dark", true) { WeatherHeaderItem(HeaderWeather.AddLocation) }
    @Test fun headerNoLocationLight() = snap("header_no_location_light", false) { WeatherHeaderItem(HeaderWeather.AddLocation) }
    @Test fun headerNoForecastDark() = snap("header_no_forecast_dark", true) { WeatherHeaderItem(HeaderWeather.NoForecast) }
}
