package uk.co.siland.culvery.capability.calendar.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsFocused
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertIsNotFocused
import androidx.compose.ui.test.assertIsNotSelected
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.performTextReplacement
import androidx.compose.ui.text.AnnotatedString
import androidx.room.execSQL
import androidx.room.useWriterConnection
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.google.common.truth.Truth.assertThat
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import uk.co.siland.culvery.capability.calendar.CHANGES_SAVED
import uk.co.siland.culvery.capability.calendar.CalendarEditor
import uk.co.siland.culvery.capability.calendar.CalendarPermissions
import uk.co.siland.culvery.capability.calendar.CalendarRepository
import uk.co.siland.culvery.capability.calendar.CalendarSource
import uk.co.siland.culvery.capability.calendar.CalendarStore
import uk.co.siland.culvery.capability.calendar.ChangeKind
import uk.co.siland.culvery.capability.calendar.DateRange
import uk.co.siland.culvery.capability.calendar.EVENT_ADDED
import uk.co.siland.culvery.capability.calendar.EventDraft
import uk.co.siland.culvery.capability.calendar.EventRef
import uk.co.siland.culvery.capability.calendar.EventTime
import uk.co.siland.culvery.capability.calendar.HouseholdZone
import uk.co.siland.culvery.capability.calendar.PendingChange
import uk.co.siland.culvery.capability.calendar.RemoteEvent
import uk.co.siland.culvery.capability.calendar.ScriptedWriter
import uk.co.siland.culvery.capability.calendar.SyncResult
import uk.co.siland.culvery.capability.calendar.TRY_AGAIN
import uk.co.siland.culvery.capability.calendar.TestAccess
import uk.co.siland.culvery.capability.calendar.WriteRejectedException
import uk.co.siland.culvery.capability.calendar.calendarDb
import uk.co.siland.culvery.capability.calendar.couldNotSave
import uk.co.siland.culvery.capability.calendar.db.CalendarDatabase
import uk.co.siland.culvery.capability.calendar.householdDb
import uk.co.siland.culvery.capability.calendar.testAccess
import uk.co.siland.culvery.capability.calendar.testEditor
import uk.co.siland.culvery.core.access.PinReason
import uk.co.siland.culvery.core.household.HomeLocation
import uk.co.siland.culvery.core.household.HouseholdRepository
import uk.co.siland.culvery.core.household.db.HouseholdDatabase
import uk.co.siland.culvery.core.plugin.Connection
import uk.co.siland.culvery.core.plugin.LocalOverlayHost
import uk.co.siland.culvery.core.plugin.OverlayHost
import uk.co.siland.culvery.core.plugin.WallClock
import uk.co.siland.culvery.core.ui.CulveryTheme

/**
 * The add/edit sheet wired to a real repository, editor and access rules, as EventDetailHostTest wires the detail
 * sheet; PIN pads are answered from a queue, and access and the editor share `access.toasts`.
 */
@RunWith(AndroidJUnit4::class)
class EventEditorHostTest {
    /** Focus and the keyboard behave as on the tablet only in touch mode, set before the activity launches. */
    @get:Rule(order = 0) val touchMode = TouchModeRule()

    @get:Rule(order = 1) val compose = createComposeRule()

