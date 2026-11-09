package uk.co.siland.culvery.capability.music

import android.util.Log
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.currentTime
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.shadows.ShadowLog

/** 6 §4.2, 19 §3 and §5: checks only while the screen is lit and a music screen shows; 2 seconds and 30 seconds; the tab opening. */
@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(AndroidJUnit4::class)
class MusicChecksTest {
    private val system = TestMusicSystem()
    private val systemFlow = MutableStateFlow<MusicSystem?>(system)
    private val lit = MutableStateFlow(true)

    private fun TestScope.checks() = MusicChecks(systemFlow, lit, { currentTime }, backgroundScope).also { it.start() }

    private fun count(call: String) = system.calls.count { it == call }

    @Test
    fun nothingIsCheckedUntilAScreenWatches() = runTest {
        checks()
        advanceTimeBy(LAYOUT_CHECK_MS * 2)
        runCurrent()
        assertThat(system.calls).isEmpty()
    }

    /** Fails at 1 999 ms if the loop is faster, at 2 000 if slower; and if playback is read before the layout. */
    @Test
    fun watchingChecksTheLayoutThenPlaybackEveryTwoSeconds() = runTest {
        val checks = checks()
        checks.watch()
        runCurrent()
        assertThat(system.calls).containsExactly("checkLayout", "checkPlayback").inOrder()
        advanceTimeBy(PLAYBACK_CHECK_MS - 1)
        runCurrent()
        assertThat(count("checkPlayback")).isEqualTo(1)
        advanceTimeBy(1)
        runCurrent()
        assertThat(count("checkPlayback")).isEqualTo(2)
    }

    @Test
    fun theLayoutIsCheckedEveryThirtySeconds() = runTest {
        checks().watch()
        runCurrent()
        advanceTimeBy(LAYOUT_CHECK_MS - 1)
        runCurrent()
        assertThat(count("checkLayout")).isEqualTo(1)
        advanceTimeBy(1)
        runCurrent()
        assertThat(count("checkLayout")).isEqualTo(2)
    }

    /** 6 §4.2: the Music tab opening reads the layout at once, though Home was already watching. */
    @Test
    fun theMusicTabOpeningChecksTheLayoutAtOnce() = runTest {
        val checks = checks()
        checks.watch()
        runCurrent()
        advanceTimeBy(5_000)
        runCurrent()
        checks.watch(layoutNow = true)
        runCurrent()
        assertThat(count("checkLayout")).isEqualTo(2)
    }

    /** Regression guard: a dark screen waits, and lighting it reads the layout then playback with no wait. */
    @Test
    fun wakingChecksTheLayoutThenPlaybackAtOnce() = runTest {
        lit.value = false
        checks().watch()
        advanceTimeBy(LAYOUT_CHECK_MS)
        runCurrent()
        assertThat(system.calls).isEmpty()
        lit.value = true
        runCurrent()
        assertThat(system.calls).containsExactly("checkLayout", "checkPlayback").inOrder()
    }

    /** Regression guard: a screen that goes dark and lights again leaves one loop running, not two. */
    @Test
    fun aQuickSleepAndWakeRunsOneLoop() = runTest {
        checks().watch()
        runCurrent()
        lit.value = false
        runCurrent()
        lit.value = true
        runCurrent()
        system.calls.clear()
        advanceTimeBy(PLAYBACK_CHECK_MS)
        runCurrent()
        assertThat(system.calls).containsExactly("checkPlayback")
    }

    /** The virtual times, in ms since [from], at which [call] was made up to [upToMs]. */
    private fun TestScope.callTimes(call: String, upToMs: Long, from: Long = currentTime): List<Long> {
        val times = mutableListOf<Long>()
        var seen = 0
        while (currentTime - from <= upToMs) {
            runCurrent()
            val n = count(call)
            repeat(n - seen) { times += currentTime - from }
            seen = n
            advanceTimeBy(1)
        }
        return times
    }

    /** Playback waits 2 s, doubling to 60 s while it keeps failing. */
    @Test
    fun aFailingPlaybackCheckBacksOffToSixtySeconds() = runTest {
        system.failures["checkPlayback"] = MusicUnreachableException(MusicProblem.UNREACHABLE)
        checks().watch()
        assertThat(callTimes("checkPlayback", 122_000))
            .containsExactly(0L, 2_000L, 6_000L, 14_000L, 30_000L, 62_000L, 122_000L).inOrder()
    }

    /** The layout waits 30 s, doubling to 5 min while it keeps failing. */
    @Test
    fun aFailingLayoutReadBacksOffToFiveMinutes() = runTest {
        system.failures["checkLayout"] = MusicUnreachableException(MusicProblem.UNREACHABLE)
        checks().watch()
        assertThat(callTimes("checkLayout", 750_000))
            .containsExactly(0L, 30_000L, 90_000L, 210_000L, 450_000L, 750_000L).inOrder()
    }

