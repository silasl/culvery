package uk.co.siland.culvery.capability.weather

import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId
import uk.co.siland.culvery.core.household.HomeLocation
import uk.co.siland.culvery.core.household.zoneOrDevice
import uk.co.siland.culvery.core.plugin.SunTimes

/** The header's days: today and the next two. */
const val FORECAST_DAYS = 3

/** Where a forecast is for: what a fetch records and matching compares (§3.5). The town's name is no part of it. */
data class WeatherPlace(val latitude: Double, val longitude: Double, val zoneId: String) {
    constructor(location: HomeLocation) : this(location.latitude, location.longitude, location.timeZoneId)

    /** The zone to ask in; the device's when [zoneId] doesn't parse, as `HouseholdZone` falls back. */
    val zone: ZoneId get() = zoneOrDevice(zoneId)

    // Printed by mistake (a log, a crash message), a place gives away where the household lives.
    override fun toString(): String = "WeatherPlace(withheld)"
}

/** What `weather.db` holds: one fetch, made for [place]. */
data class StoredWeather(
    val place: WeatherPlace,
    val fetchedAtMillis: Long,
    val days: List<DailyWeather>,
    val hours: List<HourlyWeather>,
)

/** This data if it was fetched for [location]'s coordinates and zone, else null: another town's weather is never shown (§3.5). */
fun StoredWeather?.matching(location: HomeLocation?): StoredWeather? =
    this?.takeIf { location != null && it.place == WeatherPlace(location) }

/** What the header reads (§3.7). */
sealed interface WeatherView {
    data object NoLocation : WeatherView

    /** A location is set; nothing stored matches it. */
    data object Waiting : WeatherView

    /** A location is set, nothing stored matches it, and the last fetch for it failed. */
    data object Failed : WeatherView

    /** Matching data, but nothing for today. */
    data object Expired : WeatherView

    data class Ready(
        /** The stored hour containing now; null if missing. */
        val now: HourlyWeather?,
        val today: DailyWeather,
        /** Today and up to the next two. */
        val days: List<DailyWeather>,
        val fetchedAtMillis: Long,
    ) : WeatherView
}

/** [failedFor]: the place the last finished fetch failed for, null once one succeeded. */
fun weatherView(location: HomeLocation?, stored: StoredWeather?, now: LocalDateTime, failedFor: WeatherPlace? = null): WeatherView {
    if (location == null) return WeatherView.NoLocation
    val data = stored.matching(location)
        ?: return if (failedFor == WeatherPlace(location)) WeatherView.Failed else WeatherView.Waiting
    val date = now.toLocalDate()
    val ahead = data.days.filter { !it.date.isBefore(date) }.sortedBy { it.date }
    val today = ahead.firstOrNull()?.takeIf { it.date == date } ?: return WeatherView.Expired
    // The latest start at or before now within the hour: a skipped hour leaves a gap, not a wrong hour.
    val hour = data.hours.filter { !it.start.isAfter(now) && now.isBefore(it.start.plusHours(1)) }.maxByOrNull { it.start }
    return WeatherView.Ready(hour, today, ahead.take(FORECAST_DAYS), data.fetchedAtMillis)
}

/** [date]'s sun times from matching data however old (§3.7: they barely move day to day), or null. */
fun sunTimesOn(location: HomeLocation?, stored: StoredWeather?, date: LocalDate): SunTimes? {
    val day = stored.matching(location)?.days?.firstOrNull { it.date == date } ?: return null
    val sunrise = day.sunrise ?: return null
    val sunset = day.sunset ?: return null
    return SunTimes(sunrise, sunset)
}

/** What the header item shows (9 §4.2). */
sealed interface HeaderWeather {
    /** The weather now and [days]: today and the next two. */
    data class Now(
        val condition: Condition,
        val night: Boolean,
        val temperature: Double,
        val high: Double,
        val low: Double,
        val days: List<DailyWeather>,
    ) : HeaderWeather

    /** No home location is set. */
    data object AddLocation : HeaderWeather

    /** The fetch failed and nothing matching is stored. */
    data object NoForecast : HeaderWeather
}

/** The header's weather, or null while it is hidden: while waiting, with data that has no today, or with no hour for now. */
fun headerWeather(view: WeatherView, now: LocalDateTime): HeaderWeather? = when (view) {
    WeatherView.NoLocation -> HeaderWeather.AddLocation
    WeatherView.Failed -> HeaderWeather.NoForecast
    WeatherView.Waiting, WeatherView.Expired -> null
    is WeatherView.Ready -> view.now?.let { hour ->
        val sunrise = view.today.sunrise
        val sunset = view.today.sunset
        val time = now.toLocalTime()
        val night = sunrise != null && sunset != null && (time < sunrise || time >= sunset)
        HeaderWeather.Now(hour.condition, night, hour.temperature, view.today.high, view.today.low, view.days)
    }
}
