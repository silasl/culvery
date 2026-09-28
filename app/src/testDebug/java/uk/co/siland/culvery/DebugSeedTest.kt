package uk.co.siland.culvery

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.google.common.truth.Truth.assertThat
import java.time.LocalDate
import java.time.ZoneId
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import uk.co.siland.culvery.capability.calendar.CalendarSetup
import uk.co.siland.culvery.capability.calendar.CalendarStore
import uk.co.siland.culvery.capability.calendar.DateRange
import uk.co.siland.culvery.capability.calendar.db.CalendarDatabase
import uk.co.siland.culvery.core.access.PinHasher
import uk.co.siland.culvery.core.access.PinManager
import uk.co.siland.culvery.core.household.HouseholdRepository
import uk.co.siland.culvery.core.household.PersonId
import uk.co.siland.culvery.core.household.Role
import uk.co.siland.culvery.core.household.db.HouseholdDatabase
import uk.co.siland.culvery.core.plugin.Connection
import uk.co.siland.culvery.core.plugin.Toaster
import uk.co.siland.culvery.core.plugin.WallClock
import uk.co.siland.culvery.provider.calendar_fake.FakeCalendarProvider

private object NoToasts : Toaster {
    override fun show(message: String, icon: String) = Unit
}

@RunWith(AndroidJUnit4::class)
class DebugSeedTest {
    private lateinit var householdDb: HouseholdDatabase
    private lateinit var calendarDb: CalendarDatabase
    private lateinit var household: HouseholdRepository
    private lateinit var pins: PinManager
    private lateinit var store: CalendarStore
    private lateinit var setup: CalendarSetup
    private val fake = FakeCalendarProvider()
    private var syncs = 0

    @Before
    fun setUp() {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        householdDb = Room.inMemoryDatabaseBuilder(context, HouseholdDatabase::class.java).allowMainThreadQueries().build()
        calendarDb = Room.inMemoryDatabaseBuilder(context, CalendarDatabase::class.java).allowMainThreadQueries().build()
        household = HouseholdRepository(householdDb)
        pins = PinManager(household, PinHasher())
        store = CalendarStore(calendarDb)
        setup = CalendarSetup(store, setOf(fake), { household.people.first() }, NoToasts, WallClock { 0L }) { syncs++ }
    }

    @After
    fun tearDown() {
        householdDb.close()
        calendarDb.close()
    }

    private suspend fun seed() = seedDebugData(household, pins, setup, setOf(fake))

    @Test
    fun seedsAlexSamAndMiaWithTheirRolesAndPins() = runTest {
        seed()
        assertThat(household.people.first().map { it.name }).containsExactly("Alex", "Sam", "Mia").inOrder()
        assertThat(pins.identify("1234")?.let { it.person.name to it.role }).isEqualTo("Alex" to Role.ADMIN)
        assertThat(pins.identify("2468")?.let { it.person.name to it.role }).isEqualTo("Sam" to Role.ADULT)
        assertThat(pins.identify("1357")?.let { it.person.name to it.role }).isEqualTo("Mia" to Role.CHILD)
    }

    private suspend fun seededSources() = store.visibleSourcesFor(store.connectionsNow().single().connection.id)

    @Test
    fun sampleSourcesAreMappedToThePeople() = runTest {
        seed()
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
    fun theFamilyCalendarIsTheWritableMaster() = runTest {
        seed()
        val master = setup.master()!!
        assertThat(master.source.id).isEqualTo(FakeCalendarProvider.SOURCE_FAMILY)
        assertThat(master.source.writable).isTrue()
    }

    @Test
    fun theSampleWeekIsTaggedWithTheSeededPeopleAndASyncIsRequested() = runTest {
        seed()
        val alexId = household.people.first().single { it.name == "Alex" }.id.value
        val zone = ZoneId.systemDefault()
        val today = LocalDate.now(zone)
        val family = FakeCalendarProvider.SOURCES.single { it.id == FakeCalendarProvider.SOURCE_FAMILY }
        val events = fake.sync(
            Connection("debug-sample", FakeCalendarProvider.ID, "Sample calendar", emptyMap()),
            family,
            DateRange(today.minusDays(1), today.plusDays(15), zone),
            null,
        ).upserts
        assertThat(events.single { it.title == "Dinner with Jo & Priya" }.forPerson).isEqualTo(alexId)
        assertThat(syncs).isAtLeast(1)
    }

    @Test
    fun anUpgradedInstallGetsAWritableMasterWithoutClearingData() = runTest {
        store.addConnection(
            Connection("debug-sample", FakeCalendarProvider.ID, "Sample calendar", emptyMap()),
            FakeCalendarProvider.SOURCES.map { it.copy(writable = false) },
            emptyMap(),
        )
        seed()
        val master = setup.master()!!
        assertThat(master.source.id).isEqualTo(FakeCalendarProvider.SOURCE_FAMILY)
        assertThat(master.source.writable).isTrue()
    }

    @Test
    fun seedIsIdempotent() = runTest {
        seed()
        seed()
        assertThat(household.people.first()).hasSize(3)
        assertThat(store.connectionsNow()).hasSize(1)
        assertThat(store.sources().first().count { it.isMaster }).isEqualTo(1)
    }

    @Test
    fun existingActiveAdminIsLeftAlone() = runTest {
        val admin = household.addPerson("Admin", 0xFF4CB387, Role.ADMIN)
        pins.setPin(admin.id, "9999")
        seed()
        assertThat(household.people.first().map { it.name }).containsExactly("Admin")
        assertThat(seededSources().map { it.mapping.person }.toSet()).containsExactly(PersonId.FAMILY)
    }
}
