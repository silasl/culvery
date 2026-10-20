package uk.co.siland.culvery.shell

import android.util.Log
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import java.util.concurrent.atomic.AtomicLong
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import uk.co.siland.culvery.core.plugin.AlertHost
import uk.co.siland.culvery.core.plugin.AlertQuieter
import uk.co.siland.culvery.core.plugin.ApplicationScope
import uk.co.siland.culvery.core.plugin.OverlayHost
import uk.co.siland.culvery.core.plugin.QUIET_CAP_MS
import uk.co.siland.culvery.core.plugin.Toaster
import uk.co.siland.culvery.core.plugin.attempt

/** The one sheet the shell shows above the rail and content; remembered by MainActivity. */
class OverlayState : OverlayHost {
    var content: (@Composable () -> Unit)? by mutableStateOf(null)
        private set

    var closesWhenIdle by mutableStateOf(true)
        private set

    var edited by mutableStateOf(false)
        private set

    val isShowing: Boolean get() = content != null

    override fun show(closesWhenIdle: Boolean, content: @Composable () -> Unit) {
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
}

/**
 * The app's [AlertHost]: the content the alert layer draws, whether the screen it observes is resumed ([inFront], for
 * alerts) or started ([shown], for the display's states and brightness), and the quieters
 * a loud alert runs (6 §3.3). Each quiet or restore pass starts after the one before, so a restore never overtakes the
 * quiet it undoes.
 */
@Singleton
class ShellAlerts @Inject constructor(
    private val quieters: Set<@JvmSuppressWildcards AlertQuieter>,
    @ApplicationScope private val scope: CoroutineScope,
) : AlertHost, DefaultLifecycleObserver {
    private val _content = MutableStateFlow<(@Composable () -> Unit)?>(null)
    val content: StateFlow<(@Composable () -> Unit)?> = _content.asStateFlow()

    private val _inFront = MutableStateFlow(false)
    override val inFront: StateFlow<Boolean> = _inFront.asStateFlow()

    private val _shown = MutableStateFlow(false)

    /** True from the screen's start to its stop: a pause with Culvery still on screen, as for a new intent, isn't leaving (8 §4.1). */
    val shown: StateFlow<Boolean> = _shown.asStateFlow()

    private val _lowered = MutableStateFlow(false)
    override val lowered: StateFlow<Boolean> = _lowered.asStateFlow()

    private val lock = Any()
    private var loud = false
    private var passNumber = 0
    private var lastPass: Job = Job().apply { complete() }
    private var quietened: Deferred<Unit> = CompletableDeferred(Unit)

    override fun show(loud: Boolean, content: @Composable () -> Unit) {
        synchronized(lock) {
            _content.value = content
            if (loud && !this.loud) startQuiet() else if (!loud && this.loud) startRestore()
            this.loud = loud
        }
    }

    override suspend fun quieted() {
        synchronized(lock) { quietened }.await()
    }

    override fun dismiss() {
        synchronized(lock) {
            _content.value = null
            if (loud) startRestore()
            loud = false
        }
    }

    private fun startQuiet() {
        val before = lastPass
        val number = ++passNumber
        _lowered.value = false
        val pass = scope.launch {
            before.join()
            runAll { if (it.quiet()) synchronized(lock) { if (number == passNumber) _lowered.value = true } }
        }
        lastPass = pass
        quietened = scope.async {
            withTimeoutOrNull(QUIET_CAP_MS) { pass.join() }
            Unit
        }
    }

    private fun startRestore() {
        passNumber++
        _lowered.value = false
        val before = lastPass
        lastPass = scope.launch {
            before.join()
            runAll { it.restore() }
        }
        quietened = CompletableDeferred(Unit)
    }

    /** Every quieter at once; one that fails is logged and the others go on. */
    private suspend fun runAll(step: suspend (AlertQuieter) -> Unit) = coroutineScope {
        quieters.forEach { quieter ->
            launch { attempt({ step(quieter) }) { e -> Log.w(TAG, "A quieter failed (${e::class.simpleName})") } }
        }
    }

    /**
     * This host as an observer that writes nothing once [handedOver] is true: an instance giving way to the home task is not
     * in front. A stop while [changingConfigurations] is the same screen coming straight back, so it isn't leaving.
     */
    fun unlessHandedOver(handedOver: () -> Boolean, changingConfigurations: () -> Boolean): DefaultLifecycleObserver = object : DefaultLifecycleObserver {
        override fun onStart(owner: LifecycleOwner) {
            if (!handedOver()) this@ShellAlerts.onStart(owner)
        }

        override fun onStop(owner: LifecycleOwner) {
            if (!handedOver() && !changingConfigurations()) this@ShellAlerts.onStop(owner)
        }

        override fun onResume(owner: LifecycleOwner) {
            if (!handedOver()) this@ShellAlerts.onResume(owner)
        }

        override fun onPause(owner: LifecycleOwner) {
            if (!handedOver()) this@ShellAlerts.onPause(owner)
        }
    }

    override fun onStart(owner: LifecycleOwner) {
        _shown.value = true
    }

    override fun onStop(owner: LifecycleOwner) {
        _shown.value = false
    }

    override fun onResume(owner: LifecycleOwner) {
        _inFront.value = true
    }

    override fun onPause(owner: LifecycleOwner) {
        _inFront.value = false
    }

    private companion object {
        const val TAG = "ShellAlerts"
    }
}

data class ToastMessage(val id: Long, val message: String, val icon: String, val urgent: Boolean = false)

@Singleton
class ShellToasts @Inject constructor() : Toaster {
    private val ids = AtomicLong()
    private val _current = MutableStateFlow<ToastMessage?>(null)
    val current: StateFlow<ToastMessage?> = _current.asStateFlow()

    override fun show(message: String, icon: String, urgent: Boolean) {
        _current.value = ToastMessage(ids.incrementAndGet(), message, icon, urgent)
    }

    /** Hides [id] if it is still the one showing, so an older toast's timer can't hide a newer toast. */
    fun hide(id: Long) {
        _current.update { if (it?.id == id) null else it }
    }
}
