package uk.co.siland.culvery.core.household

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.google.common.truth.Truth.assertThat
import java.time.ZoneId
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import uk.co.siland.culvery.core.household.db.HouseholdDatabase

@RunWith(AndroidJUnit4::class)
class HouseholdZoneTest {
    private lateinit var db: HouseholdDatabase
    private lateinit var household: HouseholdRepository
    private lateinit var zone: HouseholdZone

    @Before
    fun setUp() {
        db = Room.inMemoryDatabaseBuilder(ApplicationProvider.getApplicationContext(), HouseholdDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        household = HouseholdRepository(db)
        zone = HouseholdZone(household)
    }

    @After
    fun tearDown() = db.close()

    @Test
    fun noLocationUsesTheSystemZone() = runTest {
        assertThat(zone.current()).isEqualTo(ZoneId.systemDefault())
    }

    @Test
    fun usesTheHouseholdZone() = runTest {
        household.setLocation(HomeLocation("Wellington", -41.29, 174.78, "Pacific/Auckland"))
        assertThat(zone.current()).isEqualTo(ZoneId.of("Pacific/Auckland"))
    }

    @Test
    fun invalidZoneFallsBackToTheSystemZone() = runTest {
        household.setLocation(HomeLocation("Nowhere", 0.0, 0.0, "Not/AZone"))
        assertThat(zone.current()).isEqualTo(ZoneId.systemDefault())
    }
}
