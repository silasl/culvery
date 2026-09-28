package uk.co.siland.culvery.capability.calendar

import androidx.room.execSQL
import androidx.room.useWriterConnection
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.google.common.truth.Truth.assertThat
import java.time.LocalDate
import java.time.ZoneId
import kotlin.coroutines.CoroutineContext
import kotlin.coroutines.EmptyCoroutineContext
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.async
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import uk.co.siland.culvery.capability.calendar.db.CalendarDatabase
import uk.co.siland.culvery.core.access.PinReason
import uk.co.siland.culvery.core.household.HomeLocation
import uk.co.siland.culvery.core.household.HouseholdRepository
import uk.co.siland.culvery.core.household.PersonId
import uk.co.siland.culvery.core.household.db.HouseholdDatabase
import uk.co.siland.culvery.core.plugin.Connection
import uk.co.siland.culvery.core.plugin.WallClock

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(AndroidJUnit4::class)
class CalendarEditorTest {
    private lateinit var calendar: CalendarDatabase
    private lateinit var householdDb: HouseholdDatabase
    private lateinit var store: CalendarStore
    private lateinit var household: HouseholdRepository

    private val london = ZoneId.of("Europe/London")
    private val window = DateRange(LocalDate.of(2026, 9, 22), LocalDate.of(2026, 10, 8), london)
    private val family = CalendarSource("s-family", "Family calendar", writable = true)
    private val school = CalendarSource("s-school", "School terms", writable = false)
    private val writer = ScriptedWriter("calendar.a")
    private var syncRequests = 0
    private val lock = CalendarWriteLock()

    @Before
    fun setUp() = runTest {
        calendar = calendarDb()
        householdDb = householdDb()
        store = CalendarStore(calendar)
        household = HouseholdRepository(householdDb)
        household.setLocation(HomeLocation("London", 51.5, -0.12, "Europe/London"))
        store.addConnection(Connection("c1", "calendar.a", "Sample calendar", emptyMap()), listOf(family, school), emptyMap())
        store.setMaster("c1", "s-family")
    }

    @After
    fun tearDown() {
        calendar.close()
        householdDb.close()
    }

    private fun event(id: String, createdBy: String?, forPerson: String? = null, recurring: Boolean = false): RemoteEvent {
        val start = LocalDate.of(2026, 9, 23).atTime(19, 30).atZone(london).toInstant()
        return RemoteEvent(id, id, EventTime.Timed(start), EventTime.Timed(start.plusSeconds(5_400)), recurring, forPerson, createdBy)
    }

    private suspend fun put(vararg events: RemoteEvent, source: String = "s-family") =
        store.applySync("c1", source, window, SyncResult(events.toList(), emptyList(), null, fullReplace = true))

    private fun ref(id: String, source: String = "s-family") = EventRef("c1", source, id)

    private var keys = 0

    /**
     * The editor shares access's toaster, as the app shares one. [io] is the writer's context: tests that hold a
     * write open use Dispatchers.Default, so its 10 s timeout runs on real time and runTest can't skip past it
     * while the test waits for Room.
     */
    private fun TestScope.editor(access: TestAccess, io: CoroutineContext = EmptyCoroutineContext) = testEditor(
        store, setOf(writer), access.control, access.toasts, HouseholdZone(household), WallClock { testScheduler.currentTime },
        backgroundScope, requestSync = { syncRequests++ }, io = io, writeLock = lock,
    )

    /** As the sheet chooses them: "key-1", "key-2"… in the order Save is tapped. */
    private suspend fun CalendarEditor.create(draft: EventDraft): EditResult = create(draft, "key-${++keys}")

    /** The outbox drain as the sync loop runs it, [aheadMillis] after the test's clock, with this test's writer. */
    private fun TestScope.drain(access: TestAccess, aheadMillis: Long) = testSync(
        store, emptySet(), HouseholdZone(household), WallClock { testScheduler.currentTime + aheadMillis }, setOf(writer), access.toasts,
        writeLock = lock,
    )

    /** A one-hour event on Sunday 27 September at 18:00, as the add sheet builds it. */
    private fun draft(title: String, forPerson: String?, createdBy: String? = null): EventDraft {
        val start = LocalDate.of(2026, 9, 27).atTime(18, 0).atZone(london).toInstant()
        return EventDraft(title, EventTime.Timed(start), EventTime.Timed(start.plusSeconds(3_600)), forPerson, createdBy)
    }

    /** A change to an event, as the edit sheet sends it; its createdBy is ignored by the editor. */
    private fun edited(title: String, forPerson: String? = null): EventDraft {
        val start = LocalDate.of(2026, 9, 23).atTime(20, 0).atZone(london).toInstant()
        return EventDraft(title, EventTime.Timed(start), EventTime.Timed(start.plusSeconds(3_600)), forPerson, createdBy = "ignored")
    }

