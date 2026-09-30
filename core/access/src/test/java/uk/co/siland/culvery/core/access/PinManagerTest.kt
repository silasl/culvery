package uk.co.siland.culvery.core.access

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertThrows
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import uk.co.siland.culvery.core.household.HouseholdRepository
import uk.co.siland.culvery.core.household.PersonId
import uk.co.siland.culvery.core.household.Role
import uk.co.siland.culvery.core.household.db.HouseholdDatabase

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

    @Test
    fun aPersonAddedWithAPinIsKnownByIt() = runTest {
        val mia = pins.addPerson("Mia", 0xFFE07BA8, Role.CHILD, "1357")
        assertThat(pins.identify("1357")).isEqualTo(Identified(mia, Role.CHILD))
    }

    @Test
    fun aPersonAddedWithATakenPinIsNotAdded() = runTest {
        pins.addPerson("Alex", 0xFF4CB387, Role.ADMIN, "1234")
        assertThrows(PinInUseException::class.java) { runBlocking { pins.addPerson("Mia", 0xFFE07BA8, Role.CHILD, "1234") } }
        assertThat(household.people.first().map { it.name }).containsExactly("Alex")
    }

    @Test
    fun aNewHashAllowsTheOwnersOwnPinAndNobodyElses() = runTest {
        val alex = pins.addPerson("Alex", 0xFF4CB387, Role.ADMIN, "1234")
        assertThat(pins.hashNew("1234", owner = alex.id).second).isNotEmpty()
        assertThrows(PinInUseException::class.java) { runBlocking { pins.hashNew("1234", owner = null) } }
    }

    @Test
    fun aPinTakenAfterHashingIsStillRefusedInsideTheWrite() = runTest {
        val alex = pins.addPerson("Alex", 0xFF4CB387, Role.ADMIN, "1234")
        val mia = pins.addPerson("Mia", 0xFFE07BA8, Role.CHILD, null)
        val change = pins.changeTo("4321", owner = mia.id)
        pins.setPin(alex.id, "4321")
        assertThrows(PinInUseException::class.java) {
            runBlocking { household.updateMember(mia.id, "Mia", mia.color, Role.CHILD, change) }
        }
        assertThat(household.member(mia.id)?.hasPin).isFalse()
    }

    @Test
    fun aPinInUseIsRefusedThroughAMemberEditAndSetPin() = runTest {
        pins.addPerson("Alex", 0xFF4CB387, Role.ADMIN, "1234")
        val mia = pins.addPerson("Mia", 0xFFE07BA8, Role.CHILD, null)
        assertThrows(PinInUseException::class.java) { runBlocking { pins.changeTo("1234", owner = mia.id) } }
        assertThrows(PinInUseException::class.java) { runBlocking { pins.setPin(mia.id, "1234") } }
    }
}
