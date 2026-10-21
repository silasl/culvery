package uk.co.siland.culvery.capability.calendar.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsFocused
import androidx.compose.ui.test.assertIsNotFocused
import androidx.compose.ui.test.assertIsNotSelected
import androidx.compose.ui.test.assertIsOff
import androidx.compose.ui.test.assertIsOn
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.performTextReplacement
import androidx.compose.ui.text.AnnotatedString
import androidx.room.execSQL
import androidx.room.useWriterConnection
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.google.common.truth.Truth.assertThat
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
import uk.co.siland.culvery.capability.calendar.CALENDAR_TAB_ID
import uk.co.siland.culvery.capability.calendar.CANT_REPEAT_FOR_CHILD
import uk.co.siland.culvery.capability.calendar.CHANGES_SAVED
import uk.co.siland.culvery.capability.calendar.COULD_NOT_OPEN
import uk.co.siland.culvery.capability.calendar.CalendarEditor
import uk.co.siland.culvery.capability.calendar.CalendarPermissions
import uk.co.siland.culvery.capability.calendar.CalendarRepository
import uk.co.siland.culvery.capability.calendar.CalendarSource
import uk.co.siland.culvery.capability.calendar.CalendarStore
import uk.co.siland.culvery.capability.calendar.ChangeKind
import uk.co.siland.culvery.capability.calendar.DateRange
import uk.co.siland.culvery.capability.calendar.EventDraft
import uk.co.siland.culvery.capability.calendar.EventField
import uk.co.siland.culvery.capability.calendar.EventRef
import uk.co.siland.culvery.capability.calendar.EventTime
import uk.co.siland.culvery.capability.calendar.PendingChange
import uk.co.siland.culvery.capability.calendar.RemoteEvent
import uk.co.siland.culvery.capability.calendar.ScriptedWriter
import uk.co.siland.culvery.capability.calendar.SyncResult
import uk.co.siland.culvery.capability.calendar.TestAccess
import uk.co.siland.culvery.capability.calendar.UNDO
import uk.co.siland.culvery.capability.calendar.WeekFocus
import uk.co.siland.culvery.capability.calendar.WriteProblem
import uk.co.siland.culvery.capability.calendar.WriteRejectedException
import uk.co.siland.culvery.capability.calendar.calendarDb
import uk.co.siland.culvery.capability.calendar.cannotAddForOthers
import uk.co.siland.culvery.capability.calendar.couldNotDelete
import uk.co.siland.culvery.capability.calendar.couldNotSave
import uk.co.siland.culvery.capability.calendar.db.CalendarDatabase
import uk.co.siland.culvery.capability.calendar.householdDb
import uk.co.siland.culvery.capability.calendar.testAccess
import uk.co.siland.culvery.capability.calendar.testEditor
import uk.co.siland.culvery.core.access.PinReason
import uk.co.siland.culvery.core.household.HomeLocation
import uk.co.siland.culvery.core.household.HouseholdRepository
import uk.co.siland.culvery.core.household.HouseholdZone
import uk.co.siland.culvery.core.household.db.HouseholdDatabase
import uk.co.siland.culvery.core.plugin.Connection
import uk.co.siland.culvery.core.plugin.LocalOverlayHost
import uk.co.siland.culvery.core.plugin.LocalShellNavigator
import uk.co.siland.culvery.core.plugin.LocalUserActivity
import uk.co.siland.culvery.core.plugin.OverlayHost
import uk.co.siland.culvery.core.plugin.WallClock
import uk.co.siland.culvery.core.plugin.test.FakeUserActivity
import uk.co.siland.culvery.core.plugin.test.RecordingNavigator
import uk.co.siland.culvery.core.ui.CulveryTheme
import uk.co.siland.culvery.core.ui.test.TouchModeRule
import uk.co.siland.culvery.core.ui.test.assertClickablesHaveRoleAndLabel

