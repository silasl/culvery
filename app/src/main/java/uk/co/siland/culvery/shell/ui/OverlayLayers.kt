package uk.co.siland.culvery.shell.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.clearAndSetSemantics
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collectLatest
import uk.co.siland.culvery.core.plugin.CLOSING_WARNING_MS
import uk.co.siland.culvery.core.plugin.EDIT_HOLD_MS
import uk.co.siland.culvery.core.plugin.OVERLAY_IDLE_MS
import uk.co.siland.culvery.core.plugin.TOAST_WITH_ACTION_MS
import uk.co.siland.culvery.core.plugin.UserActivity
import uk.co.siland.culvery.core.ui.Countdown
import uk.co.siland.culvery.core.ui.Culvery
import uk.co.siland.culvery.core.ui.HhToast
import uk.co.siland.culvery.core.ui.Icons
import uk.co.siland.culvery.core.ui.ShellTokens
import uk.co.siland.culvery.core.ui.toastMillis
import uk.co.siland.culvery.display.ui.CameraMark
import uk.co.siland.culvery.shell.OverlayState
import uk.co.siland.culvery.shell.ToastMessage

internal const val SETTINGS_CLOSING = "Settings closes in 20 seconds. Touch the screen to stay."
internal const val SHEET_CLOSING = "This closes in 20 seconds. Touch the screen to keep your changes."

/**
 * The shell with its overlay layers stacked over it: sheet, then [alert], then [sleep], then [pinPad], then toasts, then
 * the camera mark over everything when [cameraMark].
 * The layers need a parent with its own graphics layer: removing a node redraws only its nearest layered ancestor,
 * and the composition root has none, so a layer closed with no other animation running (a scrim tap, a toast timing
 * out) would get a layout pass but no new frame and stay on screen. Every finger down on the shell, a sheet or the PIN
 * pad goes to [onTouch] before anything handles it; one on the alert doesn't (5 §5.1). The session hears of it from
 * [UserActivity] a moment later.
 * While a sheet, the PIN pad or [alert] shows, what it covers is hidden from TalkBack; the toasts never are. While [sleeping], the
 * shell, the sheet and the alert are hidden too, and no toast is drawn: it waits for the screen to wake.
 * [settingsExpiresAt] is the session's end while Settings is open; 20 seconds before it, or before an edited sheet outside
 * Settings closes, a spoken warning shows over everything. [overlaysCloseWhenIdle] is false while Settings or the wizard
 * shows: a sheet closing by itself would move TalkBack's focus, which counts as a touch and would stretch the session.
 */
@Composable
fun ShellLayers(
    overlay: OverlayState,
    toast: ToastMessage?,
    onToastHidden: (Long) -> Unit,
    pinShowing: Boolean,
    pinPad: @Composable () -> Unit,
    activity: UserActivity,
    onTouch: () -> Unit,
    settingsExpiresAt: Long? = null,
    nowMillis: () -> Long = System::currentTimeMillis,
    overlaysCloseWhenIdle: Boolean = true,
    alert: (@Composable () -> Unit)? = null,
    sleeping: Boolean = false,
    sleep: @Composable () -> Unit = {},
    cameraMark: Boolean = false,
    shell: @Composable () -> Unit,
) {
    val touched by rememberUpdatedState(onTouch)
    val settingsClosingSoon = rememberClosingSoon(settingsExpiresAt, nowMillis)
    val sheetWarningEndsAt = rememberSheetClosingSoon(overlay, activity, closes = overlaysCloseWhenIdle, nowMillis)
    Box(Modifier.fillMaxSize().graphicsLayer {}) {
        Box(Modifier.fillMaxSize().countsTouches { touched() }) {
            // A TalkBack double-tap is an action, not a touch, so a scrim can't stop it: what is covered leaves the tree.
            Box(Modifier.fillMaxSize().then(if (overlay.isShowing || pinShowing || sleeping || alert != null) Modifier.clearAndSetSemantics {} else Modifier)) { shell() }
            OverlayLayer(overlay, hidden = pinShowing || sleeping || alert != null)
        }
        // Outside the counting box: a ring's card is not someone using the sheet or Settings beneath.
        if (alert != null) AlertLayer(alert, hidden = pinShowing || sleeping)
        // Outside the counting box too: the waking tap only wakes the screen (8 §4.1).
        sleep()
        Box(Modifier.fillMaxSize().then(if (pinShowing) Modifier.countsTouches { touched() } else Modifier)) { pinPad() }
        // Held while asleep, not dropped: a toast's timer starts only once it is drawn.
        if (!sleeping) ToastLayer(toast, activity, onHidden = onToastHidden)
        // One warning at a time, and Settings' first: Settings closing takes any open sheet with it. Both keep the wall awake.
        when {
            settingsClosingSoon && settingsExpiresAt != null ->
                ClosingWarningLayer(SETTINGS_CLOSING, "settings_warning", settingsExpiresAt, nowMillis)
            sheetWarningEndsAt != null -> ClosingWarningLayer(SHEET_CLOSING, "sheet_warning", sheetWarningEndsAt, nowMillis)
        }
        if (cameraMark) CameraMark()
    }
}

