package uk.co.siland.culvery.shell

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import java.util.concurrent.atomic.AtomicLong
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import uk.co.siland.culvery.core.plugin.OverlayHost
import uk.co.siland.culvery.core.plugin.Toaster

/** The one sheet the shell shows above the rail and content; remembered by MainActivity. */
class OverlayState : OverlayHost {
    var content: (@Composable () -> Unit)? by mutableStateOf(null)
        private set

    val isShowing: Boolean get() = content != null

    override fun show(content: @Composable () -> Unit) {
        this.content = content
    }

    override fun dismiss() {
        content = null
    }
}

data class ToastMessage(val id: Long, val message: String, val icon: String)

@Singleton
class ShellToasts @Inject constructor() : Toaster {
    private val ids = AtomicLong()
    private val _current = MutableStateFlow<ToastMessage?>(null)
    val current: StateFlow<ToastMessage?> = _current.asStateFlow()

    override fun show(message: String, icon: String) {
        _current.value = ToastMessage(ids.incrementAndGet(), message, icon)
    }

    /** Hides [id] if it is still the one showing, so an older toast's timer can't hide a newer toast. */
    fun hide(id: Long) {
        _current.update { if (it?.id == id) null else it }
    }
}
