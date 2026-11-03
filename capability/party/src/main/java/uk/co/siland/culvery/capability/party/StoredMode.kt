package uk.co.siland.culvery.capability.party

import uk.co.siland.culvery.core.house.HouseRoom
import uk.co.siland.culvery.core.house.RoomChoice

/** The playlist or favourite a mode plays. [toString] leaves out the id and name. */
data class MusicChoice(val pickId: String, val pickName: String) {
    override fun toString() = "MusicChoice"
}

/** A mode the family set up (15b §4.1). [speakers] null is every speaker. [toString] leaves out every field. */
data class StoredMode(
    val id: String,
    val name: String,
    val icon: String,
    val showOnHome: Boolean,
    val needsPin: Boolean,
    val music: MusicChoice? = null,
    val speakers: List<HouseRoom>? = null,
    val lights: List<RoomChoice> = emptyList(),
) {
    /** A room already set keeps its place in the list; a new one goes last. */
    fun withLight(choice: RoomChoice): StoredMode {
        val at = lights.indexOfFirst { it.roomId == choice.roomId }
        return copy(lights = if (at < 0) lights + choice else lights.toMutableList().also { it[at] = choice })
    }

    fun withoutLight(roomId: String) = copy(lights = lights.filterNot { it.roomId == roomId })

    override fun toString() = "StoredMode"
}

/** The stored modes in the order they were added, and whether Holiday mode sits on Home. [toString] leaves out the modes. */
data class ModesPrefs(val holidayOnHome: Boolean = true, val modes: List<StoredMode> = emptyList()) {
    fun mode(id: String): StoredMode? = modes.firstOrNull { it.id == id }

    /** [id]'s mode as [change] makes it, the others as they are. */
    fun withMode(id: String, change: (StoredMode) -> StoredMode) = copy(modes = modes.map { if (it.id == id) change(it) else it })

    override fun toString() = "ModesPrefs(holidayOnHome=$holidayOnHome, modes=${modes.size})"
}
