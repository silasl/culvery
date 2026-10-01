package uk.co.siland.culvery.capability.weather

import androidx.test.ext.junit.runners.AndroidJUnit4
import app.cash.turbine.test
import com.google.common.truth.Truth.assertThat
import java.time.LocalTime
import kotlin.time.Duration.Companion.seconds
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.test.runTest
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.shadows.ShadowLog
import uk.co.siland.culvery.capability.weather.db.WeatherDatabase
import uk.co.siland.culvery.core.household.HomeLocation
import uk.co.siland.culvery.core.plugin.SunTimes

// Robolectric for android.util.Log, should a retry log, and for Room. The rules are tested in WeatherViewTest; these test the
// wiring (a move hides the old town at once; midnight moves the card and the sun times on).
@RunWith(AndroidJUnit4::class)
class WeatherRepositoryTest {
    private val location = MutableStateFlow<HomeLocation?>(LONDON)
    private val stored = MutableStateFlow<StoredWeather?>(stored())
    private val now = MutableStateFlow(THU.atTime(10, 30))
    private val repo = WeatherRepository(location, stored, now)

    @Before
    fun setUp() = ShadowLog.clear()

    @Test
    fun theViewFollowsTheLocationAndTheData() = runTest {
        location.value = null
        stored.value = null
        repo.view.test {
            assertThat(awaitItem()).isEqualTo(WeatherView.NoLocation)
            location.value = LONDON
            assertThat(awaitItem()).isEqualTo(WeatherView.Waiting)
            stored.value = stored()
            assertThat((awaitItem() as WeatherView.Ready).today.date).isEqualTo(THU)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun aMoveHidesTheOldTownsWeatherAtOnce() = runTest {
        assertThat(repo.view.first()).isInstanceOf(WeatherView.Ready::class.java)
        assertThat(repo.header.first()).isNotNull()
        assertThat(repo.today.first()).isNotNull()
        location.value = LEEDS
        assertThat(repo.view.first()).isEqualTo(WeatherView.Waiting)
        assertThat(repo.header.first()).isNull()
        assertThat(repo.today.first()).isNull()
    }

    /** The cache carries the card over midnight, and runs out into Expired. */
    @Test
    fun atMidnightTheCardMovesOnWithoutAFetch() = runTest {
        stored.value = stored(forecast = forecast(from = THU, days = 3))
        now.value = THU.atTime(23, 59)
        repo.view.test {
            assertThat((awaitItem() as WeatherView.Ready).days.map { it.date }).containsExactly(THU, FRI, SAT).inOrder()
            now.value = FRI.atStartOfDay()
            val friday = awaitItem() as WeatherView.Ready
            assertThat(friday.today.date).isEqualTo(FRI)
            assertThat(friday.days.map { it.date }).containsExactly(FRI, SAT).inOrder()
            now.value = SAT.plusDays(1).atStartOfDay()
            assertThat(awaitItem()).isEqualTo(WeatherView.Expired)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun theHeaderTurnsToNightAtSunsetWithinTheHour() = runTest {
        now.value = THU.atTime(18, 37)
        repo.header.test {
            assertThat(awaitItem()!!.night).isFalse()
            now.value = THU.atTime(18, 38)
            assertThat(awaitItem()!!.night).isTrue()
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun theHeaderHidesWhenTheHourIsMissing() = runTest {
        val f = forecast()
        stored.value = stored(forecast = f.copy(hours = f.hours.filterNot { it.start == THU.atTime(10, 0) }))
        assertThat(repo.view.first()).isInstanceOf(WeatherView.Ready::class.java)
        assertThat(repo.header.first()).isNull()
    }

    @Test
    fun daylightMovesToTomorrowsTimesAtMidnight() = runTest {
        val f = forecast(days = 2)
        val friday = day(FRI, sunrise = LocalTime.of(7, 2), sunset = LocalTime.of(18, 36))
        stored.value = stored(forecast = f.copy(days = listOf(f.days[0], friday)))
        now.value = THU.atTime(23, 59)
        repo.today.test {
            assertThat(awaitItem()).isEqualTo(SunTimes(SUNRISE, SUNSET))
            now.value = FRI.atStartOfDay()
            assertThat(awaitItem()).isEqualTo(SunTimes(LocalTime.of(7, 2), LocalTime.of(18, 36)))
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun aFailureReadingTheStoredWeatherIsLoggedByTypeAndTheViewRecovers() = runTest {
        var reads = 0
        val flaky = flow {
            if (reads++ == 0) throw IllegalStateException("no data for Leeds 53.7997,-1.5492 Europe/London")
            emit(stored())
        }
        val flakyRepo = WeatherRepository(location, flaky, now)
        assertThat(flakyRepo.view.first()).isInstanceOf(WeatherView.Ready::class.java)
        val lines = ShadowLog.getLogs().filter { it.tag == TAG }
        assertThat(lines.map { it.msg }).containsExactly("Couldn't read the weather (IllegalStateException); retrying")
        assertThat(lines.single().throwable).isNull()
        assertNoSecretsLogged(TAG, listOf("Leeds", "London", "53.7997", "-1.5492", "Europe/London"))
    }

    /** Task 4 review gap: a live collector sees a replace of the stored fetch, through the real store. */
    @Test
    fun aLiveViewShowsTheNewForecastAfterTheStoreIsReplaced() = runTest {
        val db: WeatherDatabase = weatherDb()
        try {
            val store = WeatherStore(db)
            val live = WeatherRepository(location, store.stored, now)
            live.view.test(timeout = 5.seconds) {
                assertThat(awaitItem()).isEqualTo(WeatherView.Waiting)
                store.replace(WeatherPlace(LONDON), forecast(), 0L)
                assertThat((awaitItem() as WeatherView.Ready).today.date).isEqualTo(THU)
                store.replace(WeatherPlace(LONDON), forecast(from = THU, days = 2), 0L)
                assertThat((awaitItem() as WeatherView.Ready).days).hasSize(2)
                cancelAndIgnoreRemainingEvents()
            }
        } finally {
            db.close()
        }
    }
}
