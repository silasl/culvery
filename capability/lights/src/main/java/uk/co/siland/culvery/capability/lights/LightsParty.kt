package uk.co.siland.culvery.capability.lights

import android.util.Log
import javax.inject.Inject
import kotlin.math.max
import uk.co.siland.culvery.core.house.HouseScene
import uk.co.siland.culvery.core.house.LightOptions
import uk.co.siland.culvery.core.house.LightRoomOption
import uk.co.siland.culvery.core.house.RoomBefore
import uk.co.siland.culvery.core.house.SceneChoice
import uk.co.siland.culvery.core.plugin.attempt

private const val TAG = "LightsParty"

/** What Party was doing when it failed: the only thing the log names. */
private enum class Step { BEFORE, RECALL, BACK, OPTIONS }

/** Party mode's scenes and put-back (13 §2.12, §2.13, §4.1.4, §4.2.2). */
class LightsParty internal constructor(private val system: () -> LightsSystem?) {
    @Inject
    constructor(bridges: Bridges) : this({ bridges.system.value })

    suspend fun options(): LightOptions {
        val s = system() ?: return LightOptions(emptyList(), problemLine(LightsProblem.UNREACHABLE))
        readAll(s, Step.OPTIONS)?.let { return LightOptions(emptyList(), failureLine(it)) }
        return LightOptions(s.rooms.value.map { r -> LightRoomOption(r.id, r.name, r.scenes.map { HouseScene(it.id, it.name) }) }, null)
    }

    /** Throws when the bridge can't be read. */
    suspend fun before(choices: List<SceneChoice>): List<RoomBefore> {
        val s = system() ?: throw LightsUnreachableException(LightsProblem.UNREACHABLE)
        readAll(s, Step.BEFORE)?.let { throw it }
        val rooms = s.rooms.value
        return choices.mapNotNull { c ->
            val room = rooms.firstOrNull { it.id == c.roomId } ?: return@mapNotNull null
            if (room.scenes.none { it.id == c.sceneId }) return@mapNotNull null
            RoomBefore(room.id, room.name, c.sceneId, room.on, room.brightness, room.activeSceneId)
        }
    }

    suspend fun on(rooms: List<RoomBefore>): List<String> {
        val s = system() ?: return rooms.map { it.name }
        return rooms.filterNot { before ->
            val room = s.rooms.value.firstOrNull { it.id == before.roomId } ?: return@filterNot false
            val scene = room.scenes.firstOrNull { it.id == before.sceneId } ?: return@filterNot false
            attempt({
                s.recallScene(room, scene)
                true
            }) { e ->
                logFailure(Step.RECALL, e)
                false
            }
        }.map { it.name }
    }

    /** The rooms whose active scene is still Party's but which didn't go back. */
    suspend fun back(rooms: List<RoomBefore>): List<String> {
        val s = system() ?: return rooms.map { it.name }
        readAll(s, Step.BACK)?.let { return rooms.map { it.name } }
        val now = s.rooms.value
        return rooms.filter { before ->
            val room = now.firstOrNull { it.id == before.roomId && it.activeSceneId == before.sceneId } ?: return@filter false
            !putBack(s, room, before)
        }.map { it.name }
    }

    /** Whether [room] went back as [before] had it. */
    private suspend fun putBack(s: LightsSystem, room: LightRoom, before: RoomBefore): Boolean = attempt({
        val remembered = room.scenes.firstOrNull { it.id == before.activeSceneId }
        when {
            remembered != null -> s.recallScene(room, remembered)
            !before.on -> s.setOn(room, false)
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
        Log.w(TAG, "Party lights: ${step.name} failed (${e::class.simpleName})")
    }
}
