package uk.co.siland.culvery.capability.calendar.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.google.common.truth.Truth.assertThat
import java.time.LocalDate
import java.time.ZoneId
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
import uk.co.siland.culvery.capability.calendar.EventTime
import uk.co.siland.culvery.capability.calendar.RemoteEvent
import uk.co.siland.culvery.capability.calendar.ScriptedWriter
import uk.co.siland.culvery.capability.calendar.SyncResult
import uk.co.siland.culvery.capability.calendar.calendarDb
import uk.co.siland.culvery.capability.calendar.db.CalendarDatabase
import uk.co.siland.culvery.capability.calendar.householdDb
import uk.co.siland.culvery.capability.calendar.stubEditor
import uk.co.siland.culvery.core.household.HomeLocation
import uk.co.siland.culvery.core.household.HouseholdRepository
import uk.co.siland.culvery.core.household.HouseholdZone
import uk.co.siland.culvery.core.household.db.HouseholdDatabase
import uk.co.siland.culvery.core.plugin.Connection
import uk.co.siland.culvery.core.plugin.LocalOverlayHost
import uk.co.siland.culvery.core.plugin.LocalShellNavigator
import uk.co.siland.culvery.core.plugin.WallClock
import uk.co.siland.culvery.core.ui.CulveryTheme

@RunWith(AndroidJUnit4::class)
class OpenEventTest {
    @get:Rule val compose = createComposeRule()

    private val london = ZoneId.of("Europe/London")
    private val today = LocalDate.of(2026, 9, 23)
    private val overlay = RecordingOverlay()
    private lateinit var calendar: CalendarDatabase
    private lateinit var householdDb: HouseholdDatabase
    private lateinit var store: CalendarStore
    private lateinit var household: HouseholdRepository
    private lateinit var zone: HouseholdZone
    private lateinit var repo: CalendarRepository
    private lateinit var editor: CalendarEditor

    @Before
    fun setUp() = runBlocking {
        calendar = calendarDb()
        householdDb = householdDb()
        store = CalendarStore(calendar)
        household = HouseholdRepository(householdDb)
        household.setLocation(HomeLocation("London", 51.5, -0.12, "Europe/London"))
        zone = HouseholdZone(household)
        store.addConnection(
            Connection("c1", "calendar.test", "Google", emptyMap()),
            listOf(CalendarSource("s-family", "Family calendar", writable = true)),
            emptyMap(),
        )
        val start = today.atTime(19, 30).atZone(london).toInstant()
        store.applySync(
            "c1", "s-family", DateRange(today.minusDays(1), today.plusDays(14), london),
            SyncResult(
                listOf(RemoteEvent("dinner", "Dinner with Jo & Priya", EventTime.Timed(start), EventTime.Timed(start.plusSeconds(5_400)), recurring = false)),
                emptyList(), null, fullReplace = true,
            ),
        )
        repo = CalendarRepository(store, household, zone, emptySet(), emptySet())
        editor = stubEditor(store, zone)
    }

    @After
    fun tearDown() {
        calendar.close()
        householdDb.close()
    }

    /** The Family calendar as a writable master whose provider has a writer, so the add entry points show. */
    private fun makeFamilyTheWritableMaster() {
        runBlocking { store.setMaster("c1", "s-family") }
        repo = CalendarRepository(store, household, zone, emptySet(), setOf(ScriptedWriter("calendar.test")))
        editor = stubEditor(store, zone, WallClock { SampleUi.NOW })
    }

    private fun show(host: @Composable () -> Unit) = compose.setContent {
        CompositionLocalProvider(
            LocalShellNavigator provides RecordingNavigator(),
            LocalOverlayHost provides overlay,
        ) {
            CulveryTheme(dark = true) {
                Box {
                    host()
                    overlay.content?.invoke()
                }
            }
        }
    }

    private fun waitForText(text: String) =
        compose.waitUntil(5_000) { compose.onAllNodesWithText(text).fetchSemanticsNodes().isNotEmpty() }

    private fun waitForTag(tag: String) =
        compose.waitUntil(5_000) { compose.onAllNodesWithTag(tag, useUnmergedTree = true).fetchSemanticsNodes().isNotEmpty() }

