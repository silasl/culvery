package uk.co.siland.culvery.provider.weather_openmeteo

import android.util.Log
import java.io.IOException
import java.time.DateTimeException
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import okhttp3.HttpUrl
import okhttp3.OkHttpClient
import okhttp3.Request
import uk.co.siland.culvery.capability.weather.Condition
import uk.co.siland.culvery.capability.weather.DailyWeather
import uk.co.siland.culvery.capability.weather.Forecast
import uk.co.siland.culvery.capability.weather.HourlyWeather
import uk.co.siland.culvery.capability.weather.WeatherProvider
import uk.co.siland.culvery.capability.weather.WeatherUnavailableException
import uk.co.siland.culvery.core.plugin.Feature
import uk.co.siland.culvery.core.plugin.ProviderDescriptor
import uk.co.siland.culvery.core.plugin.SunTimes
import uk.co.siland.culvery.core.plugin.WallClock

/** Open-Meteo's forecast API: no key (4b design D7). */
const val OPEN_METEO_FORECAST_URL = "https://api.open-meteo.com/v1/forecast"

const val OPEN_METEO_PROVIDER_ID = "weather.openmeteo"

/** The real answer is about 6 KB; anything past this isn't a forecast (plan review 13). */
internal const val MAX_FORECAST_BYTES = 1_048_576L

private const val DAYS_FETCHED = 7
private const val DAILY = "weather_code,temperature_2m_max,temperature_2m_min,sunrise,sunset"
private const val HOURLY = "temperature_2m,weather_code"

@Serializable
internal data class ForecastAnswer(val daily: DailyArrays? = null, val hourly: HourlyArrays? = null)

@Serializable
internal data class DailyArrays(
    val time: List<String?>? = null,
    @SerialName("weather_code") val weatherCode: List<Int?>? = null,
    @SerialName("temperature_2m_max") val high: List<Double?>? = null,
    @SerialName("temperature_2m_min") val low: List<Double?>? = null,
    val sunrise: List<String?>? = null,
    val sunset: List<String?>? = null,
)

@Serializable
internal data class HourlyArrays(
    val time: List<String?>? = null,
    @SerialName("temperature_2m") val temperature: List<Double?>? = null,
    @SerialName("weather_code") val weatherCode: List<Int?>? = null,
)

/** A coordinate as Open-Meteo reads it: plain digits, never an exponent ("-0.0005", not "-5.0E-4"). */
internal fun coordinate(value: Double): String = value.toBigDecimal().stripTrailingZeros().toPlainString()

/** A required array is missing, or the arrays of one block differ in length (§3.4). */
internal class Unreadable : Exception("A forecast array is missing or the wrong length")

private val ForecastJson = Json { ignoreUnknownKeys = true }

/**
 * Seven days of daily and hourly weather, in local times of the zone asked for (4b design §3.4). Every failure is a
 * [WeatherUnavailableException] with fixed words and no cause; nothing logged holds the URL, the body, the coordinates
 * or the zone.
 */
class OpenMeteoForecast(
    private val url: HttpUrl,
    private val client: OkHttpClient,
    private val clock: WallClock,
) : WeatherProvider {
    override val descriptor = ProviderDescriptor(
        id = OPEN_METEO_PROVIDER_ID,
        displayName = "Open-Meteo",
        icon = "partly_cloudy_day",
        features = setOf(Feature.READ),
    )

    override suspend fun forecast(latitude: Double, longitude: Double, zone: ZoneId): Forecast {
        val request = Request.Builder()
            .url(
                url.newBuilder()
                    .addQueryParameter("latitude", coordinate(latitude))
                    .addQueryParameter("longitude", coordinate(longitude))
                    .addQueryParameter("timezone", zone.id)
                    .addQueryParameter("forecast_days", DAYS_FETCHED.toString())
                    .addQueryParameter("daily", DAILY)
                    .addQueryParameter("hourly", HOURLY)
                    .build(),
            )
            .build()
        val answer = try {
            client.newCall(request).await(MAX_FORECAST_BYTES)
        } catch (e: IOException) {
            // A cancelled caller gets its cancellation, never "unavailable".
            currentCoroutineContext().ensureActive()
            Log.w(TAG, "Forecast failed (${e::class.simpleName})")
            throw WeatherUnavailableException("Couldn't reach the forecast")
        }
        if (answer.code !in 200..299) {
            Log.w(TAG, "Forecast answered ${answer.code}")
            throw WeatherUnavailableException("The forecast answered ${answer.code}")
        }
        val today = Instant.ofEpochMilli(clock.nowMillis()).atZone(zone).toLocalDate()
        return try {
            readForecast(ForecastJson.decodeFromString(ForecastAnswer.serializer(), answer.body), today)
        } catch (e: IllegalArgumentException) {
            throw unreadable(e)
        } catch (e: DateTimeException) {
            throw unreadable(e)
        } catch (e: Unreadable) {
            throw unreadable(e)
        }
    }

    private fun unreadable(e: Exception): WeatherUnavailableException {
        // The type only: kotlinx.serialization and java.time quote the input, which holds the coordinates.
        Log.w(TAG, "Forecast sent an answer the tablet can't read (${e::class.simpleName})")
        return WeatherUnavailableException("The forecast sent an answer the tablet can't read")
    }
}

