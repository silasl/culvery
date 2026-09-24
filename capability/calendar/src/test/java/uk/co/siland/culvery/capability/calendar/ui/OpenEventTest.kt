package uk.co.siland.culvery.capability.calendar.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.test.junit4.createComposeRule
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
import uk.co.siland.culvery.capability.calendar.HouseholdZone
import uk.co.siland.culvery.capability.calendar.RemoteEvent
import uk.co.siland.culvery.capability.calendar.SyncResult
import uk.co.siland.culvery.capability.calendar.calendarDb
import uk.co.siland.culvery.capability.calendar.db.CalendarDatabase
import uk.co.siland.culvery.capability.calendar.householdDb
import uk.co.siland.culvery.capability.calendar.stubEditor
import uk.co.siland.culvery.core.household.HomeLocation
import uk.co.siland.culvery.core.household.HouseholdRepository
import uk.co.siland.culvery.core.household.db.HouseholdDatabase
import uk.co.siland.culvery.core.plugin.Connection
import uk.co.siland.culvery.core.plugin.LocalOverlayHost
import uk.co.siland.culvery.core.plugin.LocalShellNavigator
import uk.co.siland.culvery.core.ui.CulveryTheme

@RunWith(AndroidJUnit4::class)
class OpenEventTest {
    @get:Rule val compose = createComposeRule()

    private val london = ZoneId.of("Europe/London")
    private val today = LocalDate.of(2026, 9, 23)
    private val overlay = RecordingOverlay()
    private lateinit var calendar: CalendarDatabase
    private lateinit var householdDb: HouseholdDatabase
    private lateinit var repo: CalendarRepository
    private lateinit var editor: CalendarEditor

    @Before
    fun setUp() = runBlocking {
        calendar = calendarDb()
        householdDb = householdDb()
        val store = CalendarStore(calendar)
        val household = HouseholdRepository(householdDb)
        household.setLocation(HomeLocation("London", 51.5, -0.12, "Europe/London"))
        val zone = HouseholdZone(household)
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
        show { WeekViewHost(repo, editor, today, nowMillis = 0L) }
        waitForText("Dinner with Jo & Priya")
        compose.onNodeWithText("Dinner with Jo & Priya").performClick()
        waitForText("Created by")
        compose.onNodeWithTag("detail_sheet").assertExists()
    }
}
