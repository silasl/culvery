package uk.co.siland.culvery

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import uk.co.siland.culvery.capability.calendar.CalendarSetup
import uk.co.siland.culvery.capability.calendar.CalendarStore
import uk.co.siland.culvery.capability.calendar.db.CalendarDatabase
import uk.co.siland.culvery.core.access.PinHasher
import uk.co.siland.culvery.core.access.PinManager
import uk.co.siland.culvery.core.household.HouseholdRepository
import uk.co.siland.culvery.core.household.PersonId
import uk.co.siland.culvery.core.household.Role
import uk.co.siland.culvery.core.household.db.HouseholdDatabase
import uk.co.siland.culvery.provider.calendar_fake.FakeCalendarProvider

@RunWith(AndroidJUnit4::class)
class DebugSeedTest {
    private lateinit var householdDb: HouseholdDatabase
    private lateinit var calendarDb: CalendarDatabase
    private lateinit var household: HouseholdRepository
    private lateinit var pins: PinManager
    private lateinit var store: CalendarStore
    private lateinit var setup: CalendarSetup

    @Before
    fun setUp() {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        householdDb = Room.inMemoryDatabaseBuilder(context, HouseholdDatabase::class.java).allowMainThreadQueries().build()
        calendarDb = Room.inMemoryDatabaseBuilder(context, CalendarDatabase::class.java).allowMainThreadQueries().build()
        household = HouseholdRepository(householdDb)
        pins = PinManager(household, PinHasher())
        store = CalendarStore(calendarDb)
        setup = CalendarSetup(store, setOf(FakeCalendarProvider()))
    }

    @After
    fun tearDown() {
        householdDb.close()
        calendarDb.close()
    }

    @Test
    fun seedsAlexSamAndMiaWithTheirRolesAndPins() = runTest {
        seedDebugData(household, pins, setup)
        assertThat(household.people.first().map { it.name }).containsExactly("Alex", "Sam", "Mia").inOrder()
        assertThat(pins.identify("1234")?.let { it.person.name to it.role }).isEqualTo("Alex" to Role.ADMIN)
        assertThat(pins.identify("2468")?.let { it.person.name to it.role }).isEqualTo("Sam" to Role.ADULT)
        assertThat(pins.identify("1357")?.let { it.person.name to it.role }).isEqualTo("Mia" to Role.CHILD)
    }

    private suspend fun seededSources() = store.visibleSourcesFor(store.connectionsNow().single().connection.id)

    @Test
    fun sampleSourcesAreMappedToThePeople() = runTest {
        seedDebugData(household, pins, setup)
        val byName = household.people.first().associate { it.name to it.id }
        val mapping = seededSources().associate { it.source.id to it.mapping.person }
        assertThat(mapping).containsExactly(
            FakeCalendarProvider.SOURCE_ALEX, byName.getValue("Alex"),
            FakeCalendarProvider.SOURCE_SAM, byName.getValue("Sam"),
            FakeCalendarProvider.SOURCE_MIA, byName.getValue("Mia"),
            FakeCalendarProvider.SOURCE_FAMILY, PersonId.FAMILY,
            FakeCalendarProvider.SOURCE_SCHOOL, PersonId.FAMILY,
        )
    }

    @Test
    fun seedIsIdempotent() = runTest {
        seedDebugData(household, pins, setup)
        seedDebugData(household, pins, setup)
        assertThat(household.people.first()).hasSize(3)
        assertThat(store.connectionsNow()).hasSize(1)
    }

    @Test
    fun existingActiveAdminIsLeftAlone() = runTest {
        val admin = household.addPerson("Admin", 0xFF4CB387, Role.ADMIN)
        pins.setPin(admin.id, "9999")
        seedDebugData(household, pins, setup)
        assertThat(household.people.first().map { it.name }).containsExactly("Admin")
        assertThat(seededSources().map { it.mapping.person }.toSet()).containsExactly(PersonId.FAMILY)
    }
}