    /** Waits, in bounded real time, for the PIN pad: the editor reads Room on its own threads before asking. */
    private suspend fun waitForThePad(access: TestAccess) =
        withContext(Dispatchers.Default) { withTimeout(5_000) { while (access.requests.isEmpty()) delay(10) } }

    @Test
    fun adultCanDeleteAnyMasterEvent() = runTest {
        val access = testAccess(household)
        put(event("dinner", createdBy = access.alex.id.value))
        access.answer(TestAccess.SAM)
        assertThat(editor(access).delete(ref("dinner"))).isEqualTo(EditResult.Done)
        assertThat(writer.calls).containsExactly("delete:dinner")
        assertThat(store.eventNow(ref("dinner"))).isNull()
        assertThat(access.requests.single().reason).isEqualTo(PinReason.Delete)
        assertThat(syncRequests).isEqualTo(1)
        assertThat(access.toasts.messages).containsExactly(EVENT_DELETED)
    }

    @Test
    fun childCanDeleteTheirOwnEvent() = runTest {
        val access = testAccess(household)
        put(event("football", createdBy = access.mia.id.value))
        access.answer(TestAccess.MIA)
        assertThat(editor(access).delete(ref("football"))).isEqualTo(EditResult.Done)
    }

    @Test
    fun childCannotDeleteSomeoneElsesEventAndIsToldWhy() = runTest {
        val access = testAccess(household)
        put(event("dinner", createdBy = access.alex.id.value))
        access.answer(TestAccess.MIA)
        assertThat(editor(access).delete(ref("dinner"))).isEqualTo(EditResult.Cancelled)
        assertThat(access.toasts.messages).containsExactly("Mia can only change events they created.")
        assertThat(writer.calls).isEmpty()
        assertThat(store.eventNow(ref("dinner"))).isNotNull()
        assertThat(access.control.session.value).isNull()
    }

    @Test
    fun childCannotDeleteAPhoneAddedEvent() = runTest {
        val access = testAccess(household)
        put(event("plumber", createdBy = null))
        access.answer(TestAccess.MIA)
        assertThat(editor(access).delete(ref("plumber"))).isEqualTo(EditResult.Cancelled)
        assertThat(access.toasts.messages).containsExactly("Mia can only change events they created.")
    }

    @Test
    fun signedInChildIsRefusedWithoutAPinPad() = runTest {
        val access = testAccess(household)
        put(event("football", createdBy = access.mia.id.value), event("dinner", createdBy = access.alex.id.value))
        access.answer(TestAccess.MIA)
        val editor = editor(access)
        assertThat(editor.mayDelete(ref("football"))).isTrue()
        assertThat(editor.delete(ref("dinner"))).isEqualTo(EditResult.Cancelled)
        assertThat(access.requests).hasSize(1)
        assertThat(access.toasts.messages).containsExactly("Mia can only change events they created.")
        // Signed out by the refusal, so the next tap asks for a PIN.
        assertThat(access.control.session.value).isNull()
    }

    @Test
    fun adultCanAssignAPhoneAddedEventAndTheTagIsWritten() = runTest {
        val access = testAccess(household)
        put(event("plumber", createdBy = null))
        access.answer(TestAccess.ALEX)
        assertThat(editor(access).assign(ref("plumber"), access.sam.id)).isEqualTo(EditResult.Done)
        assertThat(writer.calls).containsExactly("update:plumber")
        val stored = store.eventNow(ref("plumber"))!!
        assertThat(stored.forPerson).isEqualTo(access.sam.id.value)
        assertThat(stored.createdBy).isNull()
        assertThat(access.requests.single().reason).isEqualTo(PinReason.Assign)
        assertThat(access.toasts.messages).isEmpty()
    }

    @Test
    fun aRejectedAssignChangesNothingAndSaysWhy() = runTest {
        val access = testAccess(household)
        put(event("plumber", createdBy = null))
        writer.failWith = WriteRejectedException("Event is locked")
        access.answer(TestAccess.ALEX)
        assertThat(editor(access).assign(ref("plumber"), access.sam.id)).isEqualTo(EditResult.Rejected("Event is locked"))
        assertThat(store.eventNow(ref("plumber"))!!.forPerson).isNull()
        assertThat(store.pendingNow()).isEmpty()
        assertThat(access.toasts.messages).containsExactly("Couldn't save to Sample calendar — Event is locked")
    }

    @Test
    fun childCannotAssignAndIsToldToAskAnAdult() = runTest {
        val access = testAccess(household)
        put(event("plumber", createdBy = null))
        access.answer(TestAccess.MIA)
        assertThat(editor(access).assign(ref("plumber"), access.mia.id)).isEqualTo(EditResult.Cancelled)
        assertThat(access.toasts.messages).containsExactly("Ask an adult to assign this event.")
        assertThat(writer.calls).isEmpty()
    }

