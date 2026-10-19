package uk.co.siland.culvery.capability.weather.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import uk.co.siland.culvery.capability.weather.ADD_HOME_LOCATION_FOR_WEATHER
import uk.co.siland.culvery.capability.weather.DailyWeather
import uk.co.siland.culvery.capability.weather.HeaderWeather
import uk.co.siland.culvery.capability.weather.NO_FORECAST
import uk.co.siland.culvery.capability.weather.conditionWords
import uk.co.siland.culvery.capability.weather.dayLabel
import uk.co.siland.culvery.capability.weather.degrees
import uk.co.siland.culvery.capability.weather.headerDescription
import uk.co.siland.culvery.capability.weather.rowDescription
import uk.co.siland.culvery.capability.weather.weatherIcon
import uk.co.siland.culvery.capability.weather.weatherTint
import uk.co.siland.culvery.core.plugin.LocalShellNavigator
import uk.co.siland.culvery.core.plugin.SettingsPageIds
import uk.co.siland.culvery.core.ui.ButtonSurface
import uk.co.siland.culvery.core.ui.Culvery
import uk.co.siland.culvery.core.ui.HhButton
import uk.co.siland.culvery.core.ui.HhIcon
import uk.co.siland.culvery.core.ui.HhType
import uk.co.siland.culvery.core.ui.shownNotSpoken

/** The weather in Home's header (9 §4.2): the weather now and three days, or what to do when there is none. */
@Composable
fun WeatherHeaderItem(weather: HeaderWeather, modifier: Modifier = Modifier) {
    when (weather) {
        is HeaderWeather.Now -> WeatherNow(weather, modifier)
        HeaderWeather.AddLocation -> {
            val navigator = LocalShellNavigator.current
            HhButton(
                ADD_HOME_LOCATION_FOR_WEATHER,
                { navigator.openSettings(SettingsPageIds.LOCATION) },
                modifier = modifier.testTag("weather_add_location"),
                on = ButtonSurface.Bg,
            )
        }
        HeaderWeather.NoForecast -> Text(
            NO_FORECAST,
            style = HhType.secondary,
            color = Culvery.colors.mute,
            modifier = modifier.widthIn(max = WeatherDimens.noForecastMaxWidth).testTag("weather_no_forecast"),
        )
    }
}

@Composable
private fun WeatherNow(weather: HeaderWeather.Now, modifier: Modifier) {
    val c = Culvery.colors
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(WeatherDimens.stripGap),
        modifier = modifier,
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(WeatherDimens.nowIconGap),
            modifier = Modifier.testTag("weather_header").semantics(mergeDescendants = true) { contentDescription = headerDescription(weather) },
        ) {
            Box(Modifier.size(WeatherDimens.nowIcon).testTag("weather_header_icon")) {
                HhIcon(weatherIcon(weather.condition, weather.night), size = WeatherDimens.nowIcon, tint = weatherTint(weather.condition, c))
            }
            Column(Modifier.shownNotSpoken()) {
                Text(degrees(weather.temperature), style = WeatherDimens.temperature, color = c.ink, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(
                    conditionWords(weather.condition).replaceFirstChar { it.uppercase() },
                    style = WeatherDimens.condition, color = c.mute, maxLines = 1, overflow = TextOverflow.Ellipsis,
                )
            }
        }
        Box(Modifier.size(WeatherDimens.dividerWidth, WeatherDimens.dividerHeight).background(c.surf3))
        Row(horizontalArrangement = Arrangement.spacedBy(WeatherDimens.stripGap)) {
            weather.days.forEachIndexed { i, day -> StripDay(day, isToday = i == 0) }
        }
    }
}

@Composable
private fun StripDay(day: DailyWeather, isToday: Boolean) {
    val c = Culvery.colors
    val description = rowDescription(day, isToday)
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier.testTag("weather_day").clearAndSetSemantics { contentDescription = description },
    ) {
        Text(dayLabel(day, isToday), style = WeatherDimens.dayLabel, color = c.mute, maxLines = 1)
        HhIcon(weatherIcon(day.condition, night = false), size = WeatherDimens.dayIcon, tint = weatherTint(day.condition, c))
        Row(horizontalArrangement = Arrangement.spacedBy(WeatherDimens.dayTemperatureGap)) {
            Text(degrees(day.high), style = WeatherDimens.dayHigh, color = c.ink, maxLines = 1, softWrap = false)
            Text(degrees(day.low), style = WeatherDimens.dayLow, color = c.mute, maxLines = 1, softWrap = false)
        }
    }
}
