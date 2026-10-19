package uk.co.siland.culvery.capability.weather

import androidx.compose.ui.graphics.Color
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlin.math.roundToInt
import uk.co.siland.culvery.core.ui.HhColors
import uk.co.siland.culvery.core.ui.Icons

internal const val TODAY = "Today"
internal const val NO_FORECAST = "No forecast — check the tablet’s Wi-Fi. Culvery will try again."
internal const val ADD_HOME_LOCATION_FOR_WEATHER = "Add home location for weather"

private val SHORT_DAY = DateTimeFormatter.ofPattern("EEE", Locale.UK)
private val LONG_DAY = DateTimeFormatter.ofPattern("EEEE", Locale.UK)

/** Whole degrees, half up; an Int has no "-0" (§4.2). */
internal fun degrees(celsius: Double): String = "${celsius.roundToInt()}°"

/** Material Symbols names (§4.2), each in the subset font (IconFontTest). */
internal fun weatherIcon(condition: Condition, night: Boolean): String = when (condition) {
    Condition.CLEAR -> if (night) Icons.BEDTIME else Icons.SUNNY
    Condition.PARTLY_CLOUDY -> if (night) Icons.PARTLY_CLOUDY_NIGHT else Icons.PARTLY_CLOUDY_DAY
    Condition.CLOUDY -> Icons.CLOUD
    Condition.FOG -> Icons.FOGGY
    Condition.DRIZZLE -> Icons.RAINY_LIGHT
    Condition.RAIN -> Icons.RAINY
    Condition.SHOWERS -> Icons.RAINY_HEAVY
    Condition.SNOW -> Icons.WEATHER_SNOWY
    Condition.THUNDER -> Icons.THUNDERSTORM
}

/** Rain icons are blue, the rest amber (9 §4.2). */
internal fun weatherTint(condition: Condition, colors: HhColors): Color = when (condition) {
    Condition.DRIZZLE, Condition.RAIN, Condition.SHOWERS, Condition.THUNDER -> colors.rain
    else -> colors.weather
}

internal fun conditionWords(condition: Condition): String = when (condition) {
    Condition.CLEAR -> "clear"
    Condition.PARTLY_CLOUDY -> "partly cloudy"
    Condition.CLOUDY -> "cloudy"
    Condition.FOG -> "fog"
    Condition.DRIZZLE -> "drizzle"
    Condition.RAIN -> "rain"
    Condition.SHOWERS -> "showers"
    Condition.SNOW -> "snow"
    Condition.THUNDER -> "thunderstorms"
}

internal fun dayLabel(day: DailyWeather, isToday: Boolean): String = if (isToday) TODAY else day.date.format(SHORT_DAY)

/** One TalkBack item per row: "Friday, partly cloudy, high 17°, low 10°" (§4.2). */
internal fun rowDescription(day: DailyWeather, isToday: Boolean): String {
    val name = if (isToday) TODAY else day.date.format(LONG_DAY)
    return "$name, ${conditionWords(day.condition)}, high ${degrees(day.high)}, low ${degrees(day.low)}"
}

/** The header's one TalkBack item: "Now 17°, partly cloudy, high 19°, low 11°" (TalkBack reads ° as "degrees"). */
internal fun headerDescription(weather: HeaderWeather.Now): String =
    "Now ${degrees(weather.temperature)}, ${conditionWords(weather.condition)}, high ${degrees(weather.high)}, low ${degrees(weather.low)}"
