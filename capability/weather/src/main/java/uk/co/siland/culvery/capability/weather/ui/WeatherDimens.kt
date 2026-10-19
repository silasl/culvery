package uk.co.siland.culvery.capability.weather.ui

import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import uk.co.siland.culvery.core.ui.HhType

/** Weather layout numbers in Home's header (9 §4.2). */
internal object WeatherDimens {
    // The current weather: icon, then the temperature over the condition.
    val nowIcon = 56.dp
    val nowIconGap = 15.dp
    val temperature: TextStyle = HhType.headerValue.copy(fontSize = 43.sp, fontWeight = FontWeight.W700)
    val condition: TextStyle = HhType.secondary.copy(fontSize = 15.sp)

    // The strip: a divider, then three days 23 apart.
    val dividerWidth = 1.dp
    val dividerHeight = 67.dp
    val stripGap = 23.dp
    val dayIcon = 28.dp
    val dayLabel: TextStyle = HhType.label
    val dayHigh: TextStyle = HhType.secondary.copy(fontSize = 15.sp, fontWeight = FontWeight.W700)
    val dayLow: TextStyle = HhType.secondary.copy(fontSize = 15.sp)
    val dayTemperatureGap = 5.dp

    // The sentence for a failed fetch wraps rather than push the clock.
    val noForecastMaxWidth = 360.dp
}
