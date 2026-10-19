package uk.co.siland.culvery.display.camera

import android.content.Context
import android.hardware.camera2.CameraCharacteristics
import android.hardware.camera2.CameraDevice
import android.hardware.camera2.CameraManager
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.google.common.truth.Truth.assertThat
import java.nio.ByteBuffer
import java.util.concurrent.Executor
import org.junit.After
import org.junit.Assert.assertThrows
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.shadow.api.Shadow
import org.robolectric.shadows.ShadowCameraCharacteristics
import org.robolectric.shadows.ShadowCameraManager
import org.robolectric.util.ReflectionHelpers

/** Runs each task when the test says, on the test's thread, so the camera can open after the test has closed it. */
private class QueuedExecutor : Executor {
    private val tasks = ArrayDeque<Runnable>()

    override fun execute(command: Runnable) {
        tasks.addLast(command)
    }

    fun runAll() {
        while (tasks.isNotEmpty()) tasks.removeFirst().run()
    }
}

@RunWith(AndroidJUnit4::class)
class Camera2FramesTest {
    private val context = ApplicationProvider.getApplicationContext<Context>()
    private val manager = context.getSystemService(CameraManager::class.java)
    private val frames = mutableListOf<Luma>()
    private val failures = mutableListOf<Exception>()

    @After
    fun tearDown() = ShadowCameraManager.reset()

    private fun addCamera(id: String, facing: Int) {
        val characteristics = ShadowCameraCharacteristics.newCameraCharacteristics()
        Shadow.extract<ShadowCameraCharacteristics>(characteristics).set(CameraCharacteristics.LENS_FACING, facing)
        Shadow.extract<ShadowCameraManager>(manager).addCamera(id, characteristics)
    }

    private fun Camera2Frames.start() = open({ frames += it() }, { failures += it })

    /** A plane with [width] × [height] samples [pixelStride] apart and rows [rowStride] apart, its padding at 99. */
    private fun plane(width: Int, height: Int, rowStride: Int, pixelStride: Int, level: (x: Int, y: Int) -> Int): ByteBuffer {
        val bytes = ByteArray(rowStride * height) { 99 }
        for (y in 0 until height) for (x in 0 until width) bytes[y * rowStride + x * pixelStride] = level(x, y).toByte()
        return ByteBuffer.wrap(bytes)
    }

    private fun Luma.level(x: Int, y: Int) = values[y * width + x].toInt() and 0xFF

    /** Fails if the size is picked by width, or one smaller than the diff's own 32 × 24 is taken. */
    @Test
    fun theSmallestSizeIsPicked() {
        assertThat(smallestSize(listOf(1920 to 1080, 320 to 240, 176 to 144, 16 to 12))).isEqualTo(176 to 144)
    }

    /** Fails if the range is picked by its lower bound first, which takes 5..30 over 7..15. */
    @Test
    fun theSlowestRateIsPicked() {
        assertThat(slowestRange(listOf(15..30, 7..15, 30..30))).isEqualTo(7..15)
        assertThat(slowestRange(listOf(5..30, 10..15, 7..15))).isEqualTo(7..15)
    }

    /** Fails if the row or pixel stride is ignored, which reads the padding or the chroma bytes between samples. */
    @Test
    fun lumaReadsThePlaneWithItsStrides() {
        val halves = { x: Int, _: Int -> if (x < 32) 0 else 200 }
        for (pixelStride in listOf(1, 2)) {
            val rowStride = if (pixelStride == 1) 72 else 136
            val luma = lumaOf(plane(64, 48, rowStride, pixelStride, halves), rowStride, pixelStride, 64, 48)

            assertThat(luma.width).isEqualTo(DIFF_WIDTH)
            assertThat(luma.height).isEqualTo(DIFF_HEIGHT)
            for (y in 0 until DIFF_HEIGHT) {
                for (x in 0 until DIFF_WIDTH) assertThat(luma.level(x, y)).isEqualTo(if (x < 16) 0 else 200)
            }
        }
    }

    /** Fails if blocks are sampled at one pixel rather than averaged. */
    @Test
    fun lumaAveragesEachBlock() {
        val luma = lumaOf(plane(64, 48, 64, 1) { x, y -> if ((x + y) % 2 == 0) 100 else 200 }, 64, 1, 64, 48)

        assertThat(luma.values.map { it.toInt() and 0xFF }.distinct()).containsExactly(150)
    }

    /** Fails if each frame does not get its own array, since FrameDiff keeps the last one. */
    @Test
    fun eachLumaHasItsOwnBytes() {
        val buffer = plane(64, 48, 64, 1) { _, _ -> 10 }

        assertThat(lumaOf(buffer, 64, 1, 64, 48).values).isNotSameInstanceAs(lumaOf(buffer, 64, 1, 64, 48).values)
    }

    /** Fails if a tablet with only a back camera opens it, or says nothing until a frame that never comes. */
    @Test
    fun noFrontCameraFailsAtOnce() {
        addCamera("0", CameraCharacteristics.LENS_FACING_BACK)

        Camera2Frames(context) { Executor { it.run() } }.start()

        assertThat(failures.single()).isInstanceOf(NoFrontCamera::class.java)
        assertThat(frames).isEmpty()
    }

    /** Fails if a camera that opens after its handle was closed is kept open, or a second close throws. */
    @Test
    fun closingBeforeTheCameraOpensClosesItWhenItDoes() {
        addCamera("0", CameraCharacteristics.LENS_FACING_BACK)
        addCamera("1", CameraCharacteristics.LENS_FACING_FRONT)
        val executor = QueuedExecutor()

        val handle = Camera2Frames(context) { executor }.start()
        handle.close()
        executor.runAll()

        val device = ReflectionHelpers.getStaticField<CameraDevice>(ShadowCameraManager::class.java, "lastDevice")
        assertThat(device.id).isEqualTo("1")
        assertThrows(IllegalStateException::class.java) { device.createCaptureRequest(CameraDevice.TEMPLATE_PREVIEW) }
        handle.close()
        executor.runAll()
        assertThat(failures).isEmpty()
        assertThat(frames).isEmpty()
    }
}
