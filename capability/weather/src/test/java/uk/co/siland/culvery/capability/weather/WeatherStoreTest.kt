package uk.co.siland.culvery.capability.weather

import android.database.sqlite.SQLiteFullException
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import uk.co.siland.culvery.capability.weather.db.HourEntity
import uk.co.siland.culvery.capability.weather.db.WeatherDao
import uk.co.siland.culvery.capability.weather.db.WeatherDatabase

// Robolectric for Room.
@RunWith(AndroidJUnit4::class)
class WeatherStoreTest {
    private lateinit var db: WeatherDatabase
    private lateinit var store: WeatherStore

    @Before
    fun setUp() {
        db = weatherDb()
        store = WeatherStore(db)
    }

    @After
    fun tearDown() = db.close()

    @Test
    fun nothingIsStoredAtFirst() = runTest {
        assertThat(store.stored.first()).isNull()
    }

    @Test
    fun aReplaceStoresThePlaceTheTimeTheDaysAndTheHours() = runTest {
        val f = forecast()
        store.replace(WeatherPlace(LONDON), f, 1_000L)
        val s = store.stored.first()!!
        assertThat(s.place).isEqualTo(WeatherPlace(51.5074, -0.1278, "Europe/London"))
        assertThat(s.fetchedAtMillis).isEqualTo(1_000L)
        assertThat(s.days).isEqualTo(f.days)
        assertThat(s.hours).isEqualTo(f.hours)
    }

    @Test
    fun unknownSunTimesComeBackUnknown() = runTest {
        val polar = Forecast(listOf(day(THU, sunrise = null, sunset = null)), hoursOf(THU))
        store.replace(WeatherPlace(LONDON), polar, 1_000L)
        val today = store.stored.first()!!.days.single()
        assertThat(today.sunrise).isNull()
        assertThat(today.sunset).isNull()
    }

    @Test
    fun aSecondReplaceLeavesNothingOfTheFirst() = runTest {
        store.replace(WeatherPlace(LONDON), forecast(from = THU.minusDays(3)), 1_000L)
        store.replace(WeatherPlace(LEEDS), forecast(from = THU, days = 2), 2_000L)
        val s = store.stored.first()!!
        assertThat(s.place).isEqualTo(WeatherPlace(LEEDS))
        assertThat(s.fetchedAtMillis).isEqualTo(2_000L)
        assertThat(s.days.map { it.date }).containsExactly(THU, FRI).inOrder()
        assertThat(s.hours).hasSize(48)
    }

    @Test
    fun aFailedReplaceLeavesTheOldDataWhole() = runTest {
        store.replace(WeatherPlace(LONDON), forecast(), 1_000L)
        val failing = object : WeatherDao by db.weatherDao() {
            override suspend fun insertHours(rows: List<HourEntity>) {
                throw SQLiteFullException("disk full")
            }
        }
        val thrown = runCatching { WeatherStore(db, failing).replace(WeatherPlace(LEEDS), forecast(from = FRI), 2_000L) }.exceptionOrNull()
        assertThat(thrown).isInstanceOf(SQLiteFullException::class.java)
        val s = store.stored.first()!!
        assertThat(s.place).isEqualTo(WeatherPlace(LONDON))
        assertThat(s.fetchedAtMillis).isEqualTo(1_000L)
        assertThat(s.days.first().date).isEqualTo(THU)
        assertThat(s.hours).hasSize(7 * 24)
    }

    /** Review Focus 2: a provider that repeats an hour can't break every fetch; the first is kept. */
    @Test
    fun aRepeatedHourIsStoredOnce() = runTest {
        val f = forecast(days = 1)
        store.replace(WeatherPlace(LONDON), f.copy(hours = f.hours + HourlyWeather(THU.atTime(1, 0), Condition.RAIN, 4.0)), 1_000L)
        val hours = store.stored.first()!!.hours
        assertThat(hours).hasSize(24)
        assertThat(hours.single { it.start == THU.atTime(1, 0) }.temperature).isEqualTo(17.0)
    }
}
