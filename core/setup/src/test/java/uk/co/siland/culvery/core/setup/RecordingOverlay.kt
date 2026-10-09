package uk.co.siland.culvery.core.setup

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import uk.co.siland.culvery.core.plugin.OverlayHost

class RecordingOverlay : OverlayHost {
    var content: (@Composable () -> Unit)? by mutableStateOf(null)
    var edited = false
        private set
    var closesWhenIdle = true
        private set

    override fun show(closesWhenIdle: Boolean, content: @Composable () -> Unit) {
        this.content = content
        this.closesWhenIdle = closesWhenIdle
        edited = false
    }

    override fun markEdited(edited: Boolean) {
        this.edited = edited
    }

    override fun dismiss() {
        content = null
        edited = false
    }
}