    private lateinit var calendar: CalendarDatabase
    private lateinit var householdDb: HouseholdDatabase
    private lateinit var store: CalendarStore
    private lateinit var household: HouseholdRepository
    private lateinit var access: TestAccess
    private lateinit var repo: CalendarRepository
    private lateinit var editor: CalendarEditor

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Unconfined)
    private val london = ZoneId.of("Europe/London")
    private val day = LocalDate.of(2026, 9, 23)
    private val writer = ScriptedWriter("calendar.a")
    private val dinner = EventRef("c1", "s-family", "dinner")
    private val overlay = RecordingOverlay()
    private var closed = 0
    private val deleting = mutableListOf<EventRef>()

    /** The editor's "now", when a test moves it; otherwise the real time. */
    private var now: Long? = null

    @Before
    fun setUp() = runBlocking {
        calendar = calendarDb()
        householdDb = householdDb()
        store = CalendarStore(calendar)
        household = HouseholdRepository(householdDb)
        household.setLocation(HomeLocation("London", 51.5, -0.12, "Europe/London"))
        access = testAccess(household, scope, WallClock { System.currentTimeMillis() }, scope)
        val zone = HouseholdZone(household)
        store.addConnection(
            Connection("c1", "calendar.a", "Sample calendar", emptyMap()),
            listOf(CalendarSource("s-family", "Family calendar", writable = true)),
            emptyMap(),
        )
        store.setMaster("c1", "s-family")
        val start = day.atTime(19, 30).atZone(london).toInstant()
        store.applySync(
            "c1", "s-family", DateRange(day.minusDays(1), day.plusDays(14), london),
            SyncResult(
                listOf(RemoteEvent("dinner", "Dinner with Jo & Priya", EventTime.Timed(start), EventTime.Timed(start.plusSeconds(5_400)), false, null, access.alex.id.value)),
                emptyList(), null, fullReplace = true,
            ),
        )
        repo = CalendarRepository(store, household, zone, emptySet(), setOf(writer))
        editor = testEditor(store, setOf(writer), access.control, access.toasts, zone, WallClock { now ?: System.currentTimeMillis() }, scope)
    }

    @After
    fun tearDown() {
        scope.cancel()
        calendar.close()
        householdDb.close()
    }

    private fun showEditor(request: EditorRequest) = compose.setContent {
        CulveryTheme(dark = true) {
            EventEditorHost(request, repo, editor, onClose = { closed++ }, onDeleteAuthorised = { deleting += it })
        }
    }

    /** The shell's overlay as the app has it: whatever [open] shows, swaps included. */
    private fun showOverlay(open: OverlayHost.() -> Unit) {
        compose.setContent {
            CompositionLocalProvider(LocalOverlayHost provides overlay) {
                CulveryTheme(dark = true) { Box { overlay.content?.invoke() } }
            }
        }
        compose.runOnIdle { overlay.open() }
    }

    private fun waitForText(text: String) =
        compose.waitUntil(5_000) { compose.onAllNodesWithText(text).fetchSemanticsNodes().isNotEmpty() }

    private fun millisAt(date: LocalDate, hour: Int, minute: Int) = date.atTime(hour, minute).atZone(london).toInstant().toEpochMilli()

    @Test
    fun aNewEventOpensWithItsTitleFocused() {
        showEditor(EditorRequest.New(day = null))
        waitForText("New event")
        compose.onNodeWithTag("editor_title").assertIsFocused()
        compose.onNodeWithTag("who_Family").assertIsSelected()
    }

    @Test
    fun anEditOpensOnTheEventWithNothingFocused() {
        showEditor(EditorRequest.Edit(dinner))
        waitForText("Edit event")
        compose.onNodeWithTag("editor_title").assertTextEquals("Dinner with Jo & Priya").assertIsNotFocused()
        compose.onNodeWithTag("time_pick").assertTextEquals("19:30")
    }

    @Test
    fun savingANewEventAsksForThePinAddsItAndCloses() {
        access.answer(TestAccess.ALEX)
        showEditor(EditorRequest.New(day = null))
        waitForText("New event")
        compose.onNodeWithTag("editor_title").performTextInput("Parents evening")
        compose.onNodeWithTag("editor_save").performClick()
        compose.waitUntil(5_000) { closed > 0 }
        assertThat(writer.calls).containsExactly("create:Parents evening")
        assertThat(writer.drafts.single().createdBy).isEqualTo(access.alex.id.value)
        assertThat(access.requests.single().reason).isEqualTo(PinReason.Save)
        assertThat(access.toasts.messages).containsExactly(EVENT_ADDED)
    }

    @Test
    fun tomorrowChosenAt2330IsStillThatDateWhenSavedAfterMidnight() {
        // Opened at 23:30 on Saturday 24 October; Save is tapped at 00:05, when "Tomorrow" would mean the 26th.
        now = millisAt(LocalDate.of(2026, 10, 24), 23, 30)
        access.answer(TestAccess.ALEX)
        showEditor(EditorRequest.New(day = null))
        waitForText("New event")
        // "Today" is the editor's opening time, not the device's.
        compose.onNodeWithTag("day_2026-10-24").assertTextEquals("Today")
        compose.onNodeWithTag("editor_title").performTextInput("Bonfire")
        compose.onNodeWithTag("day_2026-10-25").performClick()
        now = millisAt(LocalDate.of(2026, 10, 25), 0, 5)
        // Recomposing after midnight doesn't read the clock again.
        compose.onNodeWithTag("day_2026-10-24").assertTextEquals("Today")
        compose.onNodeWithTag("editor_save").performClick()
        compose.waitUntil(5_000) { closed > 0 }
        // Morning on the 25th, the day the clocks go back: 09:00 GMT.
        assertThat(writer.drafts.single().start).isEqualTo(EventTime.Timed(Instant.parse("2026-10-25T09:00:00Z")))
    }

    @Test
    fun aRefusedSaveKeepsTheSheetAndItsInputAndOffersTryAgain() {
        writer.failWith = WriteRejectedException("Calendar is full")
        access.answer(TestAccess.ALEX)
        showEditor(EditorRequest.New(day = null))
        waitForText("New event")
        compose.onNodeWithTag("editor_title").performTextInput("Parents evening")
        compose.onNodeWithTag("editor_save").performClick()
        waitForText("Try again")
        compose.onNodeWithText("Couldn't save to Sample calendar — Calendar is full").assertExists()
        compose.onNodeWithTag("editor_title").assertTextEquals("Parents evening")
        assertThat(closed).isEqualTo(0)
        assertThat(access.toasts.messages).isEmpty()
        // Try again clears the card at once.
        writer.failWith = null
        compose.onNodeWithTag("editor_save").performClick()
        compose.onNodeWithTag("editor_failure").assertDoesNotExist()
        compose.waitUntil(5_000) { closed > 0 }
        assertThat(writer.created).hasSize(1)
        assertThat(access.toasts.messages).containsExactly(EVENT_ADDED)
    }

    @Test
    fun aStoreFailureBeforeTheWriteShowsTheCardAndKeepsTheSheet() {
        showEditor(EditorRequest.Edit(dinner))
        waitForText("Edit event")
        // An outbox row this version can't read, which reading the queue deletes, and a disk that refuses the delete:
        // the editor throws before it reaches the write, outside its own catch.
        runBlocking {
            calendar.useWriterConnection {
                it.execSQL(
                    "INSERT INTO outbox (connectionId, sourceId, remoteId, kind, draftJson, attempts, nextAttemptMillis, createdMillis) " +
                        "VALUES ('c1', 's-family', 'dinner', 'MOVE', NULL, 0, 0, 0)",
                )
                it.execSQL("CREATE TRIGGER fail_outbox BEFORE DELETE ON outbox BEGIN SELECT RAISE(ABORT, 'disk full'); END")
            }
        }
        compose.onNodeWithTag("length_120").performClick()
        compose.onNodeWithTag("editor_save").performClick()
        waitForText(couldNotSave("Sample calendar", TRY_AGAIN))
        compose.onNodeWithTag("editor_failure").assertExists()
        compose.onNodeWithTag("editor_save").assertIsEnabled()
        assertThat(closed).isEqualTo(0)
        assertThat(writer.calls).isEmpty()
    }

    @Test
    fun anUnchangedEditClosesWithoutAPin() {
        showEditor(EditorRequest.Edit(dinner))
        waitForText("Edit event")
        compose.onNodeWithTag("editor_save").performClick()
        compose.waitUntil(5_000) { closed > 0 }
        assertThat(access.requests).isEmpty()
        assertThat(writer.calls).isEmpty()
    }

    @Test
    fun savingAnEditSendsTheChange() {
        access.answer(TestAccess.SAM)
        showEditor(EditorRequest.Edit(dinner))
        waitForText("Edit event")
        compose.onNodeWithTag("length_120").performClick()
        compose.onNodeWithTag("editor_save").performClick()
        compose.waitUntil(5_000) { closed > 0 }
        assertThat(writer.calls).containsExactly("update:dinner")
        assertThat(access.requests.single().reason).isEqualTo(PinReason.Edit)
        assertThat(access.toasts.messages).containsExactly(CHANGES_SAVED)
    }

    @Test
    fun aChildSavingForFamilyIsToldAndTheSheetKeepsItsInput() {
        access.answer(TestAccess.MIA)
        showEditor(EditorRequest.New(day = null))
        waitForText("New event")
        compose.onNodeWithTag("editor_title").performTextInput("Pizza night")
        compose.onNodeWithTag("editor_save").performClick()
        compose.waitUntil(5_000) { access.toasts.messages.isNotEmpty() }
        assertThat(access.toasts.messages).containsExactly("Mia can only add events for themselves.")
        compose.onNodeWithTag("editor_title").assertTextEquals("Pizza night")
        assertThat(closed).isEqualTo(0)
        assertThat(writer.calls).isEmpty()
    }

    @Test
    fun aSignedInChildsOtherWhoChipsExplainByToast() {
        access.answer(TestAccess.MIA)
        runBlocking { access.control.authorise(CalendarPermissions.CREATE_SELF) }
        showEditor(EditorRequest.New(day = null))
        waitForText("New event")
        compose.onNodeWithTag("who_Mia").assertIsSelected()
        compose.onNodeWithTag("who_Sam").performClick()
        assertThat(access.toasts.messages).containsExactly("Mia can only add events for themselves.")
        compose.onNodeWithTag("who_Mia").assertIsSelected()
    }

    @Test
    fun aSignedInChildsEditDisablesTheOtherWhoChipsToo() {
        access.answer(TestAccess.MIA)
        runBlocking { access.control.authorise(CalendarPermissions.CREATE_SELF) }
        showEditor(EditorRequest.Edit(dinner))
        waitForText("Edit event")
        // The dinner is untagged, so Family is chosen; Mia can't move it to Sam (2b-2 design §6).
        compose.onNodeWithTag("who_Sam").performClick()
        assertThat(access.toasts.messages).containsExactly("Mia can only add events for themselves.")
        compose.onNodeWithTag("who_Family").assertIsSelected()
    }

    @Test
    fun saveIgnoresASecondTapWhileBusy() {
        val gate = CompletableDeferred<Unit>()
        writer.gate = gate
        // A second answer is queued, so a second save that got through would reach the writer too.
        access.answer(TestAccess.ALEX, TestAccess.ALEX)
        showEditor(EditorRequest.New(day = null))
        waitForText("New event")
        compose.onNodeWithTag("editor_title").performTextInput("Parents evening")
        compose.onNodeWithTag("editor_save").performClick()
        compose.waitUntil(5_000) { writer.entered.isCompleted }
        compose.onNodeWithTag("editor_save").assertIsNotEnabled().performClick()
        // Nothing can change under a save in flight: it would close with those edits lost.
        compose.onNodeWithTag("editor_title").assert(SemanticsMatcher.keyNotDefined(SemanticsActions.SetText))
        compose.onNodeWithTag("length_30").performClick()
        compose.onNodeWithTag("length_60").assertIsSelected()
        compose.onNodeWithTag("who_Sam").performClick()
        compose.onNodeWithTag("who_Family").assertIsSelected()
        compose.onNodeWithTag("time_all_day").performClick()
        compose.onNodeWithTag("time_all_day").assertIsNotSelected()
        gate.complete(Unit)
        compose.waitUntil(5_000) { closed > 0 }
        assertThat(writer.calls).containsExactly("create:Parents evening")
    }

    @Test
    fun deleteAsksForThePinThenHandsOverToTheDetailSheet() {
        access.answer(TestAccess.ALEX)
        showEditor(EditorRequest.Edit(dinner))
        waitForText("Edit event")
        compose.onNodeWithTag("editor_delete").performClick()
        compose.waitUntil(5_000) { deleting.isNotEmpty() }
        assertThat(deleting).containsExactly(dinner)
        assertThat(access.requests.single().reason).isEqualTo(PinReason.Delete)
        assertThat(writer.calls).isEmpty()
    }

    @Test
    fun aRefusedDeleteKeepsTheEditorOpen() {
        access.answer(TestAccess.MIA)
        showEditor(EditorRequest.Edit(dinner))
        waitForText("Edit event")
        compose.onNodeWithTag("editor_delete").performClick()
        compose.waitUntil(5_000) { access.toasts.messages.isNotEmpty() }
        assertThat(access.toasts.messages).containsExactly("Mia can only change events they created.")
        assertThat(deleting).isEmpty()
        compose.onNodeWithText("Edit event").assertExists()
    }

    @Test
    fun theEditorClosesWhenItsEventHasGone() {
        showEditor(EditorRequest.Edit(EventRef("c1", "s-family", "nope")))
        compose.waitUntil(5_000) { closed > 0 }
    }

    @Test
    fun anEditWhoseEventHasGoneClosesOnSaveWithoutAPin() {
        showEditor(EditorRequest.Edit(dinner))
        waitForText("Edit event")
        // Deleted on a phone, then synced, while the sheet was open.
        runBlocking { store.applyDeleted(dinner) }
        compose.onNodeWithTag("length_120").performClick()
        compose.onNodeWithTag("editor_save").performClick()
        compose.waitUntil(5_000) { closed > 0 }
        assertThat(access.requests).isEmpty()
        assertThat(access.toasts.messages).isEmpty()
    }

    @Test
    fun editInTheDetailSheetSwapsToTheEditorWithNoPin() {
        showOverlay { showDetail(dinner, day, repo, editor) }
        waitForText("Created by")
        compose.onNodeWithTag("detail_edit").performClick()
        waitForText("Edit event")
        compose.onNodeWithTag("detail_sheet").assertDoesNotExist()
        assertThat(access.requests).isEmpty()
    }

    @Test
    fun deleteInTheEditorSwapsToTheDetailSheetAskingToConfirm() {
        access.answer(TestAccess.ALEX)
        showOverlay { showEditor(EditorRequest.Edit(dinner), day, repo, editor) }
        waitForText("Edit event")
        compose.onNodeWithTag("editor_delete").performClick()
        waitForText("Delete this event?")
        compose.onNodeWithTag("detail_confirm_delete").performClick()
        compose.waitUntil(5_000) { overlay.dismissed > 0 }
        assertThat(writer.calls).containsExactly("delete:dinner")
        assertThat(access.requests).hasSize(1)
    }

    @Test
    fun closeClosesTheEditor() {
        showOverlay { showEditor(EditorRequest.New(day = null), day, repo, editor) }
        waitForText("New event")
        compose.onNodeWithTag("sheet_close").performClick()
        compose.runOnIdle { assertThat(overlay.dismissed).isEqualTo(1) }
    }

    @Test
    fun aQueuedCreateOpensAsSyncingAndItsEditQueuesBehindIt() {
        val key = "0123456789abcdef0123456789abcdef"
        val created = EventRef("c1", "s-family", key)
        val start = day.atTime(18, 0).atZone(london).toInstant()
        runBlocking {
            store.enqueue(
                PendingChange(
                    0, "c1", "s-family", null, ChangeKind.CREATE,
                    EventDraft("Sleepover", EventTime.Timed(start), EventTime.Timed(start.plusSeconds(3_600)), access.mia.id.value, access.alex.id.value),
                    attempts = 1, nextAttemptMillis = 0, createdMillis = 0, clientKey = key,
                ),
            )
        }
        access.answer(TestAccess.ALEX)
        showOverlay { showDetail(created, day, repo, editor) }
        waitForText("Sleepover")
        compose.onNodeWithTag("detail_syncing").assertExists()
        compose.onNodeWithTag("detail_edit").performClick()
        waitForText("Edit event")
        compose.onNodeWithTag("editor_title").performTextReplacement("Sleepover at Ava's")
        compose.onNodeWithTag("editor_save").performClick()
        compose.waitUntil(5_000) { overlay.dismissed > 0 }
        // Queued behind its create, never sent ahead of it.
        assertThat(writer.calls).isEmpty()
        assertThat(runBlocking { store.pendingNow() }.map { it.kind to it.ref })
            .containsExactly(ChangeKind.CREATE to created, ChangeKind.UPDATE to created).inOrder()
        assertThat(access.toasts.messages).containsExactly(CHANGES_SAVED)
    }

    @Test
    fun theSignedInPersonIsANewEventsWho() {
        access.answer(TestAccess.SAM)
        runBlocking { access.control.authorise(CalendarPermissions.CREATE) }
        showEditor(EditorRequest.New(day = null))
        waitForText("New event")
        compose.onNodeWithTag("who_Sam").assertIsSelected()
        // An adult may choose anyone.
        compose.onNodeWithTag("who_Mia").performClick()
        compose.onNodeWithTag("who_Mia").assertIsSelected()
        assertThat(access.toasts.messages).isEmpty()
    }

    @Test
    fun eachOpeningGetsAFreshSheet() {
        showOverlay { showEditor(EditorRequest.New(day = null), day, repo, editor) }
        waitForText("New event")
        compose.onNodeWithTag("editor_title").performTextInput("Half-typed")
        compose.onNodeWithTag("length_30").performClick()
        compose.runOnIdle { overlay.showEditor(EditorRequest.Edit(dinner), day, repo, editor) }
        waitForText("Edit event")
        compose.onNodeWithTag("editor_title").assertTextEquals("Dinner with Jo & Priya").assertIsNotFocused()
        compose.onNodeWithTag("length_90").assertIsSelected()
        compose.runOnIdle { overlay.showEditor(EditorRequest.New(day = null), day, repo, editor) }
        waitForText("New event")
        compose.onNodeWithTag("editor_title").assertIsFocused()
            .assert(SemanticsMatcher.expectValue(SemanticsProperties.EditableText, AnnotatedString("")))
        compose.onNodeWithTag("length_60").assertIsSelected()
    }
}
