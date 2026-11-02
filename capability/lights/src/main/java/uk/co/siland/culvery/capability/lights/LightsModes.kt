package uk.co.siland.culvery.capability.lights

import android.util.Log
import javax.inject.Inject
import kotlin.math.abs
import kotlin.math.max
import uk.co.siland.culvery.core.house.HouseScene
import uk.co.siland.culvery.core.house.LightChoice
import uk.co.siland.culvery.core.house.LightOptions
import uk.co.siland.culvery.core.house.LightRoomOption
import uk.co.siland.culvery.core.house.RoomBefore
import uk.co.siland.culvery.core.house.RoomChoice
import uk.co.siland.culvery.core.plugin.attempt

private const val TAG = "LightsModes"

/** What the mode was doing when it failed: the only thing the log names. */
private enum class Step { BEFORE, RECALL, BACK, OPTIONS }

/** A mode's room choices and put-back (13 §2.12, §2.13, §4.1.4, §4.2.2; 15b §4.2, §4.3). */
class LightsModes internal constructor(private val system: () -> LightsSystem?) {
    @Inject
    constructor(bridges: Bridges) : this({ bridges.system.value })

    suspend fun options(): LightOptions {
        val s = system() ?: return LightOptions(emptyList(), problemLine(LightsProblem.UNREACHABLE))
        readAll(s, Step.OPTIONS)?.let { return LightOptions(emptyList(), failureLine(it)) }
        return LightOptions(s.rooms.value.map { r -> LightRoomOption(r.id, r.name, r.scenes.map { HouseScene(it.id, it.name) }, r.dimmable) }, null)
    }

    /** Throws when the bridge can't be read. */
    suspend fun before(choices: List<RoomChoice>): List<RoomBefore> {
        val s = system() ?: throw LightsUnreachableException(LightsProblem.UNREACHABLE)
        readAll(s, Step.BEFORE)?.let { throw it }
        val rooms = s.rooms.value
        return choices.mapNotNull { c ->
            val room = rooms.firstOrNull { it.id == c.roomId } ?: return@mapNotNull null
            val choice = c.choice
            if (choice is LightChoice.Scene && room.scenes.none { it.id == choice.sceneId }) return@mapNotNull null
            RoomBefore(room.id, room.name, choice, room.on, room.brightness, room.activeSceneId)
        }
    }

    suspend fun on(rooms: List<RoomBefore>): List<String> {
        val s = system() ?: return rooms.map { it.name }
        return rooms.filterNot { before ->
            val room = s.rooms.value.firstOrNull { it.id == before.roomId } ?: return@filterNot false
            val send: suspend () -> Unit = when (val choice = before.choice) {
                is LightChoice.Scene -> {
                    val scene = room.scenes.firstOrNull { it.id == choice.sceneId } ?: return@filterNot false
                    suspend { s.recallScene(room, scene) }
                }
                is LightChoice.On -> suspend { s.setBrightness(room, choice.percent) }
                LightChoice.Off -> suspend { s.setOn(room, false) }
            }
            attempt({
                send()
                true
            }) { e ->
                logFailure(Step.RECALL, e)
                false
            }
        }.map { it.name }
    }

    /** The rooms still as the mode left them but which didn't go back. */
    suspend fun back(rooms: List<RoomBefore>): List<String> {
        val s = system() ?: return rooms.map { it.name }
        readAll(s, Step.BACK)?.let { return rooms.map { it.name } }
        val now = s.rooms.value
        return rooms.filter { before ->
            val room = now.firstOrNull { it.id == before.roomId && before.choice.stillAsLeft(it) } ?: return@filter false
            !putBack(s, room, before)
        }.map { it.name }
    }

    /** Whether [room] went back as [before] had it. */
    private suspend fun putBack(s: LightsSystem, room: LightRoom, before: RoomBefore): Boolean = attempt({
        val remembered = room.scenes.firstOrNull { it.id == before.activeSceneId }
        when {
            // Checked first: a group-off can leave the bridge reporting the old scene as active.
            !before.on -> s.setOn(room, false)
            remembered != null -> s.recallScene(room, remembered)
            else -> s.setBrightness(room, max(before.brightness, 1))
        }
        true
    }) { e ->
        logFailure(Step.BACK, e)
        false
    }

    /** Reads the bridge afresh: null, or the failure, logged. */
    private suspend fun readAll(s: LightsSystem, step: Step): Exception? = attempt({
        s.readAll()
        null
    }) { e ->
        logFailure(step, e)
        e
    }

    private fun failureLine(e: Exception) = if (e is LightsUnreachableException) problemLine(e.problem) else HUE_REFUSED

    private fun logFailure(step: Step, e: Exception) {
        Log.w(TAG, "Mode lights: ${step.name} failed (${e::class.simpleName})")
    }
}

/** Whether nobody has changed [room] by hand since the mode set it (15b §4.3): read from the bridge only. */
internal fun LightChoice.stillAsLeft(room: LightRoom): Boolean = when (this) {
    is LightChoice.Scene -> room.activeSceneId == sceneId
    is LightChoice.On -> room.on && (!room.dimmable || abs(room.brightness - percent) <= 5)
    LightChoice.Off -> !room.on
}
