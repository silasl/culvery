package uk.co.siland.culvery.capability.calendar

import androidx.test.ext.junit.runners.AndroidJUnit4
import com.google.common.truth.Truth.assertThat
import kotlin.coroutines.EmptyCoroutineContext
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertThrows
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import uk.co.siland.culvery.capability.calendar.db.CalendarDao
import uk.co.siland.culvery.capability.calendar.db.CalendarDatabase
import uk.co.siland.culvery.capability.calendar.db.ConnectionEntity
import uk.co.siland.culvery.core.household.Person
import uk.co.siland.culvery.core.household.PersonId
import uk.co.siland.culvery.core.plugin.Connection
import uk.co.siland.culvery.core.plugin.ConnectionHealth
import uk.co.siland.culvery.core.plugin.Feature
import uk.co.siland.culvery.core.plugin.WallClock

@RunWith(AndroidJUnit4::class)
class CalendarSetupTest {
    private lateinit var db: CalendarDatabase
    private lateinit var store: CalendarStore
    private val toaster = RecordingToaster()
    private var now = 1_000L
    private val mia = Person(PersonId("mia"), "Mia", 0xFFE07BA8)

    private fun setup(providers: Set<CalendarProvider>, onSync: () -> Unit = {}) =
        CalendarSetup(store, providers, { listOf(mia) }, toaster, WallClock { now }, EmptyCoroutineContext, 1_000, onSync)

    private val provider = ScriptedProvider(
        "calendar.a",
        sourceList = listOf(CalendarSource("s1", "Alex", writable = false), CalendarSource("s2", "Family", writable = false)),
    )

    @Before
    fun setUp() {
        db = calendarDb()
        store = CalendarStore(db)
    }

    @After
    fun tearDown() = db.close()

    @Test
    fun connectStoresTheConnectionWithMappedSources() = runTest {
        val setup = setup(setOf(provider))
        assertThat(setup.hasConnections()).isFalse()
        setup.connect(Connection("c1", "calendar.a", "A", emptyMap()), mapOf("s1" to SourceMapping(PersonId("alex"), visible = true)))
        assertThat(setup.hasConnections()).isTrue()
        assertThat(store.visibleSourcesFor("c1").associate { it.source.id to it.mapping.person }).containsExactly(
            "s1", PersonId("alex"),
            "s2", PersonId.FAMILY,
        )
    }

    @Test
    fun unknownProviderIsRejectedAndNothingIsStored() = runTest {
        val setup = setup(setOf(provider))
        assertThrows(IllegalArgumentException::class.java) {
            runBlocking { setup.connect(Connection("c1", "calendar.missing", "X", emptyMap()), emptyMap()) }
        }
        assertThat(setup.hasConnections()).isFalse()
    }

    private val writable = ScriptedProvider(
        "calendar.w",
        sourceList = listOf(CalendarSource("s1", "Alex", writable = false), CalendarSource("s2", "Family calendar", writable = true)),
        features = setOf(Feature.READ, Feature.WRITE),
    )

    @Test
    fun theMasterIsNullUntilChosen() = runTest {
        val setup = setup(setOf(writable))
        setup.connect(Connection("c1", "calendar.w", "W", emptyMap()), emptyMap())
        assertThat(setup.master()).isNull()
    }

    @Test
    fun setMasterMarksAWritableSourceAndAsksForASync() = runTest {
        var syncs = 0
        val setup = setup(setOf(writable)) { syncs++ }
        setup.connect(Connection("c1", "calendar.w", "W", emptyMap()), emptyMap())
        setup.setMaster("c1", "s2")
        assertThat(setup.master()?.source?.id).isEqualTo("s2")
        assertThat(syncs).isEqualTo(1)
    }

    @Test
    fun setMasterRecordsTheSourceAsWritableEvenIfAnOlderInstallStoredItReadOnly() = runTest {
        store.addConnection(
            Connection("c1", "calendar.w", "W", emptyMap()),
            listOf(CalendarSource("s2", "Family calendar", writable = false)),
            emptyMap(),
        )
        setup(setOf(writable)).setMaster("c1", "s2")
        assertThat(store.source("c1", "s2")!!.source.writable).isTrue()
    }

