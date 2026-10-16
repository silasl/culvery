package uk.co.siland.culvery.capability.lights

import androidx.test.ext.junit.runners.AndroidJUnit4
import com.google.common.truth.Truth.assertThat
import java.io.IOException
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Test
import org.junit.runner.RunWith
import uk.co.siland.culvery.core.plugin.retryDelayMillis

/** 7 §4.2: the stream and the full reads only while in front and a lights screen shows; reopened with backoff. */
@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(AndroidJUnit4::class)
class LightsFollowTest {
    private val system = TestLightsSystem()
    private val systemFlow = MutableStateFlow<LightsSystem?>(system)
    private val inFront = MutableStateFlow(true)

    private fun TestScope.follow() = LightsFollow(systemFlow, inFront, backgroundScope).also { it.start() }

    private fun count(call: String) = system.calls.count { it == call }

    private suspend fun TestScope.after(ms: Long) {
        advanceTimeBy(ms)
        runCurrent()
    }

    /** Fails if anything is read before a screen watches, or while only one of the two conditions holds. */
    @Test
    fun nothingIsReadUntilAScreenShowsInFront() = runTest {
        val follow = follow()
        runCurrent()
        assertThat(system.calls).isEmpty()
        inFront.value = true
        after(READ_ALL_MS * 2)
        assertThat(system.calls).isEmpty()
        follow.watch()
        runCurrent()
        assertThat(system.calls).containsExactly("readAll", "stream").inOrder()
    }

    /** Fails at 59 999 ms if the loop is faster, at 60 000 if slower. */
    @Test
    fun aFullReadEverySixtySeconds() = runTest {
        follow().watch()
        runCurrent()
        after(READ_ALL_MS - 1)
        assertThat(count("readAll")).isEqualTo(1)
        after(1)
        assertThat(count("readAll")).isEqualTo(2)
    }

    /** Fails if the stream stays open, or the reads go on, after the last screen goes. */
    @Test
    fun leavingTheScreenClosesTheStream() = runTest {
        val stop = follow().watch()
        runCurrent()
        stop()
        runCurrent()
        assertThat(system.calls).containsExactly("readAll", "stream", "stream closed").inOrder()
        after(READ_ALL_MS)
        assertThat(count("readAll")).isEqualTo(1)
    }

    /** Fails if the stream stays open while Culvery is behind another app, or doesn't come back in front. */
    @Test
    fun goingBehindClosesTheStream() = runTest {
        follow().watch()
        runCurrent()
        inFront.value = false
        runCurrent()
        assertThat(system.calls).containsExactly("readAll", "stream", "stream closed").inOrder()
        after(READ_ALL_MS)
        assertThat(count("readAll")).isEqualTo(1)
        inFront.value = true
        runCurrent()
        assertThat(system.calls.takeLast(2)).containsExactly("readAll", "stream").inOrder()
    }

    /** Fails if a drop reopens sooner than 1 s, doesn't read again on reopening, or doesn't back off on a second drop. */
    @Test
    fun aDroppedStreamReopensWithBackoffAndReadsAgain() = runTest {
        follow().watch()
        runCurrent()
        system.streams[0].completeExceptionally(IOException("dropped"))
        runCurrent()
        system.calls.clear()
        after(retryDelayMillis(0) - 1)
        assertThat(system.calls).isEmpty()
        after(1)
        assertThat(system.calls).containsExactly("readAll", "stream").inOrder()
        system.streams[1].completeExceptionally(IOException("dropped"))
        runCurrent()
        system.calls.clear()
        after(retryDelayMillis(1) - 1)
        assertThat(system.calls).isEmpty()
        after(1)
        assertThat(system.calls).containsExactly("readAll", "stream").inOrder()
    }

    /** Fails if a stream that ends without an error is left closed. */
    @Test
    fun aStreamThatEndsCleanlyIsADrop() = runTest {
        follow().watch()
        runCurrent()
        system.streams[0].complete(Unit)
        runCurrent()
        system.calls.clear()
        after(retryDelayMillis(0) - 1)
        assertThat(system.calls).isEmpty()
        after(1)
        assertThat(system.calls).containsExactly("readAll", "stream").inOrder()
    }

    /** Fails if each screen opens its own stream, or the first to go closes the other's. */
    @Test
    fun twoScreensAreOneWatch() = runTest {
        val follow = follow()
        val home = follow.watch()
        val tab = follow.watch()
        runCurrent()
        assertThat(follow.watching).isEqualTo(2)
        assertThat(count("stream")).isEqualTo(1)
        home()
        home()
        runCurrent()
        assertThat(follow.watching).isEqualTo(1)
        assertThat(count("stream closed")).isEqualTo(0)
        tab()
        runCurrent()
        assertThat(count("stream closed")).isEqualTo(1)
    }

    /** Fails if a failing periodic read stops the reads. */
    @Test
    fun aFailingFullReadDoesNotStopTheReads() = runTest {
        follow().watch()
        runCurrent()
        system.failures["readAll"] = LightsUnreachableException(LightsProblem.UNREACHABLE)
        after(READ_ALL_MS * 2)
        assertThat(count("readAll")).isAtLeast(3)
    }
}
