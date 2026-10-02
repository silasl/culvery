package uk.co.siland.culvery.core.plugin

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import app.cash.turbine.test
import com.google.common.truth.Truth.assertThat
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.take
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.shadows.ShadowLog
import uk.co.siland.culvery.core.household.HomeLocation
import uk.co.siland.culvery.core.household.HouseholdRepository
import uk.co.siland.culvery.core.household.HouseholdZone
import uk.co.siland.culvery.core.household.db.HouseholdDatabase

/** Robolectric for android.util.Log and Room. */
@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(AndroidJUnit4::class)
class HouseholdClockTest {
    private val auckland = ZoneId.of("Pacific/Auckland")
    private val london = ZoneId.of("Europe/London")

    // 12:00 UTC on 1 October 2026: 01:00 the next day in Wellington.
    private val noonUtc = Instant.parse("2026-10-01T12:00:00Z").toEpochMilli()
    private val wall = WallClock { noonUtc }

    // 09:59:30 UTC on 1 October 2026: 10:59:30 in London (BST), 22:59:30 in Auckland (NZDT).
    private val beforeTheMinute = Instant.parse("2026-10-01T09:59:30Z").toEpochMilli()

    @Test
    fun itGivesTheTimeNowThenAtTheStartOfEachMinute() = runTest {
        val clock = HouseholdClock(flowOf(london), WallClock { beforeTheMinute + testScheduler.currentTime }, backgroundScope)
        assertThat(clock.minutes.take(3).toList()).containsExactly(
            LocalDateTime.of(2026, 10, 1, 10, 59, 30),
            LocalDateTime.of(2026, 10, 1, 11, 0),
            LocalDateTime.of(2026, 10, 1, 11, 1),
        ).inOrder()
    }

    @Test
    fun aZoneChangeGivesTheTimeInTheNewZoneAtOnce() = runTest {
        val zones = MutableStateFlow(london)
        val clock = HouseholdClock(zones, WallClock { beforeTheMinute }, backgroundScope, flowOf(beforeTheMinute))
        clock.minutes.test {
            assertThat(awaitItem()).isEqualTo(LocalDateTime.of(2026, 10, 1, 10, 59, 30))
            zones.value = auckland
            assertThat(awaitItem()).isEqualTo(LocalDateTime.of(2026, 10, 1, 22, 59, 30))
        }
    }

    /** The shell's clock, date and theme follow the household's zone, read from Room, not the tablet's. */
    @Test
    fun itShowsTheHouseholdsTimeNotTheDevices() = runTest {
        val db = Room.inMemoryDatabaseBuilder(ApplicationProvider.getApplicationContext(), HouseholdDatabase::class.java).build()
        try {
            val household = HouseholdRepository(db)
            household.setLocation(HomeLocation("Wellington", -41.29, 174.78, "Pacific/Auckland"))
            val clock = HouseholdClock(HouseholdZone(household), wall, backgroundScope)
            // Room answers on its own threads: wait in bounded real time (4b test health).
            val first = withContext(Dispatchers.Default) { withTimeout(5_000) { clock.minutes.first() } }
            assertThat(first).isEqualTo(LocalDateTime.of(2026, 10, 2, 1, 0))
        } finally {
            db.close()
        }
    }

    @Test
    fun everyReaderSharesOneTicker() = runTest {
        var tickers = 0
        val ticks = flow {
            tickers++
            emit(noonUtc)
            awaitCancellation()
        }
        val clock = HouseholdClock(flowOf(auckland), wall, backgroundScope, ticks)
        repeat(3) { backgroundScope.launch { clock.minutes.collect {} } }
        backgroundScope.launch { clock.today.collect {} }
        runCurrent()
        assertThat(tickers).isEqualTo(1)
    }

    @Test
    fun todayChangesAtMidnightAndNotEachMinute() = runTest {
        val at = { t: LocalDateTime -> t.atZone(london).toInstant().toEpochMilli() }
        val ticks = MutableStateFlow(at(LocalDateTime.of(2026, 9, 23, 23, 59)))
        val clock = HouseholdClock(flowOf(london), wall, backgroundScope, ticks)
        clock.today.test {
            assertThat(awaitItem()).isEqualTo(LocalDate.of(2026, 9, 23))
            ticks.value = at(LocalDateTime.of(2026, 9, 24, 0, 0))
            assertThat(awaitItem()).isEqualTo(LocalDate.of(2026, 9, 24))
            ticks.value = at(LocalDateTime.of(2026, 9, 24, 0, 1))
            expectNoEvents()
        }
    }

    /** 4b plan review 1: the zone comes from Room, which can fail; the clock carries on. */
    @Test
    fun aFailedZoneReadIsRetriedAndTheClockCarriesOn() = runTest {
        ShadowLog.clear()
        var reads = 0
        val zones = flow {
            if (reads++ == 0) throw IllegalStateException("database locked")
            emit(auckland)
        }
        val clock = HouseholdClock(zones, wall, backgroundScope, flowOf(noonUtc))
        assertThat(clock.minutes.first()).isEqualTo(LocalDateTime.of(2026, 10, 2, 1, 0))
        assertThat(ShadowLog.getLogs().map { it.msg })
            .contains("Couldn't read the household's time zone (IllegalStateException); retrying")
    }
}
