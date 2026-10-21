package uk.co.siland.culvery.core.plugin

import androidx.compose.runtime.Composable
import androidx.compose.runtime.staticCompositionLocalOf

/**
 * Lets capability UI show a sheet above the rail and content without depending on :app. The shell draws a scrim
 * that dismisses on tap, and places [show]'s content against the right edge, full height. An overlay left alone closes
 * after 2 minutes, or 10 while it has unsaved changes (4d-2 §6.3); never while Settings or the wizard shows, nor one
 * shown with [show]'s `closesWhenIdle` false.
 */
interface OverlayHost {
    /**
     * Replaces anything already shown. [closesWhenIdle] false for a card waiting on a screen outside the app, where no
     * touch can reach Culvery.
     */
    fun show(closesWhenIdle: Boolean = true, content: @Composable () -> Unit)

    /**
     * Like [show], but the content fills the whole window, rail included, with no scrim, so nothing closes it by a tap
     * outside. The idle close is the same (3.1). A host that can't do this shows it as a sheet.
     */
    fun showFullScreen(content: @Composable () -> Unit) = show(content = content)

    /** The open sheet has changes that closing it would lose, or is saving them; reset by each show and dismiss. */
    fun markEdited(edited: Boolean)

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
 * thread. A new toast replaces the current one. [urgent] for errors and refusals: TalkBack interrupts to say it, and it
 * stays longer.
 */
interface Toaster {
    fun show(message: String, icon: String = TOAST_ICON_INFO, urgent: Boolean = false)

    /** Like [show] with one button after the message; a host that can't draw it shows the message alone. */
    fun show(message: String, icon: String = TOAST_ICON_INFO, urgent: Boolean = false, action: ToastAction) =
        show(message, icon, urgent)
}

/** The button a toast carries; a tap runs [onClick] and hides the toast. */
data class ToastAction(val label: String, val onClick: () -> Unit)

/** How long a toast with an action shows, whatever its length (3.1). */
const val TOAST_WITH_ACTION_MS = 8_000L
