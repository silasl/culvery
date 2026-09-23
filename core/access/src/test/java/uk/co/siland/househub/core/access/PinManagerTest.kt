package uk.co.siland.househub.core.access

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertThrows
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import uk.co.siland.househub.core.household.HouseholdRepository
import uk.co.siland.househub.core.household.PersonId
import uk.co.siland.househub.core.household.Role
import uk.co.siland.househub.core.household.db.HouseholdDatabase

@RunWith(AndroidJUnit4::class)
class PinManagerTest {
    private lateinit var db: HouseholdDatabase
    private lateinit var household: HouseholdRepository
    private lateinit var pins: PinManager

    @Before
    fun setUp() {
        db = Room.inMemoryDatabaseBuilder(ApplicationProvider.getApplicationContext(), HouseholdDatabase::class.java)
            .allowMainThreadQueries().build()
        household = HouseholdRepository(db)
        pins = PinManager(household, PinHasher())
    }

    @After
    fun tearDown() = db.close()

    @Test
    fun identifyReturnsPersonAndRole() = runTest {
        val alex = household.addPerson("Alex", 0xFF4CB387, Role.ADMIN)
        val mia = household.addPerson("Mia", 0xFFE07BA8, Role.CHILD)
        pins.setPin(alex.id, "1234")
        pins.setPin(mia.id, "9876")
        assertThat(pins.identify("9876")).isEqualTo(Identified(mia, Role.CHILD))
        assertThat(pins.identify("1234")).isEqualTo(Identified(alex, Role.ADMIN))
    }

    @Test
    fun identifyReturnsNullForWrongOrMalformedPin() = runTest {
        val alex = household.addPerson("Alex", 0xFF4CB387, Role.ADMIN)
        pins.setPin(alex.id, "1234")
        assertThat(pins.identify("1235")).isNull()
        assertThat(pins.identify("12")).isNull()
    }

    @Test
    fun duplicatePinIsRejected() = runTest {
        val alex = household.addPerson("Alex", 0xFF4CB387, Role.ADMIN)
        val sam = household.addPerson("Sam", 0xFF5B9BE0, Role.ADULT)
        pins.setPin(alex.id, "1234")
        assertThrows(PinInUseException::class.java) { runBlocking { pins.setPin(sam.id, "1234") } }
    }

    @Test
    fun personCanKeepTheirOwnPin() = runTest {
        val alex = household.addPerson("Alex", 0xFF4CB387, Role.ADMIN)
        pins.setPin(alex.id, "1234")
        pins.setPin(alex.id, "1234")
        assertThat(pins.identify("1234")?.person).isEqualTo(alex)
    }

    @Test
    fun malformedPinIsRejected() = runTest {
        val alex = household.addPerson("Alex", 0xFF4CB387, Role.ADMIN)
        assertThrows(IllegalArgumentException::class.java) { runBlocking { pins.setPin(alex.id, "12345") } }
    }

    @Test
    fun unknownPersonIsRejected() = runTest {
        assertThrows(IllegalArgumentException::class.java) { runBlocking { pins.setPin(PersonId("nobody"), "1234") } }
    }

    @Test
    fun removedPersonsPinNoLongerIdentifies() = runTest {
        val alex = household.addPerson("Alex", 0xFF4CB387, Role.ADMIN)
        val mia = household.addPerson("Mia", 0xFFE07BA8, Role.CHILD)
        pins.setPin(alex.id, "1234")
        pins.setPin(mia.id, "9876")
        household.removePerson(mia.id)
        assertThat(pins.identify("9876")).isNull()
    }
}
