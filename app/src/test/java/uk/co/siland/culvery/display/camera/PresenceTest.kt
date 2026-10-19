package uk.co.siland.culvery.display.camera

import android.util.Log
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.currentTime
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.shadows.ShadowLog
import uk.co.siland.culvery.display.Screen

/** A camera the test opens, feeds and fails by hand; its close records whether the mark was still showing. */
private class FakeFrameSource : FrameSource {
    var opens = 0
    var isOpen = false
    var running: () -> Boolean = { false }
    val runningAtClose = mutableListOf<Boolean>()
    var reads = 0
    private var onFrame: (() -> Luma) -> Unit = {}
    private var onFailed: (Exception) -> Unit = {}

    override fun open(onFrame: (read: () -> Luma) -> Unit, onFailed: (Exception) -> Unit): AutoCloseable {
        opens++
        isOpen = true
        this.onFrame = onFrame
        this.onFailed = onFailed
        return AutoCloseable {
            runningAtClose += running()
            isOpen = false
        }
    }

    fun frame(luma: Luma) = onFrame {
        reads++
        luma
    }

    fun fail(e: Exception) = onFailed(e)
}

/** A started presence over a fake camera, on the test's scheduler and clock. */
private class Rig(
    private val test: TestScope,
    val wanted: MutableStateFlow<Boolean> = MutableStateFlow(false),
    seen: StateFlow<Boolean> = wanted,
) {
    val source = FakeFrameSource()
    var moves = 0
    val screen = MutableStateFlow(Screen.AWAKE)
    var clock: () -> Long = { test.currentTime }
    val presence = Presence(source, { clock() }, seen, screen, { moves++ }, test.backgroundScope)

    val running: Boolean get() = presence.running.value

    init {
        ShadowLog.clear()
        source.running = { presence.running.value }
        presence.start()
        test.runCurrent()
    }

    fun want(on: Boolean) {
        wanted.value = on
        test.runCurrent()
    }

    fun at(ms: Long) {
        test.advanceTimeBy(ms - test.currentTime)
        test.runCurrent()
    }

    fun frameAt(ms: Long, luma: Luma) {
        at(ms)
        source.frame(luma)
        test.runCurrent()
    }

    fun screen(to: Screen) {
        screen.value = to
        test.runCurrent()
    }

    fun fail(e: Exception) {
        source.fail(e)
        test.runCurrent()
    }

    fun logs() = ShadowLog.getLogsForTag("Presence").map { it.type to it.msg }
}

@RunWith(AndroidJUnit4::class)
class PresenceTest {
    /** Fails if starting opens the camera before the display wants it. */
    @Test
    fun nothingOpensUntilWanted() = runTest {
        val rig = Rig(this)
        rig.at(10_000)
        assertThat(rig.source.opens).isEqualTo(0)
        assertThat(rig.running).isFalse()
    }

    /** Fails if a frame, time passing or the same wish again opens a second camera. */
    @Test
    fun wantedOpensOnce() = runTest {
        val rig = Rig(this)
        rig.want(true)
        rig.frameAt(0, block(4))
        rig.frameAt(5_000, block(4))
        rig.wanted.value = true
        runCurrent()
        assertThat(rig.source.opens).isEqualTo(1)
        assertThat(rig.source.isOpen).isTrue()
    }

    /** Fails if no longer wanted leaves the camera open, or a frame arriving after the close is still looked at. */
    @Test
    fun notWantedCloses() = runTest {
        val rig = Rig(this)
        rig.want(true)
        rig.frameAt(0, block(4))
        rig.want(false)
        assertThat(rig.source.isOpen).isFalse()
        assertThat(rig.running).isFalse()

        rig.frameAt(2_000, block(10))
        assertThat(rig.moves).isEqualTo(0)
    }

    /** Fails if the mark turns off before close() returns, or stays on with the camera closed. */
    @Test
    fun runningIsTrueWheneverTheCameraIsOpen() = runTest {
        val rig = Rig(this)
        fun matches() = assertThat(rig.running).isEqualTo(rig.source.isOpen)
        matches()
        rig.want(true)
        assertThat(rig.running).isTrue()
        matches()
        rig.frameAt(0, block(4))
        matches()
        rig.want(false)
        matches()
        rig.want(true)
        matches()
        rig.fail(CameraStopped())
        matches()
        rig.at(CAMERA_RETRY_MS)
        assertThat(rig.running).isTrue()
        matches()
        rig.want(false)
        matches()
        assertThat(rig.source.runningAtClose).containsExactly(true, true, true)
    }

    /** Fails if a frame under LOOK_EVERY_MS after the last one looked at is decoded or diffed, or one at exactly 1 000 ms is dropped. */
    @Test
    fun onlyOneFrameASecondIsLookedAt() = runTest {
        val rig = Rig(this)
        rig.want(true)
        rig.frameAt(0, block(4))
        rig.frameAt(300, block(10))
        rig.frameAt(700, block(4))
        rig.frameAt(999, block(10))
        assertThat(rig.moves).isEqualTo(0)
        assertThat(rig.source.reads).isEqualTo(1)
        rig.frameAt(1_000, block(10))
        assertThat(rig.moves).isEqualTo(1)
        assertThat(rig.source.reads).isEqualTo(2)
    }

    /** Fails if one movement calls moved more than once, or not at all. */
    @Test
    fun movementCallsMovedOnce() = runTest {
        val rig = Rig(this)
        rig.want(true)
        rig.frameAt(0, block(4))
        rig.frameAt(1_000, block(10))
        assertThat(rig.moves).isEqualTo(1)
        rig.frameAt(2_000, block(10))
        assertThat(rig.moves).isEqualTo(1)
    }

