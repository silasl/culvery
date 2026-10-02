package uk.co.siland.culvery.capability.calendar.ui

import androidx.compose.material3.Text
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.test.ext.junit.runners.AndroidJUnit4
import java.time.LocalDateTime
import java.time.ZoneId
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf
import org.junit.After
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import uk.co.siland.culvery.core.plugin.HouseholdClock
import uk.co.siland.culvery.core.plugin.WallClock

@RunWith(AndroidJUnit4::class)
class NowTest {
    @get:Rule val compose = createComposeRule()
    private val london = ZoneId.of("Europe/London")
    private val clockScope = CoroutineScope(SupervisorJob() + Dispatchers.Unconfined)

    @After
    fun tearDown() = clockScope.cancel()

    @Test
    fun todayRollsOverAtMidnight() {
        var now = LocalDateTime.of(2026, 9, 23, 23, 59, 50).atZone(london).toInstant().toEpochMilli()
        val ticks = MutableStateFlow(now)
        val clock = HouseholdClock(flowOf(london), WallClock { now }, clockScope, ticks)
        compose.setContent { Text(rememberToday(clock).toString()) }
        compose.onNodeWithText("2026-09-23").assertExists()

        now += 20_000 // 00:00:10 on the 24th
        ticks.value = now
        compose.waitForIdle()
        compose.onNodeWithText("2026-09-24").assertExists()
    }

    @Test
    fun untilTheZoneIsReadThereIsNoToday() {
        val clock = HouseholdClock(flow { awaitCancellation() }, WallClock { 0L }, clockScope, flowOf(0L))
        compose.setContent { Text(rememberToday(clock).toString()) }
        compose.onNodeWithText("null").assertExists()
    }
}
