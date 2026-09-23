package uk.co.siland.culvery.capability.calendar

import androidx.test.ext.junit.runners.AndroidJUnit4
import com.google.common.truth.Truth.assertThat
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import kotlin.coroutines.EmptyCoroutineContext
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import uk.co.siland.culvery.capability.calendar.db.CalendarDatabase
import uk.co.siland.culvery.core.household.HomeLocation
import uk.co.siland.culvery.core.household.HouseholdRepository
import uk.co.siland.culvery.core.household.PersonId
import uk.co.siland.culvery.core.household.db.HouseholdDatabase
import uk.co.siland.culvery.core.plugin.Connection
import uk.co.siland.culvery.core.plugin.ConnectionHealth
import uk.co.siland.culvery.core.plugin.WallClock

// Robolectric for Room and for android.util.Log (the engine logs timeouts).
@RunWith(AndroidJUnit4::class)
class CalendarSyncTest {
    private lateinit var calendar: CalendarDatabase
    private lateinit var householdDb: HouseholdDatabase
    private lateinit var store: CalendarStore
    private lateinit var household: HouseholdRepository

    private val london = ZoneId.of("Europe/London")
    private var now = Instant.parse("2026-09-23T11:00:00Z")
    private val clock = WallClock { now.toEpochMilli() }
    private val s1 = CalendarSource("s1", "One", writable = false)
    private val s2 = CalendarSource("s2", "Two", writable = false)
    private val a = ScriptedProvider("calendar.a")
    private val b = ScriptedProvider("calendar.b")

    @Before
    fun setUp() {
        calendar = calendarDb()
        householdDb = householdDb()
        store = CalendarStore(calendar)
        household = HouseholdRepository(householdDb)
    }

    @After
    fun tearDown() {
        calendar.close()
        householdDb.close()
    }

    /** Provider calls stay on the test dispatcher, so the 1 s timeout runs on virtual time. */
    private suspend fun engine(): CalendarSync {
        household.setLocation(HomeLocation("London", 51.5, -0.12, "Europe/London"))
        return CalendarSync(store, setOf(a, b), HouseholdZone(household), clock, EmptyCoroutineContext, timeoutMillis = 1_000)
    }

    private suspend fun connect(id: String, providerId: String, vararg sources: CalendarSource, mapping: Map<String, SourceMapping> = emptyMap()) =
        store.addConnection(Connection(id, providerId, id.uppercase(), emptyMap()), sources.toList(), mapping)

    private fun swim() = RemoteEvent(
        "swim", "Swim",
        EventTime.Timed(Instant.parse("2026-09-23T15:00:00Z")),
        EventTime.Timed(Instant.parse("2026-09-23T16:00:00Z")),
        recurring = false,
    )

    private suspend fun cachedTitles() =
        store.eventsBetween(Instant.parse("2026-09-23T00:00:00Z").toEpochMilli(), Instant.parse("2026-09-24T00:00:00Z").toEpochMilli())
            .first().map { it.title }

    private suspend fun health(id: String) = store.connectionsNow().single { it.connection.id == id }.health

    @Test
    fun syncStoresEventsAndMarksTheConnectionOk() = runTest {
        connect("c1", "calendar.a", s1)
        a.events = { listOf(swim()) }
        engine().syncAll()
        assertThat(cachedTitles()).containsExactly("Swim")
        val stored = store.connectionsNow().single()
        assertThat(stored.health).isEqualTo(ConnectionHealth.Ok)
        assertThat(stored.lastSyncMillis).isEqualTo(now.toEpochMilli())
    }

    @Test
    fun windowIsYesterdayToTwoWeeksAheadInTheHouseholdZone() = runTest {
        connect("c1", "calendar.a", s1)
        val sync = engine()
        household.setLocation(HomeLocation("Wellington", -41.29, 174.78, "Pacific/Auckland"))
        now = Instant.parse("2026-09-23T13:00:00Z") // already 24 September in Auckland
        sync.syncAll()
        val auckland = ZoneId.of("Pacific/Auckland")
        assertThat(a.calls.single().range).isEqualTo(DateRange(LocalDate.of(2026, 9, 23), LocalDate.of(2026, 10, 9), auckland))
    }

    @Test
    fun returnedCursorIsPassedToTheNextSync() = runTest {
        connect("c1", "calendar.a", s1)
        val sync = engine()
        sync.syncAll()
        sync.syncAll()
        assertThat(a.calls.map { it.cursor }).containsExactly(null, SyncCursor("k1")).inOrder()
    }

    @Test
    fun aNewDayResyncsFromScratch() = runTest {
        connect("c1", "calendar.a", s1)
        val sync = engine()
        sync.syncAll()
        now = Instant.parse("2026-09-24T11:00:00Z")
        sync.syncAll()
        assertThat(a.calls[1].cursor).isNull()
        assertThat(a.calls[1].range).isEqualTo(DateRange(LocalDate.of(2026, 9, 23), LocalDate.of(2026, 10, 9), london))
    }