    @Test
    fun setMasterRefusesAReadOnlySourceOrAProviderThatCannotWrite() = runTest {
        val setup = setup(setOf(writable, provider))
        setup.connect(Connection("c1", "calendar.w", "W", emptyMap()), emptyMap())
        setup.connect(Connection("c2", "calendar.a", "A", emptyMap()), emptyMap())
        assertThrows(IllegalArgumentException::class.java) { runBlocking { setup.setMaster("c1", "s1") } }
        assertThrows(IllegalArgumentException::class.java) { runBlocking { setup.setMaster("c2", "s2") } }
        assertThat(setup.master()).isNull()
    }

    private val google = ScriptedProvider(
        "calendar.google",
        sourceList = listOf(
            CalendarSource("family@example.com", "Family", writable = true, primary = true),
            CalendarSource("mia", "Mia's swimming", writable = false),
            CalendarSource("holidays", "UK holidays", writable = false, shown = false),
        ),
        features = setOf(Feature.READ, Feature.WRITE),
        displayName = "Google Calendar",
    )
    private val googleConnection = Connection("g1", "calendar.google", "Google", mapOf(CONFIG_ACCOUNT to "family@example.com"))

    @Test
    fun connectingWithDefaultsMakesThePrimaryTheMasterMapsByNameAndAsksForASync() = runTest {
        var syncs = 0
        assertThat(setup(setOf(google)) { syncs++ }.connectWithDefaults(googleConnection)).isTrue()
        assertThat(store.master().first()?.source?.id).isEqualTo("family@example.com")
        assertThat(store.sources().first().associate { it.source.id to it.mapping }).containsExactly(
            "family@example.com", SourceMapping(PersonId.FAMILY, visible = true),
            "mia", SourceMapping(mia.id, visible = true),
            "holidays", SourceMapping(PersonId.FAMILY, visible = false),
        )
        assertThat(syncs).isEqualTo(1)
        assertThat(toaster.messages).containsExactly("Google Calendar connected")
    }

    @Test
    fun aConnectThatFailsStoresNothingAndSaysSo() = runTest {
        google.sourcesFailWith = UnreachableException("offline")
        assertThat(setup(setOf(google)).connectWithDefaults(googleConnection)).isFalse()
        assertThat(store.connectionsNow()).isEmpty()
        assertThat(toaster.messages).containsExactly("Couldn't connect to Google Calendar — try again")
    }

    @Test
    fun aConnectThatTimesOutStoresNothingAndSaysSo() = runTest {
        // Never answers: only the setup's own (virtual) 1 s timeout ends the read.
        google.sourcesHang = true
        assertThat(setup(setOf(google)).connectWithDefaults(googleConnection)).isFalse()
        assertThat(store.connectionsNow()).isEmpty()
        assertThat(toaster.messages).containsExactly("Couldn't connect to Google Calendar — try again")
    }

    @Test
    fun connectingAnAccountAlreadyConnectedReconnectsItInsteadOfAddingASecond() = runTest {
        val setup = setup(setOf(google))
        setup.connectWithDefaults(googleConnection)
        store.setHealth("g1", ConnectionHealth.NeedsSignIn, 500L)
        // A second tap on Connect: the provider's screen made a new connection, with a new id, for the same account.
        assertThat(setup.connectWithDefaults(googleConnection.copy(id = "g2"))).isTrue()
        assertThat(store.connectionsNow().map { it.connection.id to it.health }).containsExactly("g1" to ConnectionHealth.Ok)
        assertThat(toaster.messages).containsExactly("Google Calendar connected", "Google Calendar reconnected").inOrder()
    }

    @Test
    fun theSameAccountMatchesWhateverItsCase() = runTest {
        val setup = setup(setOf(google))
        setup.connectWithDefaults(googleConnection)
        val shouted = googleConnection.copy(id = "g2", config = mapOf(CONFIG_ACCOUNT to "Family@Example.com"))
        assertThat(setup.connectWithDefaults(shouted)).isTrue()
        assertThat(store.connectionsNow().map { it.connection.id }).containsExactly("g1")
    }

    @Test
    fun reconnectSetsHealthOkMakesTheQueueDueAndAsksForASync() = runTest {
        var syncs = 0
        val setup = setup(setOf(google)) { syncs++ }
        setup.connectWithDefaults(googleConnection)
        store.setHealth("g1", ConnectionHealth.NeedsSignIn, 500L)
        store.enqueue(
            PendingChange(0, "g1", "family@example.com", "e1", ChangeKind.DELETE, null, attempts = 3, nextAttemptMillis = 90_000L, createdMillis = 100L),
        )
        now = 5_000L
        assertThat(setup.reconnect(googleConnection)).isTrue()
        assertThat(store.connectionsNow().single().health).isEqualTo(ConnectionHealth.Ok)
        // Due now without an extra attempt, and the lapse (from 500) no longer counts towards its age (D16): its
        // creation moved forward by the 4.5 s paused since then.
        assertThat(store.pendingNow().single().let { Triple(it.attempts, it.nextAttemptMillis, it.createdMillis) })
            .isEqualTo(Triple(3, 5_000L, 4_600L))
        assertThat(syncs).isEqualTo(2)
        assertThat(toaster.messages).containsExactly("Google Calendar connected", "Google Calendar reconnected").inOrder()
    }