    @Test
    fun tappingATodayRowOpensItsDetailSheetAndCloseDismissesIt() {
        show { TodayCardHost(repo, editor, today) }
        waitForText("Dinner with Jo & Priya")
        compose.onNodeWithText("Dinner with Jo & Priya").performClick()
        waitForText("Created by")
        compose.onNodeWithText("Today · 19:30–21:00").assertExists()
        compose.onNodeWithTag("sheet_close").performClick()
        compose.onNodeWithText("Created by").assertDoesNotExist()
        assertThat(overlay.dismissed).isEqualTo(1)
    }

    @Test
    fun tappingAWeekChipOpensItsDetailSheet() {
        show { WeekViewHost(repo, editor, today, nowMillis = 0L, onReconnect = {}) }
        waitForText("Dinner with Jo & Priya")
        compose.onNodeWithText("Dinner with Jo & Priya").performClick()
        waitForText("Created by")
        compose.onNodeWithTag("detail_sheet").assertExists()
    }

    /** Both hosts at once, for the tests where neither may offer to add. */
    private fun showBothHosts() = show {
        Row(Modifier.fillMaxSize()) {
            Box(Modifier.weight(1f)) { TodayCardHost(repo, editor, today) }
            Box(Modifier.weight(1f)) { WeekViewHost(repo, editor, today, nowMillis = 0L, onReconnect = {}) }
        }
    }

    private fun assertNowhereToAdd() {
        waitForText("Dinner with Jo & Priya")
        compose.onNodeWithTag("today_add").assertDoesNotExist()
        compose.onNodeWithTag("week_add_event").assertDoesNotExist()
        compose.onNodeWithTag("week_add_$today", useUnmergedTree = true).assertDoesNotExist()
    }

    @Test
    fun withNoMasterThereIsNowhereToAdd() {
        repo = CalendarRepository(store, household, zone, emptySet(), setOf(ScriptedWriter("calendar.test")))
        showBothHosts()
        assertNowhereToAdd()
    }

    @Test
    fun withAMasterWhoseProviderHasNoWriterThereIsNowhereToAdd() {
        runBlocking { store.setMaster("c1", "s-family") }
        showBothHosts()
        assertNowhereToAdd()
    }

    @Test
    fun thePlusOnTodayOpensANewEventForToday() {
        makeFamilyTheWritableMaster()
        show { TodayCardHost(repo, editor, today) }
        waitForTag("today_add")
        compose.onNodeWithTag("today_add").performClick()
        waitForText("New event")
        compose.onNodeWithTag("editor_summary").assertTextEquals("Today · 14:00–15:00 · Family")
    }

    @Test
    fun addEventInTheWeekOpensANewEventForToday() {
        makeFamilyTheWritableMaster()
        show { WeekViewHost(repo, editor, today, nowMillis = SampleUi.NOW, onReconnect = {}) }
        waitForTag("week_add_event")
        compose.onNodeWithTag("week_add_event").performClick()
        waitForText("New event")
        compose.onNodeWithTag("editor_summary").assertTextEquals("Today · 14:00–15:00 · Family")
    }

    @Test
    fun aColumnTapPresetsItsDay() {
        makeFamilyTheWritableMaster()
        show { WeekViewHost(repo, editor, today, nowMillis = SampleUi.NOW, onReconnect = {}) }
        val friday = today.plusDays(2)
        waitForTag("week_add_$friday")
        compose.onNodeWithTag("week_add_$friday", useUnmergedTree = true).performClick()
        waitForText("New event")
        // A later day starts on Morning.
        compose.onNodeWithTag("editor_summary").assertTextEquals("Fri 25 Sep · 09:00–10:00 · Family")
    }

    @Test
    fun aChipTapStillOpensTheEventNotTheEditor() {
        makeFamilyTheWritableMaster()
        show { WeekViewHost(repo, editor, today, nowMillis = SampleUi.NOW, onReconnect = {}) }
        waitForText("Dinner with Jo & Priya")
        compose.onNodeWithText("Dinner with Jo & Priya").performClick()
        waitForText("Created by")
        compose.onNodeWithText("New event").assertDoesNotExist()
    }
}
