package uk.co.siland.culvery.shell

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.google.common.truth.Truth.assertThat
import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneId
import java.util.TimeZone
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.shadows.ShadowLog
import uk.co.siland.culvery.core.household.HomeLocation
import uk.co.siland.culvery.core.household.HouseholdRepository
import uk.co.siland.culvery.core.household.HouseholdZone
import uk.co.siland.culvery.core.household.db.HouseholdDatabase
import uk.co.siland.culvery.core.plugin.WallClock
import uk.co.siland.culvery.di.AppModule

/** The shell's clock, date and theme run in the household's zone, not the tablet's. Robolectric for Room and Log. */
@RunWith(AndroidJUnit4::class)
class HouseholdTickerTest {
    private val deviceZone = TimeZone.getDefault()
    private lateinit var db: HouseholdDatabase
    private lateinit var household: HouseholdRepository

    // 12:00 UTC on 1 October 2026: 08:00 that day in New York, 01:00 the next day in Wellington.
    private val clock = WallClock { Instant.parse("2026-10-01T12:00:00Z").toEpochMilli() }

    @Before
    fun setUp() {
        TimeZone.setDefault(TimeZone.getTimeZone("America/New_York"))
        db = Room.inMemoryDatabaseBuilder(ApplicationProvider.getApplicationContext(), HouseholdDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        household = HouseholdRepository(db)
    }

    @After
    fun tearDown() {
        db.close()
        TimeZone.setDefault(deviceZone)
    }

    @Test
    fun theClockShowsTheHouseholdsTimeNotTheDevices() = runTest {
        household.setLocation(HomeLocation("Wellington", -41.29, 174.78, "Pacific/Auckland"))
        val first = AppModule.minuteTicker(HouseholdZone(household), clock).ticks().first()
        assertThat(first).isEqualTo(LocalDateTime.of(2026, 10, 2, 1, 0))
    }

    /** Plan review 1: the zone comes from Room, which can fail; the clock must carry on, not crash the shell. */
    @Test
    fun aFailedZoneReadIsRetriedAndTheClockCarriesOn() = runTest {
        ShadowLog.clear()
        var reads = 0
        val zones = flow {
            if (reads++ == 0) throw IllegalStateException("database locked")
            emit(ZoneId.of("Pacific/Auckland"))
        }
        val first = householdTicker(zones, clock).ticks().first()
        assertThat(first).isEqualTo(LocalDateTime.of(2026, 10, 2, 1, 0))
        assertThat(ShadowLog.getLogs().map { it.msg })
            .contains("Couldn't read the household's time zone (IllegalStateException); retrying")
    }
}