/**
 * Keeps only the [DAYS_FETCHED] dates from [today]: nothing else was asked for. Throws [Unreadable],
 * `IllegalArgumentException` or `DateTimeException` for an answer it can't read.
 */
internal fun readForecast(answer: ForecastAnswer, today: LocalDate): Forecast {
    val asked = today..today.plusDays(DAYS_FETCHED - 1L)
    val daily = answer.daily ?: throw Unreadable()
    val hourly = answer.hourly ?: throw Unreadable()
    val dates = daily.time.required()
    val dayCodes = daily.weatherCode.required(dates.size)
    val highs = daily.high.required(dates.size)
    val lows = daily.low.required(dates.size)
    val sunrises = daily.sunrise.required(dates.size)
    val sunsets = daily.sunset.required(dates.size)
    val starts = hourly.time.required()
    val temperatures = hourly.temperature.required(starts.size)
    val hourCodes = hourly.weatherCode.required(starts.size)

    // A null entry (past a model's horizon) drops that day or hour, never the whole answer.
    val days = dates.indices.mapNotNull { i ->
        val date = dates[i]?.let { LocalDate.parse(it) }?.takeIf { it in asked } ?: return@mapNotNull null
        val code = dayCodes[i] ?: return@mapNotNull null
        val high = highs[i] ?: return@mapNotNull null
        val low = lows[i] ?: return@mapNotNull null
        val sun = sunTimes(date, sunrises[i], sunsets[i])
        DailyWeather(date, wmoCondition(code), high, low, sun?.sunrise, sun?.sunset)
    }
    val hours = starts.indices.mapNotNull { i ->
        val start = starts[i]?.let { LocalDateTime.parse(it) }?.takeIf { it.toLocalDate() in asked } ?: return@mapNotNull null
        val code = hourCodes[i] ?: return@mapNotNull null
        val temperature = temperatures[i] ?: return@mapNotNull null
        HourlyWeather(start, wmoCondition(code), temperature)
    }
    return Forecast(days, hours)
}

private fun <T> List<T>?.required(): List<T> = this ?: throw Unreadable()

private fun <T> List<T>?.required(size: Int): List<T> = required().also { if (it.size != size) throw Unreadable() }

/**
 * Both times when the sun rises and sets on [date], else null (ruling 1): Open-Meteo gives a polar night as midnight to
 * midnight and a polar day as midnight to the next day's midnight.
 */
internal fun sunTimes(date: LocalDate, sunrise: String?, sunset: String?): SunTimes? {
    val rise = sunrise?.let { LocalDateTime.parse(it) } ?: return null
    val set = sunset?.let { LocalDateTime.parse(it) } ?: return null
    if (rise.toLocalDate() != date || set.toLocalDate() != date || rise >= set) return null
    return SunTimes(rise.toLocalTime(), set.toLocalTime())
}

/** WMO weather codes (4b design §3.4). */
internal fun wmoCondition(code: Int): Condition = when (code) {
    0 -> Condition.CLEAR
    1, 2 -> Condition.PARTLY_CLOUDY
    3 -> Condition.CLOUDY
    45, 48 -> Condition.FOG
    in 51..57 -> Condition.DRIZZLE
    in 61..67 -> Condition.RAIN
    in 71..77, 85, 86 -> Condition.SNOW
    in 80..82 -> Condition.SHOWERS
    in 95..99 -> Condition.THUNDER
    else -> Condition.CLOUDY
}
