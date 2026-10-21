package uk.co.siland.culvery.capability.calendar.ui

import android.os.Looper
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertIsOn
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.isEnabled
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.google.common.truth.Truth.assertThat
import java.time.LocalDate
import java.time.ZoneId
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Shadows.shadowOf
import uk.co.siland.culvery.capability.calendar.ASK_AN_ADULT
import uk.co.siland.culvery.capability.calendar.CalendarEditor
import uk.co.siland.culvery.capability.calendar.CalendarRepository
import uk.co.siland.culvery.capability.calendar.CalendarSource
import uk.co.siland.culvery.capability.calendar.CalendarStore
import uk.co.siland.culvery.capability.calendar.DateRange
import uk.co.siland.culvery.capability.calendar.EventRef
import uk.co.siland.culvery.capability.calendar.EventTime
import uk.co.siland.culvery.capability.calendar.RemoteEvent
import uk.co.siland.culvery.capability.calendar.ScriptedWriter
import uk.co.siland.culvery.capability.calendar.SyncResult
import uk.co.siland.culvery.capability.calendar.TestAccess
import uk.co.siland.culvery.capability.calendar.WriteProblem
import uk.co.siland.culvery.capability.calendar.WriteRejectedException
import uk.co.siland.culvery.capability.calendar.calendarDb
import uk.co.siland.culvery.capability.calendar.couldNotDelete
import uk.co.siland.culvery.capability.calendar.db.CalendarDatabase
import uk.co.siland.culvery.capability.calendar.householdDb
import uk.co.siland.culvery.capability.calendar.testAccess
import uk.co.siland.culvery.capability.calendar.testEditor
import uk.co.siland.culvery.core.household.HomeLocation
import uk.co.siland.culvery.core.household.HouseholdRepository
import uk.co.siland.culvery.core.household.HouseholdZone
import uk.co.siland.culvery.core.household.Person
import uk.co.siland.culvery.core.household.db.HouseholdDatabase
import uk.co.siland.culvery.core.plugin.Connection
import uk.co.siland.culvery.core.plugin.WallClock
import uk.co.siland.culvery.core.ui.CulveryTheme

/**
 * The sheet wired to a real repository, editor and access rules; PIN pads are answered from a queue. Access and
 * the editor share one recording toaster, `access.toasts`, as the app shares one.
 */
@RunWith(AndroidJUnit4::class)
class EventDetailHostTest {
    @get:Rule val compose = createComposeRule()

    private lateinit var calendar: CalendarDatabase
    private lateinit var householdDb: HouseholdDatabase
    private lateinit var store: CalendarStore
    private lateinit var household: HouseholdRepository
    private lateinit var access: TestAccess
    private lateinit var repo: CalendarRepository
    private lateinit var editor: CalendarEditor

