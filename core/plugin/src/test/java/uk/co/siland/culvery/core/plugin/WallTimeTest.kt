package uk.co.siland.culvery.core.plugin

import app.cash.turbine.test
import com.google.common.truth.Truth.assertThat
import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneId
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.take
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.test.runTest
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class WallTimeTest {
    // 09:59:30 UTC on 1 October 2026: 10:59:30 in London (BST), 22:59:30 in Wellington (NZDT).
    private val start = Instant.parse("2026-10-01T09:59:30Z").toEpochMilli()

    @Test
    fun itGivesTheTimeNowThenAtTheStartOfEachMinute() = runTest {
        val clock = WallClock { start + testScheduler.currentTime }
        val times = wallTimeEachMinute(flowOf(ZoneId.of("Europe/London")), clock).take(3).toList()
        assertThat(times).containsExactly(
            LocalDateTime.of(2026, 10, 1, 10, 59, 30),
            LocalDateTime.of(2026, 10, 1, 11, 0),
            LocalDateTime.of(2026, 10, 1, 11, 1),
        ).inOrder()
    }

    @Test
    fun aZoneChangeGivesTheTimeInTheNewZoneAtOnce() = runTest {
        val clock = WallClock { start + testScheduler.currentTime }
        val zones = MutableStateFlow(ZoneId.of("Europe/London"))
        wallTimeEachMinute(zones, clock).test {
            assertThat(awaitItem()).isEqualTo(LocalDateTime.of(2026, 10, 1, 10, 59, 30))
            zones.value = ZoneId.of("Pacific/Auckland")
            assertThat(awaitItem()).isEqualTo(LocalDateTime.of(2026, 10, 1, 22, 59, 30))
            cancelAndIgnoreRemainingEvents()
        }
    }
}