    @Test
    fun cancellingThePinChangesNothing() = runTest {
        val access = testAccess(household)
        put(event("dinner", createdBy = access.alex.id.value))
        access.answer(null)
        assertThat(editor(access).delete(ref("dinner"))).isEqualTo(EditResult.Cancelled)
        assertThat(writer.calls).isEmpty()
        assertThat(syncRequests).isEqualTo(0)
    }

    @Test
    fun aRejectedDeleteChangesNothing() = runTest {
        val access = testAccess(household)
        put(event("dinner", createdBy = access.alex.id.value))
        writer.failWith = WriteRejectedException("Event is locked")
        access.answer(TestAccess.ALEX)
        assertThat(editor(access).delete(ref("dinner"))).isEqualTo(EditResult.Rejected("Event is locked"))
        assertThat(store.eventNow(ref("dinner"))).isNotNull()
        assertThat(store.pendingNow()).isEmpty()
        assertThat(access.toasts.messages).containsExactly("Couldn't save to Sample calendar — Event is locked")
    }

    /** Makes every [op] on [table] fail inside SQLite, as a full disk or a corrupt page would. */
    private suspend fun failEvery(op: String, table: String) = calendar.useWriterConnection {
        it.execSQL("CREATE TRIGGER fail_$table BEFORE $op ON $table BEGIN SELECT RAISE(ABORT, 'disk full'); END")
    }

    @Test
    fun aDeleteTheProviderAcceptedIsDoneEvenIfTheTabletCantStoreIt() = runTest {
        val access = testAccess(household)
        put(event("dinner", createdBy = access.alex.id.value))
        failEvery("DELETE", "event")
        access.answer(TestAccess.ALEX)
        assertThat(editor(access).delete(ref("dinner"))).isEqualTo(EditResult.Done)
        assertThat(writer.calls).containsExactly("delete:dinner")
        assertThat(access.toasts.messages).containsExactly(EVENT_DELETED)
        // The sync this asks for brings the mirror up to date.
        assertThat(syncRequests).isEqualTo(1)
    }

    @Test
    fun aDatabaseFailureWhileQueueingIsToldNotThrown() = runTest {
        val access = testAccess(household)
        put(event("plumber", createdBy = null))
        failEvery("INSERT", "outbox")
        writer.failWith = UnreachableException("offline")
        access.answer(TestAccess.ALEX)
        assertThat(editor(access).assign(ref("plumber"), access.sam.id)).isEqualTo(EditResult.Rejected(TRY_AGAIN))
        assertThat(store.pendingNow()).isEmpty()
        assertThat(access.toasts.messages).containsExactly("Couldn't save to Sample calendar — try again")
    }

    @Test
    fun offlineDeleteIsQueued() = runTest {
        val access = testAccess(household)
        put(event("dinner", createdBy = access.alex.id.value))
        writer.failWith = UnreachableException("offline")
        access.answer(TestAccess.ALEX)
        assertThat(editor(access).delete(ref("dinner"))).isEqualTo(EditResult.Queued)
        val queued = store.pendingNow().single()
        assertThat(listOf(queued.kind, queued.remoteId, queued.attempts)).containsExactly(ChangeKind.DELETE, "dinner", 1).inOrder()
        assertThat(queued.nextAttemptMillis).isEqualTo(testScheduler.currentTime + 30_000)
        assertThat(store.eventNow(ref("dinner"))).isNotNull()
        assertThat(syncRequests).isEqualTo(1)
        // The event hides at once (the repository's overlay), so the delete reads as done.
        assertThat(access.toasts.messages).containsExactly(EVENT_DELETED)
    }

    @Test
    fun aWriteSlowerThanTenSecondsIsQueued() = runTest {
        val access = testAccess(household)
        put(event("dinner", createdBy = access.alex.id.value))
        writer.gate = CompletableDeferred()
        access.answer(TestAccess.ALEX)
        assertThat(editor(access).delete(ref("dinner"))).isEqualTo(EditResult.Queued)
        assertThat(testScheduler.currentTime).isAtLeast(WRITE_ATTEMPT_MS)
    }

    @Test
    fun needsSignInQueuesTheAssignAndAsksForASync() = runTest {
        val access = testAccess(household)
        put(event("plumber", createdBy = null))
        writer.failWith = NeedsSignInException("expired")
        access.answer(TestAccess.ALEX)
        assertThat(editor(access).assign(ref("plumber"), access.sam.id)).isEqualTo(EditResult.Queued)
        assertThat(store.pendingNow().single().let { it.kind to it.draft?.forPerson }).isEqualTo(ChangeKind.ASSIGN to access.sam.id.value)
        // The pass this asks for flags the connection from its own read.
        assertThat(syncRequests).isEqualTo(1)
    }

