package uk.co.siland.culvery.shell.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import kotlinx.coroutines.delay
import uk.co.siland.culvery.core.ui.HhToast
import uk.co.siland.culvery.core.ui.ShellTokens
import uk.co.siland.culvery.shell.OverlayState
import uk.co.siland.culvery.shell.ToastMessage

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

/** Draws [toast] bottom-centre and reports it hidden after 3.5 s; a new toast restarts the timer. */
@Composable
fun ToastLayer(toast: ToastMessage?, onHidden: (Long) -> Unit) {
    val t = toast ?: return
    LaunchedEffect(t.id) {
        delay(ShellTokens.TOAST_MILLIS)
        onHidden(t.id)
    }
    Box(Modifier.fillMaxSize().padding(bottom = ShellTokens.toastBottom), contentAlignment = Alignment.BottomCenter) {
        HhToast(t.message, t.icon)
    }
}
