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
        return attempt({
            s.readAll()
            LightOptions(s.rooms.value.map { r -> LightRoomOption(r.id, r.name, r.scenes.map { HouseScene(it.id, it.name) }) }, null)
        }) { e ->
            logFailure(Step.OPTIONS, e)
            LightOptions(emptyList(), failureLine(e))
        }
    }

    /** Throws when the bridge can't be read. */
    suspend fun before(choices: List<SceneChoice>): List<RoomBefore> {
        val s = system() ?: throw LightsUnreachableException(LightsProblem.UNREACHABLE)
        attempt({ s.readAll() }) { e ->
            logFailure(Step.BEFORE, e)
            throw e
        }
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
            attempt({
                val room = s.rooms.value.firstOrNull { it.id == before.roomId }
                val scene = room?.scenes?.firstOrNull { it.id == before.sceneId }
                if (room == null || scene == null) {
                    false
                } else {
                    s.recallScene(room, scene)
                    true
                }
            }) { e ->
                logFailure(Step.RECALL, e)
                false
            }
        }.map { it.name }
    }

    suspend fun back(rooms: List<RoomBefore>): List<String> {
        val s = system() ?: return rooms.map { it.name }
        attempt({ s.readAll() }) { e ->
            logFailure(Step.BACK, e)
            return rooms.map { it.name }
        }
        val now = s.rooms.value
        return rooms.filter { before ->
            val room = now.firstOrNull { it.id == before.roomId }
            room != null && room.activeSceneId == before.sceneId &&
                attempt({
                    putBack(s, room, before)
                    false
                }) { e ->
                    logFailure(Step.BACK, e)
                    true
                }
        }.map { it.name }
    }

    private suspend fun putBack(s: LightsSystem, room: LightRoom, before: RoomBefore) {
        val remembered = room.scenes.firstOrNull { it.id == before.activeSceneId }
        when {
            remembered != null -> s.recallScene(room, remembered)
            !before.on -> s.setOn(room, false)
            else -> s.setBrightness(room, max(before.brightness, 1))
        }
    }

    private fun failureLine(e: Exception) = if (e is LightsUnreachableException) problemLine(e.problem) else HUE_REFUSED

    private fun logFailure(step: Step, e: Exception) {
        Log.w(TAG, "Party lights: ${step.name} failed (${e::class.simpleName})")
    }
}
