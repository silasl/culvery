package uk.co.siland.culvery.capability.weather.ui

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import uk.co.siland.culvery.capability.weather.ADD_LOCATION
import uk.co.siland.culvery.capability.weather.DailyWeather
import uk.co.siland.culvery.capability.weather.FORECAST_TITLE
import uk.co.siland.culvery.capability.weather.GETTING_FORECAST
import uk.co.siland.culvery.capability.weather.NO_FORECAST
import uk.co.siland.culvery.capability.weather.NO_FORECAST_YET
import uk.co.siland.culvery.capability.weather.OPEN_SETTINGS
import uk.co.siland.culvery.capability.weather.WeatherRepository
import uk.co.siland.culvery.capability.weather.WeatherView
import uk.co.siland.culvery.capability.weather.dayLabel
import uk.co.siland.culvery.capability.weather.degrees
import uk.co.siland.culvery.capability.weather.rowDescription
import uk.co.siland.culvery.capability.weather.updatedAgo
import uk.co.siland.culvery.capability.weather.weatherIcon
import uk.co.siland.culvery.core.plugin.HouseholdClock
import uk.co.siland.culvery.core.plugin.LocalShellNavigator
import uk.co.siland.culvery.core.plugin.rememberNowMillis
import uk.co.siland.culvery.core.ui.ButtonSurface
import uk.co.siland.culvery.core.ui.ButtonTone
import uk.co.siland.culvery.core.ui.Culvery
import uk.co.siland.culvery.core.ui.HhButton
import uk.co.siland.culvery.core.ui.HhCard
import uk.co.siland.culvery.core.ui.HhIcon
import uk.co.siland.culvery.core.ui.HhType
import uk.co.siland.culvery.core.ui.ShellTokens

/**
 * The REGULAR Forecast card (4b design §4.2): today and up to two more days, each row day · icon · high · low and one
 * TalkBack item; an age line past 2 h; otherwise what to do. [nowMillis] is for the age line only.
 */
@Composable
fun ForecastCard(view: WeatherView, nowMillis: Long, modifier: Modifier = Modifier) {
    val c = Culvery.colors
    val navigator = LocalShellNavigator.current
    HhCard(
        modifier = modifier.fillMaxSize().testTag("weather_forecast"),
        radius = ShellTokens.homeCardRadius,
        padding = PaddingValues(horizontal = WeatherDimens.cardPaddingH, vertical = WeatherDimens.cardPaddingV),
    ) {
        Text(FORECAST_TITLE, style = HhType.cardTitle, color = c.ink, modifier = Modifier.semantics { heading() })
        Spacer(Modifier.height(WeatherDimens.titleGap))
        when (view) {
            is WeatherView.Ready -> {
                view.days.forEachIndexed { i, day -> ForecastRow(day, isToday = i == 0) }
                Spacer(Modifier.weight(1f))
                updatedAgo(view.fetchedAtMillis, nowMillis)?.let {
                    Text(it, style = HhType.secondary, color = c.mute, modifier = Modifier.testTag("weather_age"))
                }
            }
            WeatherView.Waiting -> Text(GETTING_FORECAST, style = HhType.body, color = c.mute)
            WeatherView.Failed -> Text(NO_FORECAST_YET, style = HhType.body, color = c.mute, modifier = Modifier.testTag("weather_failed"))
            WeatherView.Expired -> Text(NO_FORECAST, style = HhType.body, color = c.mute)
            WeatherView.NoLocation -> {
                Text(ADD_LOCATION, style = HhType.body, color = c.mute)
                Spacer(Modifier.height(WeatherDimens.promptButtonTop))
                HhButton(OPEN_SETTINGS, navigator::openSettings, tone = ButtonTone.Primary, on = ButtonSurface.Surf)
            }
        }
    }
}

@Composable
private fun ForecastRow(day: DailyWeather, isToday: Boolean) {
    val c = Culvery.colors
    val description = rowDescription(day, isToday)
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = WeatherDimens.rowHeight)
            .testTag("forecast_row")
            .clearAndSetSemantics { contentDescription = description },
    ) {
        Text(dayLabel(day, isToday), style = HhType.rowTitle, color = c.ink, modifier = Modifier.weight(1f))
        HhIcon(weatherIcon(day.condition, night = false), size = WeatherDimens.rowIcon, tint = c.weather)
        Spacer(Modifier.width(WeatherDimens.rowIconGap))
        TemperaturePair(degrees(day.high), degrees(day.low))
    }
}

/** A day's high and low, each on one line, at least 44 dp wide and right-aligned, 8 dp apart so they never run together. */
@Composable
internal fun TemperaturePair(high: String, low: String) {
    val c = Culvery.colors
    Row(verticalAlignment = Alignment.CenterVertically) {
        Temperature(high, c.ink)
        Spacer(Modifier.width(WeatherDimens.temperatureGap))
        Temperature(low, c.mute)
    }
}

@Composable
private fun Temperature(text: String, color: Color) {
    Text(
        text,
        style = HhType.rowTitle,
        color = color,
        textAlign = TextAlign.End,
        maxLines = 1,
        softWrap = false,
        modifier = Modifier.widthIn(min = WeatherDimens.temperatureWidth),
    )
}

/** The card over the repository: nothing until the first view, the age line moves on each minute. */
@Composable
internal fun ForecastCardHost(repo: WeatherRepository, clock: HouseholdClock) {
    val view by repo.view.collectAsState(initial = null)
    val nowMillis = rememberNowMillis(clock)
    view?.let { ForecastCard(it, nowMillis) }
}
