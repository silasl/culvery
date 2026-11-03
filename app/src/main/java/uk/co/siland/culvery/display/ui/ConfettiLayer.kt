package uk.co.siland.culvery.display.ui

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import kotlinx.coroutines.flow.Flow
import uk.co.siland.culvery.core.ui.ConfettiBurst

/**
 * Drops a burst on each of [wakes] while [falls], and ends it for good when the screen stops being [awake] or [falls] goes false.
 * A wake doesn't check [awake]: the screen's own state reaches here after the wake does, and a wake is awake by definition (16 §3.3).
 */
@Composable
internal fun ConfettiLayer(wakes: Flow<Unit>, awake: Boolean, falls: Boolean, seed: () -> Long) {
    var burst by remember { mutableStateOf<Long?>(null) }
    val fallsNow by rememberUpdatedState(falls)
    val seedNow by rememberUpdatedState(seed)
    LaunchedEffect(wakes) { wakes.collect { if (fallsNow) burst = seedNow() } }
    LaunchedEffect(awake, falls) { if (!awake || !falls) burst = null }
    val current = burst
    if (current != null && awake && falls) ConfettiBurst(current, onDone = { burst = null }, Modifier.fillMaxSize())
}
