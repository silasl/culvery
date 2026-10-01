package uk.co.siland.culvery.capability.weather

import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlin.math.roundToInt

internal const val FORECAST_TITLE = "Forecast"
internal const val TODAY = "Today"
internal const val GETTING_FORECAST = "Getting the forecast…"
internal const val NO_FORECAST = "No forecast — check the tablet's Wi-Fi."
internal const val ADD_LOCATION = "Add your home location to see the weather."
internal const val OPEN_SETTINGS = "Open settings"

/** Past this the card says how old its data is (D3). */
internal const val STALE_AFTER_MS = 2 * 60 * 60_000L
private const val HOUR_MS = 60 * 60_000L
private const val HOURS_IN_DAY = 24L

private val SHORT_DAY = DateTimeFormatter.ofPattern("EEE", Locale.UK)
private val LONG_DAY = DateTimeFormatter.ofPattern("EEEE", Locale.UK)

/** Whole degrees, half up; an Int has no "-0" (§4.2). */
internal fun degrees(celsius: Double): String = "${celsius.roundToInt()}°"

internal fun highLow(high: Double, low: Double): String = "High ${degrees(high)} · Low ${degrees(low)}"

/** "Updated 3 h ago" once the data is past [STALE_AFTER_MS] old, else null (§4.2). */
internal fun updatedAgo(fetchedAtMillis: Long, nowMillis: Long): String? {
    val age = nowMillis - fetchedAtMillis
    if (age <= STALE_AFTER_MS) return null
    val hours = age / HOUR_MS
    if (hours < HOURS_IN_DAY) return "Updated $hours h ago"
    val days = hours / HOURS_IN_DAY
    return if (days == 1L) "Updated 1 day ago" else "Updated $days days ago"
}

/** Material Symbols names (§4.2), all checked present in the bundled font. */
internal fun weatherIcon(condition: Condition, night: Boolean): String = when (condition) {
    Condition.CLEAR -> if (night) "clear_night" else "sunny"
    Condition.PARTLY_CLOUDY -> if (night) "partly_cloudy_night" else "partly_cloudy_day"
    Condition.CLOUDY -> "cloud"
    Condition.FOG -> "foggy"
    Condition.DRIZZLE -> "rainy_light"
    Condition.RAIN -> "rainy"
    Condition.SHOWERS -> "rainy_heavy"
    Condition.SNOW -> "weather_snowy"
    Condition.THUNDER -> "thunderstorm"
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
