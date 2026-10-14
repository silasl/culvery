package uk.co.siland.culvery.core.plugin

import androidx.compose.runtime.Composable
import androidx.compose.runtime.staticCompositionLocalOf
import kotlinx.coroutines.flow.StateFlow

/**
 * The alert layer (5 §3.3, §5.1): above sheets, Settings and the wizard, below the PIN pad. Unlike [OverlayHost.show], an
 * alert never replaces or closes the open sheet, and touching it is not a touch for the screens beneath, whose own
 * timeouts run on. Safe from any thread.
 */
interface AlertHost {
    /** True while Culvery's screen is resumed in front: an alert raised otherwise would never be seen (5 §2.7). */
    val inFront: StateFlow<Boolean>

    /** Shows [content] on the alert layer, replacing any alert already there. */
    fun show(content: @Composable () -> Unit)

    fun dismiss()
}

val LocalAlertHost = staticCompositionLocalOf<AlertHost> {
    error("LocalAlertHost not provided: wrap the content in CompositionLocalProvider(LocalAlertHost provides …)")
}
