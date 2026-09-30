package uk.co.siland.culvery.core.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

/**
 * One action at a time for a sheet or page: while one runs, [busy] is true and further taps are ignored, so a double tap
 * saves once. An exception the action lets through goes to [onError] instead of crashing the app; the sheet closing
 * mid-action cancels it, which is not an error.
 */
class SingleAction(private val scope: CoroutineScope, private val onError: (Exception) -> Unit) {
    var busy by mutableStateOf(false)
        private set

    fun run(action: suspend () -> Unit) {
        if (busy) return
        busy = true
        scope.launch {
            try {
                action()
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                onError(e)
            } finally {
                busy = false
            }
        }
    }
}

/** A [SingleAction] for one opening of a sheet or page: a new [key] starts a fresh one. */
@Composable
fun rememberSingleAction(key: Any?, onError: (Exception) -> Unit): SingleAction {
    val scope = rememberCoroutineScope()
    val currentOnError by rememberUpdatedState(onError)
    return remember(key) { SingleAction(scope) { currentOnError(it) } }
}
