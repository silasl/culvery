package uk.co.siland.culvery.core.plugin

import androidx.compose.runtime.staticCompositionLocalOf
import kotlinx.coroutines.flow.StateFlow

/**
 * The one activity signal every timer reads (4d-2 §6.1, Decision 9): the Settings session, an overlay left open, the
 * PIN pad, the week view's return to this week and the toasts. Fed by the shell's pointer input and by TalkBack.
 */
interface UserActivity {
    /** Grows by one on each finger down anywhere, and on each TalkBack focus move, double-tap or scroll. */
    val touches: StateFlow<Long>

    /** TalkBack (or another touch-exploration service) is on. */
    val exploring: StateFlow<Boolean>

    /** [baseMillis] for most people; with touch exploration on, what Android's "Time to take action" asks for, never less. */
    fun timeout(baseMillis: Long, hasControls: Boolean = true): Long
}

val LocalUserActivity = staticCompositionLocalOf<UserActivity> {
    error("LocalUserActivity not provided: wrap the content in CompositionLocalProvider(LocalUserActivity provides …)")
}

/** An overlay with nothing to lose closes after this long without a touch (4d-2 Decision 4). */
const val OVERLAY_IDLE_MS = 120_000L

/** A sheet with unsaved changes, and the Settings session over it, last this long after the last touch (4d-2 Decision 3). */
const val EDIT_HOLD_MS = 600_000L

/** How long before a close the spoken warning shows. */
const val CLOSING_WARNING_MS = 20_000L
