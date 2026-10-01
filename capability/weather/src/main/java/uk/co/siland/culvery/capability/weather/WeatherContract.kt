package uk.co.siland.culvery.capability.weather

import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.ZoneId
import uk.co.siland.culvery.core.plugin.ProviderDescriptor

/** A source of forecasts (4b design §3.1), bound `@IntoSet` by a provider module. */
interface WeatherProvider {
    val descriptor: ProviderDescriptor

    /** Times are wall times in [zone]; temperatures °C. Throws WeatherUnavailableException on any failure; nothing else escapes. */
    suspend fun forecast(latitude: Double, longitude: Double, zone: ZoneId): Forecast
}

data class Forecast(val days: List<DailyWeather>, val hours: List<HourlyWeather>)

data class DailyWeather(
    val date: LocalDate,
    val condition: Condition,
    val high: Double,
    val low: Double,
    /** Null on a polar day or night. */
    val sunrise: LocalTime?,
    val sunset: LocalTime?,
)

data class HourlyWeather(val start: LocalDateTime, val condition: Condition, val temperature: Double)

/** The capability's own conditions; each provider maps its codes onto them. */
enum class Condition { CLEAR, PARTLY_CLOUDY, CLOUDY, FOG, DRIZZLE, RAIN, SHOWERS, SNOW, THUNDER }

/** The weather contract's own failure: the calendar's `UnreachableException` is out of this module's reach. */
class WeatherUnavailableException(message: String? = null, cause: Throwable? = null) : Exception(message, cause)
