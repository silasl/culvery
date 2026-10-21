package uk.co.siland.culvery.capability.calendar

import androidx.room.execSQL
import androidx.room.useWriterConnection
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.google.common.truth.Truth.assertThat
import java.time.LocalDate
import java.time.LocalTime
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
import uk.co.siland.culvery.core.access.PermissionSource
import uk.co.siland.culvery.core.access.PinReason
import uk.co.siland.culvery.core.household.HomeLocation
import uk.co.siland.culvery.core.household.HouseholdRepository
import uk.co.siland.culvery.core.household.HouseholdZone
import uk.co.siland.culvery.core.household.Person
import uk.co.siland.culvery.core.household.PinChange
import uk.co.siland.culvery.core.household.Role
import uk.co.siland.culvery.core.household.PersonId
import uk.co.siland.culvery.core.household.db.HouseholdDatabase
import uk.co.siland.culvery.core.plugin.Connection
import uk.co.siland.culvery.core.plugin.ConnectionHealth
import uk.co.siland.culvery.core.plugin.WallClock

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(AndroidJUnit4::class)
class CalendarEditorTest {
    private lateinit var calendar: CalendarDatabase
    private lateinit var householdDb: HouseholdDatabase
    private lateinit var store: CalendarStore
    private lateinit var household: HouseholdRepository

    private val london = ZoneId.of("Europe/London")

