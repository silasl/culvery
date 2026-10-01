package uk.co.siland.culvery.capability.weather

import androidx.room.withTransaction
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import uk.co.siland.culvery.capability.weather.db.DayEntity
import uk.co.siland.culvery.capability.weather.db.FetchEntity
import uk.co.siland.culvery.capability.weather.db.HourEntity
import uk.co.siland.culvery.capability.weather.db.WeatherDao
import uk.co.siland.culvery.capability.weather.db.WeatherDatabase

/** `weather.db` (4b design §3.5): the last successful fetch, whole. */
@Singleton
class WeatherStore internal constructor(private val db: WeatherDatabase, private val dao: WeatherDao) {
    @Inject
    constructor(db: WeatherDatabase) : this(db, db.weatherDao())

    /**
     * The stored fetch, or null before the first. Every replace rewrites the `fetch` row, so watching it sees each change;
     * the three tables are then read in one transaction, so one fetch's place is never paired with another's days.
     */
    val stored: Flow<StoredWeather?> = dao.fetchChanges().map { db.withTransaction { read() } }.distinctUntilChanged()

    /** Replaces everything with [forecast], fetched for [place], in one transaction; a repeated date or hour is kept once. */
    suspend fun replace(place: WeatherPlace, forecast: Forecast, fetchedAtMillis: Long) {
        db.withTransaction {
            dao.clearHours()
            dao.clearDays()
            dao.clearFetch()
            dao.insertFetch(
                FetchEntity(latitude = place.latitude, longitude = place.longitude, zoneId = place.zoneId, fetchedAtMillis = fetchedAtMillis),
            )
            dao.insertDays(forecast.days.distinctBy { it.date }.map { it.toEntity() })
            dao.insertHours(forecast.hours.distinctBy { it.start }.map { it.toEntity() })
        }
    }

    private suspend fun read(): StoredWeather? {
        val fetch = dao.fetch() ?: return null
        return StoredWeather(
            WeatherPlace(fetch.latitude, fetch.longitude, fetch.zoneId),
            fetch.fetchedAtMillis,
            dao.days().map { it.toDaily() },
            dao.hours().map { it.toHourly() },
        )
    }
}

private fun DailyWeather.toEntity() =
    DayEntity(date.toString(), condition.name, high, low, sunrise?.toString(), sunset?.toString())

private fun HourlyWeather.toEntity() = HourEntity(start.toString(), condition.name, temperature)

private fun DayEntity.toDaily() = DailyWeather(
    LocalDate.parse(date),
    conditionNamed(condition),
    high,
    low,
    sunrise?.let { LocalTime.parse(it) },
    sunset?.let { LocalTime.parse(it) },
)

private fun HourEntity.toHourly() = HourlyWeather(LocalDateTime.parse(start), conditionNamed(condition), temperature)

// A name this build doesn't know reads as cloudy, as an unknown provider code does.
private fun conditionNamed(name: String): Condition = Condition.entries.firstOrNull { it.name == name } ?: Condition.CLOUDY
