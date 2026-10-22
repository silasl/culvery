package uk.co.siland.culvery.capability.calendar.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import uk.co.siland.culvery.core.plugin.OverlayHost

class RecordingOverlay : OverlayHost {
    var content: (@Composable () -> Unit)? by mutableStateOf(null)
    var dismissed = 0
    var edited = false
        private set
    var closesWhenIdle = true
        private set
    var fullScreen = false
        private set

    override var generation = 0
        private set

    override fun show(closesWhenIdle: Boolean, content: @Composable () -> Unit) {
        this.content = content
        this.closesWhenIdle = closesWhenIdle
        edited = false
        fullScreen = false
        generation++
    }

    /** Called as [showFullScreen] is entered, to record what was true at that moment. */
    var onShowFullScreen: () -> Unit = {}

    override fun showFullScreen(content: @Composable () -> Unit) {
        onShowFullScreen()
        show(content = content)
        fullScreen = true
    }

    override fun markEdited(edited: Boolean) {
        this.edited = edited
    }

    override fun dismiss() {
        content = null
        edited = false
        fullScreen = false
        dismissed++
    }
}
