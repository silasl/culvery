package uk.co.siland.culvery.capability.weather.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import uk.co.siland.culvery.capability.weather.HeaderWeather
import uk.co.siland.culvery.capability.weather.degrees
import uk.co.siland.culvery.capability.weather.highLow
import uk.co.siland.culvery.capability.weather.weatherIcon
import uk.co.siland.culvery.core.ui.Culvery
import uk.co.siland.culvery.core.ui.HhIcon
import uk.co.siland.culvery.core.ui.HhType
import uk.co.siland.culvery.core.ui.SunAmber

/** Hand-off §1's weather block: the condition in sun amber, "17°", and "High 19° · Low 11°" (4b design §4.1). */
@Composable
fun WeatherHeaderItem(weather: HeaderWeather, modifier: Modifier = Modifier) {
    val c = Culvery.colors
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(WeatherDimens.headerIconGap),
        modifier = modifier.testTag("weather_header"),
    ) {
        HhIcon(weatherIcon(weather.condition, weather.night), size = WeatherDimens.headerIcon, tint = SunAmber)
        Column {
            Text(degrees(weather.temperature), style = HhType.headerValue, color = c.ink)
            Text(highLow(weather.high, weather.low), style = HhType.secondary, color = c.mute)
        }
    }
}
