package uk.co.siland.culvery.capability.calendar.ui

import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.google.common.truth.Truth.assertThat
import java.time.LocalDateTime
import java.time.ZoneId
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import uk.co.siland.culvery.capability.calendar.CalendarRepository
import uk.co.siland.culvery.capability.calendar.CalendarStore
import uk.co.siland.culvery.capability.calendar.WeekFocus
import uk.co.siland.culvery.capability.calendar.calendarDb
import uk.co.siland.culvery.capability.calendar.db.CalendarDao
import uk.co.siland.culvery.capability.calendar.db.CalendarDatabase
import uk.co.siland.culvery.capability.calendar.db.EventRow
import uk.co.siland.culvery.capability.calendar.householdDb
import uk.co.siland.culvery.capability.calendar.stubEditor
import uk.co.siland.culvery.core.household.HomeLocation
import uk.co.siland.culvery.core.household.HouseholdRepository
import uk.co.siland.culvery.core.household.HouseholdZone
import uk.co.siland.culvery.core.household.db.HouseholdDatabase
import uk.co.siland.culvery.core.plugin.HouseholdClock
import uk.co.siland.culvery.core.plugin.LocalOverlayHost
import uk.co.siland.culvery.core.plugin.LocalShellNavigator
import uk.co.siland.culvery.core.plugin.LocalUserActivity
import uk.co.siland.culvery.core.plugin.WallClock
import uk.co.siland.culvery.core.plugin.rememberNowMillis
import uk.co.siland.culvery.core.plugin.test.FakeUserActivity
import uk.co.siland.culvery.core.plugin.test.RecordingNavigator
import uk.co.siland.culvery.core.ui.CulveryTheme

/** The real DAO, with the events query held back on [gate] once it is set. */
private class HoldingDao(private val real: CalendarDao) : CalendarDao by real {
    @Volatile var gate: CompletableDeferred<Unit>? = null

    override fun eventsBetween(start: Long, end: Long): Flow<List<EventRow>> {
        val held = gate ?: return real.eventsBetween(start, end)
        return flow {
            held.await()
            emitAll(real.eventsBetween(start, end))
        }
    }
}

@RunWith(AndroidJUnit4::class)
class WeekViewHostTest {
    @get:Rule val compose = createComposeRule()
    private val london = ZoneId.of("Europe/London")
    private val clockScope = CoroutineScope(SupervisorJob() + Dispatchers.Unconfined)

    private lateinit var calendar: CalendarDatabase
    private lateinit var householdDb: HouseholdDatabase
    private val dao by lazy { HoldingDao(calendar.calendarDao()) }

    @Before
    fun setUp() = runTest {
        calendar = calendarDb()
        householdDb = householdDb()
    }

    @After
    fun tearDown() {
        clockScope.cancel()
        calendar.close()
        householdDb.close()
    }

    private fun subtitle(): String = compose.onNodeWithTag("week_subtitle").fetchSemanticsNode().config
        .getOrNull(SemanticsProperties.Text)?.joinToString("") { it.text }.orEmpty()

    /** Either week is fine, but never one week's label over the other's dates. */
    private fun assertLabelMatchesDates() {
        val line = subtitle()
        assertThat(
            line.startsWith("This week · Wed 23 – Tue 29 Sep") || line.startsWith("Next week · Wed 30 Sep – Tue 6 Oct") ||
                line.startsWith("In 2 weeks · Wed 7 – Tue 13 Oct"),
        ).isTrue()
    }

    private suspend fun showHost() {
        val store = CalendarStore(calendar, dao)
        val household = HouseholdRepository(householdDb)
        val zone = HouseholdZone(household)
        household.setLocation(HomeLocation("London", 51.5, -0.12, "Europe/London"))
        val repo = CalendarRepository(store, household, zone, emptySet(), emptySet())
        val editor = stubEditor(store, zone)
        val now = LocalDateTime.of(2026, 9, 23, 12, 0).atZone(london).toInstant().toEpochMilli()
        val clock = HouseholdClock(zone.zone, WallClock { now }, clockScope, MutableStateFlow(now))
        compose.setContent {
            CompositionLocalProvider(
                LocalShellNavigator provides RecordingNavigator(),
                LocalOverlayHost provides RecordingOverlay(),
                LocalUserActivity provides FakeUserActivity(),
            ) {
                CulveryTheme(dark = true) {
                    val today = rememberToday(clock) ?: return@CulveryTheme
                    WeekViewHost(repo, editor, WeekFocus(), today, rememberNowMillis(clock), onReconnect = {})
                }
            }
        }
        compose.waitUntil(timeoutMillis = 5_000) { compose.onAllNodesWithTag("week_day_2026-09-23").fetchSemanticsNodes().isNotEmpty() }
        compose.mainClock.autoAdvance = false
    }

    /** A tap reaches the host a little after the frame that follows it, so each frame also gives it real time. */
    private fun steppedFrames(count: Int = 5) = repeat(count) {
        compose.mainClock.advanceTimeByFrame()
        assertLabelMatchesDates()
        Thread.sleep(20)
    }

    private fun stepUntil(day: String) {
        val deadline = System.currentTimeMillis() + 5_000
        while (compose.onAllNodesWithTag("week_day_$day").fetchSemanticsNodes().isEmpty()) {
            check(System.currentTimeMillis() < deadline) { "The week of $day never loaded" }
            compose.mainClock.advanceTimeByFrame()
            assertLabelMatchesDates()
            Thread.sleep(10)
        }
        compose.mainClock.advanceTimeByFrame()
    }

    @Test
    fun theWeeksLabelAndDatesChangeInTheSameFrame() = runTest {
        showHost()
        assertThat(subtitle()).startsWith("This week · Wed 23 – Tue 29 Sep")

        val gate = CompletableDeferred<Unit>()
        dao.gate = gate
        compose.onNodeWithTag("week_later").performClick()
        steppedFrames()
        assertThat(subtitle()).startsWith("This week · Wed 23 – Tue 29 Sep")

        gate.complete(Unit)
        stepUntil("2026-09-30")
        assertThat(subtitle()).startsWith("Next week · Wed 30 Sep – Tue 6 Oct")
    }

    @Test
    fun twoQuickTapsOnLaterWhileAWeekLoadsStepTwoWeeks() = runTest {
        showHost()
        val gate = CompletableDeferred<Unit>()
        dao.gate = gate
        compose.onNodeWithTag("week_later").performClick()
        steppedFrames()
        compose.onNodeWithTag("week_this_week").assertExists()
        compose.onNodeWithTag("week_later").performClick()
        steppedFrames()
        assertThat(subtitle()).startsWith("This week · Wed 23 – Tue 29 Sep")

        gate.complete(Unit)
        stepUntil("2026-10-07")
        assertThat(subtitle()).startsWith("In 2 weeks · Wed 7 – Tue 13 Oct")
    }
}
