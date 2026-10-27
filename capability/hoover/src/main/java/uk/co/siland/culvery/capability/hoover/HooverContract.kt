package uk.co.siland.culvery.capability.hoover

import kotlinx.coroutines.flow.Flow

enum class HooverActivity { DOCKED, CLEANING, RETURNING, PAUSED, IDLE, ERROR }

/** What Start is offered for (11 §4.4). */
val STARTABLE = setOf(HooverActivity.DOCKED, HooverActivity.IDLE, HooverActivity.PAUSED)

/** [activity] is null while unknown; [problem] is the line to show in place of the status. */
data class HooverState(val activity: HooverActivity?, val batteryPercent: Int?, val problem: String?)

sealed interface HooverResult {
    data object Started : HooverResult
    data object NotStarted : HooverResult
    data object Busy : HooverResult

    /** Already cleaning or going home: nothing to start. */
    data object AlreadyRunning : HooverResult
    data class Failed(val line: String) : HooverResult
}

interface HooverSystem {
    /** Null while no hoover is chosen: no tray row (11 §4.4). */
    val state: Flow<HooverState?>

    /** No PIN here: the row asks for it (11 §4.4), and 11b's scheduled clean calls this directly. */
    suspend fun start(): HooverResult

    /** A screen showing the hoover is on; call the result when it goes. */
    fun watch(): () -> Unit
}
