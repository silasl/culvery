package uk.co.siland.culvery.capability.plugs

import kotlinx.coroutines.flow.Flow

enum class PlugPower { ON, OFF, UNREACHABLE, GONE, UNKNOWN, NO_LINK }

/** [watts] is null with no power sensor, or while the plug isn't on. [toString] leaves out everything but [power]. */
data class PlugState(val entityId: String, val name: String, val power: PlugPower, val watts: Int?) {
    override fun toString() = "PlugState($power)"
}

/** True for On, false for Off, null for anything the plug can't be switched from. */
val PlugState.isOn: Boolean?
    get() = when (power) {
        PlugPower.ON -> true
        PlugPower.OFF -> false
        else -> null
    }

sealed interface PlugResult {
    data object Done : PlugResult
    data object Unreachable : PlugResult
    data object NotSwitched : PlugResult
    data object Busy : PlugResult
    data class Failed(val line: String) : PlugResult
}

/** [toString] leaves out the id and the name. */
data class PlugCandidate(val entityId: String, val name: String) {
    override fun toString() = "PlugCandidate"
}

sealed interface PlugCandidates {
    data class Found(val all: List<PlugCandidate>) : PlugCandidates
    data class Failed(val line: String) : PlugCandidates
}

interface PlugSystem {
    /** Null with no Home Assistant link: no tab (12 §4.3). The added plugs, in tab order. */
    val plugs: Flow<List<PlugState>?>

    /** The link's §6 line, null when nothing is wrong. */
    val problem: Flow<String?>

    /** The Plugs tab is showing; call the result when it goes. */
    fun watch(): () -> Unit

    /** The plugs Home Assistant has, by name; reads its registry afresh. */
    suspend fun available(): PlugCandidates

    /** One plug as it is now, read from Home Assistant when the socket isn't following it; never throws. */
    suspend fun read(entityId: String): PlugState

    /** No PIN here: the tab asks for it, and a mode's own PIN covers its plugs (12 §2.11). */
    suspend fun switch(entityId: String, on: Boolean): PlugResult
}
