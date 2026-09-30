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
        val alex = pins.addPerson("Alex", 0xFF4CB387, Role.ADMIN, "1234")
        val mia = pins.addPerson("Mia", 0xFFE07BA8, Role.CHILD, "9876")
        assertThat(pins.identify("9876")).isEqualTo(Identified(mia, Role.CHILD))
        assertThat(pins.identify("1234")).isEqualTo(Identified(alex, Role.ADMIN))
    }

    @Test
    fun identifyReturnsNullForWrongOrMalformedPin() = runTest {
        pins.addPerson("Alex", 0xFF4CB387, Role.ADMIN, "1234")
        assertThat(pins.identify("1235")).isNull()
        assertThat(pins.identify("12")).isNull()
    }

    @Test
    fun duplicatePinIsRejected() = runTest {
        pins.addPerson("Alex", 0xFF4CB387, Role.ADMIN, "1234")
        val sam = pins.addPerson("Sam", 0xFF5B9BE0, Role.ADULT, null)
        assertThrows(PinInUseException::class.java) {
            runBlocking { household.updateMember(sam.id, "Sam", sam.color, Role.ADULT, pins.changeTo("1234", owner = sam.id)) }
        }
        assertThat(household.member(sam.id)?.hasPin).isFalse()
    }

    @Test
    fun personCanKeepTheirOwnPin() = runTest {
        val alex = pins.addPerson("Alex", 0xFF4CB387, Role.ADMIN, "1234")
        household.updateMember(alex.id, "Alex", alex.color, Role.ADMIN, pins.changeTo("1234", owner = alex.id))
        assertThat(pins.identify("1234")?.person).isEqualTo(alex)
    }

    @Test
    fun malformedPinIsRejected() = runTest {
        val alex = pins.addPerson("Alex", 0xFF4CB387, Role.ADMIN, null)
        assertThrows(IllegalArgumentException::class.java) { runBlocking { pins.changeTo("12345", owner = alex.id) } }
        assertThrows(IllegalArgumentException::class.java) { runBlocking { pins.addPerson("Mia", 0xFFE07BA8, Role.CHILD, "12345") } }
    }

    @Test
    fun unknownPersonIsRejected() = runTest {
        val nobody = PersonId("nobody")
        assertThrows(IllegalArgumentException::class.java) {
            runBlocking { household.updateMember(nobody, "Nobody", 0xFF4CB387, Role.ADULT, pins.changeTo("1234", owner = nobody)) }
        }
    }

    @Test
    fun removedPersonsPinNoLongerIdentifies() = runTest {
        pins.addPerson("Alex", 0xFF4CB387, Role.ADMIN, "1234")
        val mia = pins.addPerson("Mia", 0xFFE07BA8, Role.CHILD, "9876")
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
    fun aPinChangeAllowsTheOwnersOwnPinAndNobodyElses() = runTest {
        val alex = pins.addPerson("Alex", 0xFF4CB387, Role.ADMIN, "1234")
        assertThat(pins.changeTo("1234", owner = alex.id).salt).isNotEmpty()
        assertThrows(PinInUseException::class.java) { runBlocking { pins.changeTo("1234", owner = null) } }
    }

    @Test
    fun aPinTakenAfterHashingIsStillRefusedInsideTheWrite() = runTest {
        val alex = pins.addPerson("Alex", 0xFF4CB387, Role.ADMIN, "1234")
        val mia = pins.addPerson("Mia", 0xFFE07BA8, Role.CHILD, null)
        val change = pins.changeTo("4321", owner = mia.id)
        household.updateMember(alex.id, "Alex", alex.color, Role.ADMIN, pins.changeTo("4321", owner = alex.id))
        assertThrows(PinInUseException::class.java) {
            runBlocking { household.updateMember(mia.id, "Mia", mia.color, Role.CHILD, change) }
        }
        assertThat(household.member(mia.id)?.hasPin).isFalse()
    }

    @Test
    fun aPinInUseIsRefusedBeforeHashing() = runTest {
        pins.addPerson("Alex", 0xFF4CB387, Role.ADMIN, "1234")
        val mia = pins.addPerson("Mia", 0xFFE07BA8, Role.CHILD, null)
        assertThrows(PinInUseException::class.java) { runBlocking { pins.changeTo("1234", owner = mia.id) } }
    }
}
