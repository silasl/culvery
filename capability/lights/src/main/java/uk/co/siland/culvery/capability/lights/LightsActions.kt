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
import uk.co.siland.culvery.core.access.AccessControl
import uk.co.siland.culvery.core.access.Refusal
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
 * The lights are free for everyone; Holiday mode asks for a fresh Adult or Admin PIN both ways (7 §2.8).
 */
@Singleton
class LightsActions internal constructor(
    private val system: () -> LightsSystem?,
    private val scope: CoroutineScope,
    private val now: () -> Long,
    private val house: LightsHouse? = null,
    private val access: AccessControl? = null,
) {
    @Inject
    constructor(bridges: Bridges, house: LightsHouse, access: AccessControl, @ApplicationScope scope: CoroutineScope) :
        this({ bridges.system.value }, scope, System::currentTimeMillis, house, access)

    private class Drag {
        var lastSentAt: Long? = null
        var waiting: Int? = null
        var job: Job? = null
    }

    private val drags = HashMap<String, Drag>()
    private val _lines = MutableStateFlow<Map<String, String>>(emptyMap())

    /** A room's line after its last command went wrong (7 §6), by room id. */
    val lines: StateFlow<Map<String, String>> = _lines.asStateFlow()

    private val _houseLine = MutableStateFlow<String?>(null)

    /** What went wrong in the last All lights off or Holiday mode run (7 §6); null when it went well. */
    val houseLine: StateFlow<String?> = _houseLine.asStateFlow()

    fun allOff() {
        val house = house ?: return
        if (house.running.value != null) return
        scope.launch { house.allOff()?.let { _houseLine.value = houseLine(it) } }
    }

    /** Asks for the PIN, then turns Holiday mode the other way; a tap while a run is going asks nothing. */
    fun toggleHoliday() {
        val house = house ?: return
        val access = access ?: return
        if (house.running.value != null) return
        scope.launch {
            val by = access.authorise(LightsPermissions.HOLIDAY, refusal = Refusal.InPad) ?: return@launch
            val result = house.toggleHoliday(by)
            if (result != HolidayResult.Busy) _houseLine.value = houseLine(result, house::nameOf)
        }
    }

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
            sendWaiting(s, room, drag)
        }
    }

    private fun sendWaiting(s: LightsSystem, room: LightRoom, drag: Drag) {
        val percent = drag.waiting ?: return
        drag.waiting = null
        drag.lastSentAt = now()
        launchCommand(room, LightsCommand.BRIGHTNESS) { s.setBrightness(room, percent) }
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
