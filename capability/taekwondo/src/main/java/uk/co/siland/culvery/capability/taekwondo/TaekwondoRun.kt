package uk.co.siland.culvery.capability.taekwondo

import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

private const val TAG = "TaekwondoRun"

/** The countdown before the first move, and again between a run's last move and its first. */
private const val COUNTDOWN_SECONDS = 10
private const val GAP_MS = 3_000L

sealed interface RunState {
    data object Idle : RunState

    data class Counting(val seconds: Int) : RunState

    /** [toString] leaves out the move. */
    data class Moving(val index: Int, val move: Move) : RunState {
        override fun toString() = "Moving($index)"
    }

    data class Again(val seconds: Int) : RunState
}

/**
 * Reads a pattern's moves aloud until [stop]: a 10 s countdown, each move, 3 s of quiet after all but the last, then 10 s
 * and the pattern again. Only that a run started or stopped is logged, never a move.
 */
class TaekwondoRun(private val speaker: Speaker, private val scope: CoroutineScope) {
    private val _state = MutableStateFlow<RunState>(RunState.Idle)
    val state: StateFlow<RunState> = _state.asStateFlow()

    private var job: Job? = null

    fun start(pattern: Pattern, detailed: Boolean) {
        if (job?.isActive == true) return
        Log.i(TAG, "A run started")
        _state.value = RunState.Counting(COUNTDOWN_SECONDS)
        job = scope.launch {
            for (second in COUNTDOWN_SECONDS downTo 1) {
                _state.value = RunState.Counting(second)
                delay(1_000)
            }
            while (true) {
                pattern.moves.forEachIndexed { index, move ->
                    _state.value = RunState.Moving(index, move)
                    speak(move, detailed)
                    if (index != pattern.moves.lastIndex) delay(GAP_MS)
                }
                for (second in COUNTDOWN_SECONDS downTo 1) {
                    _state.value = RunState.Again(second)
                    delay(1_000)
                }
            }
        }
    }

    fun stop() {
        val running = job?.isActive == true
        job?.cancel()
        job = null
        speaker.stop()
        _state.value = RunState.Idle
        if (running) Log.i(TAG, "A run stopped")
    }

    private suspend fun speak(move: Move, detailed: Boolean) {
        val hangul = move.spokenHangul?.takeIf { speaker.korean.value == true }
        val name = hangul ?: move.romanised ?: move.english
        speaker.say(name, korean = hangul != null)
        if (detailed) {
            if (name != move.english) speaker.say(move.english, korean = false)
            speaker.say(move.description, korean = false)
        }
    }
}
