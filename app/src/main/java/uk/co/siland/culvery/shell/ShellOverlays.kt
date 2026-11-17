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
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import uk.co.siland.culvery.core.plugin.AlertHandle
import uk.co.siland.culvery.core.plugin.AlertHost
import uk.co.siland.culvery.core.plugin.AlertQuieter
import uk.co.siland.culvery.core.plugin.ApplicationScope
import uk.co.siland.culvery.core.plugin.OverlayHost
import uk.co.siland.culvery.core.plugin.QUIET_CAP_MS
import uk.co.siland.culvery.core.plugin.ToastAction
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

    /** The content covers the whole window with no scrim; reset by each [show] and [dismiss]. */
    var fullScreen by mutableStateOf(false)
        private set

    private var keptOpen by mutableStateOf(false)

    /** The idle close applies: the overlay asked for it and isn't being kept open. */
    val closesNow: Boolean get() = closesWhenIdle && !keptOpen

    val isShowing: Boolean get() = content != null

    override var generation = 0
        private set

    override fun show(closesWhenIdle: Boolean, content: @Composable () -> Unit) = open(closesWhenIdle, fullScreen = false, content)

    override fun showFullScreen(content: @Composable () -> Unit) = open(closesWhenIdle = true, fullScreen = true, content)

    private fun open(closesWhenIdle: Boolean, fullScreen: Boolean, content: @Composable () -> Unit) {
        this.closesWhenIdle = closesWhenIdle
        keptOpen = false
        edited = false
        this.fullScreen = fullScreen
        this.content = content
        generation++
    }

    override fun markEdited(edited: Boolean) {
        this.edited = edited
    }

    override fun keepOpen(on: Boolean) {
        keptOpen = on
    }

    override fun dismiss() {
        content = null
        edited = false
        keptOpen = false
        fullScreen = false
    }
}

/**
 * The app's [AlertHost]: the content the alert layer draws, whether the screen it observes is resumed ([inFront], for
 * alerts) or started ([shown], for the display's states and brightness), and the quieters
 * a loud alert runs (6 §3.3). Each quiet or restore pass, a duck's included, starts after the one before, so a restore
 * never overtakes the quiet it undoes. The content, the current handle, [holds] and the passes change under one lock, so
 * a handle's dismiss never takes a newer alert.
 */
@Singleton
class ShellAlerts @Inject constructor(
    private val quieters: Set<@JvmSuppressWildcards AlertQuieter>,
    @ApplicationScope private val scope: CoroutineScope,
) : AlertHost, DefaultLifecycleObserver {
    private val _content = MutableStateFlow<(@Composable () -> Unit)?>(null)
    val content: StateFlow<(@Composable () -> Unit)?> = _content.asStateFlow()

    private val _showing = MutableStateFlow(false)
    override val showing: StateFlow<Boolean> = _showing.asStateFlow()

    private val _inFront = MutableStateFlow(false)
    override val inFront: StateFlow<Boolean> = _inFront.asStateFlow()

    private val _shown = MutableStateFlow(false)

    /** True from the screen's start to its stop: a pause with Culvery still on screen, as for a new intent, isn't leaving (8 §4.1). */
    val shown: StateFlow<Boolean> = _shown.asStateFlow()

    private val _lowered = MutableStateFlow(false)
    override val lowered: StateFlow<Boolean> = _lowered.asStateFlow()

    private val _holds = MutableStateFlow(false)

    /** True while the alert showing holds the wall awake: any alert but one shown with `holdsAwake = false`. */
    val holds: StateFlow<Boolean> = _holds.asStateFlow()

    private val _wakes = MutableSharedFlow<Unit>(extraBufferCapacity = 1, onBufferOverflow = BufferOverflow.DROP_OLDEST)

    /** One per [wake]; hot and never replayed. */
    val wakes: SharedFlow<Unit> = _wakes.asSharedFlow()

    private val lock = Any()
    private var current: Handle? = null
    private var loud = false
    private var duckDue = 0
    private var passNumber = 0
    private var lastPass: Job = Job().apply { complete() }
    private var quietened: Deferred<Unit> = CompletableDeferred(Unit)

    private inner class Handle : AlertHandle {
        private val _showing = MutableStateFlow(true)
        override val showing: StateFlow<Boolean> = _showing.asStateFlow()

        fun end() {
            _showing.value = false
        }

        override fun dismiss() {
            synchronized(lock) { if (current === this) clear() }
        }
    }

    override fun show(loud: Boolean, holdsAwake: Boolean, content: @Composable () -> Unit): AlertHandle =
        synchronized(lock) { install(loud, holdsAwake, content) }

    override fun showIfFree(holdsAwake: Boolean, content: @Composable () -> Unit): AlertHandle? =
        synchronized(lock) { if (current != null) null else install(loud = false, holdsAwake, content) }

    override suspend fun quieted() {
        synchronized(lock) { quietened }.await()
    }

    override fun wake() {
        _wakes.tryEmit(Unit)
    }

    override suspend fun duck(forMillis: Long) {
        val (quiet, due) = synchronized(lock) {
            if (loud) return
            val before = lastPass
            val pass = scope.launch {
                before.join()
                runAll { it.quiet() }
            }
            lastPass = pass
            pass to ++duckDue
        }
        withTimeoutOrNull(QUIET_CAP_MS) { quiet.join() }
        scope.launch {
            delay(forMillis)
            synchronized(lock) { if (due == duckDue) startRestorePass() }
        }
    }

    private fun install(loud: Boolean, holdsAwake: Boolean, content: @Composable () -> Unit): AlertHandle {
        current?.end()
        val handle = Handle()
        current = handle
        _content.value = content
        _showing.value = true
        _holds.value = holdsAwake
        if (loud && !this.loud) startQuiet() else if (!loud && this.loud) startRestore()
        this.loud = loud
        return handle
    }

    private fun clear() {
        current?.end()
        current = null
        _content.value = null
        _showing.value = false
        _holds.value = false
        if (loud) startRestore()
        loud = false
    }

    private fun startQuiet() {
        val before = lastPass
        val number = ++passNumber
        // The loud alert's own restore covers a duck still waiting to restore.
        duckDue++
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
        startRestorePass()
        quietened = CompletableDeferred(Unit)
    }

    private fun startRestorePass() {
        val before = lastPass
        lastPass = scope.launch {
            before.join()
            runAll { it.restore() }
        }
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

data class ToastMessage(val id: Long, val message: String, val icon: String, val urgent: Boolean = false, val action: ToastAction? = null)

@Singleton
class ShellToasts @Inject constructor() : Toaster {
    private val ids = AtomicLong()
    private val _current = MutableStateFlow<ToastMessage?>(null)
    val current: StateFlow<ToastMessage?> = _current.asStateFlow()

    override fun show(message: String, icon: String, urgent: Boolean, action: ToastAction?) {
        _current.value = ToastMessage(ids.incrementAndGet(), message, icon, urgent, action)
    }

    /** Hides [id] if it is still the one showing, so an older toast's timer can't hide a newer toast. */
    fun hide(id: Long) {
        _current.update { if (it?.id == id) null else it }
    }
}
