package uk.co.siland.culvery.capability.weather.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
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
import uk.co.siland.culvery.capability.weather.WeatherView
import uk.co.siland.culvery.core.plugin.LocalShellNavigator
import uk.co.siland.culvery.core.ui.Culvery
import uk.co.siland.culvery.core.ui.CulveryTheme

/** A REGULAR cell as the Home grid gives it on the 1280×800 canvas: half the WIDE card's 705 dp less the 14 dp gap. */
private val REGULAR_W = 345.dp
private val REGULAR_H = 279.dp
private const val THREE_HOURS = 3 * 3_600_000L

@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class WeatherScreenshotTest {
    @get:Rule val compose = createComposeRule()

    private val day = HeaderWeather(Condition.PARTLY_CLOUDY, night = false, temperature = 17.0, high = 19.0, low = 11.0)
    private val night = day.copy(night = true)
    private val clearNight = night.copy(condition = Condition.CLEAR)

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

    private fun card(name: String, dark: Boolean, view: WeatherView, nowMillis: Long = READY.fetchedAtMillis) =
        snap(name, dark) { Box(Modifier.size(REGULAR_W, REGULAR_H)) { ForecastCard(view, nowMillis) } }

    @Test fun forecastReadyDark() = card("forecast_ready_dark", true, READY)
    @Test fun forecastReadyLight() = card("forecast_ready_light", false, READY)
    @Test fun forecastAgeDark() = card("forecast_age_dark", true, READY, READY.fetchedAtMillis + THREE_HOURS)
    @Test fun forecastAgeLight() = card("forecast_age_light", false, READY, READY.fetchedAtMillis + THREE_HOURS)
    @Test fun forecastWaitingDark() = card("forecast_waiting_dark", true, WeatherView.Waiting)
    @Test fun forecastWaitingLight() = card("forecast_waiting_light", false, WeatherView.Waiting)
    @Test fun forecastExpiredDark() = card("forecast_expired_dark", true, WeatherView.Expired)
    @Test fun forecastExpiredLight() = card("forecast_expired_light", false, WeatherView.Expired)
    @Test fun forecastNoLocationDark() = card("forecast_no_location_dark", true, WeatherView.NoLocation)
    @Test fun forecastNoLocationLight() = card("forecast_no_location_light", false, WeatherView.NoLocation)
    @Test fun headerDayDark() = snap("header_day_dark", true) { WeatherHeaderItem(day) }
    @Test fun headerDayLight() = snap("header_day_light", false) { WeatherHeaderItem(day) }
    @Test fun headerNightDark() = snap("header_night_dark", true) { WeatherHeaderItem(night) }
    @Test fun headerNightLight() = snap("header_night_light", false) { WeatherHeaderItem(night) }
    @Test fun headerClearNightDark() = snap("header_clear_night_dark", true) { WeatherHeaderItem(clearNight) }
    @Test fun headerClearNightLight() = snap("header_clear_night_light", false) { WeatherHeaderItem(clearNight) }
}
