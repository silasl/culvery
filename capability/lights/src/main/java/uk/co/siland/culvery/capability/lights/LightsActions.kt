package uk.co.siland.culvery.capability.lights

import android.util.Log
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import uk.co.siland.culvery.core.plugin.ApplicationScope
import uk.co.siland.culvery.core.plugin.attempt

private const val TAG = "LightsActions"

/** The least time between two brightness commands to one room while its slider moves (7 §4.3). */
const val DRAG_GAP_MS = 300L

/** What the household asked of the lights, the only thing the logs name (7 §6). */
enum class LightsCommand { ON, OFF, BRIGHTNESS, SCENE, ALL_OFF, HOLIDAY_ON, HOLIDAY_OFF }

/**
 * The screens' commands (7 §4.3): each runs on the application scope, so leaving the screen doesn't cancel it. When Hue
 * refuses one, its room's row says so until that room's next command; the bridge not answering is the status line's to say.
 */
@Singleton
class LightsActions internal constructor(
    private val system: () -> LightsSystem?,
    private val scope: CoroutineScope,
    private val now: () -> Long,
) {
    @Inject
    constructor(bridges: Bridges, @ApplicationScope scope: CoroutineScope) : this({ bridges.system.value }, scope, System::currentTimeMillis)

    private class Drag {
        var lastSentAt: Long? = null
        var waiting: Int? = null
        var job: Job? = null
        var turnedOn = false
    }

    private val drags = HashMap<String, Drag>()
    private val _lines = MutableStateFlow<Map<String, String>>(emptyMap())

    /** A room's line after its last command went wrong (7 §6), by room id. */
    val lines: StateFlow<Map<String, String>> = _lines.asStateFlow()

    fun setOn(room: LightRoom, on: Boolean) = send(room, if (on) LightsCommand.ON else LightsCommand.OFF) { it.setOn(room, on) }

    fun toggle(room: LightRoom) = setOn(room, !room.on)

    fun recallScene(room: LightRoom, scene: LightScene) = send(room, LightsCommand.SCENE) { it.recallScene(room, scene) }

    /** The slider is moving: sends now when the room has had no command for [DRAG_GAP_MS], else the newest value when the gap is up. */
    fun dragBrightness(room: LightRoom, percent: Int) {
        _lines.update { it - room.id }
        val s = system() ?: return
        synchronized(drags) {
            val drag = drags.getOrPut(room.id) { Drag() }
            drag.waiting = percent
            if (drag.job != null) return
            val last = drag.lastSentAt
            if (last == null || now() - last >= DRAG_GAP_MS) {
                sendWaiting(s, room, drag)
            } else {
                drag.job = scope.launch {
                    delay(last + DRAG_GAP_MS - now())
                    synchronized(drags) {
                        drag.job = null
                        sendWaiting(s, room, drag)
                    }
                }
            }
        }
    }

    /** The slider is let go at [percent]: any value waiting is dropped and this one goes at once. */
    fun letGoBrightness(room: LightRoom, percent: Int) {
        _lines.update { it - room.id }
        val s = system() ?: return
        synchronized(drags) {
            val drag = drags.getOrPut(room.id) { Drag() }
            drag.job?.cancel()
            drag.job = null
            drag.waiting = percent
            sendWaiting(s, room, drag, ending = true)
        }
    }

    private fun sendWaiting(s: LightsSystem, room: LightRoom, drag: Drag, ending: Boolean = false) {
        val percent = drag.waiting ?: return
        drag.waiting = null
        drag.lastSentAt = now()
        val turnOn = !room.on && !drag.turnedOn
        if (turnOn) drag.turnedOn = true
        if (ending) drag.turnedOn = false
        launchCommand(room, LightsCommand.BRIGHTNESS) {
            if (turnOn) s.setOn(room, true)
            s.setBrightness(room, percent)
        }
    }

    private fun send(room: LightRoom, command: LightsCommand, step: suspend (LightsSystem) -> Unit) {
        _lines.update { it - room.id }
        val s = system() ?: return
        launchCommand(room, command) { step(s) }
    }

    private fun launchCommand(room: LightRoom, command: LightsCommand, step: suspend () -> Unit) {
        scope.launch {
            attempt({ step() }) { e ->
                Log.w(TAG, "${command.name} failed (${e::class.simpleName})")
                if (e is LightsRefusedException) _lines.update { it + (room.id to HUE_REFUSED) }
            }
        }
    }
}