    @Test
    fun withinTheSessionTheConfirmationDoesNotAskAgain() = runTest {
        val access = testAccess(household)
        put(event("dinner", createdBy = access.alex.id.value))
        access.answer(TestAccess.ALEX)
        val editor = editor(access)
        assertThat(editor.mayDelete(ref("dinner"))).isTrue()
        assertThat(editor.delete(ref("dinner"))).isEqualTo(EditResult.Done)
        assertThat(access.requests).hasSize(1)
    }

    @Test
    fun sessionExpiryBetweenGuardAndConfirmAsksForThePinAgain() = runTest {
        val access = testAccess(household)
        put(event("dinner", createdBy = access.alex.id.value))
        access.answer(TestAccess.ALEX, TestAccess.ALEX)
        val editor = editor(access)
        assertThat(editor.mayDelete(ref("dinner"))).isTrue()
        // What the 2-minute timer does when it fires (the timer itself is tested in DefaultAccessControlTest).
        access.control.lock()
        assertThat(editor.delete(ref("dinner"))).isEqualTo(EditResult.Done)
        assertThat(access.requests).hasSize(2)
    }

    @Test
    fun deletingAnEventAlreadyQueuedForDeleteDoesNotQueueItTwice() = runTest {
        val access = testAccess(household)
        put(event("dinner", createdBy = access.alex.id.value))
        writer.failWith = UnreachableException("offline")
        access.answer(TestAccess.ALEX)
        val editor = editor(access)
        assertThat(editor.delete(ref("dinner"))).isEqualTo(EditResult.Queued)
        assertThat(editor.delete(ref("dinner"))).isEqualTo(EditResult.Queued)
        assertThat(store.pendingNow()).hasSize(1)
        assertThat(writer.calls).hasSize(1)
    }

    @Test
    fun aChangeBehindAPendingOneIsQueuedNotWrittenDirectly() = runTest {
        val access = testAccess(household)
        put(event("plumber", createdBy = null))
        writer.failWith = UnreachableException("offline")
        access.answer(TestAccess.ALEX)
        val editor = editor(access)
        assertThat(editor.assign(ref("plumber"), access.sam.id)).isEqualTo(EditResult.Queued)
        writer.failWith = null
        assertThat(editor.assign(ref("plumber"), access.mia.id)).isEqualTo(EditResult.Queued)
        assertThat(writer.calls).containsExactly("update:plumber")
        val second = store.pendingNow()[1]
        assertThat(second.kind).isEqualTo(ChangeKind.ASSIGN)
        assertThat(second.draft?.forPerson).isEqualTo(access.mia.id.value)
        assertThat(second.attempts).isEqualTo(0)
        assertThat(second.nextAttemptMillis).isEqualTo(testScheduler.currentTime)
    }

    @Test
    fun closingTheSheetMidWriteStillFinishesTheWrite() = runTest {
        val access = testAccess(household)
        put(event("dinner", createdBy = access.alex.id.value))
        val gate = CompletableDeferred<Unit>()
        writer.gate = gate
        access.answer(TestAccess.ALEX)
        val editor = editor(access, io = Dispatchers.Default)
        val sheet = launch { editor.delete(ref("dinner")) }
        writer.entered.await()
        assertThat(writer.calls).containsExactly("delete:dinner")
        sheet.cancel()
        gate.complete(Unit)
        // Room applies the delete on its own thread; wait (in real time, bounded) for the mirror to show it.
        withContext(Dispatchers.Default) { withTimeout(5_000) { store.event(ref("dinner")).first { it == null } } }
        assertThat(store.pendingNow()).isEmpty()
        assertThat(writer.calls).containsExactly("delete:dinner")
    }

    @Test
    fun concurrentWritesToOneEventRunOneAtATime() = runTest {
        val access = testAccess(household)
        put(event("plumber", createdBy = null))
        writer.gate = CompletableDeferred()
        access.answer(TestAccess.ALEX)
        val editor = editor(access, io = Dispatchers.Default)
        val first = async { editor.assign(ref("plumber"), access.sam.id) }
        writer.entered.await()
        // A second sheet on the same event, on the session the first one started.
        val second = async { editor.assign(ref("plumber"), access.mia.id) }
        // Real time for the second write to reach the writer, if the lock didn't hold it back.
        withContext(Dispatchers.Default) { delay(200) }
        assertThat(writer.calls).containsExactly("update:plumber")
        writer.gate?.complete(Unit)
        assertThat(first.await()).isEqualTo(EditResult.Done)
        assertThat(second.await()).isEqualTo(EditResult.Done)
        assertThat(writer.calls).containsExactly("update:plumber", "update:plumber")
        assertThat(store.eventNow(ref("plumber"))!!.forPerson).isEqualTo(access.mia.id.value)
    }

