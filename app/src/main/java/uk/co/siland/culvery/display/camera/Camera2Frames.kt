package uk.co.siland.culvery.display.camera

import android.content.Context
import android.graphics.ImageFormat
import android.hardware.camera2.CameraCaptureSession
import android.hardware.camera2.CameraCharacteristics
import android.hardware.camera2.CameraDevice
import android.hardware.camera2.CameraManager
import android.hardware.camera2.CaptureRequest
import android.hardware.camera2.params.OutputConfiguration
import android.hardware.camera2.params.SessionConfiguration
import android.media.ImageReader
import android.os.Handler
import android.os.HandlerThread
import android.util.Range
import dagger.hilt.android.qualifiers.ApplicationContext
import java.nio.ByteBuffer
import java.util.concurrent.Executor
import javax.inject.Inject
import javax.inject.Singleton

/** The smallest size by area that still fills the diff's [DIFF_WIDTH] × [DIFF_HEIGHT] (8 §4.3). */
internal fun smallestSize(sizes: List<Pair<Int, Int>>): Pair<Int, Int> =
    sizes.filter { (w, h) -> w >= DIFF_WIDTH && h >= DIFF_HEIGHT }.minBy { (w, h) -> w * h }

/** The range with the lowest top rate, then the lowest bottom rate (8 §4.3). */
internal fun slowestRange(ranges: List<IntRange>): IntRange = ranges.minWith(compareBy({ it.last }, { it.first }))

/** The Y plane averaged in blocks down to [DIFF_WIDTH] × [DIFF_HEIGHT], into a new array each time. */
internal fun lumaOf(plane: ByteBuffer, rowStride: Int, pixelStride: Int, width: Int, height: Int): Luma {
    val values = ByteArray(DIFF_WIDTH * DIFF_HEIGHT)
    for (by in 0 until DIFF_HEIGHT) {
        val top = by * height / DIFF_HEIGHT
        val bottom = (by + 1) * height / DIFF_HEIGHT
        for (bx in 0 until DIFF_WIDTH) {
            val left = bx * width / DIFF_WIDTH
            val right = (bx + 1) * width / DIFF_WIDTH
            var sum = 0
            for (y in top until bottom) {
                for (x in left until right) sum += plane.get(y * rowStride + x * pixelStride).toInt() and 0xFF
            }
            values[by * DIFF_WIDTH + bx] = (sum / ((bottom - top) * (right - left))).toByte()
        }
    }
    return Luma(DIFF_WIDTH, DIFF_HEIGHT, values)
}

/** The front camera at its smallest size and slowest rate; frames arrive on a thread of its own, which runs only while it is open (8 §4.3). */
@Singleton
internal class Camera2Frames internal constructor(
    private val context: Context,
    private val executorOf: (Handler) -> Executor,
) : FrameSource {
    @Inject
    constructor(@ApplicationContext context: Context) : this(context, { handler -> Executor { handler.post(it) } })

    override fun open(onFrame: (read: () -> Luma) -> Unit, onFailed: (Exception) -> Unit): AutoCloseable =
        Opening(onFrame, onFailed).also { it.start() }

    private inner class Opening(
        private val onFrame: (read: () -> Luma) -> Unit,
        private val onFailed: (Exception) -> Unit,
    ) : AutoCloseable {
        // Held while a frame is read, so close() can't free the reader under it.
        private val lock = Any()
        private var closed = false
        private val thread = HandlerThread("CulveryPresence")
        private lateinit var handler: Handler
        private lateinit var executor: Executor
        private var device: CameraDevice? = null
        private var session: CameraCaptureSession? = null
        private var reader: ImageReader? = null

        fun start() {
            try {
                thread.start()
                handler = Handler(thread.looper)
                executor = executorOf(handler)
                val manager = context.getSystemService(CameraManager::class.java)
                val front = manager.cameraIdList.firstNotNullOfOrNull { id ->
                    manager.getCameraCharacteristics(id)
                        .takeIf { it.get(CameraCharacteristics.LENS_FACING) == CameraCharacteristics.LENS_FACING_FRONT }
                        ?.let { id to it }
                }
                if (front == null) return fail(NoFrontCamera())
                val (id, characteristics) = front
                manager.openCamera(
                    id,
                    executor,
                    object : CameraDevice.StateCallback() {
                        override fun onOpened(camera: CameraDevice) = guarded(camera) {
                            device = it
                            configure(it, characteristics)
                        }

                        override fun onDisconnected(camera: CameraDevice) = lost(camera)

                        override fun onError(camera: CameraDevice, error: Int) = lost(camera)
                    },
                )
            } catch (e: Exception) {
                fail(e)
            }
        }

        /** Runs [use] under the lock unless already closed, when [opened] is closed instead; what it throws fails the opening. */
        private fun <T : AutoCloseable> guarded(opened: T, use: (T) -> Unit) {
            val failure = synchronized(lock) {
                if (closed) {
                    opened.close()
                    return
                }
                try {
                    use(opened)
                    null
                } catch (e: Exception) {
                    e
                }
            }
            failure?.let(::fail)
        }

        private fun lost(what: AutoCloseable) {
            what.close()
            fail(CameraStopped())
        }

        private fun configure(camera: CameraDevice, chosen: CameraCharacteristics) {
            val (width, height) = smallestSize(
                checkNotNull(chosen.get(CameraCharacteristics.SCALER_STREAM_CONFIGURATION_MAP))
                    .getOutputSizes(ImageFormat.YUV_420_888).map { it.width to it.height },
            )
            val rates = slowestRange(
                checkNotNull(chosen.get(CameraCharacteristics.CONTROL_AE_AVAILABLE_TARGET_FPS_RANGES)).map { it.lower..it.upper },
            )
            val images = ImageReader.newInstance(width, height, ImageFormat.YUV_420_888, 2).also { reader = it }
            images.setOnImageAvailableListener({ frame(it) }, handler)
            val request = camera.createCaptureRequest(CameraDevice.TEMPLATE_PREVIEW).apply {
                addTarget(images.surface)
                set(CaptureRequest.CONTROL_AE_TARGET_FPS_RANGE, Range(rates.first, rates.last))
            }.build()
            val sessionCallback = object : CameraCaptureSession.StateCallback() {
                override fun onConfigured(configured: CameraCaptureSession) = guarded(configured) {
                    session = it
                    it.setRepeatingRequest(request, null, null)
                }

                override fun onConfigureFailed(failed: CameraCaptureSession) = lost(failed)
            }
            camera.createCaptureSession(
                SessionConfiguration(
                    SessionConfiguration.SESSION_REGULAR,
                    listOf(OutputConfiguration(images.surface)),
                    executor,
                    sessionCallback,
                ),
            )
        }

        /** Every frame is taken and closed; it is decoded only if [onFrame] reads it. */
        private fun frame(images: ImageReader) {
            synchronized(lock) {
                if (closed) return
                val image = images.acquireLatestImage() ?: return
                try {
                    onFrame {
                        val y = image.planes[0]
                        lumaOf(y.buffer, y.rowStride, y.pixelStride, image.width, image.height)
                    }
                } finally {
                    image.close()
                }
            }
        }

        private fun fail(e: Exception) {
            if (shut()) onFailed(e)
        }

        override fun close() {
            shut()
        }

        /** Closes everything once; false if it was already closed. */
        private fun shut(): Boolean {
            synchronized(lock) {
                if (closed) return false
                closed = true
                session?.close()
                device?.close()
                reader?.close()
                thread.quitSafely()
                session = null
                device = null
                reader = null
                return true
            }
        }
    }
}