    /** Fails if closing keeps the last frame, so the first frame after reopening is compared against it. */
    @Test
    fun closingForgetsTheLastFrame() = runTest {
        val rig = Rig(this)
        rig.want(true)
        rig.frameAt(0, block(4))
        rig.want(false)
        rig.want(true)
        rig.frameAt(2_000, block(10))
        assertThat(rig.moves).isEqualTo(0)
        rig.frameAt(3_000, block(4))
        assertThat(rig.moves).isEqualTo(1)
    }

    /** Fails if a failure leaves the camera open or the mark on, logs more or other than the class name, or logs twice. */
    @Test
    fun aCameraErrorIsNoPresenceAndOneLine() = runTest {
        val rig = Rig(this)
        rig.want(true)
        rig.frameAt(0, block(4))
        rig.fail(CameraStopped())
        assertThat(rig.source.isOpen).isFalse()
        assertThat(rig.running).isFalse()
        rig.frameAt(1_000, block(10))
        assertThat(rig.moves).isEqualTo(0)
        assertThat(rig.logs()).containsExactly(Log.WARN to "The camera stopped (CameraStopped)")
    }

    /** Fails if the retry comes before CAMERA_RETRY_MS, or never comes while still wanted. */
    @Test
    fun aFailedCameraIsTriedAgainAfterFiveMinutes() = runTest {
        val rig = Rig(this)
        rig.want(true)
        rig.fail(CameraStopped())
        rig.at(299_999)
        assertThat(rig.source.opens).isEqualTo(1)
        rig.at(300_000)
        assertThat(rig.source.opens).isEqualTo(2)
        assertThat(rig.source.isOpen).isTrue()
    }

    /** Fails if the retry opens the camera after the display stopped wanting it. */
    @Test
    fun aRetryWaitsOnlyWhileWanted() = runTest {
        val rig = Rig(this)
        rig.want(true)
        rig.fail(CameraStopped())
        rig.at(100_000)
        rig.want(false)
        rig.at(600_000)
        assertThat(rig.source.opens).isEqualTo(1)
        assertThat(rig.running).isFalse()
    }

    /** Fails if the retry checks wanted outside the lock, so a stop landing between its check and its open leaves the camera on. */
    @Test
    fun aStopBetweenTheRetrysCheckAndItsOpenLeavesTheCameraClosed() = runTest {
        val backing = MutableStateFlow(false)
        var stopOnRead = false
        val wanted = object : StateFlow<Boolean> by backing {
            override val value: Boolean
                get() {
                    val read = backing.value
                    if (stopOnRead) {
                        stopOnRead = false
                        backing.value = false
                        testScheduler.runCurrent()
                    }
                    return read
                }
        }
        val rig = Rig(this, backing, wanted)
        rig.want(true)
        rig.fail(CameraStopped())
        rig.at(299_999)
        stopOnRead = true
        rig.at(300_000)
        assertThat(stopOnRead).isFalse()
        assertThat(rig.source.opens).isEqualTo(1)
        assertThat(rig.source.isOpen).isFalse()
        assertThat(rig.running).isFalse()
    }

    /** Fails if a tablet with no front camera is asked again every five minutes. */
    @Test
    fun noFrontCameraIsNeverRetried() = runTest {
        val rig = Rig(this)
        rig.want(true)
        rig.fail(NoFrontCamera())
        rig.at(3_600_000)
        assertThat(rig.source.opens).isEqualTo(1)
        assertThat(rig.running).isFalse()
        assertThat(rig.logs()).containsExactly(Log.WARN to "The camera stopped (NoFrontCamera)")
    }

    /** Fails if a failure reported by a camera already closed logs, or schedules a retry. */
    @Test
    fun aLateFailureFromAClosedCameraIsIgnored() = runTest {
        val rig = Rig(this)
        rig.want(true)
        rig.want(false)
        rig.fail(CameraStopped())
        rig.want(true)
        rig.at(600_000)
        assertThat(rig.source.opens).isEqualTo(2)
        assertThat(rig.logs()).isEmpty()
    }

    /** Fails if the frame lit by the screen before it went dark is kept, so the first dark frame compared against it wakes the wall. */
    @Test
    fun goingToSleepForgetsTheLitFrame() = runTest {
        val rig = Rig(this)
        rig.want(true)
        rig.frameAt(0, block(4))
        rig.screen(Screen.ASLEEP)
        rig.frameAt(1_000, block(10))
        assertThat(rig.moves).isEqualTo(0)
        assertThat(rig.source.isOpen).isTrue()
        rig.frameAt(2_000, block(4))
        assertThat(rig.moves).isEqualTo(1)
    }

    /** Fails if a frame under LOOK_EVERY_MS after going dark is looked at, when the backlight and exposure are still settling, or one at exactly 1 000 ms is not. */
    @Test
    fun goingToSleepWaitsALookBeforeTheNextFrame() = runTest {
        val rig = Rig(this)
        rig.want(true)
        rig.frameAt(0, block(4))
        rig.at(1_500)
        rig.screen(Screen.ASLEEP)
        val reads = rig.source.reads
        rig.frameAt(2_499, block(10))
        assertThat(rig.source.reads).isEqualTo(reads)
        rig.frameAt(2_500, block(10))
        assertThat(rig.source.reads).isEqualTo(reads + 1)
    }
}
