package uk.co.siland.culvery.capability.door

import androidx.test.ext.junit.runners.AndroidJUnit4
import com.google.common.truth.Truth.assertThat
import java.io.IOException
import java.time.Instant
import java.time.ZoneId
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.shadows.ShadowLog
import uk.co.siland.culvery.core.plugin.WallClock

/** 5 §4.5, 15a §3.1: at start, at each household midnight, when the keep changes, and each time Culvery comes back to the front. */
@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(AndroidJUnit4::class)
class DoorPruneLoopTest {
    private val london = ZoneId.of("Europe/London")
    private val minute = 60_000L

    /** Wednesday 14 October 2026, 23:50 in London. */
    private val lateEvening = NOW + (9 * 60 + 48) * minute

    private fun TestScope.loop(
        prune: suspend (Long) -> Unit,
        start: Long = NOW,
        inFront: MutableStateFlow<Boolean> = MutableStateFlow(false),
        keep: MutableStateFlow<KeepDays> = MutableStateFlow(KeepDays.TWO),
    ) = DoorPruneLoop(prune, WallClock { start + testScheduler.currentTime }, inFront, keep, flowOf(london), backgroundScope).also { it.start() }

    @Test
    fun itPrunesAtStartAndOnEachReturnToTheFront() = runTest {
        val prunedAt = mutableListOf<Long>()
        val inFront = MutableStateFlow(false)
        loop({ prunedAt += it }, inFront = inFront)
        runCurrent()
        assertThat(prunedAt).containsExactly(NOW)
        inFront.value = true
        runCurrent()
        assertThat(prunedAt).hasSize(2)
    }

    /** Started at 23:50, it prunes at the household's 00:00, and the next one is a day on, not 23:50 the next day. */
    @Test
    fun prunesAtTheHouseholdMidnight() = runTest {
        val prunedAt = mutableListOf<Long>()
        loop({ prunedAt += it }, start = lateEvening)
        runCurrent()
        assertThat(prunedAt).containsExactly(lateEvening)
        advanceTimeBy(10 * minute - 1)
        runCurrent()
        assertThat(prunedAt).hasSize(1)
        advanceTimeBy(1)
        runCurrent()
        assertThat(prunedAt).containsExactly(lateEvening, lateEvening + 10 * minute).inOrder()
        advanceTimeBy(23 * 60 * minute + 50 * minute)
        runCurrent()
        assertThat(prunedAt).hasSize(2)
        advanceTimeBy(10 * minute)
        runCurrent()
        assertThat(prunedAt).containsExactly(lateEvening, lateEvening + 10 * minute, lateEvening + 10 * minute + DAY_MS).inOrder()
    }

    /** The clocks go back on Sunday 25 October 2026: from Saturday 23:50 the next prune is at Sunday 00:00, the one after 25 hours on, not 24. */
    @Test
    fun theLongDayIsTwentyFiveHours() = runTest {
        val saturdayLate = Instant.parse("2026-10-24T22:50:00Z").toEpochMilli()
        val sundayMidnight = saturdayLate + 10 * minute
        val hour = 60 * minute
        val prunedAt = mutableListOf<Long>()
        loop({ prunedAt += it }, start = saturdayLate)
        runCurrent()
        advanceTimeBy(10 * minute)
        runCurrent()
        assertThat(prunedAt).containsExactly(saturdayLate, sundayMidnight).inOrder()
        advanceTimeBy(24 * hour)
        runCurrent()
        assertThat(prunedAt).hasSize(2)
        advanceTimeBy(hour)
        runCurrent()
        assertThat(prunedAt).containsExactly(saturdayLate, sundayMidnight, sundayMidnight + 25 * hour).inOrder()
    }

    /** A new keep prunes at once; the value already read at start doesn't, nor does the same value again. */
    @Test
    fun aKeepChangePrunes() = runTest {
        val prunedAt = mutableListOf<Long>()
        val keep = MutableStateFlow(KeepDays.TWO)
        loop({ prunedAt += it }, keep = keep)
        runCurrent()
        assertThat(prunedAt).hasSize(1)
        keep.value = KeepDays.SEVEN
        runCurrent()
        assertThat(prunedAt).hasSize(2)
        keep.value = KeepDays.SEVEN
        runCurrent()
        assertThat(prunedAt).hasSize(2)
    }

    /** A prune that fails is logged by class name and tried again by the next trigger, on every path that prunes. */
    @Test
    fun aPruneFailureIsLoggedNotThrown() = runTest {
        ShadowLog.clear()
        var attempts = 0
        val keep = MutableStateFlow(KeepDays.TWO)
        val inFront = MutableStateFlow(false)
        loop({ attempts++; throw IOException("disk full at Front Door") }, inFront = inFront, keep = keep)
        runCurrent()
        keep.value = KeepDays.THIRTY
        runCurrent()
        inFront.value = true
        runCurrent()
        advanceTimeBy(DAY_MS)
        runCurrent()
        assertThat(attempts).isAtLeast(4)
        val logs = ShadowLog.getLogs().filter { it.tag == "DoorPruneLoop" }
        assertThat(logs).hasSize(attempts)
        logs.forEach {
            assertThat(it.msg).contains("IOException")
            assertThat(it.msg).doesNotContain("Front Door")
        }
    }
}