/** Every finger down here goes to [touched] before anything handles it. */
private fun Modifier.countsTouches(touched: () -> Unit): Modifier = pointerInput(Unit) {
    awaitEachGesture {
        awaitFirstDown(requireUnconsumed = false, pass = PointerEventPass.Initial)
        touched()
    }
}

/**
 * The alert layer (9 §4.3): a scrim over the whole screen that takes every tap and does nothing with it, so the alert
 * goes only by its own buttons or timer; the alert at its own width, centred above the keyboard.
 */
@Composable
internal fun AlertLayer(content: @Composable () -> Unit, hidden: Boolean, keyboard: WindowInsets = WindowInsets.ime) {
    Box(Modifier.fillMaxSize().then(if (hidden) Modifier.clearAndSetSemantics {} else Modifier)) {
        // A gesture, not a clickable: TalkBack never lands on the scrim.
        Box(Modifier.fillMaxSize().testTag("alert_scrim").background(ShellTokens.cardScrim).pointerInput(Unit) { detectTapGestures {} })
        Box(Modifier.fillMaxSize().windowInsetsPadding(keyboard.only(WindowInsetsSides.Bottom)), contentAlignment = Alignment.Center) { content() }
    }
}

/** The sheet layer: a scrim over the whole shell that dismisses on a tap, and the content against the right edge; a full-screen overlay has neither. */
@Composable
fun OverlayLayer(state: OverlayState, hidden: Boolean = false) {
    val content = state.content ?: return
    if (state.fullScreen) {
        Box(Modifier.fillMaxSize().then(if (hidden) Modifier.clearAndSetSemantics {} else Modifier).background(Culvery.colors.bg)) { content() }
        return
    }
    Box(
        Modifier.fillMaxSize().then(if (hidden) Modifier.clearAndSetSemantics {} else Modifier),
        contentAlignment = Alignment.CenterEnd,
    ) {
        Box(
            Modifier
                .fillMaxSize()
                .testTag("overlay_scrim")
                .background(ShellTokens.sheetScrim)
                // A gesture, not a clickable: TalkBack never lands on the scrim; every sheet has its own Close.
                .pointerInput(state) { detectTapGestures { state.dismiss() } },
        )
        content()
    }
}

/** The PIN pad covers the whole screen over a full-screen overlay, and only the sheet's width over a sheet. */
internal fun pinOverSheet(overlay: OverlayState): Boolean = overlay.isShowing && !overlay.fullScreen

