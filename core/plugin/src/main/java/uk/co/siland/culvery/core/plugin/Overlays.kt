package uk.co.siland.culvery.core.plugin

import androidx.compose.runtime.Composable
import androidx.compose.runtime.staticCompositionLocalOf

/**
 * Lets capability UI show a sheet above the rail and content without depending on :app. The shell draws a scrim
 * that dismisses on tap, and places [show]'s content against the right edge, full height.
 */
interface OverlayHost {
    /** Replaces anything already shown. */
    fun show(content: @Composable () -> Unit)

    fun dismiss()
}

val LocalOverlayHost = staticCompositionLocalOf<OverlayHost> {
    error("LocalOverlayHost not provided: wrap the content in CompositionLocalProvider(LocalOverlayHost provides …)")
}

/** The hand-off's toast icon. */
const val TOAST_ICON_INFO = "info"

/**
 * A short bottom-centre message (hand-off §7). Injected wherever a toast is shown (access control, the calendar
 * editor, the outbox drain), so a toast never depends on a composable still being on screen. Safe from any
 * thread. A new toast replaces the current one.
 */
interface Toaster {
    fun show(message: String, icon: String = TOAST_ICON_INFO)
}
