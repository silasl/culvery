package uk.co.siland.culvery

import androidx.room.Room
import androidx.room.useReaderConnection
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.google.common.truth.Truth.assertThat
import java.time.LocalDate
import java.time.ZoneId
import java.io.File
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import org.robolectric.shadows.ShadowLog
import uk.co.siland.culvery.capability.calendar.CalendarProvider
import uk.co.siland.culvery.capability.calendar.CalendarSetup
import uk.co.siland.culvery.capability.calendar.CalendarSource
import uk.co.siland.culvery.capability.calendar.CalendarStore
import uk.co.siland.culvery.capability.calendar.ChangeKind
import uk.co.siland.culvery.capability.calendar.DateRange
import uk.co.siland.culvery.capability.calendar.PendingChange
import uk.co.siland.culvery.capability.calendar.UnreachableException
import uk.co.siland.culvery.capability.calendar.db.CalendarDatabase
import uk.co.siland.culvery.core.access.PinHasher
import uk.co.siland.culvery.core.access.PinManager
import uk.co.siland.culvery.core.household.HomeLocation
import uk.co.siland.culvery.core.household.HouseholdRepository
import uk.co.siland.culvery.core.household.PersonId
import uk.co.siland.culvery.core.household.Role
import uk.co.siland.culvery.core.household.db.HouseholdDatabase
import uk.co.siland.culvery.core.plugin.Connection
import uk.co.siland.culvery.core.plugin.Toaster
import uk.co.siland.culvery.core.plugin.WallClock
import uk.co.siland.culvery.core.setup.SetupState
import uk.co.siland.culvery.core.setup.setupStore
import uk.co.siland.culvery.core.ui.PersonPalette
import uk.co.siland.culvery.provider.calendar_fake.FakeCalendarProvider

private object NoToasts : Toaster {
    override fun show(message: String, icon: String) = Unit
}

