package uk.co.siland.culvery.capability.music

import androidx.test.ext.junit.runners.AndroidJUnit4
import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Test
import org.junit.runner.RunWith

/** 6 §4.2: checks only while in front and a music screen shows; 2 seconds and 30 seconds; the tab opening. */
@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(AndroidJUnit4::class)
class MusicChecksTest {
    private val system = TestMusicSystem()
    private val systemFlow = MutableStateFlow<MusicSystem?>(system)
    private val inFront = MutableStateFlow(true)

    private fun TestScope.checks() = MusicChecks(systemFlow, inFront, backgroundScope).also { it.start() }

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

    /** Fails if checks run on after the last screen goes, or while Culvery is behind another app. */
    @Test
    fun checksStopWhenTheLastScreenGoesOrCulveryLeavesTheFront() = runTest {
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
        inFront.value = false
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