    /** The editor's and the drain's clock start here: Wednesday 23 September, 09:00 in London. */
    private val opened = LocalDate.of(2026, 9, 23).atTime(9, 0).atZone(london).toInstant().toEpochMilli()
    private val window = DateRange(LocalDate.of(2026, 9, 22), LocalDate.of(2026, 10, 8), london)
    private val family = CalendarSource("s-family", "Family calendar", writable = true)
    private val school = CalendarSource("s-school", "School terms", writable = false)
    private val writer = ScriptedWriter("calendar.a")
    private var syncRequests = 0
    private val lock = CalendarWriteLock()
    private val undone = UndoneKeys()

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
        store, setOf(writer), access.control, access.toasts, HouseholdZone(household), WallClock { opened + testScheduler.currentTime },
        backgroundScope, requestSync = { syncRequests++ }, io = io, writeLock = lock, personOf = household::person,
        order = { household.people.first().map { it.id } }, undone = undone,
    )

    /** As the sheet chooses them: "key-1", "key-2"… in the order Save is tapped. */
    private suspend fun CalendarEditor.create(draft: EventDraft): EditResult = create(draft, "key-${++keys}")

    /** A change to everything the sheet shows, as 2b-2's sheet sent it. */
    private suspend fun CalendarEditor.update(ref: EventRef, draft: EventDraft): EditResult = update(ref, draft, EventField.entries.toSet())

    /** The outbox drain as the sync loop runs it, [aheadMillis] after the test's clock, with this test's writer. */
    private fun TestScope.drain(access: TestAccess, aheadMillis: Long, io: CoroutineContext = EmptyCoroutineContext) = testSync(
        store, emptySet(), HouseholdZone(household), WallClock { opened + testScheduler.currentTime + aheadMillis }, setOf(writer), access.toasts,
        io = io, writeLock = lock, undone = undone,
    )

    /** A one-hour event on Sunday 27 September at 18:00, as the add sheet builds it. */
    private fun draft(title: String, forPerson: String?, createdBy: String? = null): EventDraft {
        val start = LocalDate.of(2026, 9, 27).atTime(18, 0).atZone(london).toInstant()
        return EventDraft(title, EventTime.Timed(start), EventTime.Timed(start.plusSeconds(3_600)), forPerson, createdBy)
    }

    /** A one-hour event at 18:00 London on [day]. */
    private fun draftOn(day: LocalDate, title: String): EventDraft {
        val start = day.atTime(18, 0).atZone(london).toInstant()
        return EventDraft(title, EventTime.Timed(start), EventTime.Timed(start.plusSeconds(3_600)), PersonId.FAMILY.value, null)
    }

    /** 4c §6.6: an event after the last day the Calendar tab shows names its day; one on that day doesn't. */
    @Test
    fun anEventAddedBeyondTheFurthestWeekNamesItsDay() = runTest {
        val access = testAccess(household)
        access.answer(TestAccess.ALEX)
        val editor = editor(access)
        val last = lastShownDay(LocalDate.of(2026, 9, 23))
        assertThat(editor.create(draftOn(last, "Last shown"))).isEqualTo(EditResult.Done)
        assertThat(editor.create(draftOn(last.plusDays(1), "Beyond"))).isEqualTo(EditResult.Done)
        assertThat(access.toasts.messages).containsExactly(addedLine("Last shown", null, null), addedLine("Beyond", null, last.plusDays(1))).inOrder()
    }

    @Test
    fun aMultiDayAllDayEventIsWrittenWithItsExclusiveEnd() = runTest {
        val access = testAccess(household)
        access.answer(TestAccess.ALEX)
        val monday = LocalDate.of(2026, 10, 19)
        val form = EditorForm(EditorForm.Mode.New, monday, LocalTime.of(9, 0), london, null, null, listOf(access.alex.id), null)
        form.updateTitle("Camping")
        form.toggleWho(access.alex.id)
        form.setMultiDay(true)
        form.tapDay(monday)
        form.tapDay(monday.plusDays(2))
        assertThat(editor(access).create(form.draft(createdBy = access.alex.id.value))).isEqualTo(EditResult.Done)
        val sent = writer.drafts.single()
        assertThat(sent.start).isEqualTo(EventTime.AllDay(monday))
        assertThat(sent.end).isEqualTo(EventTime.AllDay(monday.plusDays(3)))
    }

    /** A change to an event, as the edit sheet sends it; its createdBy is ignored by the editor. */
    private fun edited(title: String, forPerson: String? = null): EventDraft {
        val start = LocalDate.of(2026, 9, 23).atTime(20, 0).atZone(london).toInstant()
        return EventDraft(title, EventTime.Timed(start), EventTime.Timed(start.plusSeconds(3_600)), forPerson, createdBy = "ignored")
    }

    /** Waits, in bounded real time, for the PIN pad: the editor reads Room on its own threads before asking. */
    private suspend fun waitForThePad(access: TestAccess) =
        withContext(Dispatchers.Default) { withTimeout(5_000) { while (access.requests.isEmpty()) delay(10) } }

    /** 4e: the week's Update now asks for a pass at once, without a PIN (it changes nothing). */
    @Test
    fun updateNowAsksForASyncWithoutAPin() = runTest {
        val access = testAccess(household)
        editor(access).updateNow()
        assertThat(syncRequests).isEqualTo(1)
        assertThat(access.requests).isEmpty()
    }

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
        assertThat(access.toasts.urgent).isEqualTo(access.toasts.messages)
        // Signed out by the refusal, so the next tap asks for a PIN.
        assertThat(access.control.session.value).isNull()
    }

    @Test
    fun adultCanAssignAPhoneAddedEventAndTheTagIsWritten() = runTest {
        val access = testAccess(household)
        put(event("plumber", createdBy = null))
        access.answer(TestAccess.ALEX)
        assertThat(editor(access).assign(ref("plumber"), listOf(access.sam.id))).isEqualTo(EditResult.Done)
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
        writer.failWith = WriteRejectedException("event is locked")
        access.answer(TestAccess.ALEX)
        assertThat(editor(access).assign(ref("plumber"), listOf(access.sam.id))).isEqualTo(EditResult.Rejected(WriteProblem.Refused("event is locked")))
        assertThat(store.eventNow(ref("plumber"))!!.forPerson).isNull()
        assertThat(store.pendingNow()).isEmpty()
        assertThat(access.toasts.messages).containsExactly(couldNotSave("Sample calendar", WriteProblem.Refused("event is locked")))
    }

    @Test
    fun childCannotAssignAndIsToldToAskAnAdult() = runTest {
        val access = testAccess(household)
        put(event("plumber", createdBy = null))
        access.answer(TestAccess.MIA)
        assertThat(editor(access).assign(ref("plumber"), listOf(access.mia.id))).isEqualTo(EditResult.Cancelled)
        assertThat(access.toasts.messages).containsExactly("Ask a grown-up to choose who this is for.")
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
        writer.failWith = WriteRejectedException("event is locked")
        access.answer(TestAccess.ALEX)
        assertThat(editor(access).delete(ref("dinner"))).isEqualTo(EditResult.Rejected(WriteProblem.Refused("event is locked")))
        assertThat(store.eventNow(ref("dinner"))).isNotNull()
        assertThat(store.pendingNow()).isEmpty()
        assertThat(access.toasts.messages).containsExactly(couldNotDelete("Sample calendar", WriteProblem.Refused("event is locked")))
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
        assertThat(editor(access).assign(ref("plumber"), listOf(access.sam.id))).isEqualTo(EditResult.Rejected(WriteProblem.Tablet))
        assertThat(store.pendingNow()).isEmpty()
        assertThat(access.toasts.messages).containsExactly(couldNotSave("Sample calendar", WriteProblem.Tablet))
        assertThat(access.toasts.urgent).isEqualTo(access.toasts.messages)
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
        assertThat(queued.nextAttemptMillis).isEqualTo(opened + testScheduler.currentTime + 30_000)
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
        assertThat(editor(access).assign(ref("plumber"), listOf(access.sam.id))).isEqualTo(EditResult.Queued)
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
        assertThat(editor.assign(ref("plumber"), listOf(access.sam.id))).isEqualTo(EditResult.Queued)
        writer.failWith = null
        assertThat(editor.assign(ref("plumber"), listOf(access.mia.id))).isEqualTo(EditResult.Queued)
        assertThat(writer.calls).containsExactly("update:plumber")
        val second = store.pendingNow()[1]
        assertThat(second.kind).isEqualTo(ChangeKind.ASSIGN)
        assertThat(second.draft?.forPerson).isEqualTo(access.mia.id.value)
        assertThat(second.attempts).isEqualTo(0)
        assertThat(second.nextAttemptMillis).isEqualTo(opened + testScheduler.currentTime)
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
        val first = async { editor.assign(ref("plumber"), listOf(access.sam.id)) }
        writer.entered.await()
        // A second sheet on the same event, on the session the first one started.
        val second = async { editor.assign(ref("plumber"), listOf(access.mia.id)) }
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
        assertThat(editor.assign(ref("inset", "s-school"), listOf(access.sam.id))).isEqualTo(EditResult.NotEditable)
        assertThat(editor.mayDelete(ref("swim"))).isFalse()
        assertThat(access.requests).isEmpty()
    }

    /** Two Wednesdays of a weekly swim, occurrences of the series "swim". */
    private suspend fun putWeeklySwim(forPerson: String? = null) {
        val first = event("swim_1", createdBy = null, forPerson, recurring = true).copy(seriesId = "swim")
        fun EventTime.nextWeek() = EventTime.Timed((this as EventTime.Timed).instant.plusSeconds(7 * 86_400L))
        put(first, first.copy(remoteId = "swim_2", start = first.start.nextWeek(), end = first.end.nextWeek()))
    }

    @Test
    fun adultCanChooseWhoARepeatingEventIsForAndEveryTimeItRepeatsChanges() = runTest {
        val access = testAccess(household)
        putWeeklySwim(forPerson = access.alex.id.value)
        access.answer(TestAccess.ALEX)
        assertThat(editor(access).assign(ref("swim_1"), listOf(access.sam.id))).isEqualTo(EditResult.Done)
        assertThat(writer.calls).containsExactly("assignSeries:swim")
        assertThat(writer.drafts.single().let { it.forPerson to it.forPersonColor }).isEqualTo(access.sam.id.value to access.sam.color)
        assertThat(access.requests.single().reason).isEqualTo(PinReason.Assign)
        assertThat(syncRequests).isEqualTo(1)
        assertThat(listOf("swim_1", "swim_2").map { store.eventNow(ref(it))!!.forPerson }).containsExactly(access.sam.id.value, access.sam.id.value)
        assertThat(access.toasts.messages).isEmpty()
    }

    @Test
    fun assigningASetToASeriesTagsEveryOccurrence() = runTest {
        val access = testAccess(household)
        putWeeklySwim(forPerson = access.alex.id.value)
        access.answer(TestAccess.ALEX)
        assertThat(editor(access).assign(ref("swim_1"), listOf(access.mia.id, access.sam.id))).isEqualTo(EditResult.Done)
        val tag = "${access.sam.id.value},${access.mia.id.value}"
        assertThat(writer.calls).containsExactly("assignSeries:swim")
        assertThat(writer.drafts.single().let { it.forPerson to it.forPersonColor }).isEqualTo(tag to null)
        assertThat(listOf("swim_1", "swim_2").map { store.eventNow(ref(it))!!.forPerson }).containsExactly(tag, tag)
    }

    @Test
    fun childCannotChooseWhoARepeatingEventIsFor() = runTest {
        val access = testAccess(household)
        putWeeklySwim()
        access.answer(TestAccess.MIA)
        assertThat(editor(access).assign(ref("swim_1"), listOf(access.mia.id))).isEqualTo(EditResult.Cancelled)
        assertThat(access.toasts.messages).containsExactly("Ask a grown-up to choose who this is for.")
        assertThat(writer.calls).isEmpty()
    }

    @Test
    fun anOfflineChoiceForARepeatingEventIsQueuedForTheSeriesAndSentByTheDrain() = runTest {
        val access = testAccess(household)
        putWeeklySwim()
        writer.failWith = UnreachableException("offline")
        access.answer(TestAccess.ALEX)
        assertThat(editor(access).assign(ref("swim_2"), listOf(access.sam.id))).isEqualTo(EditResult.Queued)
        val queued = store.pendingNow().single()
        assertThat(listOf(queued.kind, queued.remoteId, queued.draft?.forPerson)).containsExactly(ChangeKind.ASSIGN_SERIES, "swim", access.sam.id.value).inOrder()
        assertThat(syncRequests).isEqualTo(1)
        writer.failWith = null
        drain(access, aheadMillis = 60_000).syncAll()
        assertThat(writer.calls).containsExactly("assignSeries:swim", "assignSeries:swim")
        assertThat(store.pendingNow()).isEmpty()
        assertThat(listOf("swim_1", "swim_2").map { store.eventNow(ref(it))!!.forPerson }).containsExactly(access.sam.id.value, access.sam.id.value)
    }

    @Test
    fun aRepeatingEventStillCanNotBeChangedOrDeleted() = runTest {
        val access = testAccess(household)
        putWeeklySwim()
        val editor = editor(access)
        assertThat(editor.update(ref("swim_1"), draft("Swim club", null))).isEqualTo(EditResult.NotEditable)
        assertThat(editor.delete(ref("swim_1"))).isEqualTo(EditResult.NotEditable)
        assertThat(editor.mayDelete(ref("swim_1"))).isFalse()
        assertThat(access.requests).isEmpty()
        assertThat(writer.calls).isEmpty()
    }

    @Test
    fun aRepeatingEventWhoseSeriesIsNotKnownYetCanNotBeAssigned() = runTest {
        val access = testAccess(household)
        put(event("swim", createdBy = null, recurring = true))
        assertThat(editor(access).assign(ref("swim"), listOf(access.sam.id))).isEqualTo(EditResult.NotEditable)
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
        assertThat(access.toasts.messages).containsExactly(addedLine("Sleepover", null, null))
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
        assertThat(access.toasts.messages).containsExactly("Mia can add events for Mia only.")
        assertThat(writer.calls).isEmpty()
        assertThat(store.pendingNow()).isEmpty()
    }

    @Test
    fun aChildCannotSaveARepeatingEvent() = runTest {
        val access = testAccess(household)
        access.answer(TestAccess.MIA)
        val series = draft("Swimming", access.mia.id.value).copy(recurrence = Recurrence("RRULE:FREQ=WEEKLY;INTERVAL=1", london))
        assertThat(editor(access).create(series, "key-series")).isEqualTo(EditResult.Cancelled)
        assertThat(access.toasts.messages).containsExactly(CANT_REPEAT_FOR_CHILD)
        assertThat(writer.calls).isEmpty()
        assertThat(store.pendingNow()).isEmpty()
    }

    /** Pin: an adult's repeating event is written. */
    @Test
    fun anAdultMaySaveARepeatingEvent() = runTest {
        val access = testAccess(household)
        access.answer(TestAccess.ALEX)
        val series = draft("Swimming", access.mia.id.value).copy(recurrence = Recurrence("RRULE:FREQ=WEEKLY;INTERVAL=1", london))
        assertThat(editor(access).create(series, "key-series")).isEqualTo(EditResult.Done)
        assertThat(writer.drafts.single().recurrence).isEqualTo(series.recurrence)
    }

    @Test
    fun aChildWhoseSessionEndedCannotSaveForSomeoneElse() = runTest {
        val access = testAccess(household)
        val editor = editor(access)
        access.answer(TestAccess.MIA, TestAccess.MIA)
        assertThat(editor.create(draft("Sleepover", access.mia.id.value), "key-1")).isEqualTo(EditResult.Done)
        access.control.lock()
        assertThat(access.control.session.value).isNull()
        assertThat(editor.create(draft("Sleepover", access.alex.id.value), "key-2")).isEqualTo(EditResult.Cancelled)
        assertThat(access.requests).hasSize(2)
        assertThat(access.toasts.messages).containsExactly(addedLine("Sleepover", null, null), "Mia can add events for Mia only.").inOrder()
        assertThat(writer.calls).containsExactly("create:Sleepover")
        assertThat(store.eventNow(ref("key-2"))).isNull()
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
        assertThat(access.toasts.messages).containsExactly(addedLine("Sleepover", null, null), "Mia can add events for Mia only.").inOrder()
        assertThat(access.control.session.value).isNull()
        assertThat(writer.calls).containsExactly("create:Sleepover")
    }

    @Test
    fun aRefusedAddChangesNothingAndLeavesTheSayingToTheSheet() = runTest {
        val access = testAccess(household)
        writer.failWith = WriteRejectedException("calendar is full")
        access.answer(TestAccess.ALEX)
        assertThat(editor(access).create(draft("Sleepover", PersonId.FAMILY.value))).isEqualTo(EditResult.Rejected(WriteProblem.Refused("calendar is full")))
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
        assertThat(access.toasts.messages).containsExactly(addedLine("Sleepover", null, null))
        assertThat(syncRequests).isEqualTo(1)

        // Once the disk has room, the sync the editor asked for mirrors the event the provider holds.
        calendar.useWriterConnection { it.execSQL("DROP TRIGGER fail_event") }
        val reads = ScriptedProvider("calendar.a", sourceList = listOf(family.copy(primary = true), school)).apply {
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
        assertThat(queued.nextAttemptMillis).isEqualTo(opened + testScheduler.currentTime + OUTBOX_BACKOFF_MS.first())
        assertThat(access.toasts.messages).containsExactly(addedLine("Sleepover", null, null))
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
        writer.failWith = WriteRejectedException("calendar is full")
        access.answer(TestAccess.ALEX)
        val editor = editor(access, io = Dispatchers.Default)
        val sheet = launch { editor.create(draft("Sleepover", PersonId.FAMILY.value)) }
        writer.entered.await()
        sheet.cancel()
        writer.gate?.complete(Unit)
        // Nothing shows the failure card any more, so the editor says it.
        withContext(Dispatchers.Default) { withTimeout(5_000) { while (access.toasts.messages.isEmpty()) delay(10) } }
        assertThat(access.toasts.messages).containsExactly(couldNotSave("Sample calendar", WriteProblem.Refused("calendar is full")))
        assertThat(access.toasts.urgent).isEqualTo(access.toasts.messages)
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
        assertThat(access.toasts.messages).containsExactly("Mia can add events for Mia only.")
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
        writer.failWith = WriteRejectedException("event is locked")
        access.answer(TestAccess.ALEX)
        assertThat(editor(access).update(ref("dinner"), edited("Pizza"))).isEqualTo(EditResult.Rejected(WriteProblem.Refused("event is locked")))
        assertThat(store.eventNow(ref("dinner"))!!.title).isEqualTo("dinner")
        assertThat(access.toasts.messages).isEmpty()
    }

    @Test
    fun closingTheSheetBeforeARefusedChangeToastsIt() = runTest {
        val access = testAccess(household)
        put(event("dinner", createdBy = access.alex.id.value))
        writer.gate = CompletableDeferred()
        writer.failWith = WriteRejectedException("event is locked")
        access.answer(TestAccess.ALEX)
        val editor = editor(access, io = Dispatchers.Default)
        val sheet = launch { editor.update(ref("dinner"), edited("Pizza")) }
        writer.entered.await()
        sheet.cancel()
        writer.gate?.complete(Unit)
        withContext(Dispatchers.Default) { withTimeout(5_000) { while (access.toasts.messages.isEmpty()) delay(10) } }
        assertThat(access.toasts.messages).containsExactly(couldNotSave("Sample calendar", WriteProblem.Refused("event is locked")))
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
        assertThat(access.toasts.messages).containsExactly(addedLine("Sleepover", null, null), CHANGES_SAVED, EVENT_DELETED).inOrder()

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

    /** The dinner as a phone left it: an hour later than [event] puts it. */
    private fun movedOnAPhone(createdBy: String?): RemoteEvent {
        val later = LocalDate.of(2026, 9, 23).atTime(20, 30).atZone(london).toInstant()
        return RemoteEvent("dinner", "dinner", EventTime.Timed(later), EventTime.Timed(later.plusSeconds(5_400)), false, null, createdBy)
    }

    @Test
    fun anEditSendsOnlyItsFieldsLaidOverTheEventAsItIsNow() = runTest {
        val access = testAccess(household)
        // The sheet opened on the event at 19:30; a phone moved it while the sheet was open.
        put(movedOnAPhone(createdBy = access.alex.id.value))
        access.answer(TestAccess.ALEX)
        assertThat(editor(access).update(ref("dinner"), edited("Dinner at Gran's"), setOf(EventField.TITLE))).isEqualTo(EditResult.Done)
        assertThat(writer.fieldSets.single()).containsExactly(EventField.TITLE)
        val sent = writer.drafts.single()
        assertThat(listOf(sent.title, sent.start, sent.forPerson, sent.createdBy))
            .containsExactly("Dinner at Gran's", movedOnAPhone(null).start, null, access.alex.id.value).inOrder()
    }

    @Test
    fun anOfflineTitleEditIsSentAsATitleOnlyUpdateAfterAPhoneMovedTheEvent() = runTest {
        val access = testAccess(household)
        put(event("dinner", createdBy = access.alex.id.value))
        writer.failWith = UnreachableException("offline")
        access.answer(TestAccess.ALEX)
        assertThat(editor(access).update(ref("dinner"), edited("Dinner at Gran's"), setOf(EventField.TITLE))).isEqualTo(EditResult.Queued)
        assertThat(store.pendingNow().single().fields).containsExactly(EventField.TITLE)
        // Before the tablet is back online, a phone moves the event an hour later.
        put(movedOnAPhone(createdBy = access.alex.id.value))
        writer.failWith = null
        drain(access, aheadMillis = OUTBOX_BACKOFF_MS.first()).syncAll()
        // Only the title goes: the provider keeps the phone's new time.
        assertThat(writer.fieldSets.last()).containsExactly(EventField.TITLE)
        assertThat(writer.drafts.last().title).isEqualTo("Dinner at Gran's")
        assertThat(store.pendingNow()).isEmpty()
    }

    @Test
    fun aChangeOfWhoIsARetagOnlyWhenWhoIsTouched() = runTest {
        val access = testAccess(household)
        put(event("football", createdBy = access.mia.id.value, forPerson = access.mia.id.value))
        access.answer(TestAccess.MIA)
        // The sheet's draft names Family, but Who isn't among the touched fields: Mia may change her own title.
        assertThat(editor(access).update(ref("football"), edited("Football at the park", PersonId.FAMILY.value), setOf(EventField.TITLE)))
            .isEqualTo(EditResult.Done)
        assertThat(writer.drafts.single().forPerson).isEqualTo(access.mia.id.value)
    }

    @Test
    fun newEventsAndAssignsCarryThePersonsColourAndFamilyCarriesNone() = runTest {
        val access = testAccess(household)
        put(event("plumber", createdBy = null))
        access.answer(TestAccess.ALEX)
        val editor = editor(access)
        editor.create(draft("Sleepover", access.mia.id.value))
        editor.create(draft("Pizza night", PersonId.FAMILY.value))
        editor.assign(ref("plumber"), listOf(access.sam.id))
        assertThat(writer.drafts.map { it.forPersonColor }).containsExactly(access.mia.color, null, access.sam.color).inOrder()
    }

    /** Pin (9 §3.3): a shared event has no colour of its own; one person's still carries theirs. */
    @Test
    fun aSharedEventGetsNoColour() = runTest {
        val access = testAccess(household)
        access.answer(TestAccess.ALEX)
        val editor = editor(access)
        editor.create(draft("Cinema", "${access.sam.id.value},${access.mia.id.value}"))
        editor.create(draft("Football", access.sam.id.value))
        assertThat(writer.drafts.map { it.forPersonColor }).containsExactly(null, access.sam.color).inOrder()
    }

    /** Pin (9 §3.3): an adult may add for any set of people. */
    @Test
    fun anAdultMayAddForAnySet() = runTest {
        val access = testAccess(household)
        access.answer(TestAccess.ALEX)
        val both = "${access.sam.id.value},${access.mia.id.value}"
        assertThat(editor(access).create(draft("Cinema", both))).isEqualTo(EditResult.Done)
        assertThat(writer.calls).containsExactly("create:Cinema")
        assertThat(writer.drafts.single().forPerson).isEqualTo(both)
    }

    /** Pin (9 §3.3): a child's set must be exactly them, so one that includes them is still refused. */
    @Test
    fun aChildCannotAddASharedEventEvenWithThemselvesInIt() = runTest {
        val access = testAccess(household)
        access.answer(TestAccess.MIA)
        val both = "${access.sam.id.value},${access.mia.id.value}"
        assertThat(editor(access).create(draft("Cinema", both))).isEqualTo(EditResult.Cancelled)
        assertThat(access.toasts.messages).containsExactly("Mia can add events for Mia only.")
        assertThat(writer.calls).isEmpty()
        assertThat(store.pendingNow()).isEmpty()
    }

    /** Pin. */
    @Test
    fun aChildMayAddForThemselvesAlone() = runTest {
        val access = testAccess(household)
        access.answer(TestAccess.MIA)
        assertThat(editor(access).create(draft("Swimming", access.mia.id.value))).isEqualTo(EditResult.Done)
        assertThat(writer.drafts.single().forPerson).isEqualTo(access.mia.id.value)
    }

    /** Pin (9 §3.3): retagging her own event to a set that includes her is still a retag to others. */
    @Test
    fun aChildCannotRetagToASet() = runTest {
        val access = testAccess(household)
        put(event("football", createdBy = access.mia.id.value, forPerson = access.mia.id.value))
        access.answer(TestAccess.MIA)
        val both = "${access.sam.id.value},${access.mia.id.value}"
        val result = editor(access).update(ref("football"), edited("Football", both), setOf(EventField.TITLE, EventField.FOR_PERSON))
        assertThat(result).isEqualTo(EditResult.Cancelled)
        assertThat(access.toasts.messages).containsExactly("Mia can add events for Mia only.")
        assertThat(writer.calls).isEmpty()
        assertThat(store.pendingNow()).isEmpty()
    }

    /** Pin. */
    @Test
    fun anAdultMayRetagToASet() = runTest {
        val access = testAccess(household)
        put(event("football", createdBy = access.mia.id.value, forPerson = access.mia.id.value))
        access.answer(TestAccess.ALEX)
        val both = "${access.sam.id.value},${access.mia.id.value}"
        val result = editor(access).update(ref("football"), edited("Football", both), setOf(EventField.TITLE, EventField.FOR_PERSON))
        assertThat(result).isEqualTo(EditResult.Done)
        assertThat(writer.drafts.single().let { it.forPerson to it.forPersonColor }).isEqualTo(both to null)
    }

    @Test
    fun anEditThatTouchesWhoCarriesThePersonsColour() = runTest {
        val access = testAccess(household)
        put(event("dinner", createdBy = access.alex.id.value))
        access.answer(TestAccess.ALEX)
        assertThat(editor(access).update(ref("dinner"), edited("dinner", access.sam.id.value), setOf(EventField.FOR_PERSON)))
            .isEqualTo(EditResult.Done)
        assertThat(writer.drafts.single().forPersonColor).isEqualTo(access.sam.color)
    }

    /** Plan review 17: a save the provider accepts shows sign-in works again, so the chip a refused one left goes. */
    @Test
    fun anAcceptedSaveClearsTheSignInARefusedOneSet() = runTest {
        val access = testAccess(household)
        access.answer(TestAccess.ALEX)
        val editor = editor(access)
        writer.failWith = NeedsSignInException("a calendar scope is missing")
        assertThat(editor.create(draft("Sleepover", PersonId.FAMILY.value))).isEqualTo(EditResult.Queued)
        assertThat(store.connectionsNow().single().health).isEqualTo(ConnectionHealth.NeedsSignIn)
        writer.failWith = null
        assertThat(editor.create(draft("Pizza night", PersonId.FAMILY.value))).isEqualTo(EditResult.Done)
        val stored = store.connectionsNow().single()
        assertThat(stored.health).isEqualTo(ConnectionHealth.Ok)
        assertThat(stored.needsSignInSinceMillis).isNull()
    }

    @Test
    fun aSaveThatNeedsSignInIsQueuedAndFlagsTheConnectionAtOnce() = runTest {
        val access = testAccess(household)
        writer.failWith = NeedsSignInException("expired")
        access.answer(TestAccess.ALEX)
        assertThat(editor(access).create(draft("Sleepover", PersonId.FAMILY.value))).isEqualTo(EditResult.Queued)
        val stored = store.connectionsNow().single()
        // The reconnect chip shows now, not at the next sync (3a design §3.7), and the queue stops ageing (D16).
        assertThat(stored.health).isEqualTo(ConnectionHealth.NeedsSignIn)
        assertThat(stored.needsSignInSinceMillis).isEqualTo(opened + testScheduler.currentTime)
        assertThat(store.pendingNow()).hasSize(1)
    }

    /** The service returns the series itself, which a read never lists; the sync brings its occurrences. */
    @Test
    fun aNewSeriesIsLeftForTheSyncToBring() = runTest {
        val access = testAccess(household)
        access.answer(TestAccess.ALEX)
        val series = draft("Swimming", PersonId.FAMILY.value).copy(recurrence = Recurrence("RRULE:FREQ=WEEKLY;INTERVAL=1", london))
        assertThat(editor(access).create(series, "key-series")).isEqualTo(EditResult.Done)
        assertThat(writer.drafts.single().recurrence).isEqualTo(series.recurrence)
        assertThat(store.eventNow(ref("key-series"))).isNull()
        assertThat(syncRequests).isAtLeast(1)
        // A one-off is still mirrored at once.
        assertThat(editor(access).create(draft("Pizza night", PersonId.FAMILY.value), "key-once")).isEqualTo(EditResult.Done)
        assertThat(store.eventNow(ref("key-once"))).isNotNull()
    }

    /** The outbox's path: a queued series is sent, its row completed, and nothing mirrored before the sync. */
    @Test
    fun aQueuedNewSeriesDrainsWithoutAMirroredRow() = runTest {
        val access = testAccess(household)
        access.answer(TestAccess.ALEX)
        writer.failWith = UnreachableException("offline")
        val series = draft("Swimming", PersonId.FAMILY.value).copy(recurrence = Recurrence("RRULE:FREQ=WEEKLY;INTERVAL=1", london))
        assertThat(editor(access).create(series, "key-series")).isEqualTo(EditResult.Queued)
        writer.failWith = null
        drain(access, aheadMillis = OUTBOX_BACKOFF_MS.first()).syncAll()
        assertThat(writer.created.keys).contains("key-series")
        assertThat(store.pendingNow()).isEmpty()
        assertThat(store.eventNow(ref("key-series"))).isNull()
    }

    /** A weekly series of three from Sunday 27 September at 18:00, for everyone. */
    private fun seriesDraft(title: String = "Swimming") =
        draft(title, PersonId.FAMILY.value).copy(recurrence = Recurrence("RRULE:FREQ=WEEKLY;INTERVAL=1;COUNT=3", london))

    /** What the added toast's Undo carries, for an event [by] added as [key]. */
    private fun added(key: String, title: String, by: Person, series: Boolean = false) =
        AddedEvent(key, "c1", "s-family", title, series, by.id.value, sample)

    /** The series [key]'s three Sundays, as a sync brings them once the service has made it. */
    private suspend fun syncOccurrences(key: String) = put(
        *(0L..2L).map { week ->
            val start = LocalDate.of(2026, 9, 27).plusWeeks(week).atTime(18, 0).atZone(london).toInstant()
            RemoteEvent("${key}_$week", "Swimming", EventTime.Timed(start), EventTime.Timed(start.plusSeconds(3_600)), recurring = true, seriesId = key)
        }.toTypedArray(),
    )

    private suspend fun seriesRows(key: String) =
        store.eventsBetween(Long.MIN_VALUE, Long.MAX_VALUE).first().filter { it.seriesId == key || it.remoteId == key }

    /** Waits, in bounded real time, for [done]: the writes run on Room's and the app scope's threads. */
    private suspend fun waitUntil(done: () -> Boolean) =
        withContext(Dispatchers.Default) { withTimeout(5_000) { while (!done()) delay(10) } }

    private val sample = "Sample calendar"

    @Test
    fun anAddedEventsToastOffersUndo() = runTest {
        val access = testAccess(household)
        access.answer(TestAccess.ALEX)
        val editor = editor(access)
        assertThat(editor.create(draft("Dinner", PersonId.FAMILY.value))).isEqualTo(EditResult.Done)
        writer.failWith = UnreachableException("offline")
        assertThat(editor.create(draft("Dinner", PersonId.FAMILY.value))).isEqualTo(EditResult.Queued)
        assertThat(access.toasts.messages).containsExactly("Added Dinner.", "Added Dinner.")
        assertThat(access.toasts.actions.map { it.label }).containsExactly(UNDO, UNDO)
    }

    @Test
    fun undoingAOneOffTheServiceHasDeletesIt() = runTest {
        val access = testAccess(household)
        access.answer(TestAccess.ALEX)
        assertThat(editor(access).create(draft("Dinner", PersonId.FAMILY.value))).isEqualTo(EditResult.Done)
        assertThat(store.eventNow(ref("key-1"))).isNotNull()
        access.toasts.actions.single().onClick()
        waitUntil { access.toasts.messages.size == 2 }
        assertThat(writer.calls).containsExactly("create:Dinner", "delete:key-1").inOrder()
        assertThat(store.eventNow(ref("key-1"))).isNull()
        assertThat(store.pendingNow()).isEmpty()
        assertThat(access.toasts.messages).containsExactly("Added Dinner.", "Removed Dinner.").inOrder()
    }

    @Test
    fun undoingASeriesTheServiceHasDeletesItAndEveryOccurrence() = runTest {
        val access = testAccess(household)
        access.answer(TestAccess.ALEX)
        val editor = editor(access)
        assertThat(editor.create(seriesDraft(), "key-s")).isEqualTo(EditResult.Done)
        syncOccurrences("key-s")
        assertThat(seriesRows("key-s")).hasSize(3)
        assertThat(editor.undoAdd(added("key-s", "Swimming", access.alex, series = true))).isEqualTo(EditResult.Done)
        assertThat(writer.calls).containsExactly("create:Swimming", "deleteSeries:key-s").inOrder()
        assertThat(seriesRows("key-s")).isEmpty()
        assertThat(access.toasts.messages.last()).isEqualTo("Removed Swimming.")
    }

    @Test
    fun anOfflineCreateIsQueuedAsNeverSent() = runTest {
        val access = testAccess(household)
        access.answer(TestAccess.ALEX)
        val editor = editor(access)
        writer.failWith = OfflineException()
        assertThat(editor.create(draft("Dinner", PersonId.FAMILY.value), "key-a")).isEqualTo(EditResult.Queued)
        writer.failWith = UnreachableException("Couldn't reach Google Calendar", java.net.UnknownHostException())
        assertThat(editor.create(draft("Dinner", PersonId.FAMILY.value), "key-b")).isEqualTo(EditResult.Queued)
        val queued = store.pendingNow()
        assertThat(queued.map { it.attempts }).containsExactly(0, 0)
        // The backoff is the same as for a send that failed.
        assertThat(queued.map { it.nextAttemptMillis }.distinct()).containsExactly(opened + OUTBOX_BACKOFF_MS.first())
    }

    @Test
    fun undoingASeriesStillInTheOutboxDropsItAndWhatIsBehindIt() = runTest {
        val access = testAccess(household)
        access.answer(TestAccess.ALEX)
        val editor = editor(access)
        writer.failWith = OfflineException()
        assertThat(editor.create(seriesDraft(), "key-s")).isEqualTo(EditResult.Queued)
        assertThat(editor.assign(ref("key-s"), listOf(access.sam.id))).isEqualTo(EditResult.Queued)
        assertThat(store.pendingNow().map { it.ref }).containsExactly(ref("key-s"), ref("key-s"))

        assertThat(editor.undoAdd(added("key-s", "Swimming", access.alex, series = true))).isEqualTo(EditResult.Done)
        assertThat(store.pendingNow()).isEmpty()
        assertThat(writer.calls).containsExactly("create:Swimming", "deleteSeries:key-s").inOrder()
        assertThat(access.toasts.messages.last()).isEqualTo("Removed Swimming.")
        // Kept in case a drain had it on its way; nothing lands, so it goes at restart.
        assertThat(undone.pending).containsExactly("key-s")

        writer.failWith = null
        drain(access, aheadMillis = OUTBOX_BACKOFF_MS.first()).syncAll()
        assertThat(writer.calls).containsExactly("create:Swimming", "deleteSeries:key-s").inOrder()
    }

    @Test
    fun undoingAOneOffStillInTheOutboxQueuesNothing() = runTest {
        val access = testAccess(household)
        access.answer(TestAccess.ALEX)
        val editor = editor(access)
        writer.failWith = OfflineException()
        assertThat(editor.create(draft("Dinner", PersonId.FAMILY.value))).isEqualTo(EditResult.Queued)
        assertThat(editor.undoAdd(added("key-1", "Dinner", access.alex))).isEqualTo(EditResult.Done)
        assertThat(store.pendingNow()).isEmpty()
        assertThat(access.toasts.messages.last()).isEqualTo("Removed Dinner.")
    }

    @Test
    fun undoBeforeTheDrainsCreateLandsStillRemovesIt() = runTest {
        val access = testAccess(household)
        access.answer(TestAccess.ALEX)
        val editor = editor(access)
        writer.failWith = UnreachableException("offline")
        assertThat(editor.create(draft("Dinner", PersonId.FAMILY.value))).isEqualTo(EditResult.Queued)
        writer.failWith = null
        writer.createGate = CompletableDeferred()
        val drain = drain(access, aheadMillis = OUTBOX_BACKOFF_MS.first(), io = Dispatchers.Default)
        val pass = launch { drain.syncAll() }
        waitUntil { writer.calls.size == 2 }

        assertThat(editor.undoAdd(added("key-1", "Dinner", access.alex))).isEqualTo(EditResult.Done)
        // Its delete reached the service first and found nothing.
        assertThat(writer.calls).containsExactly("create:Dinner", "create:Dinner", "delete:key-1").inOrder()
        assertThat(undone.pending).containsExactly("key-1")

        writer.createGate?.complete(Unit)
        pass.join()
        assertThat(writer.calls).containsExactly("create:Dinner", "create:Dinner", "delete:key-1", "delete:key-1").inOrder()
        assertThat(store.eventNow(ref("key-1"))).isNull()
        assertThat(store.pendingNow()).isEmpty()
        assertThat(undone.pending).isEmpty()
        assertThat(access.toasts.messages).containsExactly("Added Dinner.", "Removed Dinner.").inOrder()

        val reads = ScriptedProvider("calendar.a", sourceList = listOf(family.copy(primary = true), school)).apply {
            events = { source -> if (source.id == family.id) writer.created.values.filter { it.remoteId !in writer.deleted } else emptyList() }
        }
        testSync(store, setOf(reads), HouseholdZone(household), WallClock { opened }, setOf(writer), access.toasts, writeLock = lock, undone = undone).syncAll()
        assertThat(store.eventNow(ref("key-1"))).isNull()
    }

    @Test
    fun aFailedDrainDeleteIsRetried() = runTest {
        val access = testAccess(household)
        access.answer(TestAccess.ALEX)
        val editor = editor(access)
        writer.failWith = UnreachableException("offline")
        assertThat(editor.create(draft("Dinner", PersonId.FAMILY.value))).isEqualTo(EditResult.Queued)
        writer.failWith = null
        writer.createGate = CompletableDeferred()
        val drain = drain(access, aheadMillis = OUTBOX_BACKOFF_MS.first(), io = Dispatchers.Default)
        val pass = launch { drain.syncAll() }
        waitUntil { writer.calls.size == 2 }
        assertThat(editor.undoAdd(added("key-1", "Dinner", access.alex))).isEqualTo(EditResult.Done)

        writer.failNextDelete = UnreachableException("offline")
        writer.createGate?.complete(Unit)
        pass.join()
        assertThat(writer.calls.count { it == "delete:key-1" }).isEqualTo(2)
        assertThat(undone.pending).containsExactly("key-1")
        assertThat(store.eventNow(ref("key-1"))).isNull()

        drain.syncAll()
        assertThat(writer.calls.count { it == "delete:key-1" }).isEqualTo(3)
        assertThat(writer.deleted).contains("key-1")
        assertThat(undone.pending).isEmpty()
    }

    @Test
    fun undoBeforeTheDrainsSeriesCreateLandsStillRemovesTheSeries() = runTest {
        val access = testAccess(household)
        access.answer(TestAccess.ALEX)
        val editor = editor(access)
        writer.failWith = UnreachableException("offline")
        assertThat(editor.create(seriesDraft(), "key-s")).isEqualTo(EditResult.Queued)
        writer.failWith = null
        writer.createGate = CompletableDeferred()
        val pass = launch { drain(access, aheadMillis = OUTBOX_BACKOFF_MS.first(), io = Dispatchers.Default).syncAll() }
        waitUntil { writer.calls.size == 2 }
        assertThat(editor.undoAdd(added("key-s", "Swimming", access.alex, series = true))).isEqualTo(EditResult.Done)

        writer.createGate?.complete(Unit)
        pass.join()
        assertThat(writer.calls).containsExactly("create:Swimming", "create:Swimming", "deleteSeries:key-s", "deleteSeries:key-s").inOrder()
        assertThat(undone.pending).isEmpty()
        assertThat(seriesRows("key-s")).isEmpty()
    }

    @Test
    fun undoWhileTheDrainSendsANeverSentCreateStillRemovesIt() = runTest {
        val access = testAccess(household)
        access.answer(TestAccess.ALEX)
        val editor = editor(access)
        writer.failWith = OfflineException()
        assertThat(editor.create(draft("Dinner", PersonId.FAMILY.value))).isEqualTo(EditResult.Queued)
        assertThat(store.pendingNow().single().attempts).isEqualTo(0)
        // Back online: the queue is due at once, so the drain sends it while the toast still shows.
        store.makeDue("c1", opened)
        writer.failWith = null
        writer.createGate = CompletableDeferred()
        val pass = launch { drain(access, aheadMillis = 0, io = Dispatchers.Default).syncAll() }
        waitUntil { writer.calls.size == 2 }
        assertThat(editor.undoAdd(added("key-1", "Dinner", access.alex))).isEqualTo(EditResult.Done)
        assertThat(access.toasts.messages.last()).isEqualTo("Removed Dinner.")

        writer.createGate?.complete(Unit)
        pass.join()
        assertThat(writer.calls).containsExactly("create:Dinner", "create:Dinner", "delete:key-1", "delete:key-1").inOrder()
        assertThat(store.eventNow(ref("key-1"))).isNull()
        assertThat(undone.pending).isEmpty()

        val reads = ScriptedProvider("calendar.a", sourceList = listOf(family.copy(primary = true), school)).apply {
            events = { source -> if (source.id == family.id) writer.created.values.filter { it.remoteId !in writer.deleted } else emptyList() }
        }
        testSync(store, setOf(reads), HouseholdZone(household), WallClock { opened }, setOf(writer), access.toasts, writeLock = lock, undone = undone).syncAll()
        assertThat(store.eventNow(ref("key-1"))).isNull()
    }

    @Test
    fun anUndoneCreateTheServiceRefusesIsLetGo() = runTest {
        val access = testAccess(household)
        access.answer(TestAccess.ALEX)
        val editor = editor(access)
        writer.failWith = UnreachableException("offline")
        assertThat(editor.create(draft("Dinner", PersonId.FAMILY.value))).isEqualTo(EditResult.Queued)
        writer.failWith = null
        writer.createGate = CompletableDeferred()
        val pass = launch { drain(access, aheadMillis = OUTBOX_BACKOFF_MS.first(), io = Dispatchers.Default).syncAll() }
        waitUntil { writer.calls.size == 2 }
        assertThat(editor.undoAdd(added("key-1", "Dinner", access.alex))).isEqualTo(EditResult.Done)

        writer.failWith = WriteRejectedException()
        writer.createGate?.complete(Unit)
        pass.join()
        assertThat(undone.pending).isEmpty()
        // Undo has already said what happened.
        assertThat(access.toasts.messages).containsExactly("Added Dinner.", "Removed Dinner.").inOrder()
    }

    @Test
    fun anUndoneCreateWithNoReplyIsDeletedNextPass() = runTest {
        val access = testAccess(household)
        access.answer(TestAccess.ALEX)
        val editor = editor(access)
        writer.failWith = UnreachableException("offline")
        assertThat(editor.create(draft("Dinner", PersonId.FAMILY.value))).isEqualTo(EditResult.Queued)
        writer.failWith = null
        writer.createGate = CompletableDeferred()
        val drain = drain(access, aheadMillis = OUTBOX_BACKOFF_MS.first(), io = Dispatchers.Default)
        val pass = launch { drain.syncAll() }
        waitUntil { writer.calls.size == 2 }
        assertThat(editor.undoAdd(added("key-1", "Dinner", access.alex))).isEqualTo(EditResult.Done)

        // It may have landed before the connection dropped.
        writer.dropNextReply = true
        writer.createGate?.complete(Unit)
        pass.join()
        assertThat(writer.created.keys).contains("key-1")
        assertThat(undone.pending).containsExactly("key-1")

        drain.syncAll()
        assertThat(writer.calls.count { it == "delete:key-1" }).isEqualTo(2)
        assertThat(writer.deleted).contains("key-1")
        assertThat(undone.pending).isEmpty()
        assertThat(access.toasts.messages).containsExactly("Added Dinner.", "Removed Dinner.").inOrder()
    }

    @Test
    fun undoingWhenTheCalendarHasGoneSaysItCouldNot() = runTest {
        val access = testAccess(household)
        access.answer(TestAccess.ALEX)
        val editor = editor(access)
        assertThat(editor.create(draft("Dinner", PersonId.FAMILY.value))).isEqualTo(EditResult.Done)
        val dinner = added("key-1", "Dinner", access.alex)
        assertThat(editor.undoAdd(dinner.copy(sourceId = "s-gone"))).isEqualTo(EditResult.NotEditable)
        assertThat(editor.undoAdd(dinner.copy(connectionId = "c-gone"))).isEqualTo(EditResult.NotEditable)
        assertThat(access.toasts.messages.drop(1)).containsExactly(couldNotUndo(sample), couldNotUndo(sample))
        assertThat(writer.calls).containsExactly("create:Dinner")
    }

    @Test
    fun undoingAOneOffOfflineQueuesItsDelete() = runTest {
        val access = testAccess(household)
        access.answer(TestAccess.ALEX)
        val editor = editor(access)
        assertThat(editor.create(draft("Dinner", PersonId.FAMILY.value))).isEqualTo(EditResult.Done)
        writer.failWith = UnreachableException("offline")
        assertThat(editor.undoAdd(added("key-1", "Dinner", access.alex))).isEqualTo(EditResult.Queued)
        val queued = store.pendingNow().single()
        assertThat(queued.kind to queued.remoteId).isEqualTo(ChangeKind.DELETE to "key-1")
        assertThat(access.toasts.messages.last()).isEqualTo("Removed Dinner.")
    }

    @Test
    fun undoingASeriesThatCannotBeReachedSaysSo() = runTest {
        val access = testAccess(household)
        access.answer(TestAccess.ALEX)
        val editor = editor(access)
        assertThat(editor.create(seriesDraft(), "key-s")).isEqualTo(EditResult.Done)
        syncOccurrences("key-s")
        writer.failWith = UnreachableException("offline")
        assertThat(editor.undoAdd(added("key-s", "Swimming", access.alex, series = true))).isEqualTo(EditResult.NotEditable)
        assertThat(store.pendingNow()).isEmpty()
        assertThat(access.toasts.messages.last()).isEqualTo(couldNotUndo(sample))
        assertThat(seriesRows("key-s")).hasSize(3)
    }

    @Test
    fun undoingASeriesThatIsNoLongerASeriesSaysSo() = runTest {
        val access = testAccess(household)
        access.answer(TestAccess.ALEX)
        val editor = editor(access)
        assertThat(editor.create(seriesDraft(), "key-s")).isEqualTo(EditResult.Done)
        syncOccurrences("key-s")
        writer.failWith = WriteRejectedException()
        assertThat(editor.undoAdd(added("key-s", "Swimming", access.alex, series = true))).isEqualTo(EditResult.NotEditable)
        assertThat(access.toasts.messages.last()).isEqualTo(couldNotUndo(sample))
        assertThat(seriesRows("key-s")).hasSize(3)
    }

    @Test
    fun undoingASeriesWhoseSendTimedOutWhileStillOfflineSaysItCouldNot() = runTest {
        val access = testAccess(household)
        access.answer(TestAccess.ALEX)
        val editor = editor(access)
        writer.loseNextReply = true
        assertThat(editor.create(seriesDraft(), "key-s")).isEqualTo(EditResult.Queued)
        assertThat(store.pendingNow().single().attempts).isEqualTo(1)
        val mirrored = store.eventsBetween(Long.MIN_VALUE, Long.MAX_VALUE).first()
        writer.failWith = UnreachableException("offline")
        assertThat(editor.undoAdd(added("key-s", "Swimming", access.alex, series = true))).isEqualTo(EditResult.NotEditable)
        assertThat(store.pendingNow()).isEmpty()
        assertThat(access.toasts.messages.last()).isEqualTo(couldNotUndo(sample))
        assertThat(store.eventsBetween(Long.MIN_VALUE, Long.MAX_VALUE).first()).isEqualTo(mirrored)
    }

    @Test
    fun aDrainAttemptAfterTheAddCountsAtUndo() = runTest {
        val access = testAccess(household)
        access.answer(TestAccess.ALEX)
        val editor = editor(access)
        writer.failWith = OfflineException()
        assertThat(editor.create(seriesDraft(), "key-s")).isEqualTo(EditResult.Queued)
        assertThat(store.pendingNow().single().attempts).isEqualTo(0)
        // The drain's attempt may have reached the service before the reply was lost.
        writer.failWith = UnreachableException("offline")
        drain(access, aheadMillis = OUTBOX_BACKOFF_MS.first()).syncAll()
        assertThat(store.pendingNow().single().attempts).isEqualTo(1)
        assertThat(editor.undoAdd(added("key-s", "Swimming", access.alex, series = true))).isEqualTo(EditResult.NotEditable)
        assertThat(store.pendingNow()).isEmpty()
        assertThat(access.toasts.messages.last()).isEqualTo(couldNotUndo(sample))
    }

    @Test
    fun onlyThePersonWhoAddedItCanUndo() = runTest {
        val access = testAccess(household)
        access.answer(TestAccess.ALEX)
        val editor = editor(access)
        assertThat(editor.create(draft("Dinner", PersonId.FAMILY.value))).isEqualTo(EditResult.Done)
        val dinner = added("key-1", "Dinner", access.alex)
        suspend fun refusedFor(pin: String, name: String) {
            access.control.lock()
            access.answer(pin)
            assertThat(editor.undoAdd(dinner)).isEqualTo(EditResult.Cancelled)
            assertThat(access.toasts.messages.last()).isEqualTo(cannotChangeOthers(name))
            assertThat(access.requests.last().reason).isEqualTo(PinReason.Delete)
        }
        refusedFor(TestAccess.SAM, "Sam")
        household.updateMember(access.sam.id, "Sam", access.sam.color, Role.ADMIN, PinChange.Keep)
        refusedFor(TestAccess.SAM, "Sam")
        refusedFor(TestAccess.MIA, "Mia")
        assertThat(writer.calls).containsExactly("create:Dinner")
        assertThat(store.eventNow(ref("key-1"))).isNotNull()
        assertThat(store.pendingNow()).isEmpty()
        assertThat(undone.pending).isEmpty()
    }

    @Test
    fun thePersonWhoAddedItCanUndoWithoutEditOwn() = runTest {
        // Nobody may change events: having added it is the authority.
        val noChanging = object : PermissionSource {
            override val permissions = CalendarPermissionSource().permissions.map {
                if (it.id == CalendarPermissions.EDIT || it.id == CalendarPermissions.EDIT_OWN) it.copy(defaultRoles = emptySet()) else it
            }
        }
        val access = testAccess(household, noChanging)
        access.answer(TestAccess.ALEX)
        val editor = editor(access)
        assertThat(editor.create(draft("Dinner", PersonId.FAMILY.value))).isEqualTo(EditResult.Done)
        assertThat(editor.mayDelete(ref("key-1"))).isFalse()
        access.answer(TestAccess.ALEX)
        assertThat(editor.undoAdd(added("key-1", "Dinner", access.alex))).isEqualTo(EditResult.Done)
        assertThat(store.eventNow(ref("key-1"))).isNull()
        assertThat(access.toasts.messages.last()).isEqualTo("Removed Dinner.")
    }

    /** Pin (9 §2.3): Undo is the one way to delete a series; an occurrence still can't be deleted from the wall. */
    @Test
    fun undoDoesNotOpenSeriesDeleteElsewhere() = runTest {
        val access = testAccess(household)
        put(event("piano_1", createdBy = access.alex.id.value, recurring = true).copy(seriesId = "piano"))
        access.answer(TestAccess.ALEX)
        assertThat(editor(access).delete(ref("piano_1"))).isEqualTo(EditResult.NotEditable)
        assertThat(writer.calls).isEmpty()
    }
}