@RunWith(AndroidJUnit4::class)
class DebugSeedTest {
    @get:Rule val folder = TemporaryFolder()
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private lateinit var state: SetupState
    private lateinit var householdDb: HouseholdDatabase
    private lateinit var calendarDb: CalendarDatabase
    private lateinit var household: HouseholdRepository
    private lateinit var pins: PinManager
    private lateinit var store: CalendarStore
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
        state = SetupState(setupStore(scope) { File(folder.root, "setup.preferences_pb") }, household)
    }

    @After
    fun tearDown() {
        scope.cancel()
        householdDb.close()
        calendarDb.close()
    }

    private fun setupFor(provider: FakeCalendarProvider) =
        CalendarSetup(store, setOf(provider), { household.people.first() }, NoToasts, WallClock { 0L }) { syncs++ }

    private suspend fun seed(provider: FakeCalendarProvider = fake) = seedDebugData(household, setupFor(provider), setOf(provider))

    private suspend fun sampleHousehold() = DebugSampleHousehold(household, pins, setupFor(fake), setOf(fake), state::markComplete).create()

    @Test
    fun aFreshDebugInstallHasNoPeopleNoCalendarAndIsNotSetUp() = runTest {
        seed()
        assertThat(household.people.first()).isEmpty()
        assertThat(store.connectionsNow()).isEmpty()
        assertThat(store.master().first()).isNull()
        // So the app opens the wizard.
        assertThat(state.setupComplete.first()).isFalse()
    }

    @Test
    fun theSampleHouseholdIsTheOneTheOldSeedMadeAndCompletesSetup() = runTest {
        sampleHousehold()
        val members = household.members.first()
        assertThat(members.map { Triple(it.person.name, it.person.color, it.role) }).containsExactly(
            Triple("Alex", PersonPalette.colors[0], Role.ADMIN),
            Triple("Sam", PersonPalette.colors[1], Role.ADULT),
            Triple("Mia", PersonPalette.colors[2], Role.CHILD),
        ).inOrder()
        assertThat(pins.identify("1234")?.person?.name).isEqualTo("Alex")
        assertThat(pins.identify("2468")?.person?.name).isEqualTo("Sam")
        assertThat(pins.identify("1357")?.person?.name).isEqualTo("Mia")
        assertThat(household.location.first()).isEqualTo(HomeLocation("London, England, United Kingdom", 51.5074, -0.1278, "Europe/London"))
        assertThat(state.setupComplete.first()).isTrue()
    }

    /** The sample still opens when its Family calendar can't be made the master; a master is chosen in Settings later. */
    @Test
    fun theSampleHouseholdCompletesWhenItsMasterCantBeSet() = runTest {
        ShadowLog.clear()
        val secondListFails = object : CalendarProvider by fake {
            private var lists = 0

            override suspend fun sources(conn: Connection): List<CalendarSource> =
                if (lists++ == 0) fake.sources(conn) else throw UnreachableException("offline")
        }
        val setup = CalendarSetup(store, setOf(secondListFails), { household.people.first() }, NoToasts, WallClock { 0L }) { syncs++ }
        DebugSampleHousehold(household, pins, setup, setOf(fake), state::markComplete).create()
        assertThat(state.setupComplete.first()).isTrue()
        assertThat(store.connectionsNow().map { it.connection.id }).containsExactly(DEBUG_CONNECTION_ID)
        assertThat(store.master().first()).isNull()
        assertThat(ShadowLog.getLogs().map { it.msg }).contains("Couldn't make the sample Family calendar the master (UnreachableException)")
    }

    @Test
    fun sampleSourcesAreMappedToThePeopleWithTheFamilyCalendarAsTheWritableMaster() = runTest {
        sampleHousehold()
        val byName = household.people.first().associate { it.name to it.id }
        val sources = store.visibleSourcesFor(DEBUG_CONNECTION_ID)
        assertThat(sources.associate { it.source.id to it.mapping.person }).containsExactly(
            FakeCalendarProvider.SOURCE_ALEX, byName.getValue("Alex"),
            FakeCalendarProvider.SOURCE_SAM, byName.getValue("Sam"),
            FakeCalendarProvider.SOURCE_MIA, byName.getValue("Mia"),
            FakeCalendarProvider.SOURCE_FAMILY, PersonId.FAMILY,
            FakeCalendarProvider.SOURCE_SCHOOL, PersonId.FAMILY,
        )
        val master = store.master().first()!!
        assertThat(master.source.id).isEqualTo(FakeCalendarProvider.SOURCE_FAMILY)
        assertThat(master.source.writable).isTrue()
    }

    @Test
    fun eachStartTagsTheSampleWeekWithThePeopleAndAsksForASync() = runTest {
        sampleHousehold()
        // A later start: the fake has forgotten its tags.
        val restarted = FakeCalendarProvider()
        syncs = 0
        seed(restarted)
        val alexId = household.people.first().single { it.name == "Alex" }.id.value
        val zone = ZoneId.systemDefault()
        val today = LocalDate.now(zone)
        val family = FakeCalendarProvider.SOURCES.single { it.id == FakeCalendarProvider.SOURCE_FAMILY }
        val events = restarted.sync(
            Connection(DEBUG_CONNECTION_ID, FakeCalendarProvider.ID, "Sample calendar", emptyMap()),
            family,
            DateRange(today.minusDays(1), today.plusDays(15), zone),
            null,
        ).upserts
        assertThat(events.single { it.title == "Dinner with Jo & Priya" }.forPerson).isEqualTo(alexId)
        assertThat(syncs).isAtLeast(1)
    }

    @Test
    fun aRealConnectionRemovesTheSampleWithEverythingItHeld() = runTest {
        sampleHousehold()
        // The sample holds events, a cursor and a queued change, as it would after a sync and an offline add.
        val zone = ZoneId.systemDefault()
        val today = LocalDate.now(zone)
        val range = DateRange(today.minusDays(1), today.plusDays(15), zone)
        val sample = store.connectionsNow().single().connection
        val family = FakeCalendarProvider.SOURCES.single { it.id == FakeCalendarProvider.SOURCE_FAMILY }
        store.applySync(DEBUG_CONNECTION_ID, family.id, range, fake.sync(sample, family, range, null))
        store.enqueue(PendingChange(0, DEBUG_CONNECTION_ID, family.id, "e1", ChangeKind.DELETE, null, 0, 0L, 0L))
        assertThat(sampleRows()).containsExactly("event", true, "outbox", true, "sync_state", true)
        store.addConnection(Connection("g1", "calendar.google", "Google", emptyMap()), emptyList(), emptyMap())
        val watcher = launch { removeSampleWhenReplaced(setupFor(fake)) }
        withContext(Dispatchers.Default) {
            withTimeout(5_000) { while (store.connectionsNow().any { it.connection.id == DEBUG_CONNECTION_ID }) delay(10) }
        }
        watcher.cancel()
        assertThat(store.connectionsNow().map { it.connection.id }).containsExactly("g1")
        assertThat(sampleRows()).containsExactly("event", false, "outbox", false, "sync_state", false)
    }

    /** Whether each table still holds a row of the sample's, counted directly. */
    private suspend fun sampleRows(): Map<String, Boolean> = listOf("event", "outbox", "sync_state").associateWith { table ->
        calendarDb.useReaderConnection { connection ->
            connection.usePrepared("SELECT COUNT(*) FROM $table WHERE connectionId = ?") { statement ->
                statement.bindText(1, DEBUG_CONNECTION_ID)
                statement.step()
                statement.getLong(0) > 0
            }
        }
    }

    @Test
    fun aStartAfterGoogleIsConnectedAddsNoSampleAndSaysNothing() = runTest {
        store.addConnection(Connection("g1", "calendar.google", "Google", emptyMap()), emptyList(), emptyMap())
        seed()
        assertThat(store.connectionsNow().map { it.connection.id }).containsExactly("g1")
        assertThat(ShadowLog.getLogsForTag("Culvery")).isEmpty()
    }
}
