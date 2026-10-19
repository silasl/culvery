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
import java.util.concurrent.Executors
import javax.inject.Inject

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

/** The front camera at its smallest size and slowest rate; frames arrive on a thread of its own (8 §4.3). */
internal class Camera2Frames internal constructor(
    private val context: Context,
    private val executor: Executor,
) : FrameSource {
    @Inject
    constructor(@ApplicationContext context: Context) : this(context, Executors.newSingleThreadExecutor())

    override fun open(onFrame: (Luma) -> Unit, onFailed: (Exception) -> Unit): AutoCloseable =
        Opening(onFrame, onFailed).also { it.start() }

    private inner class Opening(
        private val onFrame: (Luma) -> Unit,
        private val onFailed: (Exception) -> Unit,
    ) : CameraDevice.StateCallback(), AutoCloseable {
        // Held while a frame is read, so close() can't free the reader under it.
        private val lock = Any()
        private var closed = false
        private var characteristics: CameraCharacteristics? = null
        private var device: CameraDevice? = null
        private var session: CameraCaptureSession? = null
        private var reader: ImageReader? = null
        private var thread: HandlerThread? = null

        fun start() {
            try {
                val manager = context.getSystemService(CameraManager::class.java)
                val front = manager.cameraIdList.firstNotNullOfOrNull { id ->
                    manager.getCameraCharacteristics(id)
                        .takeIf { it.get(CameraCharacteristics.LENS_FACING) == CameraCharacteristics.LENS_FACING_FRONT }
                        ?.let { id to it }
                }
                if (front == null) return fail(NoFrontCamera())
                characteristics = front.second
                manager.openCamera(front.first, executor, this)
            } catch (e: Exception) {
                fail(e)
            }
        }

        override fun onOpened(camera: CameraDevice) {
            val failure = synchronized(lock) {
                if (closed) {
                    camera.close()
                    return
                }
                device = camera
                try {
                    configure(camera)
                    null
                } catch (e: Exception) {
                    e
                }
            }
            failure?.let(::fail)
        }

        override fun onDisconnected(camera: CameraDevice) {
            camera.close()
            fail(CameraStopped())
        }

        override fun onError(camera: CameraDevice, error: Int) {
            camera.close()
            fail(CameraStopped())
        }

        private fun configure(camera: CameraDevice) {
            val chosen = checkNotNull(characteristics)
            val (width, height) = smallestSize(
                checkNotNull(chosen.get(CameraCharacteristics.SCALER_STREAM_CONFIGURATION_MAP))
                    .getOutputSizes(ImageFormat.YUV_420_888).map { it.width to it.height },
            )
            val rates = slowestRange(
                checkNotNull(chosen.get(CameraCharacteristics.CONTROL_AE_AVAILABLE_TARGET_FPS_RANGES)).map { it.lower..it.upper },
            )
            val looper = HandlerThread("CulveryPresence").also { thread = it; it.start() }.looper
            val images = ImageReader.newInstance(width, height, ImageFormat.YUV_420_888, 2).also { reader = it }
            images.setOnImageAvailableListener({ frame(it) }, Handler(looper))
            val request = camera.createCaptureRequest(CameraDevice.TEMPLATE_PREVIEW).apply {
                addTarget(images.surface)
                set(CaptureRequest.CONTROL_AE_TARGET_FPS_RANGE, Range(rates.first, rates.last))
            }.build()
            val sessionCallback = object : CameraCaptureSession.StateCallback() {
                override fun onConfigured(configured: CameraCaptureSession) {
                    val failure = synchronized(lock) {
                        if (closed) {
                            configured.close()
                            return
                        }
                        session = configured
                        try {
                            configured.setRepeatingRequest(request, null, null)
                            null
                        } catch (e: Exception) {
                            e
                        }
                    }
                    failure?.let(::fail)
                }

                override fun onConfigureFailed(failed: CameraCaptureSession) {
                    failed.close()
                    fail(CameraStopped())
                }
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

        private fun frame(images: ImageReader) {
            val luma = synchronized(lock) {
                if (closed) return
                val image = images.acquireLatestImage() ?: return
                try {
                    val y = image.planes[0]
                    lumaOf(y.buffer, y.rowStride, y.pixelStride, image.width, image.height)
                } finally {
                    image.close()
                }
            }
            onFrame(luma)
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
                thread?.quitSafely()
                session = null
                device = null
                reader = null
                thread = null
                return true
            }
        }
    }
}
