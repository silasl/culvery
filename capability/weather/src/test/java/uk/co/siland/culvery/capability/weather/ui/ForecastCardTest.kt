package uk.co.siland.culvery.capability.weather.ui

import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import uk.co.siland.culvery.capability.weather.LONDON
import uk.co.siland.culvery.capability.weather.READY
import uk.co.siland.culvery.capability.weather.THU
import uk.co.siland.culvery.capability.weather.WeatherRepository
import uk.co.siland.culvery.capability.weather.WeatherView
import uk.co.siland.culvery.capability.weather.stored
import uk.co.siland.culvery.core.plugin.LocalShellNavigator
import uk.co.siland.culvery.core.plugin.WallClock
import uk.co.siland.culvery.core.ui.CulveryTheme

@RunWith(AndroidJUnit4::class)
class ForecastCardTest {
    @get:Rule val compose = createComposeRule()
    private val navigator = RecordingNavigator()
    private val fetched = 1_000_000L
    private val hour = 3_600_000L

    private fun show(view: WeatherView, nowMillis: Long = fetched) = compose.setContent {
        CompositionLocalProvider(LocalShellNavigator provides navigator) {
            CulveryTheme(dark = true) { ForecastCard(view, nowMillis) }
        }
    }

    @Test
    fun readyShowsTodayThenTheNextTwoDaysEachAsOneSpokenRow() {
        show(READY)
        compose.onNodeWithText("Forecast").assertExists()
        compose.onAllNodesWithTag("forecast_row").assertCountEquals(3)
        compose.onNodeWithContentDescription("Today, partly cloudy, high 19°, low 11°").assertExists()
        compose.onNodeWithContentDescription("Friday, rain, high 17°, low 10°").assertExists()
        compose.onNodeWithContentDescription("Saturday, clear, high 21°, low 12°").assertExists()
        // A row's own texts are folded into its description, not read one by one.
        compose.onAllNodesWithText("Fri").assertCountEquals(0)
    }

    @Test
    fun fewerDaysShowOnlyTheRowsThatExist() {
        show(READY.copy(days = READY.days.take(2)))
        compose.onAllNodesWithTag("forecast_row").assertCountEquals(2)
    }

    @Test
    fun waitingSaysItIsGettingTheForecast() {
        show(WeatherView.Waiting)
        compose.onNodeWithText("Getting the forecast…").assertExists()
        compose.onAllNodesWithTag("forecast_row").assertCountEquals(0)
    }

    @Test
    fun expiredSaysToCheckTheWifi() {
        show(WeatherView.Expired)
        compose.onNodeWithText("No forecast — check the tablet's Wi-Fi.").assertExists()
    }

    @Test
    fun noLocationOpensSettings() {
        show(WeatherView.NoLocation)
        compose.onNodeWithText("Add your home location to see the weather.").assertExists()
        compose.onNodeWithText("Open settings").performClick()
        assertThat(navigator.settingsOpened).isEqualTo(1)
    }

    /** The age rule is `WeatherWordsTest`'s; this pins that the card shows it and moves it on with the clock. */
    @Test
    fun theHostMovesTheAgeLineOnWithTheClock() {
        var now = fetched + 2 * hour
        val clock = WallClock { now }
        val ticks = MutableSharedFlow<Unit>(extraBufferCapacity = 1)
        val repo = WeatherRepository(
            MutableStateFlow(LONDON),
            MutableStateFlow(stored(fetchedAtMillis = fetched)),
            MutableStateFlow(THU.atTime(10, 30)),
        )
        compose.setContent {
            CompositionLocalProvider(LocalShellNavigator provides navigator) {
                CulveryTheme(dark = true) { ForecastCardHost(repo, clock, ticks) }
            }
        }
        compose.waitUntil(5_000) { compose.onAllNodesWithTag("forecast_row").fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithTag("weather_age").assertDoesNotExist()
        now = fetched + 3 * hour
        ticks.tryEmit(Unit)
        compose.waitUntil(5_000) { compose.onAllNodesWithText("Updated 3 h ago").fetchSemanticsNodes().isNotEmpty() }
    }
}
