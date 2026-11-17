package uk.co.siland.culvery.core.plugin

import androidx.compose.runtime.Composable
import androidx.compose.runtime.staticCompositionLocalOf
import kotlinx.coroutines.flow.StateFlow

/**
 * Something loud the shell turns down while a loud alert shows (6 §3.3), bound `@IntoSet` by a capability: Music lowers
 * the playing groups for the doorbell. Neither call may assume the other ran; each may fail, which the shell logs.
 */
interface AlertQuieter {
    /** Returns true when it turned something down, false when there was nothing to turn down or it couldn't. */
    suspend fun quiet(): Boolean

    suspend fun restore()
}

/** A loud alert waits this long at most for the quieters before its own sound (6 §3.3). */
const val QUIET_CAP_MS = 500L

/** An owner's hold on the alert layer. */
interface AlertHandle {
    /** True while this alert is the one on the layer; false for good once it is replaced or dismissed. */
    val showing: StateFlow<Boolean>

    /** Takes this alert off the layer if it is still the one showing; nothing otherwise. */
    fun dismiss()
}

/**
 * The alert layer (5 §3.3, §5.1): above sheets, Settings and the wizard, below the PIN pad. Unlike [OverlayHost.show], an
 * alert never replaces or closes the open sheet, and touching it is not a touch for the screens beneath, whose own
 * timeouts run on. Safe from any thread.
 */
interface AlertHost {
    /** True while Culvery's screen is resumed in front: an alert raised otherwise would never be seen (5 §2.7). */
    val inFront: StateFlow<Boolean>

    /** True while any alert, quiet or loud, is on the alert layer. */
    val showing: StateFlow<Boolean>

    /**
     * True once a quieter of the loud alert showing has turned something down (9 §6.3): false as each quiet pass starts,
     * and when the loud alert goes.
     */
    val lowered: StateFlow<Boolean>

    /**
     * Shows [content] on the alert layer, replacing any alert already there. A [loud] alert runs every [AlertQuieter]'s
     * quiet() while it shows and their restore() when it goes (6 §3.3); one replacing a loud alert runs nothing again.
     * [holdsAwake] false: the wall's sleep timer runs as if the alert weren't there.
     */
    fun show(loud: Boolean = false, holdsAwake: Boolean = true, content: @Composable () -> Unit): AlertHandle

    /** A quiet alert onto an empty layer only: null, and nothing changed, while any alert shows. */
    fun showIfFree(holdsAwake: Boolean = true, content: @Composable () -> Unit): AlertHandle?

    /** Returns once the quieters have finished for the loud alert showing, or [QUIET_CAP_MS] after it showed; at once otherwise. */
    suspend fun quieted()

    /** Wakes a dark screen as someone moving in front of it would: by day and in front only, never the night clock or on Holiday mode. */
    fun wake()

    /**
     * Turns the quieters down, returns once they finish or after [QUIET_CAP_MS], and restores them [forMillis] later. Nothing
     * while a loud alert shows; a loud alert showing when the restore comes due keeps them down. Never sets [lowered].
     */
    suspend fun duck(forMillis: Long)
}

val LocalAlertHost = staticCompositionLocalOf<AlertHost> {
    error("LocalAlertHost not provided: wrap the content in CompositionLocalProvider(LocalAlertHost provides …)")
}