    @Test
    fun removingAConnectionTakesEverythingWithIt() = runTest {
        val setup = setup(setOf(google))
        setup.connectWithDefaults(googleConnection)
        setup.removeConnection("g1")
        assertThat(setup.connectionIds().first()).isEmpty()
        assertThat(store.sources().first()).isEmpty()
    }

    @Test
    fun twoConnectsOfTheSameAccountAtOnceMakeOneConnection() = runTest {
        // The reads run on a real thread under a real timeout: virtual time would time the held read out.
        val setup = CalendarSetup(store, setOf(google), { listOf(mia) }, toaster, WallClock { now }, Dispatchers.Default, 5_000)
        val gate = CompletableDeferred<Unit>()
        google.sourcesGate = gate
        val first = async { setup.connectWithDefaults(googleConnection) }
        withContext(Dispatchers.Default) { withTimeout(5_000) { while (google.sourcesCalls < 1) delay(10) } }
        val second = async { setup.connectWithDefaults(googleConnection.copy(id = "g2")) }
        // Without the lock the second would get past the check and read the list too; give it the chance.
        withContext(Dispatchers.Default) { withTimeoutOrNull(500) { while (google.sourcesCalls < 2) delay(10) } }
        gate.complete(Unit)
        assertThat(first.await() to second.await()).isEqualTo(true to true)
        assertThat(store.connectionsNow().map { it.connection.id }).containsExactly("g1")
        assertThat(toaster.messages).containsExactly("Google Calendar connected", "Google Calendar reconnected").inOrder()
    }

    @Test
    fun aListWithoutThePrimaryIsAFailedConnect() = runTest {
        google.sourceList = google.sourceList.filterNot { it.primary }
        assertThat(setup(setOf(google)).connectWithDefaults(googleConnection)).isFalse()
        assertThat(store.connectionsNow()).isEmpty()
        assertThat(toaster.messages).containsExactly("Couldn't connect to Google Calendar — try again")
    }

    @Test
    fun anUnknownProviderIsNamedByItsConnectionLabelNotACrash() = runTest {
        // calendar.google isn't installed (only calendar.a is): the toast falls back to the connection's own label
        // ("Google"), never the raw provider id, and the setup itself doesn't crash.
        assertThat(setup(setOf(provider)).connectWithDefaults(googleConnection)).isFalse()
        assertThat(setup(setOf(provider)).reconnect(googleConnection)).isFalse()
        assertThat(store.connectionsNow()).isEmpty()
        assertThat(toaster.messages).containsExactly(
            "Couldn't connect to Google — try again", "Couldn't connect to Google — try again",
        )
    }

    @Test
    fun reconnectingAConnectionRemovedMeanwhileRecordsNothing() = runTest {
        var syncs = 0
        val setup = setup(setOf(google)) { syncs++ }
        setup.connectWithDefaults(googleConnection)
        setup.removeConnection("g1")
        assertThat(setup.reconnect(googleConnection)).isFalse()
        assertThat(store.connectionsNow()).isEmpty()
        assertThat(syncs).isEqualTo(1)
        assertThat(toaster.messages).containsExactly("Google Calendar connected", "Couldn't connect to Google Calendar — try again").inOrder()
    }

    @Test
    fun aStoreFailureCheckingForTheAccountIsAFailedConnect() = runTest {
        val failing = object : CalendarDao by db.calendarDao() {
            override suspend fun allConnections(): List<ConnectionEntity> = throw IllegalStateException("disk I/O error")
        }
        val setup = CalendarSetup(CalendarStore(db, failing), setOf(google), { listOf(mia) }, toaster, WallClock { now }, EmptyCoroutineContext, 1_000)
        assertThat(setup.connectWithDefaults(googleConnection)).isFalse()
        assertThat(store.sources().first()).isEmpty()
        assertThat(toaster.messages).containsExactly("Couldn't connect to Google Calendar — try again")
    }
}