/**
 * The full-screen editor wired to a real repository, editor and access rules, as EventDetailHostTest wires the detail
 * sheet; PIN pads are answered from a queue, and access and the editor share `access.toasts`. Opened through the
 * shell's overlay ([RecordingOverlay]) beside the week or Home's card, as the app has it.
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
    private val swim = EventRef("c1", "s-family", "swim")
    private val overlay = RecordingOverlay()
    private val navigator = RecordingNavigator()
    private val weekFocus = WeekFocus()
    private var closed = 0
    private val deleting = mutableListOf<EventRef>()

    /** The editor's "now": a fixed morning on Wednesday 23 September; a test may move it. */
    private var now: Long = millisAt(day, 9, 0)

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
        val swimStart = day.atTime(7, 0).atZone(london).toInstant()
        store.applySync(
            "c1", "s-family", DateRange(day.minusDays(1), day.plusDays(14), london),
            SyncResult(
                listOf(
                    RemoteEvent("dinner", "Dinner with Jo & Priya", EventTime.Timed(start), EventTime.Timed(start.plusSeconds(5_400)), false, null, access.alex.id.value),
                    RemoteEvent("swim", "Swim", EventTime.Timed(swimStart), EventTime.Timed(swimStart.plusSeconds(3_600)), true, access.sam.id.value, null),
                ),
                emptyList(), null, fullReplace = true,
            ),
        )
        repo = CalendarRepository(store, household, zone, emptySet(), setOf(writer))
        editor = testEditor(store, setOf(writer), access.control, access.toasts, zone, WallClock { now }, scope)
    }

    @After
    fun tearDown() {
        scope.cancel()
        calendar.close()
        householdDb.close()
    }

    private fun showEditor(request: EditorRequest) = compose.setContent {
        CompositionLocalProvider(LocalShellNavigator provides navigator) {
            CulveryTheme(dark = true) {
                EventEditorHost(request, repo, editor, weekFocus, onClose = { closed++ }, onDeleteAuthorised = { deleting += it })
            }
        }
    }

    /** The shell's overlay as the app has it, over [under]: whatever [open] shows, swaps included. */
    private fun showOverlay(under: @Composable () -> Unit = {}, open: OverlayHost.() -> Unit = {}) {
        compose.setContent {
            CompositionLocalProvider(
                LocalOverlayHost provides overlay,
                LocalShellNavigator provides navigator,
                LocalUserActivity provides FakeUserActivity(),
            ) {
                CulveryTheme(dark = true) {
                    Box {
                        under()
                        overlay.content?.invoke()
                    }
                }
            }
        }
        compose.runOnIdle { overlay.open() }
    }

    private fun showWeek() = showOverlay(under = { WeekViewHost(repo, editor, weekFocus, day, nowMillis = now, onReconnect = {}) })

    private fun newEvent(on: LocalDate?, from: EditorFrom = EditorFrom.Calendar) =
        showOverlay { showEditor(EditorRequest.New(on, from), day, repo, editor, weekFocus) }

    private fun waitForText(text: String) =
        compose.waitUntil(5_000) { compose.onAllNodesWithText(text).fetchSemanticsNodes().isNotEmpty() }

    private fun waitForTag(tag: String) =
        compose.waitUntil(5_000) { compose.onAllNodesWithTag(tag, useUnmergedTree = true).fetchSemanticsNodes().isNotEmpty() }

    private fun rowValue(row: String) = compose.onNodeWithTag("editor_row_${row}_value", useUnmergedTree = true)

    private fun millisAt(date: LocalDate, hour: Int, minute: Int) = date.atTime(hour, minute).atZone(london).toInstant().toEpochMilli()

    /** A title and Alex: what a new event with its day needs before Add event. */
    private fun fillTitleAndAlex(title: String = "Dinner") {
        compose.onNodeWithTag("editor_title").performTextInput(title)
        compose.onNodeWithTag("who_Alex").performClick()
    }

    @Test
    fun theWeeksAddEventOpensFullScreenWithNoDay() {
        showWeek()
        waitForTag("week_add_event")
        compose.onNodeWithTag("week_add_event").performClick()
        waitForText("New event")
        rowValue("day").assertTextEquals("Choose a day")
        assertThat(overlay.fullScreen).isTrue()
        assertThat(compose.onNodeWithTag("editor_screen").getUnclippedBoundsInRoot()).isEqualTo(compose.onRoot().getUnclippedBoundsInRoot())
        compose.assertClickablesHaveRoleAndLabel()
    }

    @Test
    fun anEmptyColumnOpensOnItsDay() {
        showWeek()
        val friday = day.plusDays(2)
        waitForTag("week_add_$friday")
        compose.onNodeWithTag("week_add_$friday", useUnmergedTree = true).performClick()
        waitForText("New event")
        rowValue("day").assertTextEquals("Fri 25 Sep")
        assertThat(overlay.fullScreen).isTrue()
        compose.assertClickablesHaveRoleAndLabel()
    }

    @Test
    fun homesPlusOpensOnToday() {
        showOverlay(under = { TodayCardHost(repo, editor, weekFocus, day) })
        waitForTag("today_add")
        compose.onNodeWithTag("today_add").performClick()
        waitForText("New event")
        rowValue("day").assertTextEquals("Wed 23 Sep")
        compose.onNodeWithTag("editor_context", useUnmergedTree = true).assertTextEquals("From Home")
        assertThat(overlay.fullScreen).isTrue()
        compose.assertClickablesHaveRoleAndLabel()
    }

    @Test
    fun detailsChangeOpensChangeEventWithEveryRowFilled() {
        showOverlay { showDetail(dinner, day, repo, editor, weekFocus, EditorFrom.Calendar) }
        waitForText("Created by")
        compose.onNodeWithTag("detail_edit").performClick()
        waitForText("Change event")
        assertThat(overlay.fullScreen).isTrue()
        rowValue("who").assertTextEquals("Everyone")
        rowValue("day").assertTextEquals("Wed 23 Sep")
        rowValue("time").assertTextEquals("19:30–21:00")
        compose.onNodeWithTag("editor_question", useUnmergedTree = true).assertTextEquals("Check it")
        compose.onNodeWithTag("editor_title").assertTextEquals("Dinner with Jo & Priya").assertIsNotFocused()
        compose.onNodeWithTag("detail_sheet").assertDoesNotExist()
        assertThat(access.requests).isEmpty()
        compose.assertClickablesHaveRoleAndLabel()
    }

    /** 9 §3.3: the detail's For row asks who it's for in the editor, where a set can be chosen. */
    @Test
    fun theDetailsForRowOpensTheEditorOnWho() {
        showOverlay { showDetail(dinner, day, repo, editor, weekFocus, EditorFrom.Calendar) }
        waitForText("Created by")
        compose.onNodeWithTag("detail_for")
            .assert(SemanticsMatcher.expectValue(SemanticsProperties.Role, androidx.compose.ui.semantics.Role.Button))
        compose.assertClickablesHaveRoleAndLabel()
        compose.onNodeWithTag("detail_for").performClick()
        waitForText("Change event")
        compose.onNodeWithTag("editor_question", useUnmergedTree = true).assertTextEquals("Who’s it for?")
        compose.onNodeWithTag("editor_row_who").assertIsSelected()
        compose.assertClickablesHaveRoleAndLabel()
        // A repeating event can't change here, so its For row opens nothing.
        compose.runOnIdle { overlay.showDetail(swim, day, repo, editor, weekFocus, EditorFrom.Calendar) }
        waitForText("Repeating event")
        compose.onNodeWithTag("detail_for").assert(SemanticsMatcher.keyNotDefined(SemanticsActions.OnClick))
        compose.assertClickablesHaveRoleAndLabel()
    }

    @Test
    fun savingANewEventAsksForThePinAddsItAndCloses() {
        access.answer(TestAccess.ALEX)
        showEditor(EditorRequest.New(day = day, from = EditorFrom.Calendar))
        waitForText("Add event")
        compose.onNodeWithTag("editor_title").assertIsFocused()
        fillTitleAndAlex("Parents evening")
        compose.onNodeWithTag("editor_save").performClick()
        compose.waitUntil(5_000) { closed > 0 }
        assertThat(writer.calls).containsExactly("create:Parents evening")
        assertThat(writer.drafts.single().createdBy).isEqualTo(access.alex.id.value)
        assertThat(writer.drafts.single().forPerson).isEqualTo(access.alex.id.value)
        assertThat(access.requests.single().reason).isEqualTo(PinReason.Save)
        assertThat(access.toasts.messages).containsExactly("Added Parents evening.")
        assertThat(access.toasts.actions.single().label).isEqualTo(UNDO)
        compose.assertClickablesHaveRoleAndLabel()
    }

    @Test
    fun tomorrowChosenAt2330IsStillThatDateWhenSavedAfterMidnight() {
        // Opened at 23:30 on Saturday 24 October; Save is tapped at 00:05, when "Tomorrow" would mean the 26th.
        now = millisAt(LocalDate.of(2026, 10, 24), 23, 30)
        access.answer(TestAccess.ALEX)
        showEditor(EditorRequest.New(day = null, from = EditorFrom.Calendar))
        waitForText("Add event")
        fillTitleAndAlex("Bonfire")
        compose.onNodeWithTag("editor_row_day").performClick()
        compose.onNodeWithTag("day_quick_1").assertTextEquals("Tomorrow").performClick()
        now = millisAt(LocalDate.of(2026, 10, 25), 0, 5)
        compose.onNodeWithTag("editor_save").performClick()
        compose.waitUntil(5_000) { closed > 0 }
        assertThat(writer.drafts.single().start).isEqualTo(EventTime.AllDay(LocalDate.of(2026, 10, 25)))
        compose.assertClickablesHaveRoleAndLabel()
    }

    /** 9 §3.1 After Add: the week moves to the event's first date, and its chip is there. */
    @Test
    fun afterAddTheWeekShowsTheEventsFirstDate() {
        access.answer(TestAccess.ALEX)
        showWeek()
        waitForTag("week_add_event")
        compose.runOnIdle { overlay.showEditor(EditorRequest.New(day.plusDays(9), EditorFrom.Calendar), day, repo, editor, weekFocus) }
        waitForTag("editor_title")
        fillTitleAndAlex()
        compose.onNodeWithTag("editor_save").performClick()
        compose.waitUntil(5_000) { overlay.dismissed > 0 }
        waitForText("Dinner")
        compose.onNode(androidx.compose.ui.test.hasText("Next week", substring = true)).assertExists()
        assertThat(weekFocus.requested.value).isNull()
        assertThat(navigator.tabs).isEmpty()
        assertThat(access.toasts.messages).containsExactly("Added Dinner.")
        compose.assertClickablesHaveRoleAndLabel()
    }

    @Test
    fun afterAddPastTheWeeksReachTheWeekStays() {
        access.answer(TestAccess.ALEX)
        showWeek()
        waitForTag("week_add_event")
        compose.runOnIdle { overlay.showEditor(EditorRequest.New(day.plusDays(40), EditorFrom.Calendar), day, repo, editor, weekFocus) }
        waitForTag("editor_title")
        fillTitleAndAlex()
        compose.onNodeWithTag("editor_save").performClick()
        compose.waitUntil(5_000) { overlay.dismissed > 0 }
        compose.waitForIdle()
        compose.onNode(androidx.compose.ui.test.hasText("This week", substring = true)).assertExists()
        assertThat(weekFocus.requested.value).isNull()
        assertThat(access.toasts.messages).containsExactly("Added Dinner, Mon 2 Nov.")
        assertThat(access.toasts.actions.single().label).isEqualTo(UNDO)
        compose.assertClickablesHaveRoleAndLabel()
    }

    /** 9 §3.1: a day before today can't show on the week either, so the toast names it. */
    @Test
    fun afterAddOnAPastDayTheToastNamesTheDate() {
        access.answer(TestAccess.ALEX)
        newEvent(day.minusDays(1))
        waitForText("Add event")
        fillTitleAndAlex()
        compose.onNodeWithTag("editor_save").performClick()
        compose.waitUntil(5_000) { overlay.dismissed > 0 }
        assertThat(weekFocus.requested.value).isNull()
        assertThat(access.toasts.messages).containsExactly("Added Dinner, Tue 22 Sep.")
        compose.assertClickablesHaveRoleAndLabel()
    }

    @Test
    fun afterAddFromHomeTheCalendarOpensOnTheEventsWeek() {
        access.answer(TestAccess.ALEX)
        newEvent(day.plusDays(9), EditorFrom.Home)
        waitForText("Add event")
        fillTitleAndAlex()
        compose.onNodeWithTag("editor_save").performClick()
        compose.waitUntil(5_000) { overlay.dismissed > 0 }
        assertThat(navigator.tabs).containsExactly(CALENDAR_TAB_ID)
        assertThat(weekFocus.requested.value).isEqualTo(1)
        compose.assertClickablesHaveRoleAndLabel()
    }

    /** 9 §3.4: from Home, a date the week can't show stays on Home, and the toast names it. */
    @Test
    fun afterAddFromHomePastTheWeeksReachStaysOnHome() {
        access.answer(TestAccess.ALEX)
        newEvent(day.plusDays(40), EditorFrom.Home)
        waitForText("Add event")
        fillTitleAndAlex()
        compose.onNodeWithTag("editor_save").performClick()
        compose.waitUntil(5_000) { overlay.dismissed > 0 }
        assertThat(navigator.tabs).isEmpty()
        assertThat(weekFocus.requested.value).isNull()
        assertThat(access.toasts.messages).containsExactly("Added Dinner, Mon 2 Nov.")
        compose.assertClickablesHaveRoleAndLabel()
    }

    /** 9 §3.3: an untagged event is for its calendar's person, so it opens with that person chosen. */
    @Test
    fun anUntaggedEventOnACalendarMappedToSamOpensWithSamChosen() {
        runBlocking { store.setMapping("c1", "s-family", access.sam.id, visible = true) }
        showEditor(EditorRequest.Edit(dinner, EditorFrom.Calendar, start = EditorStep.Who))
        waitForText("Change event")
        rowValue("who").assertTextEquals("Sam")
        compose.onNodeWithTag("who_Sam").assertIsOn()
        compose.onNodeWithTag("who_Alex").assertIsOff()
        compose.assertClickablesHaveRoleAndLabel()
    }

    /** A child's lock is the UI's; the editor's own gate still refuses a set once the session has gone (9 §3.3). */
    @Test
    fun aChildWhoseSessionEndsCannotSaveForSomeoneElse() {
        access.answer(TestAccess.MIA, TestAccess.MIA)
        runBlocking { access.control.authorise(CalendarPermissions.CREATE_SELF) }
        newEvent(day)
        waitForText("Add event")
        compose.onNodeWithTag("who_Mia").assertIsOn()
        compose.onNodeWithTag("editor_title").performTextInput("Sleepover")
        compose.runOnIdle { access.control.lock() }
        waitForText("From Calendar")
        compose.waitUntil(5_000) { compose.onAllNodesWithTag("who_Alex_lock", useUnmergedTree = true).fetchSemanticsNodes().isEmpty() }
        // With the lock gone, Alex can be picked as toggleWho(alex) would.
        compose.onNodeWithTag("who_Alex").performClick()
        compose.onNodeWithTag("editor_save").performClick()
        compose.waitUntil(5_000) { access.toasts.messages.isNotEmpty() }
        assertThat(access.toasts.messages).containsExactly(cannotAddForOthers("Mia"))
        // The first is the sign-in.
        assertThat(access.requests.map { it.reason }).containsExactly(PinReason.Generic, PinReason.Save).inOrder()
        assertThat(writer.calls).isEmpty()
        assertThat(runBlocking { store.pendingNow() }).isEmpty()
        assertThat(overlay.dismissed).isEqualTo(0)
        compose.assertClickablesHaveRoleAndLabel()
    }

    @Test
    fun aSignedInChildsOtherTilesExplainByToast() {
        access.answer(TestAccess.MIA)
        runBlocking { access.control.authorise(CalendarPermissions.CREATE_SELF) }
        newEvent(day)
        waitForText("Add event")
        // The tiles come from the household's members, with their roles.
        compose.onNodeWithText("Child", useUnmergedTree = true).assertExists()
        compose.onNodeWithText("Admin", useUnmergedTree = true).assertExists()
        compose.onNodeWithTag("who_Sam").performClick()
        assertThat(access.toasts.messages).containsExactly(cannotAddForOthers("Mia"))
        compose.onNodeWithTag("who_Sam").assertIsOff()
        compose.onNodeWithTag("who_Mia").assertIsOn()
        compose.assertClickablesHaveRoleAndLabel()
    }

    @Test
    fun theLockedTilesFollowTheSessionRole() {
        access.answer(TestAccess.MIA)
        runBlocking { access.control.authorise(CalendarPermissions.CREATE_SELF) }
        newEvent(day)
        waitForText("Add event")
        compose.onNodeWithTag("who_Sam_lock", useUnmergedTree = true).assertExists()
        compose.onNodeWithTag("editor_row_repeats").assertDoesNotExist()
        compose.onNodeWithTag("editor_context", useUnmergedTree = true).assertTextEquals("From Calendar · Mia signed in")
        compose.runOnIdle { access.control.lock() }
        compose.waitUntil(5_000) { compose.onAllNodesWithTag("who_Sam_lock", useUnmergedTree = true).fetchSemanticsNodes().isEmpty() }
        compose.onNodeWithTag("who_Sam").performClick()
        compose.onNodeWithTag("who_Sam").assertIsOn()
        assertThat(access.toasts.messages).isEmpty()
        compose.assertClickablesHaveRoleAndLabel()
    }

    /** An unedited editor asks the shell for the idle close; its 2-minute timing is tested in app OverlayLayersTest. */
    @Test
    fun idleCloseStillApplies() {
        newEvent(null)
        waitForText("Add event")
        assertThat(overlay.fullScreen).isTrue()
        assertThat(overlay.closesWhenIdle).isTrue()
        assertThat(overlay.edited).isFalse()
        compose.assertClickablesHaveRoleAndLabel()
    }

    /** Only a day chosen counts as edited, so the shell waits 10 minutes (timed in app OverlayLayersTest); ✕ asks first. */
    @Test
    fun anEditorWithOnlyADayChosenWaitsTenMinutesAndAsksBeforeDiscarding() {
        newEvent(null)
        waitForText("Add event")
        compose.onNodeWithTag("editor_row_day").performClick()
        compose.onNodeWithTag("day_quick_0").performClick()
        compose.waitUntil(5_000) { overlay.edited }
        compose.onNodeWithTag("sheet_close").performClick()
        compose.onNodeWithText("Discard this event?").assertExists()
        assertThat(overlay.dismissed).isEqualTo(0)
        compose.onNodeWithTag("editor_discard_confirm").performClick()
        compose.runOnIdle { assertThat(overlay.dismissed).isEqualTo(1) }
        assertThat(writer.calls).isEmpty()
        compose.assertClickablesHaveRoleAndLabel()
    }

    /** A pin: the editor's gate refuses a series for a child whoever set it up. */
    @Test
    fun aRepeatSetByAnAdultCannotBeSavedByAChild() {
        access.answer(TestAccess.ALEX, TestAccess.MIA)
        runBlocking { access.control.authorise(CalendarPermissions.CREATE) }
        newEvent(day.plusDays(3))
        waitForText("Add event")
        compose.onNodeWithTag("editor_title").performTextInput("Piano")
        compose.onNodeWithTag("who_Mia").performClick()
        compose.onNodeWithTag("editor_row_repeats").performClick()
        compose.onNodeWithTag("repeat_Weekly").performClick()
        rowValue("repeats").assertTextEquals("Every Saturday")
        compose.runOnIdle { access.control.lock() }
        waitForText("From Calendar")
        compose.onNodeWithTag("editor_save").performClick()
        compose.waitUntil(5_000) { access.toasts.messages.isNotEmpty() }
        assertThat(access.toasts.messages).containsExactly(CANT_REPEAT_FOR_CHILD)
        assertThat(writer.calls).isEmpty()
        assertThat(runBlocking { store.pendingNow() }).isEmpty()
        compose.assertClickablesHaveRoleAndLabel()
    }

    @Test
    fun aRefusedSaveKeepsTheEditorAndItsInputAndOffersTryAgain() {
        writer.failWith = WriteRejectedException("calendar is full")
        access.answer(TestAccess.ALEX)
        showEditor(EditorRequest.New(day = day, from = EditorFrom.Calendar))
        waitForText("Add event")
        fillTitleAndAlex("Parents evening")
        compose.onNodeWithTag("editor_save").performClick()
        waitForText("Try again")
        compose.onNodeWithText(couldNotSave("Sample calendar", WriteProblem.Refused("calendar is full"))).assertExists()
        compose.onNodeWithTag("editor_title").assertTextEquals("Parents evening")
        assertThat(closed).isEqualTo(0)
        assertThat(access.toasts.messages).isEmpty()
        writer.failWith = null
        compose.onNodeWithTag("editor_save").performClick()
        compose.onNodeWithTag("editor_failure").assertDoesNotExist()
        compose.waitUntil(5_000) { closed > 0 }
        assertThat(writer.created).hasSize(1)
        assertThat(access.toasts.messages).containsExactly("Added Parents evening.")
        compose.assertClickablesHaveRoleAndLabel()
    }

    @Test
    fun aStoreFailureBeforeTheWriteShowsTheCardAndKeepsTheEditor() {
        showEditor(EditorRequest.Edit(dinner, EditorFrom.Calendar))
        waitForText("Change event")
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
        compose.onNodeWithTag("editor_row_time").performClick()
        compose.onNodeWithTag("length_120").performClick()
        compose.onNodeWithTag("editor_save").performClick()
        waitForText(couldNotSave("Sample calendar", WriteProblem.Tablet))
        compose.onNodeWithTag("editor_failure").assertExists()
        compose.onNodeWithTag("editor_save").assertIsEnabled()
        assertThat(closed).isEqualTo(0)
        assertThat(writer.calls).isEmpty()
        compose.assertClickablesHaveRoleAndLabel()
    }

    @Test
    fun anUnchangedEditClosesWithoutAPin() {
        showEditor(EditorRequest.Edit(dinner, EditorFrom.Calendar))
        waitForText("Change event")
        compose.onNodeWithTag("editor_save").performClick()
        compose.waitUntil(5_000) { closed > 0 }
        assertThat(access.requests).isEmpty()
        assertThat(writer.calls).isEmpty()
        compose.assertClickablesHaveRoleAndLabel()
    }

    @Test
    fun savingAnEditSendsTheChange() {
        access.answer(TestAccess.SAM)
        showEditor(EditorRequest.Edit(dinner, EditorFrom.Calendar))
        waitForText("Change event")
        compose.onNodeWithTag("editor_row_time").performClick()
        compose.onNodeWithTag("length_120").performClick()
        compose.onNodeWithTag("editor_save").performClick()
        compose.waitUntil(5_000) { closed > 0 }
        assertThat(writer.calls).containsExactly("update:dinner")
        // Only the length was changed, so only the times are sent (3a design C3).
        assertThat(writer.fieldSets.single()).containsExactly(EventField.TIMES)
        assertThat(access.requests.single().reason).isEqualTo(PinReason.Edit)
        assertThat(access.toasts.messages).containsExactly(CHANGES_SAVED)
        // A change doesn't move the week.
        assertThat(weekFocus.requested.value).isNull()
        compose.assertClickablesHaveRoleAndLabel()
    }

    @Test
    fun saveIgnoresASecondTapWhileBusy() {
        val gate = CompletableDeferred<Unit>()
        writer.gate = gate
        // A second answer is queued, so a second save that got through would reach the writer too.
        access.answer(TestAccess.ALEX, TestAccess.ALEX)
        showEditor(EditorRequest.New(day = day.plusDays(9), from = EditorFrom.Calendar))
        waitForText("Add event")
        fillTitleAndAlex("Parents evening")
        compose.onNodeWithTag("editor_row_day").performClick()
        val chosen = rowValue("day").fetchSemanticsNode().config[SemanticsProperties.Text].joinToString()
        compose.onNodeWithTag("editor_save").performClick()
        compose.waitUntil(5_000) { writer.entered.isCompleted }
        compose.onNodeWithTag("editor_save").performClick()
        // Nothing can change under a save in flight: it would close with that edit lost.
        compose.onNodeWithTag("editor_title").assert(SemanticsMatcher.keyNotDefined(SemanticsActions.SetText))
        compose.onNodeWithTag("day_quick_0").performClick()
        rowValue("day").assertTextEquals(chosen)
        // No other pane opens either; Who's own tiles ignore taps too (WhoPaneTest).
        compose.onNodeWithTag("editor_row_who").performClick()
        compose.onNodeWithTag("editor_row_time").performClick()
        compose.onNodeWithTag("editor_row_repeats").performClick()
        compose.onNodeWithTag("editor_row_day").assertIsSelected()
        compose.assertClickablesHaveRoleAndLabel()
        gate.complete(Unit)
        compose.waitUntil(5_000) { closed > 0 }
        assertThat(writer.calls).containsExactly("create:Parents evening")
        assertThat(writer.drafts.single().start).isEqualTo(EventTime.AllDay(day.plusDays(9)))
        // The week goes to the date that was saved.
        assertThat(weekFocus.requested.value).isEqualTo(1)
        compose.assertClickablesHaveRoleAndLabel()
    }

    @Test
    fun deleteStillAsksForAPinThenHandsOverToTheDetailSheet() {
        access.answer(TestAccess.ALEX)
        showOverlay { showEditor(EditorRequest.Edit(dinner, EditorFrom.Calendar), day, repo, editor, weekFocus) }
        waitForText("Change event")
        compose.onNodeWithTag("editor_delete").performClick()
        waitForText("Delete this event?")
        assertThat(access.requests.map { it.reason }).containsExactly(PinReason.Delete)
        assertThat(overlay.fullScreen).isFalse()
        assertThat(writer.calls).isEmpty()
        compose.onNodeWithTag("detail_confirm_delete").performClick()
        compose.waitUntil(5_000) { overlay.dismissed > 0 }
        assertThat(writer.calls).containsExactly("delete:dinner")
        assertThat(access.requests).hasSize(1)
        compose.assertClickablesHaveRoleAndLabel()
    }

    @Test
    fun aRefusedDeleteKeepsTheEditorOpen() {
        access.answer(TestAccess.MIA)
        showEditor(EditorRequest.Edit(dinner, EditorFrom.Calendar))
        waitForText("Change event")
        compose.onNodeWithTag("editor_delete").performClick()
        compose.waitUntil(5_000) { access.toasts.messages.isNotEmpty() }
        assertThat(access.toasts.messages).containsExactly("Mia can only change events they created.")
        assertThat(deleting).isEmpty()
        compose.onNodeWithText("Change event").assertExists()
        compose.assertClickablesHaveRoleAndLabel()
    }

    @Test
    fun theEditorClosesWhenItsEventHasGone() {
        showEditor(EditorRequest.Edit(EventRef("c1", "s-family", "nope"), EditorFrom.Calendar))
        compose.waitUntil(5_000) { closed > 0 }
        compose.assertClickablesHaveRoleAndLabel()
    }

    @Test
    fun anEditWhoseEventHasGoneClosesOnSaveWithoutAPin() {
        showEditor(EditorRequest.Edit(dinner, EditorFrom.Calendar))
        waitForText("Change event")
        // Deleted on a phone, then synced, while the editor was open.
        runBlocking { store.applyDeleted(dinner) }
        compose.onNodeWithTag("editor_row_time").performClick()
        compose.onNodeWithTag("length_120").performClick()
        compose.onNodeWithTag("editor_save").performClick()
        compose.waitUntil(5_000) { closed > 0 }
        assertThat(access.requests).isEmpty()
        assertThat(access.toasts.messages).isEmpty()
        compose.assertClickablesHaveRoleAndLabel()
    }

    @Test
    fun closeClosesAnUneditedEditor() {
        newEvent(null)
        waitForText("Add event")
        compose.onNodeWithTag("sheet_close").performClick()
        compose.runOnIdle { assertThat(overlay.dismissed).isEqualTo(1) }
        compose.assertClickablesHaveRoleAndLabel()
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
        showOverlay { showDetail(created, day, repo, editor, weekFocus, EditorFrom.Calendar) }
        waitForText("Sleepover")
        compose.onNodeWithTag("detail_syncing").assertExists()
        compose.onNodeWithTag("detail_edit").performClick()
        waitForText("Change event")
        rowValue("who").assertTextEquals("Mia")
        compose.onNodeWithTag("editor_title").performTextReplacement("Sleepover at Ava's")
        compose.onNodeWithTag("editor_save").performClick()
        compose.waitUntil(5_000) { overlay.dismissed > 0 }
        // Queued behind its create, never sent ahead of it.
        assertThat(writer.calls).isEmpty()
        assertThat(runBlocking { store.pendingNow() }.map { it.kind to it.ref })
            .containsExactly(ChangeKind.CREATE to created, ChangeKind.UPDATE to created).inOrder()
        assertThat(access.toasts.messages).containsExactly(CHANGES_SAVED)
        compose.assertClickablesHaveRoleAndLabel()
    }

    @Test
    fun eachOpeningGetsAFreshEditor() {
        newEvent(null)
        waitForText("Add event")
        compose.onNodeWithTag("editor_title").performTextInput("Half-typed")
        compose.onNodeWithTag("who_Sam").performClick()
        compose.runOnIdle { overlay.showEditor(EditorRequest.Edit(dinner, EditorFrom.Calendar), day, repo, editor, weekFocus) }
        waitForText("Change event")
        compose.onNodeWithTag("editor_title").assertTextEquals("Dinner with Jo & Priya").assertIsNotFocused()
        rowValue("time").assertTextEquals("19:30–21:00")
        compose.runOnIdle { overlay.showEditor(EditorRequest.New(null, EditorFrom.Calendar), day, repo, editor, weekFocus) }
        waitForText("Add event")
        compose.onNodeWithTag("editor_title").assertIsFocused()
            .assert(SemanticsMatcher.expectValue(SemanticsProperties.EditableText, AnnotatedString("")))
        rowValue("who").assertTextEquals("Choose who")
        compose.onNodeWithTag("who_Sam").assertIsOff()
        compose.assertClickablesHaveRoleAndLabel()
    }

    @Test
    fun tryAgainAfterATabletFailureReusesTheKeyAndMakesOneEvent() {
        // The provider makes the event, the connection drops before its reply, and queueing it fails on the tablet.
        writer.dropNextReply = true
        runBlocking {
            calendar.useWriterConnection {
                it.execSQL("CREATE TRIGGER fail_outbox BEFORE INSERT ON outbox BEGIN SELECT RAISE(ABORT, 'disk full'); END")
            }
        }
        access.answer(TestAccess.ALEX)
        showEditor(EditorRequest.New(day = day, from = EditorFrom.Calendar))
        waitForText("Add event")
        fillTitleAndAlex("Parents evening")
        compose.onNodeWithTag("editor_save").performClick()
        waitForText(couldNotSave("Sample calendar", WriteProblem.Tablet))
        runBlocking { calendar.useWriterConnection { it.execSQL("DROP TRIGGER fail_outbox") } }
        compose.onNodeWithTag("editor_save").performClick()
        compose.waitUntil(5_000) { closed > 0 }
        // The same key both times: the provider returned the event it had made.
        assertThat(writer.calls).containsExactly("create:Parents evening", "create:Parents evening")
        assertThat(writer.created).hasSize(1)
        compose.assertClickablesHaveRoleAndLabel()
    }

    @Test
    fun aSheetThatCannotLoadClosesAndSaysSo() {
        // A row with neither a start time nor a start date can't be read, so loading the event throws.
        runBlocking {
            calendar.useWriterConnection {
                it.execSQL(
                    "INSERT INTO event (connectionId, sourceId, remoteId, title, startInstant, startDate, endInstant, endDate, " +
                        "recurring, forPerson, createdBy, startSort, endSort) " +
                        "VALUES ('c1', 's-family', 'broken', 'Broken', NULL, NULL, NULL, NULL, 0, NULL, NULL, 0, 0)",
                )
            }
        }
        showEditor(EditorRequest.Edit(EventRef("c1", "s-family", "broken"), EditorFrom.Calendar))
        compose.waitUntil(5_000) { closed > 0 }
        assertThat(access.toasts.messages).containsExactly(COULD_NOT_OPEN)
        assertThat(access.toasts.urgent).isEqualTo(access.toasts.messages)
        compose.assertClickablesHaveRoleAndLabel()
    }

    @Test
    fun aStoreFailureInTheEditorsDeleteToastsLikeTheDetailSheet() {
        showEditor(EditorRequest.Edit(dinner, EditorFrom.Calendar))
        waitForText("Change event")
        // An unreadable queued row, which reading the queue deletes, and a disk that refuses the delete.
        runBlocking {
            calendar.useWriterConnection {
                it.execSQL(
                    "INSERT INTO outbox (connectionId, sourceId, remoteId, kind, draftJson, attempts, nextAttemptMillis, createdMillis) " +
                        "VALUES ('c1', 's-family', 'dinner', 'MOVE', NULL, 0, 0, 0)",
                )
                it.execSQL("CREATE TRIGGER fail_outbox BEFORE DELETE ON outbox BEGIN SELECT RAISE(ABORT, 'disk full'); END")
            }
        }
        compose.onNodeWithTag("editor_delete").performClick()
        compose.waitUntil(5_000) { access.toasts.messages.isNotEmpty() }
        assertThat(access.toasts.messages).containsExactly(couldNotDelete("Sample calendar", WriteProblem.Tablet))
        assertThat(access.toasts.urgent).isEqualTo(access.toasts.messages)
        compose.onNodeWithTag("editor_failure").assertDoesNotExist()
        assertThat(deleting).isEmpty()
        compose.assertClickablesHaveRoleAndLabel()
    }

    @Test
    fun typingATitleMarksTheSheetEditedAndClearingItDoesNot() {
        newEvent(null)
        waitForText("Add event")
        compose.onNodeWithTag("editor_title").performTextInput("Swim")
        compose.waitUntil(5_000) { overlay.edited }
        compose.onNodeWithTag("editor_title").performTextReplacement("")
        compose.waitUntil(5_000) { !overlay.edited }
        assertThat(overlay.closesWhenIdle).isTrue()
        compose.assertClickablesHaveRoleAndLabel()
    }

    /** While the PIN pad of a Save or Delete is up the editor counts as edited, so the shell gives it the longer wait. */
    @Test
    fun anEditorWaitingOnItsPinPadCountsAsEditedUntilThePadGoes() {
        showOverlay { showEditor(EditorRequest.Edit(dinner, EditorFrom.Calendar), day, repo, editor, weekFocus) }
        waitForText("Change event")
        assertThat(overlay.edited).isFalse()
        compose.onNodeWithTag("editor_delete").performClick()
        compose.waitUntil(5_000) { access.requests.isNotEmpty() }
        compose.waitUntil(5_000) { overlay.edited }
        compose.runOnIdle { access.prompt.cancel() }
        compose.waitUntil(5_000) { !overlay.edited }
        assertThat(deleting).isEmpty()
        compose.assertClickablesHaveRoleAndLabel()
    }

    /** Closing by itself is the overlay's dismiss: an edited editor goes with its edits and nothing is saved. */
    @Test
    fun dismissingAnEditedSheetSavesNothing() {
        newEvent(null)
        waitForText("Add event")
        compose.onNodeWithTag("editor_title").performTextInput("Swim")
        compose.waitUntil(5_000) { overlay.edited }
        compose.runOnIdle { overlay.dismiss() }
        compose.waitForIdle()
        assertThat(writer.calls).isEmpty()
        assertThat(access.requests).isEmpty()
        assertThat(overlay.edited).isFalse()
        compose.assertClickablesHaveRoleAndLabel()
    }

    @Test
    fun theContextLineSaysWhereFromAndWho() {
        access.answer(TestAccess.ALEX)
        runBlocking { access.control.authorise(CalendarPermissions.CREATE) }
        newEvent(null)
        waitForText("Add event")
        compose.onNodeWithTag("editor_context", useUnmergedTree = true).assertTextEquals("From Calendar · Alex signed in")
        compose.runOnIdle { access.control.lock() }
        compose.onNodeWithTag("editor_context", useUnmergedTree = true).assertTextEquals("From Calendar")
        compose.runOnIdle { overlay.showEditor(EditorRequest.New(null, EditorFrom.Home), day, repo, editor, weekFocus) }
        waitForText("From Home")
        assertThat(contextLine(EditorFrom.Home, null)).isEqualTo("From Home")
        assertThat(contextLine(EditorFrom.Home, "Sam")).isEqualTo("From Home · Sam signed in")
        compose.assertClickablesHaveRoleAndLabel()
    }

    @Test
    fun theFootSaysWhereANewEventAndAChangeSave() {
        newEvent(null)
        waitForText("Add event")
        compose.onNodeWithTag("editor_saves_to", useUnmergedTree = true).assertExists()
        compose.onNodeWithText("Saves to the Family calendar", useUnmergedTree = true).assertExists()
        compose.runOnIdle { overlay.showEditor(EditorRequest.Edit(dinner, EditorFrom.Calendar), day, repo, editor, weekFocus) }
        waitForText("Change event")
        compose.onNodeWithText("Saves to the Family calendar", useUnmergedTree = true).assertExists()
        compose.onNodeWithTag("editor_row_who").assertIsNotSelected()
        compose.assertClickablesHaveRoleAndLabel()
    }
}