    @Test
    fun needsSignInKeepsCachedEventsAndFlagsConnection() = runTest {
        connect("c1", "calendar.a", s1)
        a.events = { listOf(swim()) }
        val sync = engine()
        sync.syncAll()
        a.failWith = NeedsSignInException("token expired")
        now = now.plusSeconds(300)
        sync.syncAll()
        assertThat(health("c1")).isEqualTo(ConnectionHealth.NeedsSignIn)
        assertThat(cachedTitles()).containsExactly("Swim")
        assertThat(store.connectionsNow().single().lastSyncMillis).isEqualTo(Instant.parse("2026-09-23T11:00:00Z").toEpochMilli())
    }

    @Test
    fun unreachableKeepsCachedEvents() = runTest {
        connect("c1", "calendar.a", s1)
        a.events = { listOf(swim()) }
        val sync = engine()
        sync.syncAll()
        a.failWith = UnreachableException("no network")
        sync.syncAll()
        assertThat(health("c1")).isEqualTo(ConnectionHealth.Unreachable)
        assertThat(cachedTitles()).containsExactly("Swim")
    }

    @Test
    fun unexpectedExceptionBecomesErrorWithItsMessage() = runTest {
        connect("c1", "calendar.a", s1)
        a.failWith = IllegalStateException("quota exceeded")
        engine().syncAll()
        assertThat(health("c1")).isEqualTo(ConnectionHealth.Error("quota exceeded"))
    }

    @Test
    fun missingProviderMarksTheConnectionError() = runTest {
        connect("c1", "calendar.gone", s1)
        engine().syncAll()
        assertThat(health("c1")).isEqualTo(ConnectionHealth.Error("Provider not installed"))
    }

    @Test
    fun oneFailingConnectionDoesNotStopTheOthers() = runTest {
        connect("c1", "calendar.a", s1)
        connect("c2", "calendar.b", s1)
        a.failWith = UnreachableException()
        b.events = { listOf(swim()) }
        engine().syncAll()
        assertThat(health("c1")).isEqualTo(ConnectionHealth.Unreachable)
        assertThat(health("c2")).isEqualTo(ConnectionHealth.Ok)
        assertThat(cachedTitles()).containsExactly("Swim")
    }

    @Test
    fun hiddenSourcesAreNotSynced() = runTest {
        connect("c1", "calendar.a", s1, s2, mapping = mapOf("s2" to SourceMapping(PersonId.FAMILY, visible = false)))
        engine().syncAll()
        assertThat(a.calls.map { it.sourceId }).containsExactly("s1")
    }

    @Test
    fun oneFailingSourceDoesNotBlockItsSiblingsButHoldsBackMarkSynced() = runTest {
        connect("c1", "calendar.a", s1, s2)
        a.events = { listOf(swim()) }
        a.failFor = mapOf("s1" to UnreachableException("s1 down"))
        engine().syncAll()
        assertThat(a.calls.map { it.sourceId }).containsExactly("s1", "s2")
        assertThat(cachedTitles()).containsExactly("Swim")
        assertThat(health("c1")).isEqualTo(ConnectionHealth.Unreachable)
        assertThat(store.connectionsNow().single().lastSyncMillis).isNull()
    }

    @Test
    fun connectionHealthIsTheWorstAcrossItsSources() = runTest {
        connect("c1", "calendar.a", s1, s2)
        a.failFor = mapOf("s1" to IllegalStateException("quota exceeded"), "s2" to NeedsSignInException("expired"))
        engine().syncAll()
        assertThat(health("c1")).isEqualTo(ConnectionHealth.NeedsSignIn)
    }

    @Test
    fun hangingProviderTimesOutAsUnreachableAndTheNextSyncStillRuns() = runTest {
        connect("c1", "calendar.a", s1)
        connect("c2", "calendar.b", s1)
        a.hang = true
        b.events = { listOf(swim()) }
        val sync = engine()
        sync.syncAll()
        assertThat(health("c1")).isEqualTo(ConnectionHealth.Unreachable)
        assertThat(health("c2")).isEqualTo(ConnectionHealth.Ok)
        a.hang = false
        sync.syncAll()
        assertThat(health("c1")).isEqualTo(ConnectionHealth.Ok)
    }

    @Test
    fun providerTimeoutCancellationBecomesUnreachable() = runTest {
        connect("c1", "calendar.a", s1)
        a.failWith = timeoutCancellation()
        val sync = engine()
        sync.syncAll()
        assertThat(health("c1")).isEqualTo(ConnectionHealth.Unreachable)
        a.failWith = null
        sync.syncAll()
        assertThat(health("c1")).isEqualTo(ConnectionHealth.Ok)
    }

    @Test
    fun strayCancellationFromAProviderBecomesUnreachable() = runTest {
        connect("c1", "calendar.a", s1)
        a.failWith = CancellationException("provider cancelled its own job")
        engine().syncAll()
        assertThat(health("c1")).isEqualTo(ConnectionHealth.Unreachable)
    }
}
