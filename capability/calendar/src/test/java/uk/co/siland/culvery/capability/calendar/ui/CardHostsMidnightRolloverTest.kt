package uk.co.siland.culvery.capability.calendar.ui

import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.test.ext.junit.runners.AndroidJUnit4
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.test.runTest
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

/**
 * Regression for the hand-off Home cards and week view not rolling over at midnight: [TodayCardHost] and
 * [WeekViewHost] must re-subscribe to the repository when `today` changes, not just once per composition.
 */
@RunWith(AndroidJUnit4::class)
class CardHostsMidnightRolloverTest {
    @get:Rule val compose = createComposeRule()
    private val london = ZoneId.of("Europe/London")
    private val d23 = LocalDate.of(2026, 9, 23)
    private val d24 = LocalDate.of(2026, 9, 24)

    private lateinit var calendar: CalendarDatabase
    private lateinit var householdDb: HouseholdDatabase
    private lateinit var repo: CalendarRepository
    private lateinit var zone: HouseholdZone
    private lateinit var editor: CalendarEditor

    @Before
    fun setUp() = runTest {
        calendar = calendarDb()
        householdDb = householdDb()
        val store = CalendarStore(calendar)
        val household = HouseholdRepository(householdDb)
        zone = HouseholdZone(household)
        repo = CalendarRepository(store, household, zone, emptySet(), emptySet())
        editor = stubEditor(store, zone)
        household.setLocation(HomeLocation("London", 51.5, -0.12, "Europe/London"))
        store.addConnection(
            Connection("c1", "calendar.test", "Google", emptyMap()),
            listOf(CalendarSource("s-family", "Family", writable = false)),
            emptyMap(),
        )
        store.applySync(
            "c1",
            "s-family",
            DateRange(d23.minusDays(1), d24.plusDays(7), london),
            SyncResult(listOf(timed("Twenty-third event", d23), timed("Twenty-fourth event", d24)), emptyList(), null, fullReplace = true),
        )
    }

    @After
    fun tearDown() {
        calendar.close()
        householdDb.close()
    }

    private fun timed(title: String, date: LocalDate): RemoteEvent {
        val start = date.atTime(10, 0).atZone(london).toInstant()
        return RemoteEvent(title, title, EventTime.Timed(start), EventTime.Timed(start.plusSeconds(3_600)), recurring = false)
    }

    @Test
    fun todayCardHostRollsOverAtMidnight() {
        var now = LocalDateTime.of(2026, 9, 23, 23, 59, 50).atZone(london).toInstant().toEpochMilli()
        val clock = WallClock { now }
        val ticks = MutableSharedFlow<Unit>(extraBufferCapacity = 1)
        compose.setContent {
            CompositionLocalProvider(LocalShellNavigator provides RecordingNavigator(), LocalOverlayHost provides RecordingOverlay()) {
                CulveryTheme(dark = true) { TodayCardHost(repo, editor, rememberToday(zone, clock, ticks)) }
            }
        }
        compose.waitUntil(timeoutMillis = 5_000) {
            compose.onAllNodesWithText("Twenty-third event").fetchSemanticsNodes().isNotEmpty()
        }
        compose.onNodeWithText("Twenty-third event").assertExists()
        compose.onNodeWithText("Twenty-fourth event").assertDoesNotExist()

        now += 20_000 // 00:00:10 on the 24th
        ticks.tryEmit(Unit)
        compose.waitUntil(timeoutMillis = 5_000) {
            compose.onAllNodesWithText("Twenty-fourth event").fetchSemanticsNodes().isNotEmpty()
        }

        compose.onNodeWithText("Twenty-fourth event").assertExists()
        compose.onNodeWithText("Twenty-third event").assertDoesNotExist()
    }

    @Test
    fun weekViewHostRollsOverAtMidnight() {
        var now = LocalDateTime.of(2026, 9, 23, 23, 59, 50).atZone(london).toInstant().toEpochMilli()
        val clock = WallClock { now }
        val ticks = MutableSharedFlow<Unit>(extraBufferCapacity = 1)
        compose.setContent {
            CompositionLocalProvider(LocalShellNavigator provides RecordingNavigator(), LocalOverlayHost provides RecordingOverlay()) {
                CulveryTheme(dark = true) {
                    val today = rememberToday(zone, clock, ticks)
                    WeekViewHost(repo, editor, today, rememberNowMillis(clock, ticks), onReconnect = {})
                }
            }
        }
        compose.waitUntil(timeoutMillis = 5_000) {
            compose.onAllNodesWithTag("week_day_2026-09-23").fetchSemanticsNodes().isNotEmpty()
        }
        compose.onNodeWithTag("week_day_2026-09-23").assertExists()
        compose.onNodeWithTag("week_day_2026-09-30").assertDoesNotExist()

        now += 20_000 // 00:00:10 on the 24th
        ticks.tryEmit(Unit)
        compose.waitUntil(timeoutMillis = 5_000) {
            compose.onAllNodesWithTag("week_day_2026-09-30").fetchSemanticsNodes().isNotEmpty()
        }

        // The first column's date has moved from the 23rd to the 24th, so the seven-day window now runs 24-30.
        compose.onNodeWithTag("week_day_2026-09-23").assertDoesNotExist()
        compose.onNodeWithTag("week_day_2026-09-30").assertExists()
    }
}
