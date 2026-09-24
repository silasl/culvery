package uk.co.siland.culvery.capability.calendar

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

    /**
     * The editor shares access's toaster, as the app shares one. [io] is the writer's context: tests that hold a
     * write open use Dispatchers.Default, so its 10 s timeout runs on real time and runTest can't skip past it
     * while the test waits for Room.
     */
    private fun TestScope.editor(access: TestAccess, io: CoroutineContext = EmptyCoroutineContext) = CalendarEditor(
        store = store,
        writers = setOf(writer),
        access = access.control,
        toaster = access.toasts,
        zone = HouseholdZone(household),
        clock = WallClock { testScheduler.currentTime },
        scope = backgroundScope,
        requestSync = { syncRequests++ },
        io = io,
        attemptMillis = WRITE_ATTEMPT_MS,
    )

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
        val noWriter = CalendarEditor(
            store, emptySet(), access.control, access.toasts, HouseholdZone(household),
            WallClock { testScheduler.currentTime }, backgroundScope, {}, EmptyCoroutineContext, WRITE_ATTEMPT_MS,
        )
        assertThat(noWriter.delete(ref("dinner"))).isEqualTo(EditResult.NotEditable)
    }
}