    // The main thread: Room's continuations resume here, never on Room's own threads, so Compose state is only touched on it.
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val london = ZoneId.of("Europe/London")
    private val today = LocalDate.of(2026, 9, 23)
    private val writer = ScriptedWriter("calendar.a")
    private var closed = 0

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
        store.applySync(
            "c1", "s-family", DateRange(today.minusDays(1), today.plusDays(14), london),
            SyncResult(
                listOf(event("dinner", "Dinner with Jo & Priya", access.alex.id.value), event("plumber", "Plumber quote call", null)),
                emptyList(), null, fullReplace = true,
            ),
        )
        repo = CalendarRepository(store, household, zone, emptySet(), setOf(writer))
        editor = testEditor(
            store, setOf(writer), access.control, access.toasts, zone, WallClock { System.currentTimeMillis() }, scope,
            personOf = household::person, order = { household.people.first().map { it.id } },
        )
    }

    @After
    fun tearDown() {
        // The sheet stops reading the store before it closes; a write's last change may still be re-querying.
        shown = false
        compose.waitForIdle()
        scope.cancel()
        calendar.close()
        householdDb.close()
    }

    private fun event(id: String, title: String, createdBy: String?): RemoteEvent {
        val start = today.atTime(19, 30).atZone(london).toInstant()
        return RemoteEvent(id, title, EventTime.Timed(start), EventTime.Timed(start.plusSeconds(5_400)), false, null, createdBy)
    }

    private val swim = EventRef("c1", "s-family", "swim_1")

    /** A weekly swim on the Family calendar tagged [forPerson]: a repeating event, so its Who chips stay up. */
    private fun putSwim(forPerson: String) = runBlocking {
        store.applySync(
            "c1", "s-family", DateRange(today.minusDays(1), today.plusDays(14), london),
            SyncResult(listOf(event("swim_1", "Swimming", null).copy(recurring = true, seriesId = "swim", forPerson = forPerson)), emptyList(), null, fullReplace = false),
        )
    }

    private fun tagOf(ref: EventRef) = runBlocking { store.eventNow(ref) }?.forPerson

    private fun ids(vararg people: Person) = people.joinToString(",") { it.id.value }

    /** Opens the swim's detail and its Who chips. */
    private fun chooseFor(forPerson: String) {
        putSwim(forPerson)
        show("swim_1")
        waitForText("Swimming")
        compose.onNodeWithTag("detail_assign").performClick()
        compose.onNodeWithTag("assign_Family").assertExists()
    }

    private fun tap(name: String) = compose.onNodeWithTag("assign_$name").performClick()

    private var shown by mutableStateOf(true)

    private fun show(id: String) = compose.setContent {
        CulveryTheme(dark = true) {
            if (shown) EventDetailHost(EventRef("c1", "s-family", id), today, repo, editor, onClose = { closed++ }, onEdit = {})
        }
    }

    // The editor's scope is the main thread's looper: running it between polls keeps Compose state on that thread alone.
    private fun waitUntil(condition: () -> Boolean) = compose.waitUntil(5_000) {
        shadowOf(Looper.getMainLooper()).idle()
        condition()
    }

    private fun waitForText(text: String) =
        waitUntil { compose.onAllNodesWithText(text).fetchSemanticsNodes().isNotEmpty() }

    @Test
    fun deleteAsksForThePinBeforeTheConfirmationThenDeletesWithoutAskingAgain() {
        access.answer(TestAccess.ALEX)
        show("dinner")
        waitForText("Dinner with Jo & Priya")
        compose.onNodeWithTag("detail_delete").performClick()
        waitForText("Delete this event?")
        assertThat(access.requests).hasSize(1)
        compose.onNodeWithTag("detail_confirm_delete").performClick()
        waitUntil { closed > 0 }
        assertThat(access.requests).hasSize(1)
        assertThat(writer.calls).containsExactly("delete:dinner")
    }

    @Test
    fun aRejectedDeleteKeepsTheSheetOpen() {
        writer.failWith = WriteRejectedException("event is locked")
        access.answer(TestAccess.ALEX)
        show("dinner")
        waitForText("Dinner with Jo & Priya")
        compose.onNodeWithTag("detail_delete").performClick()
        waitForText("Delete this event?")
        compose.onNodeWithTag("detail_confirm_delete").performClick()
        // The editor toasts the refusal; the sheet goes back to its footer and stays open.
        waitUntil { access.toasts.messages.isNotEmpty() }
        waitUntil { compose.onAllNodesWithText("Delete this event?").fetchSemanticsNodes().isEmpty() }
        assertThat(access.toasts.messages).containsExactly(couldNotDelete("Sample calendar", WriteProblem.Refused("event is locked")))
        compose.onNodeWithTag("detail_delete").assertExists()
        assertThat(closed).isEqualTo(0)
    }

    @Test
    fun deleteEventIgnoresASecondTapWhileBusy() {
        val gate = CompletableDeferred<Unit>()
        writer.gate = gate
        access.answer(TestAccess.ALEX)
        show("dinner")
        waitForText("Dinner with Jo & Priya")
        compose.onNodeWithTag("detail_delete").performClick()
        waitForText("Delete this event?")
        compose.onNodeWithTag("detail_confirm_delete").performClick()
        compose.onNodeWithTag("detail_confirm_delete").performClick()
        waitUntil { writer.calls.isNotEmpty() }
        gate.complete(Unit)
        waitUntil { closed > 0 }
        assertThat(writer.calls).containsExactly("delete:dinner")
    }

    @Test
    fun aChildIsRefusedWithAToastAndNoConfirmation() {
        access.answer(TestAccess.MIA)
        show("dinner")
        waitForText("Dinner with Jo & Priya")
        compose.onNodeWithTag("detail_delete").performClick()
        // Access control shows refusals itself, through the toaster.
        waitUntil { access.toasts.messages.isNotEmpty() }
        assertThat(access.toasts.messages).containsExactly("Mia can only change events they created.")
        compose.onNodeWithTag("detail_confirm").assertDoesNotExist()
        assertThat(writer.calls).isEmpty()
    }

    @Test
    fun assigningAPhoneEventTagsItAndClosesTheChips() {
        access.answer(TestAccess.SAM)
        show("plumber")
        waitForText("Added from a phone")
        compose.onNodeWithTag("detail_assign").performClick()
        compose.onNodeWithTag("assign_Mia").performClick()
        waitUntil { writer.calls.isNotEmpty() }
        waitUntil { compose.onAllNodesWithText("Showing as Family until someone chooses who it’s for.").fetchSemanticsNodes().isEmpty() }
        assertThat(writer.calls).containsExactly("update:plumber")
        assertThat(runBlocking { store.eventNow(EventRef("c1", "s-family", "plumber")) }?.forPerson).isEqualTo(access.mia.id.value)
    }

    @Test
    fun theSheetClosesWhenItsEventDisappears() {
        show("dinner")
        waitForText("Dinner with Jo & Priya")
        runBlocking { store.applyDeleted(EventRef("c1", "s-family", "dinner")) }
        waitUntil { closed > 0 }
    }

    @Test
    fun tappingAChipAddsThatPersonToTheEvent() {
        access.answer(TestAccess.ALEX)
        chooseFor(ids(access.mia))
        tap("Sam")
        waitUntil { tagOf(swim) == ids(access.sam, access.mia) }
        assertThat(writer.calls).containsExactly("assignSeries:swim")
    }

    @Test
    fun theLastPersonCannotBeCleared() {
        access.answer(TestAccess.ALEX)
        chooseFor(ids(access.mia))
        tap("Mia")
        // A real change next: once it lands, the only write is its own.
        tap("Sam")
        waitUntil { tagOf(swim) == ids(access.sam, access.mia) }
        assertThat(writer.calls).containsExactly("assignSeries:swim")
        assertThat(access.requests).hasSize(1)
    }

    @Test
    fun familyStaysExclusive() {
        access.answer(TestAccess.ALEX)
        chooseFor(ids(access.sam, access.mia))
        tap("Family")
        waitUntil { tagOf(swim) == "family" }
        waitUntil { compose.onAllNodes(hasTestTag("assign_Sam") and isEnabled()).fetchSemanticsNodes().isNotEmpty() }
        tap("Sam")
        waitUntil { tagOf(swim) == ids(access.sam) }
    }

    @Test
    fun aChildTappingADetailChipIsAskedForAnAdultAndNothingChanges() {
        access.answer(TestAccess.MIA)
        chooseFor(ids(access.mia))
        tap("Sam")
        waitUntil { access.toasts.messages.isNotEmpty() }
        assertThat(access.toasts.messages).containsExactly(ASK_AN_ADULT)
        assertThat(writer.calls).isEmpty()
        assertThat(runBlocking { store.pendingNow() }).isEmpty()
        assertThat(tagOf(swim)).isEqualTo(ids(access.mia))
    }

    @Test
    fun theChooserStaysOpenAfterAToggle() {
        access.answer(TestAccess.ALEX)
        chooseFor(ids(access.mia))
        tap("Sam")
        waitUntil { tagOf(swim) == ids(access.sam, access.mia) }
        shadowOf(Looper.getMainLooper()).idle()
        compose.onNodeWithTag("assign_Alex").assertExists()
        compose.onNodeWithTag("assign_Sam").assertIsOn()
    }

    @Test
    fun twoTogglesInARowBothLand() {
        val gate = CompletableDeferred<Unit>()
        writer.gate = gate
        access.answer(TestAccess.ALEX)
        chooseFor(ids(access.mia))
        tap("Sam")
        waitUntil { writer.calls.isNotEmpty() }
        compose.onNodeWithTag("assign_Alex").assertIsNotEnabled()
        gate.complete(Unit)
        waitUntil { compose.onAllNodes(hasTestTag("assign_Alex") and isEnabled()).fetchSemanticsNodes().isNotEmpty() }
        tap("Alex")
        waitUntil { tagOf(swim) == ids(access.alex, access.sam, access.mia) }
        assertThat(writer.calls).containsExactly("assignSeries:swim", "assignSeries:swim")
    }

    @Test
    fun aRemovedPersonIsDroppedWhenTheSetIsChanged() {
        access.answer(TestAccess.ALEX)
        chooseFor("${access.sam.id.value},gone")
        tap("Mia")
        waitUntil { tagOf(swim) == ids(access.sam, access.mia) }
    }
}
