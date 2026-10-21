package uk.co.siland.culvery.core.plugin.test

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import java.util.concurrent.CopyOnWriteArrayList
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import uk.co.siland.culvery.core.plugin.OverlayHost
import uk.co.siland.culvery.core.plugin.ToastAction
import uk.co.siland.culvery.core.plugin.Toaster

/** [block] on a real dispatcher, as DataStore's writes need, failing after 5 s. */
suspend fun <T> soon(block: suspend CoroutineScope.() -> T): T = withContext(Dispatchers.Default) { withTimeout(5_000, block) }

/** A [Toaster] that keeps each message [shown], from whatever thread showed it. */
class RecordingToaster : Toaster {
    val shown = CopyOnWriteArrayList<String>()

    override fun show(message: String, icon: String, urgent: Boolean, action: ToastAction?) {
        shown += message
    }
}

/**
 * An overlay host that keeps the sheet it was asked to show, for the test to draw, and counts each show;
 * [closesWhenIdle] is that of the last show.
 */
class TestOverlay : OverlayHost {
    var content by mutableStateOf<(@Composable () -> Unit)?>(null)
    var shown = 0
    var closesWhenIdle = true

    /** As the shell's: reset by each show and dismiss. */
    var edited = false

    override fun show(closesWhenIdle: Boolean, content: @Composable () -> Unit) {
        shown++
        this.closesWhenIdle = closesWhenIdle
        edited = false
        this.content = content
    }

    override fun markEdited(edited: Boolean) {
        this.edited = edited
    }

    override fun dismiss() {
        content = null
        edited = false
    }

    /** The shell closing the sheet by itself (a scrim tap, the idle timer), without a dismiss from the sheet's owner. */
    fun closedByHost() {
        content = null
    }
}
