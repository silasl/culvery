package uk.co.siland.culvery.display.camera

import android.util.Log
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import uk.co.siland.culvery.core.plugin.ApplicationScope
import uk.co.siland.culvery.core.plugin.Startable
import uk.co.siland.culvery.core.plugin.WallClock
import uk.co.siland.culvery.display.DisplayState

private const val TAG = "Presence"

internal const val LOOK_EVERY_MS = 1_000L
internal const val CAMERA_RETRY_MS = 300_000L

internal class NoFrontCamera : Exception()

internal class CameraStopped : Exception()

/**
 * The front camera as small greyscale frames, given to `onFrame` on the camera's own thread until the returned handle is
 * closed. A failure closes the camera before `onFailed` is called.
 */
internal fun interface FrameSource {
    fun open(onFrame: (Luma) -> Unit, onFailed: (Exception) -> Unit): AutoCloseable
}

/**
 * Runs the camera only while [wanted], looks at one frame a second and calls [moved] when something moved; no frame
 * leaves it (8 §4.3). [running] is true from before the camera opens until after it has closed, for the camera mark.
 */
@Singleton
internal class Presence internal constructor(
    private val frames: FrameSource,
    private val clock: WallClock,
    private val wanted: StateFlow<Boolean>,
    private val moved: () -> Unit,
    private val scope: CoroutineScope,
    private val diff: FrameDiff = FrameDiff(),
) : Startable {
    @Inject
    constructor(frames: FrameSource, clock: WallClock, display: DisplayState, @ApplicationScope scope: CoroutineScope) :
        this(frames, clock, display.cameraWanted, display::moved, scope)

    // Opening, closing and failures take [turning]; the frame state shared with the camera's thread takes [looking].
    private val turning = Any()
    private val looking = Any()
    private var camera: AutoCloseable? = null
    private var retry: Job? = null

    // Written under both locks; a callback from an earlier opening carries an older number and is ignored.
    private var session = 0
    private var lastLook: Long? = null

    private val _running = MutableStateFlow(false)
    val running: StateFlow<Boolean> = _running.asStateFlow()

    override fun start() {
        scope.launch { wanted.collect { if (it) open() else stop() } }
    }

    private fun open() {
        synchronized(turning) {
            if (camera != null) return
            val mine = synchronized(looking) { ++session }
            _running.value = true
            // A failure is handled on the scope: closing from the camera's own thread could wait on itself.
            camera = frames.open({ look(mine, it) }, { e -> scope.launch { failed(mine, e) } })
        }
    }

    private fun stop() {
        synchronized(turning) {
            retry?.cancel()
            retry = null
            close()
        }
    }

    private fun close() {
        val closing = camera
        camera = null
        synchronized(looking) { session++ }
        closing?.close()
        synchronized(looking) {
            diff.reset()
            lastLook = null
        }
        _running.value = false
    }

    private fun failed(mine: Int, e: Exception) {
        synchronized(turning) {
            if (mine != session) return
            close()
            Log.w(TAG, "The camera stopped (${e::class.simpleName})")
            if (e !is NoFrontCamera) {
                retry = scope.launch {
                    delay(CAMERA_RETRY_MS)
                    // Past its delay a cancel can't stop it, so a stop that came first is seen as retry no longer being this job.
                    synchronized(turning) {
                        if (wanted.value && retry === coroutineContext[Job]) open()
                    }
                }
            }
        }
    }

    private fun look(mine: Int, frame: Luma) {
        val now = clock.nowMillis()
        val moving = synchronized(looking) {
            if (mine != session) return
            lastLook?.let { if (now - it < LOOK_EVERY_MS) return }
            lastLook = now
            diff.moved(frame)
        }
        if (moving) scope.launch { moved() }
    }
}