/** True from [CLOSING_WARNING_MS] before [expiresAt] until it moves; false without one. Once per countdown: keyed on it. */
@Composable
private fun rememberClosingSoon(expiresAt: Long?, nowMillis: () -> Long): Boolean {
    // Keyed, so a new end resets it in the same frame the touch lands in, not a frame later.
    var soon by remember(expiresAt) { mutableStateOf(false) }
    LaunchedEffect(expiresAt) {
        val at = expiresAt ?: return@LaunchedEffect
        // Measured once from the wall clock, then waited monotonically: a clock change afterwards can't move it.
        delay(at - CLOSING_WARNING_MS - nowMillis())
        soon = true
    }
    return soon
}

/**
 * Closes the overlay after [OVERLAY_IDLE_MS] without a touch, or [EDIT_HOLD_MS] while it has unsaved changes, and is true
 * from [CLOSING_WARNING_MS] before an edited one goes (4d-2 §6.3); then it returns the moment the sheet goes, else null. Any touch, a TalkBack gesture's included, starts it
 * again; a card that waits on a screen outside the app, or any overlay while not [closes], stays.
 */
@Composable
private fun rememberSheetClosingSoon(overlay: OverlayState, activity: UserActivity, closes: Boolean, nowMillis: () -> Long): Long? {
    val content = overlay.content
    val edited = overlay.edited
    var endsAt by remember(content, edited, closes) { mutableStateOf<Long?>(null) }
    LaunchedEffect(content, edited, overlay.closesWhenIdle, closes) {
        if (content == null || !overlay.closesWhenIdle || !closes) return@LaunchedEffect
        // Collected here rather than as state, so a touch restarts the wait without recomposing the shell.
        activity.touches.collectLatest {
            endsAt = null
            if (edited) {
                delay(activity.timeout(EDIT_HOLD_MS) - CLOSING_WARNING_MS)
                endsAt = nowMillis() + CLOSING_WARNING_MS
                delay(CLOSING_WARNING_MS)
            } else {
                delay(activity.timeout(OVERLAY_IDLE_MS))
            }
            overlay.dismiss()
        }
    }
    return endsAt
}

/** Where toasts and warnings sit: bottom-centre, 28 dp above the bottom or above the on-screen keyboard (hand-off §7). */
@Composable
private fun ToastSlot(keyboard: WindowInsets, content: @Composable () -> Unit) {
    Box(
        Modifier
            .fillMaxSize()
            .windowInsetsPadding(keyboard.only(WindowInsetsSides.Bottom))
            .padding(bottom = ShellTokens.toastBottom),
        contentAlignment = Alignment.BottomCenter,
    ) {
        content()
    }
}

/** A warning pill where toasts sit (hand-off §7's toast), spoken at once; its words are fixed and a ring shows the time left until [endsAt]. */
@Composable
internal fun ClosingWarningLayer(text: String, tag: String, endsAt: Long, nowMillis: () -> Long, keyboard: WindowInsets = WindowInsets.ime) {
    ToastSlot(keyboard) {
        HhToast(text, Icons.SCHEDULE, Modifier.testTag(tag), urgent = true, countdown = Countdown(endsAt, CLOSING_WARNING_MS, nowMillis))
    }
}

/**
 * Draws [toast] bottom-centre, 28 dp above the bottom or above the on-screen keyboard while it shows (hand-off §7),
 * and reports it hidden after its reading time ([toastMillis]), or TalkBack's longer one; a toast with an action stays [TOAST_WITH_ACTION_MS], and its tap runs the action and hides it; a new toast restarts the timer.
 */
@Composable
fun ToastLayer(toast: ToastMessage?, activity: UserActivity, keyboard: WindowInsets = WindowInsets.ime, onHidden: (Long) -> Unit) {
    val t = toast ?: return
    LaunchedEffect(t.id) {
        delay(if (t.action == null) activity.timeout(toastMillis(t.message), hasControls = false) else activity.timeout(TOAST_WITH_ACTION_MS, hasControls = true))
        onHidden(t.id)
    }
    ToastSlot(keyboard) {
        HhToast(
            t.message, t.icon, urgent = t.urgent, actionLabel = t.action?.label,
            onAction = { t.action?.onClick?.invoke(); onHidden(t.id) },
        )
    }
}