    @Test
    fun recurringAndOtherCalendarEventsAreNotEditableAndAskForNoPin() = runTest {
        val access = testAccess(household)
        put(event("swim", createdBy = access.alex.id.value, recurring = true))
        put(event("inset", createdBy = null), source = "s-school")
        val editor = editor(access)
        assertThat(editor.delete(ref("swim"))).isEqualTo(EditResult.NotEditable)
        assertThat(editor.delete(ref("inset", "s-school"))).isEqualTo(EditResult.NotEditable)
        assertThat(editor.assign(ref("inset", "s-school"), access.sam.id)).isEqualTo(EditResult.NotEditable)
        assertThat(editor.mayDelete(ref("swim"))).isFalse()
        assertThat(access.requests).isEmpty()
    }

    @Test
    fun aMissingEventIsNotEditable() = runTest {
        val access = testAccess(household)
        assertThat(editor(access).delete(ref("nope"))).isEqualTo(EditResult.NotEditable)
    }

    @Test
    fun aMasterWithoutAWriterIsNotEditable() = runTest {
        val access = testAccess(household)
        put(event("dinner", createdBy = access.alex.id.value))
        val noWriter = testEditor(
            store, emptySet(), access.control, access.toasts, HouseholdZone(household), WallClock { testScheduler.currentTime }, backgroundScope,
        )
        assertThat(noWriter.delete(ref("dinner"))).isEqualTo(EditResult.NotEditable)
    }

    @Test
    fun adultCanAddAnEventForAnyoneAndIsRecordedAsItsCreator() = runTest {
        val access = testAccess(household)
        access.answer(TestAccess.SAM)
        val result = editor(access).create(draft("Sleepover", access.mia.id.value, createdBy = "someone-else"))
        assertThat(result).isEqualTo(EditResult.Done)
        assertThat(writer.calls).containsExactly("create:Sleepover")
        assertThat(writer.drafts.single().let { it.forPerson to it.createdBy }).isEqualTo(access.mia.id.value to access.sam.id.value)
        val stored = store.eventNow(ref("key-1"))!!
        assertThat(stored.title to stored.createdBy).isEqualTo("Sleepover" to access.sam.id.value)
        assertThat(access.requests.single().reason).isEqualTo(PinReason.Save)
        assertThat(access.toasts.messages).containsExactly(EVENT_ADDED)
        assertThat(syncRequests).isEqualTo(1)
    }

    @Test
    fun anAdminCanAddForFamily() = runTest {
        val access = testAccess(household)
        access.answer(TestAccess.ALEX)
        assertThat(editor(access).create(draft("Pizza night", PersonId.FAMILY.value))).isEqualTo(EditResult.Done)
    }

    @Test
    fun childCanAddAnEventForThemselves() = runTest {
        val access = testAccess(household)
        access.answer(TestAccess.MIA)
        assertThat(editor(access).create(draft("Sleepover", access.mia.id.value))).isEqualTo(EditResult.Done)
        assertThat(writer.drafts.single().createdBy).isEqualTo(access.mia.id.value)
    }

    @Test
    fun childIsRefusedAddingForFamilyAndIsToldWhy() = runTest {
        val access = testAccess(household)
        access.answer(TestAccess.MIA)
        assertThat(editor(access).create(draft("Pizza night", PersonId.FAMILY.value))).isEqualTo(EditResult.Cancelled)
        assertThat(access.toasts.messages).containsExactly("Mia can only add events for themselves.")
        assertThat(writer.calls).isEmpty()
        assertThat(store.pendingNow()).isEmpty()
    }

    @Test
    fun aSignedInChildIsRefusedAddingForOthersWithoutAPinPadAndIsSignedOut() = runTest {
        val access = testAccess(household)
        access.answer(TestAccess.MIA)
        val editor = editor(access)
        assertThat(editor.create(draft("Sleepover", access.mia.id.value))).isEqualTo(EditResult.Done)
        assertThat(editor.create(draft("Pizza night", PersonId.FAMILY.value))).isEqualTo(EditResult.Cancelled)
        assertThat(access.requests).hasSize(1)
        assertThat(access.toasts.messages).containsExactly(EVENT_ADDED, "Mia can only add events for themselves.").inOrder()
        assertThat(access.control.session.value).isNull()
        assertThat(writer.calls).containsExactly("create:Sleepover")
    }

    @Test
    fun aRefusedAddChangesNothingAndLeavesTheSayingToTheSheet() = runTest {
        val access = testAccess(household)
        writer.failWith = WriteRejectedException("Calendar is full")
        access.answer(TestAccess.ALEX)
        assertThat(editor(access).create(draft("Sleepover", PersonId.FAMILY.value))).isEqualTo(EditResult.Rejected("Calendar is full"))
        assertThat(store.eventNow(ref("key-1"))).isNull()
        assertThat(store.pendingNow()).isEmpty()
        assertThat(access.toasts.messages).isEmpty()
    }