    /** Checks at 0, 2, 6 and 14 s leave the 16 s wait; the calls are cleared. */
    private fun TestScope.failPlaybackToTheSixteenSecondWait(checks: MusicChecks) {
        system.failures["checkPlayback"] = MusicUnreachableException(MusicProblem.UNREACHABLE)
        checks.watch()
        runCurrent()
        advanceTimeBy(14_000)
        runCurrent()
        assertThat(count("checkPlayback")).isEqualTo(4)
        system.calls.clear()
    }

    private fun TestScope.theNextPlaybackCheckIsTwoSecondsOn() {
        advanceTimeBy(PLAYBACK_CHECK_MS - 1)
        runCurrent()
        assertThat(system.calls).isEmpty()
        advanceTimeBy(1)
        runCurrent()
        assertThat(system.calls).containsExactly("checkPlayback")
    }

    @Test
    fun theMusicTabResetsTheBackoff() = runTest {
        val checks = checks()
        failPlaybackToTheSixteenSecondWait(checks)
        checks.watch(layoutNow = true)
        runCurrent()
        assertThat(system.calls).containsExactly("checkLayout", "checkPlayback").inOrder()
        system.calls.clear()
        theNextPlaybackCheckIsTwoSecondsOn()
    }

    @Test
    fun wakingResetsTheBackoff() = runTest {
        failPlaybackToTheSixteenSecondWait(checks())
        lit.value = false
        runCurrent()
        lit.value = true
        runCurrent()
        assertThat(system.calls).containsExactly("checkLayout", "checkPlayback").inOrder()
        system.calls.clear()
        theNextPlaybackCheckIsTwoSecondsOn()
    }

    @Test
    fun oneLineForARunAndOneForTheRecovery() = runTest {
        ShadowLog.clear()
        system.failures["checkPlayback"] = MusicUnreachableException(MusicProblem.UNREACHABLE)
        checks().watch()
        runCurrent()
        advanceTimeBy(6_000)
        runCurrent()
        assertThat(count("checkPlayback")).isEqualTo(3)
        system.failures.remove("checkPlayback")
        advanceTimeBy(8_000)
        runCurrent()
        val lines = ShadowLog.getLogsForTag("MusicChecks").filter { it.type >= Log.INFO }.map { it.type to it.msg }
        assertThat(lines).containsExactly(
            Log.WARN to "A music playback check failed (MusicUnreachableException)",
            Log.INFO to "Music playback checks work again after 3 failures",
        ).inOrder()
    }

    /** The doorbell's quieter reads the layout only if the loop hasn't just done so. */
    @Test
    fun aRecentLayoutIsNotReadAgain() = runTest {
        val checks = checks()
        val stop = checks.watch()
        runCurrent()
        stop()
        runCurrent()
        system.calls.clear()
        advanceTimeBy(LAYOUT_CHECK_MS - 1)
        assertThat(checks.layoutIfOlderThan(system, LAYOUT_CHECK_MS)).isFalse()
        assertThat(system.calls).isEmpty()
        advanceTimeBy(1)
        assertThat(checks.layoutIfOlderThan(system, LAYOUT_CHECK_MS)).isTrue()
        assertThat(system.calls).containsExactly("checkLayout")
    }

    @Test
    fun noLayoutReadYetIsRead() = runTest {
        lit.value = false
        val checks = checks()
        runCurrent()
        assertThat(checks.layoutIfOlderThan(system, LAYOUT_CHECK_MS)).isTrue()
        assertThat(system.calls).containsExactly("checkLayout")
    }

    /** Regression guard: fails if checks run on after the last screen goes, or while the screen is dark. */
    @Test
    fun checksStopWhenTheLastScreenGoesOrTheScreenDarkens() = runTest {
        val checks = checks()
        val stop = checks.watch()
        runCurrent()
        stop()
        system.calls.clear()
        advanceTimeBy(LAYOUT_CHECK_MS)
        runCurrent()
        assertThat(system.calls).isEmpty()
        checks.watch()
        runCurrent()
        lit.value = false
        runCurrent()
        system.calls.clear()
        advanceTimeBy(LAYOUT_CHECK_MS)
        runCurrent()
        assertThat(system.calls).isEmpty()
    }

    /** A screen's stop called twice (a recomposition, say) mustn't stop another screen's checks. */
    @Test
    fun stoppingTwiceCountsOnce() = runTest {
        val checks = checks()
        val home = checks.watch()
        checks.watch()
        runCurrent()
        home()
        home()
        system.calls.clear()
        advanceTimeBy(PLAYBACK_CHECK_MS)
        runCurrent()
        assertThat(system.calls).contains("checkPlayback")
    }

    /** The speakers being off is the status line's business; the checks keep trying. */
    @Test
    fun aFailingCheckDoesNotStopTheChecks() = runTest {
        system.failures["checkPlayback"] = MusicUnreachableException(MusicProblem.UNREACHABLE)
        checks().watch()
        runCurrent()
        advanceTimeBy(PLAYBACK_CHECK_MS)
        runCurrent()
        assertThat(count("checkPlayback")).isEqualTo(2)
    }

    @Test
    fun noSystemNoChecks() = runTest {
        systemFlow.value = null
        checks().watch()
        runCurrent()
        assertThat(system.calls).isEmpty()
    }
}
