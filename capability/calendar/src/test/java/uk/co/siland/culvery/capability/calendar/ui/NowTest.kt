package uk.co.siland.culvery.capability.calendar.ui

import androidx.compose.material3.Text
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.test.ext.junit.runners.AndroidJUnit4
import java.time.LocalDateTime
import java.time.ZoneId
import kotlinx.coroutines.flow.MutableSharedFlow
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import uk.co.siland.culvery.core.plugin.WallClock

@RunWith(AndroidJUnit4::class)
class NowTest {
    @get:Rule val compose = createComposeRule()
    private val london = ZoneId.of("Europe/London")

    @Test
    fun todayRollsOverAtMidnight() {
        var now = LocalDateTime.of(2026, 9, 23, 23, 59, 50).atZone(london).toInstant().toEpochMilli()
        val clock = WallClock { now }
        val ticks = MutableSharedFlow<Unit>(extraBufferCapacity = 1)
        compose.setContent { Text(todayIn(london, rememberNowMillis(clock, ticks)).toString()) }
        compose.onNodeWithText("2026-09-23").assertExists()

        now += 20_000 // 00:00:10 on the 24th
        ticks.tryEmit(Unit)
        compose.waitForIdle()
        compose.onNodeWithText("2026-09-24").assertExists()
    }
}