    @Test
    fun aCreateAcceptedButNotStoredIsDoneAndMakesOneEvent() = runTest {
        val access = testAccess(household)
        // The provider accepts the create, then the tablet can't store it (a full disk).
        failEvery("INSERT", "event")
        access.answer(TestAccess.ALEX)
        assertThat(editor(access).create(draft("Sleepover", PersonId.FAMILY.value))).isEqualTo(EditResult.Done)
        assertThat(writer.created.keys).containsExactly("key-1")
        assertThat(store.pendingNow()).isEmpty()
        assertThat(access.toasts.messages).containsExactly(EVENT_ADDED)
        assertThat(syncRequests).isEqualTo(1)

        // Once the disk has room, the sync the editor asked for mirrors the event the provider holds.
        calendar.useWriterConnection { it.execSQL("DROP TRIGGER fail_event") }
        val reads = ScriptedProvider("calendar.a").apply {
            events = { source -> if (source.id == family.id) writer.created.values.toList() else emptyList() }
        }
        testSync(
            store, setOf(reads), HouseholdZone(household), WallClock { LocalDate.of(2026, 9, 23).atStartOfDay(london).toInstant().toEpochMilli() },
            setOf(writer), access.toasts, writeLock = lock,
        ).syncAll()
        assertThat(store.eventNow(ref("key-1"))!!.title).isEqualTo("Sleepover")
        assertThat(writer.calls).containsExactly("create:Sleepover")
    }

    @Test
    fun anOfflineAddIsQueuedWithItsKey() = runTest {
        val access = testAccess(household)
        writer.failWith = UnreachableException("offline")
        access.answer(TestAccess.ALEX)
        assertThat(editor(access).create(draft("Sleepover", PersonId.FAMILY.value))).isEqualTo(EditResult.Queued)
        val queued = store.pendingNow().single()
        assertThat(listOf(queued.kind, queued.clientKey, queued.remoteId, queued.attempts))
            .containsExactly(ChangeKind.CREATE, "key-1", null, 1).inOrder()
        assertThat(queued.ref).isEqualTo(ref("key-1"))
        assertThat(queued.draft?.createdBy).isEqualTo(access.alex.id.value)
        assertThat(queued.nextAttemptMillis).isEqualTo(testScheduler.currentTime + OUTBOX_BACKOFF_MS.first())
        assertThat(access.toasts.messages).containsExactly(EVENT_ADDED)
    }

    @Test
    fun aTimeoutAfterTheProviderCreatedTheEventMakesOneEventAfterTheDrain() = runTest {
        val access = testAccess(household)
        writer.loseNextReply = true
        access.answer(TestAccess.ALEX)
        assertThat(editor(access).create(draft("Sleepover", PersonId.FAMILY.value))).isEqualTo(EditResult.Queued)
        // The provider made the event before its reply was lost; the tablet only knows it timed out.
        assertThat(writer.created.keys).containsExactly("key-1")
        assertThat(store.pendingNow().single().clientKey).isEqualTo("key-1")

        drain(access, aheadMillis = OUTBOX_BACKOFF_MS.first()).syncAll()
        assertThat(writer.calls).containsExactly("create:Sleepover", "create:Sleepover")
        assertThat(writer.created.keys).containsExactly("key-1")
        assertThat(store.eventNow(ref("key-1"))!!.title).isEqualTo("Sleepover")
        assertThat(store.pendingNow()).isEmpty()
    }

    @Test
    fun aCreateUsesTheKeyTheSheetGivesIt() = runTest {
        val access = testAccess(household)
        access.answer(TestAccess.ALEX)
        assertThat(editor(access).create(draft("Sleepover", PersonId.FAMILY.value), "chosen-by-the-sheet")).isEqualTo(EditResult.Done)
        assertThat(writer.created.keys).containsExactly("chosen-by-the-sheet")
    }

    @Test
    fun closingTheSheetBeforeARefusalToastsIt() = runTest {
        val access = testAccess(household)
        writer.gate = CompletableDeferred()
        writer.failWith = WriteRejectedException("Calendar is full")
        access.answer(TestAccess.ALEX)
        val editor = editor(access, io = Dispatchers.Default)
        val sheet = launch { editor.create(draft("Sleepover", PersonId.FAMILY.value)) }
        writer.entered.await()
        sheet.cancel()
        writer.gate?.complete(Unit)
        // Nothing shows the failure card any more, so the editor says it.
        withContext(Dispatchers.Default) { withTimeout(5_000) { while (access.toasts.messages.isEmpty()) delay(10) } }
        assertThat(access.toasts.messages).containsExactly("Couldn't save to Sample calendar — Calendar is full")
    }

