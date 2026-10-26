package uk.co.siland.culvery.capability.heating

import kotlinx.coroutines.flow.Flow

/** [toString] leaves out [name]. */
data class HeatingState(
    val name: String,
    val eco: Boolean,
    val currentC: Double?,
    val targetC: Double?,
    val heating: Boolean,
    val problem: String?,
) {
    override fun toString() = "HeatingState(eco=$eco, currentC=$currentC, targetC=$targetC, heating=$heating, problem=${problem != null})"
}

sealed interface HeatingResult {
    data object Done : HeatingResult
    data object NotConfirmed : HeatingResult
    data class Failed(val line: String) : HeatingResult
}

interface HeatingSystem {
    /** Null while no thermostat is chosen. */
    val state: Flow<HeatingState?>

    suspend fun setEco(on: Boolean): HeatingResult
}
