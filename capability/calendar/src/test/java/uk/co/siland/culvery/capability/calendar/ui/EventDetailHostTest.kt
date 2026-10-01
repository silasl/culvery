package uk.co.siland.culvery.capability.calendar.ui

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
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
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
import uk.co.siland.culvery.capability.calendar.WriteRejectedException
import uk.co.siland.culvery.capability.calendar.calendarDb
import uk.co.siland.culvery.capability.calendar.db.CalendarDatabase
import uk.co.siland.culvery.capability.calendar.householdDb
import uk.co.siland.culvery.capability.calendar.testAccess
import uk.co.siland.culvery.capability.calendar.testEditor
import uk.co.siland.culvery.core.household.HomeLocation
import uk.co.siland.culvery.core.household.HouseholdRepository
import uk.co.siland.culvery.core.household.HouseholdZone
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

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Unconfined)
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
        editor = testEditor(store, setOf(writer), access.control, access.toasts, zone, WallClock { System.currentTimeMillis() }, scope)
    }

    @After
    fun tearDown() {
        scope.cancel()
        calendar.close()
        householdDb.close()
    }

    private fun event(id: String, title: String, createdBy: String?): RemoteEvent {
        val start = today.atTime(19, 30).atZone(london).toInstant()
        return RemoteEvent(id, title, EventTime.Timed(start), EventTime.Timed(start.plusSeconds(5_400)), false, null, createdBy)
    }

    private fun show(id: String) = compose.setContent {
        CulveryTheme(dark = true) {
            EventDetailHost(EventRef("c1", "s-family", id), today, repo, editor, onClose = { closed++ }, onEdit = {})
        }
    }

    private fun waitForText(text: String) =
        compose.waitUntil(5_000) { compose.onAllNodesWithText(text).fetchSemanticsNodes().isNotEmpty() }

    @Test
    fun deleteAsksForThePinBeforeTheConfirmationThenDeletesWithoutAskingAgain() {
        access.answer(TestAccess.ALEX)
        show("dinner")
        waitForText("Dinner with Jo & Priya")
        compose.onNodeWithTag("detail_delete").performClick()
        waitForText("Delete this event?")
        assertThat(access.requests).hasSize(1)
        compose.onNodeWithTag("detail_confirm_delete").performClick()
        compose.waitUntil(5_000) { closed > 0 }
        assertThat(access.requests).hasSize(1)
        assertThat(writer.calls).containsExactly("delete:dinner")
    }

    @Test
    fun aRejectedDeleteKeepsTheSheetOpen() {
        writer.failWith = WriteRejectedException("Event is locked")
        access.answer(TestAccess.ALEX)
        show("dinner")
        waitForText("Dinner with Jo & Priya")
        compose.onNodeWithTag("detail_delete").performClick()
        waitForText("Delete this event?")
        compose.onNodeWithTag("detail_confirm_delete").performClick()
        // The editor toasts the refusal; the sheet goes back to its footer and stays open.
        compose.waitUntil(5_000) { access.toasts.messages.isNotEmpty() }
        compose.waitUntil(5_000) { compose.onAllNodesWithText("Delete this event?").fetchSemanticsNodes().isEmpty() }
        assertThat(access.toasts.messages).containsExactly("Couldn't save to Sample calendar — Event is locked")
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
        compose.waitUntil(5_000) { writer.calls.isNotEmpty() }
        gate.complete(Unit)
        compose.waitUntil(5_000) { closed > 0 }
        assertThat(writer.calls).containsExactly("delete:dinner")
    }

    @Test
    fun aChildIsRefusedWithAToastAndNoConfirmation() {
        access.answer(TestAccess.MIA)
        show("dinner")
        waitForText("Dinner with Jo & Priya")
        compose.onNodeWithTag("detail_delete").performClick()
        // Access control shows refusals itself, through the toaster.
        compose.waitUntil(5_000) { access.toasts.messages.isNotEmpty() }
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
        compose.waitUntil(5_000) { writer.calls.isNotEmpty() }
        compose.waitUntil(5_000) {
            compose.onAllNodesWithText("Added from a phone").fetchSemanticsNodes().isEmpty()
        }
        assertThat(writer.calls).containsExactly("update:plumber")
        assertThat(runBlocking { store.eventNow(EventRef("c1", "s-family", "plumber")) }?.forPerson).isEqualTo(access.mia.id.value)
    }

    @Test
    fun theSheetClosesWhenItsEventDisappears() {
        show("dinner")
        waitForText("Dinner with Jo & Priya")
        runBlocking { store.applyDeleted(EventRef("c1", "s-family", "dinner")) }
        compose.waitUntil(5_000) { closed > 0 }
    }
}
