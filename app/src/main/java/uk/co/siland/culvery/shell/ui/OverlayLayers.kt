package uk.co.siland.culvery.shell.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
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
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.testTag
import kotlinx.coroutines.delay
import uk.co.siland.culvery.core.ui.HhToast
import uk.co.siland.culvery.core.ui.ShellTokens
import uk.co.siland.culvery.shell.OverlayState
import uk.co.siland.culvery.shell.ToastMessage

/**
 * The shell with its overlay layers stacked over it: sheet, then [pinPad] over the sheet, then toasts over everything.
 * The layers need a parent with its own graphics layer: removing a node redraws only its nearest layered ancestor,
 * and the composition root has none, so a layer closed with no other animation running (a scrim tap, a toast timing
 * out) would get a layout pass but no new frame and stay on screen.
 */
@Composable
fun ShellLayers(
    overlay: OverlayState,
    toast: ToastMessage?,
    onToastHidden: (Long) -> Unit,
    pinPad: @Composable () -> Unit,
    shell: @Composable () -> Unit,
) {
    Box(Modifier.fillMaxSize().graphicsLayer {}) {
        shell()
        OverlayLayer(overlay)
        pinPad()
        ToastLayer(toast, onHidden = onToastHidden)
    }
}

/** The sheet layer: a scrim over the whole shell that dismisses on tap, and the content against the right edge. */
@Composable
fun OverlayLayer(state: OverlayState) {
    val content = state.content ?: return
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.CenterEnd) {
        Box(
            Modifier
                .fillMaxSize()
                .testTag("overlay_scrim")
                .background(ShellTokens.sheetScrim)
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                    onClickLabel = "Close",
                    onClick = state::dismiss,
                ),
        )
        content()
    }
}

/**
 * Draws [toast] bottom-centre, 28 dp above the bottom or above the on-screen keyboard while it shows (hand-off §7),
 * and reports it hidden after 3.5 s; a new toast restarts the timer.
 */
@Composable
fun ToastLayer(toast: ToastMessage?, keyboard: WindowInsets = WindowInsets.ime, onHidden: (Long) -> Unit) {
    val t = toast ?: return
    LaunchedEffect(t.id) {
        delay(ShellTokens.TOAST_MILLIS)
        onHidden(t.id)
    }
    Box(
        Modifier
            .fillMaxSize()
            .windowInsetsPadding(keyboard.only(WindowInsetsSides.Bottom))
            .padding(bottom = ShellTokens.toastBottom),
        contentAlignment = Alignment.BottomCenter,
    ) {
        HhToast(t.message, t.icon)
    }
}
