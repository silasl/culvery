package uk.co.siland.culvery.capability.weather

import java.time.LocalDate
import java.time.LocalTime
import uk.co.siland.culvery.core.household.HomeLocation

internal val LONDON = HomeLocation("London", 51.5074, -0.1278, "Europe/London")
internal val LEEDS = HomeLocation("Leeds", 53.7997, -1.5492, "Europe/London")
internal val WELLINGTON = HomeLocation("Wellington", -41.29, 174.78, "Pacific/Auckland")

/** Thursday 1 October 2026. */
internal val THU: LocalDate = LocalDate.of(2026, 10, 1)
internal val FRI: LocalDate = THU.plusDays(1)
internal val SAT: LocalDate = THU.plusDays(2)

internal val SUNRISE: LocalTime = LocalTime.of(7, 1)
internal val SUNSET: LocalTime = LocalTime.of(18, 38)

internal fun day(
    date: LocalDate,
    condition: Condition = Condition.PARTLY_CLOUDY,
    high: Double = 19.0,
    low: Double = 11.0,
    sunrise: LocalTime? = SUNRISE,
    sunset: LocalTime? = SUNSET,
) = DailyWeather(date, condition, high, low, sunrise, sunset)

internal fun hoursOf(date: LocalDate, condition: Condition = Condition.PARTLY_CLOUDY, temperature: Double = 17.0): List<HourlyWeather> =
    (0 until 24).map { HourlyWeather(date.atTime(it, 0), condition, temperature) }

/** [days] days from [from], each with its 24 hours. */
internal fun forecast(from: LocalDate = THU, days: Int = 7): Forecast {
    val dates = (0 until days).map { from.plusDays(it.toLong()) }
    return Forecast(dates.map { day(it) }, dates.flatMap { hoursOf(it) })
}

internal fun stored(location: HomeLocation = LONDON, forecast: Forecast = forecast(), fetchedAtMillis: Long = 0L) =
    StoredWeather(WeatherPlace(location), fetchedAtMillis, forecast.days, forecast.hours)

/** Thursday at 11:00, as the card and header screenshots show it. */
internal val READY = WeatherView.Ready(
    now = HourlyWeather(THU.atTime(11, 0), Condition.PARTLY_CLOUDY, 17.0),
    today = day(THU),
    days = listOf(
        day(THU),
        day(FRI, Condition.RAIN, high = 17.0, low = 10.0),
        day(SAT, Condition.CLEAR, high = 21.0, low = 12.0),
    ),
    fetchedAtMillis = 0L,
)
