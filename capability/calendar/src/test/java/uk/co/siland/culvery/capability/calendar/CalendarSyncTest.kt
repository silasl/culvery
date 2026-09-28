package uk.co.siland.culvery.capability.calendar

import androidx.room.execSQL
import androidx.room.useWriterConnection
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.google.common.truth.Truth.assertThat
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import kotlin.coroutines.CoroutineContext
import kotlin.coroutines.EmptyCoroutineContext
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.shadows.ShadowLog
import uk.co.siland.culvery.capability.calendar.db.CalendarDatabase
import uk.co.siland.culvery.capability.calendar.db.OutboxEntity
import uk.co.siland.culvery.core.household.HomeLocation
import uk.co.siland.culvery.core.household.HouseholdRepository
import uk.co.siland.culvery.core.household.PersonId
import uk.co.siland.culvery.core.household.db.HouseholdDatabase
import uk.co.siland.culvery.core.plugin.Connection
import uk.co.siland.culvery.core.plugin.ConnectionHealth
import uk.co.siland.culvery.core.plugin.Toaster
import uk.co.siland.culvery.core.plugin.WallClock

// Robolectric for Room and for android.util.Log (the engine logs timeouts).
@OptIn(ExperimentalCoroutinesApi::class)
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

    /** Every engine this class builds. Provider calls stay on the test dispatcher, so the 1 s timeout runs on virtual time. */
    private suspend fun engineWith(
        writers: Set<CalendarWriter>,
        toaster: Toaster,
        io: CoroutineContext = EmptyCoroutineContext,
        timeoutMillis: Long = 1_000,
        lock: CalendarWriteLock = CalendarWriteLock(),
    ): CalendarSync {
        household.setLocation(HomeLocation("London", 51.5, -0.12, "Europe/London"))
        return testSync(store, setOf(a, b), HouseholdZone(household), clock, writers, toaster, io, timeoutMillis, lock)
    }

    private suspend fun engine(): CalendarSync = engineWith(emptySet(), SilentToaster)

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

    private val w = ScriptedWriter("calendar.a")
    private val toaster = RecordingToaster()
    private val key = "0123456789abcdef0123456789abcdef"

    private suspend fun writingEngine(io: CoroutineContext = EmptyCoroutineContext, timeoutMillis: Long = 1_000): CalendarSync =
        engineWith(setOf(w), toaster, io, timeoutMillis)

    private fun swimDraft(forPerson: String?) = EventDraft("Swim", swim().start, swim().end, forPerson, "alex-id")

    private suspend fun queue(
        kind: ChangeKind,
        remoteId: String? = "swim",
        draft: EventDraft? = swimDraft("sam-id"),
        attempts: Int = 1,
        sourceId: String = "s1",
        next: Instant = now,
        created: Instant = now,
        clientKey: String? = null,
    ): Long = store.enqueue(
        PendingChange(0, "c1", sourceId, remoteId, kind, draft, attempts, next.toEpochMilli(), created.toEpochMilli(), clientKey),
    )

    @Test
    fun drainDeliversADueDeleteAndCompletesIt() = runTest {
        connect("c1", "calendar.a", s1)
        a.events = { listOf(swim()) }
        val sync = writingEngine()
        sync.syncAll()
        queue(ChangeKind.DELETE, draft = null)
        a.events = { emptyList() }
        sync.syncAll()
        assertThat(w.calls).containsExactly("delete:swim")
        assertThat(store.pendingNow()).isEmpty()
        assertThat(cachedTitles()).isEmpty()
    }

    @Test
    fun drainAppliesAnAcceptedUpdateEvenWhenTheReadFails() = runTest {
        connect("c1", "calendar.a", s1)
        a.events = { listOf(swim()) }
        val sync = writingEngine()
        sync.syncAll()
        queue(ChangeKind.UPDATE)
        a.failWith = UnreachableException("reads are down")
        sync.syncAll()
        assertThat(w.calls).containsExactly("update:swim")
        assertThat(store.eventNow(EventRef("c1", "s1", "swim"))!!.forPerson).isEqualTo("sam-id")
        assertThat(store.pendingNow()).isEmpty()
    }

    @Test
    fun drainDeliversACreateWithItsKeyAndMirrorsItUnderThatKey() = runTest {
        connect("c1", "calendar.a", s1)
        val sync = writingEngine()
        queue(ChangeKind.CREATE, remoteId = null, draft = swimDraft("mia-id"), clientKey = key)
        a.failWith = UnreachableException("reads are down")
        sync.syncAll()
        assertThat(w.calls).containsExactly("create:Swim")
        assertThat(w.created.keys).containsExactly(key)
        assertThat(store.eventNow(EventRef("c1", "s1", key))!!.forPerson).isEqualTo("mia-id")
        assertThat(store.pendingNow()).isEmpty()
    }

    @Test
    fun queuedChangesDrainInTheOrderTheyWereMade() = runTest {
        connect("c1", "calendar.a", s1)
        val sync = writingEngine()
        queue(ChangeKind.UPDATE)
        queue(ChangeKind.DELETE, draft = null)
        sync.syncAll()
        assertThat(w.calls).containsExactly("update:swim", "delete:swim").inOrder()
    }

    @Test
    fun aLaterChangeWaitsForAnEarlierOneInBackoff() = runTest {
        connect("c1", "calendar.a", s1)
        val sync = writingEngine()
        queue(ChangeKind.UPDATE, next = now.plusSeconds(30))
        queue(ChangeKind.DELETE, draft = null)
        sync.syncAll()
        assertThat(w.calls).isEmpty()
        assertThat(store.pendingNow()).hasSize(2)
        now = now.plusSeconds(30)
        sync.syncAll()
        assertThat(w.calls).containsExactly("update:swim", "delete:swim").inOrder()
        assertThat(store.pendingNow()).isEmpty()
    }

    @Test
    fun aQueuedAssignIsAppliedToTheEventAsItIsNow() = runTest {
        connect("c1", "calendar.a", s1)
        // Renamed on a phone after the assign was queued with the old title.
        a.events = { listOf(swim().copy(title = "Swim club")) }
        val sync = writingEngine()
        sync.syncAll()
        queue(ChangeKind.ASSIGN, draft = swimDraft("sam-id"))
        a.failWith = UnreachableException("reads are down")
        sync.syncAll()
        val sent = w.drafts.single()
        assertThat(listOf(sent.title, sent.forPerson)).containsExactly("Swim club", "sam-id").inOrder()
        assertThat(store.eventNow(EventRef("c1", "s1", "swim"))!!.let { it.title to it.forPerson }).isEqualTo("Swim club" to "sam-id")
        assertThat(store.pendingNow()).isEmpty()
    }

    @Test
    fun aQueuedAssignForAnEventThatIsGoneIsDroppedWithAToast() = runTest {
        connect("c1", "calendar.a", s1)
        val sync = writingEngine()
        queue(ChangeKind.ASSIGN)
        sync.syncAll()
        assertThat(w.calls).isEmpty()
        assertThat(store.pendingNow()).isEmpty()
        assertThat(toaster.messages).containsExactly("Couldn't save to C1 — The event no longer exists")
    }

    @Test
    fun drainRejectionDropsTheChangeAndToastsWhy() = runTest {
        connect("c1", "calendar.a", s1)
        a.events = { listOf(swim()) }
        val sync = writingEngine()
        sync.syncAll()
        queue(ChangeKind.DELETE, draft = null)
        w.failWith = WriteRejectedException("Event is locked")
        sync.syncAll()
        assertThat(store.pendingNow()).isEmpty()
        assertThat(cachedTitles()).containsExactly("Swim")
        assertThat(toaster.messages).containsExactly("Couldn't save to C1 — Event is locked")
    }

    @Test
    fun severalRejectionsInOnePassMakeOneToast() = runTest {
        connect("c1", "calendar.a", s1)
        val sync = writingEngine()
        queue(ChangeKind.DELETE, remoteId = "swim", draft = null)
        queue(ChangeKind.DELETE, remoteId = "walk", draft = null)
        w.failWith = WriteRejectedException("Event is locked")
        sync.syncAll()
        assertThat(w.calls).containsExactly("delete:swim", "delete:walk").inOrder()
        assertThat(toaster.messages).containsExactly("Couldn't save 2 changes to C1")
    }

    @Test
    fun anUnreachableDrainRetriesWithBackoff() = runTest {
        connect("c1", "calendar.a", s1)
        val sync = writingEngine()
        queue(ChangeKind.DELETE, draft = null, attempts = 1)
        w.failWith = UnreachableException("offline")
        sync.syncAll()
        assertThat(store.pendingNow().single().let { it.attempts to it.nextAttemptMillis })
            .isEqualTo(2 to now.toEpochMilli() + 60_000)
        sync.syncAll()
        assertThat(w.calls).hasSize(1)
        now = now.plusSeconds(60)
        sync.syncAll()
        assertThat(w.calls).hasSize(2)
        assertThat(store.pendingNow().single().let { it.attempts to it.nextAttemptMillis })
            .isEqualTo(3 to now.toEpochMilli() + 120_000)
    }

    @Test
    fun anUnreachableConnectionIsNotTriedAgainInThePass() = runTest {
        connect("c1", "calendar.a", s1)
        val sync = writingEngine()
        queue(ChangeKind.DELETE, remoteId = "swim", draft = null, attempts = 1)
        queue(ChangeKind.DELETE, remoteId = "walk", draft = null, attempts = 1)
        w.failWith = UnreachableException("offline")
        sync.syncAll()
        assertThat(w.calls).containsExactly("delete:swim")
        assertThat(store.pendingNow().map { it.attempts to it.nextAttemptMillis })
            .containsExactly(2 to now.toEpochMilli() + 60_000, 2 to now.toEpochMilli() + 60_000)
    }

    @Test
    fun backoffIsThirtySecondsOneMinuteTwoMinutesThenFive() {
        assertThat((1..6).map(::backoffMillis))
            .containsExactly(30_000L, 60_000L, 120_000L, 300_000L, 300_000L, 300_000L).inOrder()
    }

    @Test
    fun needsSignInRetriesWithTheNormalBackoff() = runTest {
        connect("c1", "calendar.a", s1)
        val sync = writingEngine()
        queue(ChangeKind.DELETE, draft = null, attempts = 1)
        w.failWith = NeedsSignInException("expired")
        a.failWith = NeedsSignInException("expired")
        sync.syncAll()
        // The pass's own read flags the connection; the failed write only reschedules.
        assertThat(health("c1")).isEqualTo(ConnectionHealth.NeedsSignIn)
        assertThat(store.pendingNow().single().let { it.attempts to it.nextAttemptMillis })
            .isEqualTo(2 to now.toEpochMilli() + 60_000)
        now = now.plusSeconds(60)
        sync.syncAll()
        assertThat(w.calls).hasSize(2)
    }

    @Test
    fun aChangeUnsentForTwoDaysIsDroppedWithAToast() = runTest {
        connect("c1", "calendar.a", s1)
        val sync = writingEngine()
        queue(ChangeKind.DELETE, draft = null, created = now.minusMillis(OUTBOX_MAX_AGE_MS + 1))
        sync.syncAll()
        assertThat(w.calls).isEmpty()
        assertThat(store.pendingNow()).isEmpty()
        assertThat(toaster.messages).containsExactly("Couldn't save to C1")
    }

    @Test
    fun aChangeWithNowhereToGoIsDroppedWithAToast() = runTest {
        connect("c1", "calendar.a", s1)
        val sync = writingEngine()
        queue(ChangeKind.DELETE, draft = null, sourceId = "gone")
        sync.syncAll()
        assertThat(store.pendingNow()).isEmpty()
        assertThat(w.calls).isEmpty()
        assertThat(toaster.messages).containsExactly("Couldn't save to C1")
    }

    @Test
    fun anUnreadableQueuedRowIsDroppedAndTheSyncStillRuns() = runTest {
        connect("c1", "calendar.a", s1)
        a.events = { listOf(swim()) }
        val sync = writingEngine()
        calendar.calendarDao().insertOutbox(
            OutboxEntity(
                connectionId = "c1", sourceId = "s1", remoteId = "swim", kind = "BOGUS", draftJson = null,
                attempts = 0, nextAttemptMillis = 0, createdMillis = 0,
            ),
        )
        sync.syncAll()
        assertThat(cachedTitles()).containsExactly("Swim")
        assertThat(calendar.calendarDao().outboxNow()).isEmpty()
    }

    @Test
    fun aPassWaitsForTheOneBeforeIt() = runTest {
        connect("c1", "calendar.a", s1)
        // Real threads and a real timeout: runTest must not skip virtual time past the held-up pass.
        val sync = writingEngine(io = Dispatchers.Default, timeoutMillis = PROVIDER_TIMEOUT_MS)
        a.gate = CompletableDeferred()
        val first = launch { sync.syncAll() }
        a.entered.await()
        val second = launch { sync.syncAll() }
        // Real time for the second pass to reach the provider, if nothing held it back.
        withContext(Dispatchers.Default) { delay(200) }
        assertThat(a.calls).hasSize(1)
        a.gate?.complete(Unit)
        first.join()
        second.join()
        assertThat(a.calls).hasSize(2)
        assertThat(a.maxInFlight).isEqualTo(1)
    }

    @Test
    fun realCancellationPropagatesAndLeavesTheConnectionAlone() = runTest {
        connect("c1", "calendar.a", s1)
        a.hang = true
        val sync = engine()
        val job = launch { sync.syncAll() }
        a.entered.await()
        job.cancelAndJoin()
        assertThat(job.isCancelled).isTrue()
        assertThat(health("c1")).isEqualTo(ConnectionHealth.Ok)
        assertThat(store.connectionsNow().single().lastSyncMillis).isNull()
        a.hang = false
        sync.syncAll()
        assertThat(store.connectionsNow().single().lastSyncMillis).isEqualTo(now.toEpochMilli())
    }

    @Test
    fun aChangeWaitingBehindAnEarlierOneIsRescheduledToItsTimeWithoutAnAttempt() = runTest {
        connect("c1", "calendar.a", s1)
        val sync = writingEngine()
        val blockerAt = now.plusSeconds(30).toEpochMilli()
        queue(ChangeKind.UPDATE, next = now.plusSeconds(30))
        val later = queue(ChangeKind.DELETE, draft = null)
        sync.syncAll()
        // Otherwise the loop would wake every second for the later change until the blocker is due.
        assertThat(store.nextAttemptMillis()).isEqualTo(blockerAt)
        assertThat(store.pendingNow().single { it.id == later }.let { it.attempts to it.nextAttemptMillis })
            .isEqualTo(1 to blockerAt)
    }

    @Test
    fun aFailedDrainIsLoggedAndTheSyncStillRuns() = runTest {
        connect("c1", "calendar.a", s1)
        a.events = { listOf(swim()) }
        val broken = object : Toaster {
            override fun show(message: String, icon: String) = error("toasts are down")
        }
        val sync = engineWith(setOf(w), broken)
        queue(ChangeKind.DELETE, draft = null, sourceId = "gone")
        sync.syncAll()
        assertThat(cachedTitles()).containsExactly("Swim")
    }

    @Test
    fun toastsForDroppedChangesAreShownWhenTheRestOfThePassIsCancelled() = runTest {
        connect("c1", "calendar.a", s1)
        val sync = writingEngine()
        queue(ChangeKind.DELETE, draft = null, sourceId = "gone")
        queue(ChangeKind.DELETE, draft = null)
        w.gate = CompletableDeferred()
        val job = launch { sync.syncAll() }
        w.entered.await()
        job.cancelAndJoin()
        assertThat(toaster.messages).containsExactly("Couldn't save to C1")
    }

    @Test
    fun anIncompleteChangeIsDroppedWithAToastNotRetried() = runTest {
        connect("c1", "calendar.a", s1)
        val sync = writingEngine()
        queue(ChangeKind.UPDATE, draft = null)
        sync.syncAll()
        assertThat(w.calls).isEmpty()
        assertThat(store.pendingNow()).isEmpty()
        assertThat(toaster.messages).containsExactly("Couldn't save to C1")
    }

    @Test
    fun aRefusedCreateDropsTheChangesQueuedBehindItWithOneToast() = runTest {
        connect("c1", "calendar.a", s1)
        val sync = writingEngine()
        queue(ChangeKind.CREATE, remoteId = null, draft = swimDraft("mia-id"), clientKey = key)
        queue(ChangeKind.UPDATE, remoteId = key, draft = swimDraft("sam-id"))
        queue(ChangeKind.DELETE, remoteId = key, draft = null)
        w.failWith = WriteRejectedException("Calendar is full")
        sync.syncAll()
        // Nothing is sent for an event that was never made.
        assertThat(w.calls).containsExactly("create:Swim")
        assertThat(store.pendingNow()).isEmpty()
        assertThat(toaster.messages).containsExactly("Couldn't save 3 changes to C1")
    }

    @Test
    fun aCreateDroppedAfterTwoDaysTakesItsQueuedChangesWithIt() = runTest {
        connect("c1", "calendar.a", s1)
        val sync = writingEngine()
        val longAgo = now.minusMillis(OUTBOX_MAX_AGE_MS + 1)
        queue(ChangeKind.CREATE, remoteId = null, draft = swimDraft("mia-id"), clientKey = key, created = longAgo)
        queue(ChangeKind.UPDATE, remoteId = key, draft = swimDraft("sam-id"))
        sync.syncAll()
        assertThat(w.calls).isEmpty()
        assertThat(store.pendingNow()).isEmpty()
        assertThat(toaster.messages).containsExactly("Couldn't save 2 changes to C1")
    }

    @Test
    fun aChangeForAnotherEventIsNotDroppedWithARefusedCreate() = runTest {
        connect("c1", "calendar.a", s1)
        val sync = writingEngine()
        queue(ChangeKind.CREATE, remoteId = null, draft = swimDraft("mia-id"), clientKey = key)
        queue(ChangeKind.DELETE, remoteId = "swim", draft = null)
        w.failWith = WriteRejectedException("Calendar is full")
        sync.syncAll()
        // The delete was tried (and refused too) on its own account.
        assertThat(w.calls).containsExactly("create:Swim", "delete:swim").inOrder()
    }

    @Test
    fun aQueuedCreateEditAndDeleteAreDeliveredInOrder() = runTest {
        connect("c1", "calendar.a", s1)
        val sync = writingEngine()
        queue(ChangeKind.CREATE, remoteId = null, draft = swimDraft("mia-id"), clientKey = key)
        queue(ChangeKind.UPDATE, remoteId = key, draft = swimDraft("sam-id"))
        queue(ChangeKind.DELETE, remoteId = key, draft = null)
        a.failWith = UnreachableException("reads are down")
        sync.syncAll()
        assertThat(w.calls).containsExactly("create:Swim", "update:$key", "delete:$key").inOrder()
        assertThat(store.pendingNow()).isEmpty()
        assertThat(store.eventNow(EventRef("c1", "s1", key))).isNull()
    }

    @Test
    fun aCreateWhoseMirrorWriteFailsIsRetriedLaterAndMakesOneEvent() = runTest {
        connect("c1", "calendar.a", s1)
        val sync = writingEngine()
        queue(ChangeKind.CREATE, remoteId = null, draft = swimDraft("mia-id"), clientKey = key)
        a.failWith = UnreachableException("reads are down")
        // The provider accepts the create, then the tablet can't store it (a full disk).
        calendar.useWriterConnection {
            it.execSQL("CREATE TRIGGER fail_event BEFORE INSERT ON event BEGIN SELECT RAISE(ABORT, 'disk full'); END")
        }
        sync.syncAll()
        // Rescheduled with the next backoff step, not left due: the loop would run a pass every second.
        assertThat(store.pendingNow().single().let { it.attempts to it.nextAttemptMillis }).isEqualTo(2 to now.toEpochMilli() + 60_000)
        calendar.useWriterConnection { it.execSQL("DROP TRIGGER fail_event") }
        sync.syncAll()
        assertThat(w.calls).containsExactly("create:Swim")
        now = now.plusSeconds(60)
        sync.syncAll()
        // Sent twice with one key: the provider returned the event it had already made.
        assertThat(w.calls).containsExactly("create:Swim", "create:Swim")
        assertThat(w.created.keys).containsExactly(key)
        assertThat(store.eventNow(EventRef("c1", "s1", key))).isNotNull()
        assertThat(store.pendingNow()).isEmpty()
    }

    @Test
    fun aProviderThrowingAnErrorFlagsOnlyItsConnection() = runTest {
        connect("c1", "calendar.a", s1)
        connect("c2", "calendar.b", s1)
        a.failWith = StackOverflowError("provider recursed")
        b.events = { listOf(swim()) }
        engine().syncAll()
        assertThat(health("c1")).isEqualTo(ConnectionHealth.Error("provider recursed"))
        assertThat(health("c2")).isEqualTo(ConnectionHealth.Ok)
        assertThat(cachedTitles()).containsExactly("Swim")
    }

    @Test
    fun aStoreFailureFailsThePassNotTheProvidersHealth() = runTest {
        connect("c1", "calendar.a", s1)
        a.events = { listOf(swim()) }
        val sync = engine()
        calendar.useWriterConnection {
            it.execSQL("CREATE TRIGGER fail_event BEFORE INSERT ON event BEGIN SELECT RAISE(ABORT, 'disk full'); END")
        }
        assertThat(runCatching { sync.syncAll() }.exceptionOrNull()).isNotNull()
        assertThat(health("c1")).isEqualTo(ConnectionHealth.Ok)
        calendar.useWriterConnection { it.execSQL("DROP TRIGGER fail_event") }
        sync.syncAll()
        assertThat(cachedTitles()).containsExactly("Swim")
    }

    @Test
    fun aFailingDrainBacksTheLoopOffUntilADrainCompletes() = runTest {
        connect("c1", "calendar.a", s1)
        var toastsWork = false
        val flaky = object : Toaster {
            override fun show(message: String, icon: String) {
                if (!toastsWork) error("toasts are down")
            }
        }
        val sync = engineWith(setOf(w), flaky)
        assertThat(sync.drainBackoffMillis()).isNull()
        queue(ChangeKind.DELETE, draft = null, sourceId = "gone")
        sync.syncAll()
        assertThat(sync.drainBackoffMillis()).isEqualTo(30_000L)
        queue(ChangeKind.DELETE, draft = null, sourceId = "gone")
        sync.syncAll()
        assertThat(sync.drainBackoffMillis()).isEqualTo(60_000L)
        toastsWork = true
        sync.syncAll()
        assertThat(sync.drainBackoffMillis()).isNull()
    }

    @Test
    fun anEditQueuedWhileARefusedCreateIsDroppedGoesWithIt() = runTest {
        connect("c1", "calendar.a", s1)
        val lock = CalendarWriteLock()
        // Real threads for the write, so runTest can't skip virtual time while the test holds the lock.
        val sync = engineWith(setOf(w), toaster, io = Dispatchers.Default, timeoutMillis = PROVIDER_TIMEOUT_MS, lock = lock)
        queue(ChangeKind.CREATE, remoteId = null, draft = swimDraft("mia-id"), clientKey = key)
        w.failWith = WriteRejectedException("Calendar is full")
        a.failWith = UnreachableException("reads are down")
        // The editor holds the lock while it queues a change behind the create.
        val holding = CompletableDeferred<Unit>()
        val release = CompletableDeferred<Unit>()
        val editor = launch(Dispatchers.Default) { lock.withLock { holding.complete(Unit); release.await() } }
        withContext(Dispatchers.Default) { withTimeout(5_000) { holding.await() } }
        val pass = launch { sync.syncAll() }
        withContext(Dispatchers.Default) {
            withTimeout(5_000) { w.entered.await() }
            // Real time for a drop outside the lock to go ahead, so such a drop fails this test every time.
            delay(200)
        }
        queue(ChangeKind.UPDATE, remoteId = key, draft = swimDraft("sam-id"))
        release.complete(Unit)
        editor.join()
        pass.join()
        assertThat(store.pendingNow()).isEmpty()
        assertThat(toaster.messages).containsExactly("Couldn't save 2 changes to C1")
    }

    @Test
    fun aWriterThrowingAnErrorIsLoggedAndTheChangeIsTriedAgainLater() = runTest {
        connect("c1", "calendar.a", s1)
        val sync = writingEngine()
        queue(ChangeKind.DELETE, draft = null)
        w.failWith = StackOverflowError("writer recursed")
        a.failWith = UnreachableException("reads are down")
        sync.syncAll()
        // Queued with one attempt behind it, so this is the second: the next backoff step.
        assertThat(store.pendingNow().single().let { it.attempts to it.nextAttemptMillis }).isEqualTo(2 to now.toEpochMilli() + 60_000)
        assertThat(ShadowLog.getLogsForTag("CalendarWrites").map { it.throwable?.message }).contains("writer recursed")
    }
}
