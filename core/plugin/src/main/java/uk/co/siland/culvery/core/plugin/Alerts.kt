package uk.co.siland.culvery.core.plugin

import androidx.compose.runtime.Composable
import androidx.compose.runtime.staticCompositionLocalOf
import kotlinx.coroutines.flow.StateFlow

/**
 * Something loud the shell turns down while a loud alert shows (6 §3.3), bound `@IntoSet` by a capability: Music lowers
 * the playing groups for the doorbell. Neither call may assume the other ran; each may fail, which the shell logs.
 */
interface AlertQuieter {
    suspend fun quiet()

    suspend fun restore()
}

/** A loud alert waits this long at most for the quieters before its own sound (6 §3.3). */
const val QUIET_CAP_MS = 500L

/**
 * The alert layer (5 §3.3, §5.1): above sheets, Settings and the wizard, below the PIN pad. Unlike [OverlayHost.show], an
 * alert never replaces or closes the open sheet, and touching it is not a touch for the screens beneath, whose own
 * timeouts run on. Safe from any thread.
 */
interface AlertHost {
    /** True while Culvery's screen is resumed in front: an alert raised otherwise would never be seen (5 §2.7). */
    val inFront: StateFlow<Boolean>

    /**
     * Shows [content] on the alert layer, replacing any alert already there. A [loud] alert runs every [AlertQuieter]'s
     * quiet() while it shows and their restore() when it goes (6 §3.3); one replacing a loud alert runs nothing again.
     */
    fun show(loud: Boolean = false, content: @Composable () -> Unit)

    /** Returns once the quieters have finished for the loud alert showing, or [QUIET_CAP_MS] after it showed; at once otherwise. */
    suspend fun quieted()

    fun dismiss()
}

val LocalAlertHost = staticCompositionLocalOf<AlertHost> {
    error("LocalAlertHost not provided: wrap the content in CompositionLocalProvider(LocalAlertHost provides …)")
}
