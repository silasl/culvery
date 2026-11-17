package uk.co.siland.culvery.core.plugin.test

import androidx.compose.runtime.Composable
import java.util.concurrent.CopyOnWriteArrayList
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.flow.MutableStateFlow
import uk.co.siland.culvery.core.plugin.AlertHandle
import uk.co.siland.culvery.core.plugin.AlertHost

/**
 * An alert host a test drives: whether Culvery is in front, and what was shown, dismissed, woken and ducked. Handles behave as the
 * shell's do: a show replaces the one before, and a handle's dismiss takes the layer only while it is still that handle's.
 */
class FakeAlertHost(inFront: Boolean = true) : AlertHost {
    override val inFront = MutableStateFlow(inFront)
    override val lowered = MutableStateFlow(false)
    override val showing = MutableStateFlow(false)

    @Volatile var shown = 0

    /** Dismissals that took an alert off the layer. */
    @Volatile var dismissed = 0

    @Volatile var content: (@Composable () -> Unit)? = null

    /** Whether the last show was loud. */
    @Volatile var loud: Boolean? = null

    /** Whether the last show holds the wall awake. */
    @Volatile var holdsAwake: Boolean? = null

    @Volatile var wakes = 0
    val ducks: MutableList<Long> = CopyOnWriteArrayList()

    /** Holds quieted() until completed; null returns at once. */
    var quietedGate: CompletableDeferred<Unit>? = null
    var quietedCalls = 0

    private val lock = Any()
    private var current: FakeHandle? = null

    override fun show(loud: Boolean, holdsAwake: Boolean, content: @Composable () -> Unit): AlertHandle = synchronized(lock) {
        current?.showing?.value = false
        shown++
        this.loud = loud
        this.holdsAwake = holdsAwake
        this.content = content
        showing.value = true
        FakeHandle().also { current = it }
    }

    override fun showIfFree(holdsAwake: Boolean, content: @Composable () -> Unit): AlertHandle? = synchronized(lock) {
        if (current != null) null else show(loud = false, holdsAwake, content)
    }

    override suspend fun quieted() {
        quietedCalls++
        quietedGate?.await()
    }

    override fun wake() {
        wakes++
    }

    override suspend fun duck(forMillis: Long) {
        ducks += forMillis
    }

    private inner class FakeHandle : AlertHandle {
        override val showing = MutableStateFlow(true)

        override fun dismiss() {
            synchronized(lock) {
                if (current !== this) return
                current = null
                showing.value = false
                this@FakeAlertHost.showing.value = false
                dismissed++
                content = null
            }
        }
    }
}
