package uk.co.siland.culvery.core.setup

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import uk.co.siland.culvery.core.plugin.OverlayHost

class RecordingOverlay : OverlayHost {
    var content: (@Composable () -> Unit)? by mutableStateOf(null)

    override fun show(content: @Composable () -> Unit) {
        this.content = content
    }

    override fun dismiss() {
        content = null
    }
}