    @Test
    fun aMasterWithoutAWriterCanNotBeAddedToAndAsksForNoPin() = runTest {
        val access = testAccess(household)
        val noWriter = testEditor(
            store, emptySet(), access.control, access.toasts, HouseholdZone(household), WallClock { testScheduler.currentTime }, backgroundScope,
        )
        assertThat(noWriter.create(draft("Sleepover", PersonId.FAMILY.value))).isEqualTo(EditResult.NotEditable)
        assertThat(access.requests).isEmpty()
    }

    @Test
    fun adultCanChangeAnyMasterEventAndItsCreatorIsKept() = runTest {
        val access = testAccess(household)
        put(event("dinner", createdBy = access.alex.id.value))
        access.answer(TestAccess.SAM)
        assertThat(editor(access).update(ref("dinner"), edited("Dinner at Gran's", access.sam.id.value))).isEqualTo(EditResult.Done)
        assertThat(writer.calls).containsExactly("update:dinner")
        assertThat(writer.drafts.single().createdBy).isEqualTo(access.alex.id.value)
        val stored = store.eventNow(ref("dinner"))!!
        assertThat(listOf(stored.title, stored.forPerson, stored.createdBy))
            .containsExactly("Dinner at Gran's", access.sam.id.value, access.alex.id.value).inOrder()
        assertThat(access.requests.single().reason).isEqualTo(PinReason.Edit)
        assertThat(access.toasts.messages).containsExactly(CHANGES_SAVED)
        assertThat(syncRequests).isEqualTo(1)
    }

    @Test
    fun childCanChangeTheirOwnEvent() = runTest {
        val access = testAccess(household)
        put(event("football", createdBy = access.mia.id.value))
        access.answer(TestAccess.MIA)
        assertThat(editor(access).update(ref("football"), edited("Football at the park"))).isEqualTo(EditResult.Done)
    }

    @Test
    fun childCannotChangeSomeoneElsesEventAndIsToldWhy() = runTest {
        val access = testAccess(household)
        put(event("dinner", createdBy = access.alex.id.value))
        access.answer(TestAccess.MIA)
        assertThat(editor(access).update(ref("dinner"), edited("Pizza"))).isEqualTo(EditResult.Cancelled)
        assertThat(access.toasts.messages).containsExactly("Mia can only change events they created.")
        assertThat(writer.calls).isEmpty()
    }

    @Test
    fun childCannotMoveTheirOwnEventToSomeoneElseAndIsToldWhy() = runTest {
        val access = testAccess(household)
        put(event("football", createdBy = access.mia.id.value, forPerson = access.mia.id.value))
        access.answer(TestAccess.MIA)
        assertThat(editor(access).update(ref("football"), edited("Football", PersonId.FAMILY.value))).isEqualTo(EditResult.Cancelled)
        assertThat(access.toasts.messages).containsExactly("Mia can only add events for themselves.")
        assertThat(writer.calls).isEmpty()
    }

    @Test
    fun childCanChangeTheirOwnEventWhileWhoStaysAsItWas() = runTest {
        val access = testAccess(household)
        // An adult tagged Mia's event for Family; she can still rename it while Who stays on Family.
        put(event("football", createdBy = access.mia.id.value, forPerson = PersonId.FAMILY.value))
        access.answer(TestAccess.MIA)
        assertThat(editor(access).update(ref("football"), edited("Football at the park", PersonId.FAMILY.value)))
            .isEqualTo(EditResult.Done)
        assertThat(writer.drafts.single().forPerson).isEqualTo(PersonId.FAMILY.value)
    }

    @Test
    fun aRefusedChangeLeavesTheSayingToTheSheet() = runTest {
        val access = testAccess(household)
        put(event("dinner", createdBy = access.alex.id.value))
        writer.failWith = WriteRejectedException("Event is locked")
        access.answer(TestAccess.ALEX)
        assertThat(editor(access).update(ref("dinner"), edited("Pizza"))).isEqualTo(EditResult.Rejected("Event is locked"))
        assertThat(store.eventNow(ref("dinner"))!!.title).isEqualTo("dinner")
        assertThat(access.toasts.messages).isEmpty()
    }

    @Test
    fun closingTheSheetBeforeARefusedChangeToastsIt() = runTest {
        val access = testAccess(household)
        put(event("dinner", createdBy = access.alex.id.value))
        writer.gate = CompletableDeferred()
        writer.failWith = WriteRejectedException("Event is locked")
        access.answer(TestAccess.ALEX)
        val editor = editor(access, io = Dispatchers.Default)
        val sheet = launch { editor.update(ref("dinner"), edited("Pizza")) }
        writer.entered.await()
        sheet.cancel()
        writer.gate?.complete(Unit)
        withContext(Dispatchers.Default) { withTimeout(5_000) { while (access.toasts.messages.isEmpty()) delay(10) } }
        assertThat(access.toasts.messages).containsExactly("Couldn't save to Sample calendar — Event is locked")
    }

