package uk.co.siland.culvery.capability.shopping

import android.util.Log
import java.util.concurrent.atomic.AtomicBoolean
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import uk.co.siland.culvery.core.plugin.AlertHost
import uk.co.siland.culvery.core.plugin.ApplicationScope
import uk.co.siland.culvery.core.plugin.Startable

/** How often the list is read while a screen that shows it is on (10 §4.4). */
const val SHOPPING_CHECK_MS = 60_000L

private const val TAG = "ShoppingChecks"

/**
 * Reads the list on a timer rather than subscribing (10 §4.4): only while a list is connected, Culvery is in front and a
 * screen that shows it (Home's card, the Shopping tab) is on, which [watch] counts. A read is also made at once when such
 * a screen opens or [askNow] is called.
 */
@Singleton
class ShoppingChecks internal constructor(
    private val connected: Flow<Boolean>,
    private val inFront: StateFlow<Boolean>,
    private val check: suspend () -> Unit,
    private val scope: CoroutineScope,
) : Startable {
    @Inject
    constructor(store: ShoppingStore, sync: ShoppingSync, host: AlertHost, @ApplicationScope scope: CoroutineScope) :
        this(store.connection.map { it != null }, host.inFront, sync::check, scope)

    private val watchers = MutableStateFlow(0)
    private val asked = Channel<Unit>(Channel.CONFLATED)

    /** How many shopping screens are on, for the screens' tests. */
    internal val watching: Int get() = watchers.value

    /** A screen showing the list is on; call the result when it goes. [checkNow] reads the list at once if it is already being read. */
    fun watch(checkNow: Boolean = true): () -> Unit {
        watchers.update { it + 1 }
        if (checkNow) asked.trySend(Unit)
        val stopped = AtomicBoolean(false)
        return { if (stopped.compareAndSet(false, true)) watchers.update { it - 1 } }
    }

    /** Reads the list at once, if a screen is showing it; otherwise at the next one that does. */
    fun askNow() {
        asked.trySend(Unit)
    }

    override fun start() {
        scope.launch {
            combine(connected, inFront, watchers) { c, front, n -> c && front && n > 0 }
                .distinctUntilChanged()
                .collectLatest { on -> if (on) checking() }
        }
    }

    private suspend fun checking() {
        // The read about to be made answers any request already waiting.
        asked.tryReceive()
        while (true) {
            safely()
            withTimeoutOrNull(SHOPPING_CHECK_MS) { asked.receive() }
        }
    }

    /** A failed check shows in the list's problem line; the next one tries again. */
    private suspend fun safely() {
        try {
            check()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.w(TAG, "A shopping check failed (${e::class.simpleName})")
        }
    }
}
