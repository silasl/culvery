package uk.co.siland.culvery.capability.weather.ui

import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.test.assertContentDescriptionEquals
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertHeightIsEqualTo
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertWidthIsEqualTo
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.google.common.truth.Truth.assertThat
import java.time.LocalDate
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.GraphicsMode
import uk.co.siland.culvery.capability.weather.ADD_HOME_LOCATION_FOR_WEATHER
import uk.co.siland.culvery.capability.weather.Condition
import uk.co.siland.culvery.capability.weather.HeaderWeather
import uk.co.siland.culvery.capability.weather.NO_FORECAST
import uk.co.siland.culvery.capability.weather.day
import uk.co.siland.culvery.core.plugin.LocalShellNavigator
import uk.co.siland.culvery.core.plugin.SettingsPageIds
import uk.co.siland.culvery.core.plugin.test.RecordingNavigator
import uk.co.siland.culvery.core.ui.CulveryTheme
import uk.co.siland.culvery.core.ui.test.assertClickablesHaveRoleAndLabel
import uk.co.siland.culvery.core.ui.test.assertNothingReadTwice

/** Real text metrics: the temperature's size is read from its layout. */
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class WeatherHeaderItemTest {
    @get:Rule val compose = createComposeRule()
    private val navigator = RecordingNavigator()

    private val wednesday = LocalDate.of(2026, 10, 21)
    private val now = HeaderWeather.Now(
        Condition.PARTLY_CLOUDY, night = false, temperature = 17.0, high = 19.0, low = 11.0,
        days = listOf(
            day(wednesday, Condition.PARTLY_CLOUDY, high = 19.0, low = 11.0),
            day(wednesday.plusDays(1), Condition.RAIN, high = 16.0, low = 10.0),
            day(wednesday.plusDays(2), Condition.CLEAR, high = 21.0, low = 12.0),
        ),
    )

    private fun show(weather: HeaderWeather) = compose.setContent {
        CompositionLocalProvider(LocalShellNavigator provides navigator) {
            CulveryTheme(dark = true) { WeatherHeaderItem(weather) }
        }
    }

    @Test
    fun theCurrentWeatherIsOneItem() {
        show(now)
        compose.onNodeWithTag("weather_header").assertContentDescriptionEquals("Now 17°, partly cloudy, high 19°, low 11°")
        // Drawn but not spoken, so the weather is said once.
        compose.onAllNodesWithText("Partly cloudy", useUnmergedTree = true).assertCountEquals(1)
        compose.onNodeWithTag("weather_header_icon", useUnmergedTree = true).assertHeightIsEqualTo(56.dp).assertWidthIsEqualTo(56.dp)
        assertThat(compose.onNodeWithText("17°", useUnmergedTree = true).textLayout().layoutInput.style.fontSize).isEqualTo(43.sp)
        compose.assertNothingReadTwice()
        compose.assertClickablesHaveRoleAndLabel()
    }

    @Test
    fun theStripReadsTodayThenTwoDays() {
        show(now)
        val rows = compose.onAllNodesWithTag("weather_day")
        rows.assertCountEquals(3)
        rows[0].assertContentDescriptionEquals("Today, partly cloudy, high 19°, low 11°")
        rows[1].assertContentDescriptionEquals("Thursday, rain, high 16°, low 10°")
        rows[2].assertContentDescriptionEquals("Friday, clear, high 21°, low 12°")
        compose.onNodeWithText("Today", useUnmergedTree = true).assertIsDisplayed()
        compose.onNodeWithText("Thu", useUnmergedTree = true).assertIsDisplayed()
        compose.onNodeWithText("Fri", useUnmergedTree = true).assertIsDisplayed()
        compose.assertNothingReadTwice()
        compose.assertClickablesHaveRoleAndLabel()
    }

    @Test
    fun theNoLocationPillOpensHomeLocation() {
        show(HeaderWeather.AddLocation)
        compose.onNodeWithText(ADD_HOME_LOCATION_FOR_WEATHER).performClick()
        assertThat(navigator.pages).containsExactly(SettingsPageIds.LOCATION)
        compose.assertClickablesHaveRoleAndLabel()
    }

    @Test
    fun noForecastSaysWhy() {
        show(HeaderWeather.NoForecast)
        compose.onNodeWithText(NO_FORECAST).assertIsDisplayed()
        compose.assertClickablesHaveRoleAndLabel()
    }
}