    @Test
    fun anOfflineChangeIsQueuedAndSaysSaved() = runTest {
        val access = testAccess(household)
        put(event("dinner", createdBy = access.alex.id.value))
        writer.failWith = UnreachableException("offline")
        access.answer(TestAccess.ALEX)
        assertThat(editor(access).update(ref("dinner"), edited("Pizza"))).isEqualTo(EditResult.Queued)
        val queued = store.pendingNow().single()
        assertThat(listOf(queued.kind, queued.remoteId, queued.draft?.title)).containsExactly(ChangeKind.UPDATE, "dinner", "Pizza").inOrder()
        assertThat(access.toasts.messages).containsExactly(CHANGES_SAVED)
    }

    @Test
    fun aChangeAndADeleteOfAQueuedCreateQueueBehindItAndDrainInOrder() = runTest {
        val access = testAccess(household)
        writer.failWith = UnreachableException("offline")
        access.answer(TestAccess.ALEX)
        val editor = editor(access)
        assertThat(editor.create(draft("Sleepover", access.mia.id.value))).isEqualTo(EditResult.Queued)
        writer.failWith = null
        val created = ref("key-1")
        assertThat(editor.mayDelete(created)).isTrue()
        assertThat(editor.update(created, edited("Sleepover at Ava's", access.mia.id.value))).isEqualTo(EditResult.Queued)
        assertThat(editor.delete(created)).isEqualTo(EditResult.Queued)
        // Nothing goes past the queued create: it would reach the provider before the event exists.
        assertThat(writer.calls).containsExactly("create:Sleepover")
        val queued = store.pendingNow()
        assertThat(queued.map { it.kind }).containsExactly(ChangeKind.CREATE, ChangeKind.UPDATE, ChangeKind.DELETE).inOrder()
        assertThat(queued.map { it.ref }).containsExactly(created, created, created)
        assertThat(queued[1].draft?.createdBy).isEqualTo(access.alex.id.value)
        assertThat(access.requests).hasSize(1)
        assertThat(access.toasts.messages).containsExactly(EVENT_ADDED, CHANGES_SAVED, EVENT_DELETED).inOrder()

        drain(access, aheadMillis = OUTBOX_BACKOFF_MS.first()).syncAll()
        assertThat(writer.calls)
            .containsExactly("create:Sleepover", "create:Sleepover", "update:key-1", "delete:key-1").inOrder()
        assertThat(store.pendingNow()).isEmpty()
        assertThat(store.eventNow(created)).isNull()
    }

    @Test
    fun theCreatorIsCheckedAgainAfterThePinPadForAChange() = runTest {
        val access = testAccess(household)
        put(event("football", createdBy = access.mia.id.value))
        val editor = editor(access)
        val result = async { editor.update(ref("football"), edited("Football at the park")) }
        waitForThePad(access)
        // While the pad is up, a sync brings the event back as someone else's.
        put(event("football", createdBy = access.alex.id.value))
        access.prompt.submit(TestAccess.MIA)
        assertThat(result.await()).isEqualTo(EditResult.Cancelled)
        assertThat(access.toasts.messages).containsExactly("Mia can only change events they created.")
        assertThat(writer.calls).isEmpty()
        // Signed out, as a refusal on the session shortcut would be (2b-1 U2).
        assertThat(access.control.session.value).isNull()
    }

    @Test
    fun theCreatorIsCheckedAgainAfterThePinPadForADelete() = runTest {
        val access = testAccess(household)
        put(event("football", createdBy = access.mia.id.value))
        val editor = editor(access)
        val result = async { editor.delete(ref("football")) }
        waitForThePad(access)
        put(event("football", createdBy = access.alex.id.value))
        access.prompt.submit(TestAccess.MIA)
        assertThat(result.await()).isEqualTo(EditResult.Cancelled)
        assertThat(access.toasts.messages).containsExactly("Mia can only change events they created.")
        assertThat(writer.calls).isEmpty()
        assertThat(store.eventNow(ref("football"))).isNotNull()
        assertThat(access.control.session.value).isNull()
    }

    @Test
    fun anEventQueuedForDeletionOrGoneCanNotBeChanged() = runTest {
        val access = testAccess(household)
        put(event("dinner", createdBy = access.alex.id.value))
        writer.failWith = UnreachableException("offline")
        access.answer(TestAccess.ALEX)
        val editor = editor(access)
        assertThat(editor.delete(ref("dinner"))).isEqualTo(EditResult.Queued)
        assertThat(editor.update(ref("dinner"), edited("Pizza"))).isEqualTo(EditResult.NotEditable)
        assertThat(editor.update(ref("nope"), edited("Pizza"))).isEqualTo(EditResult.NotEditable)
        assertThat(access.requests).hasSize(1)
    }
}
