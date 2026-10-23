package uk.co.siland.culvery.capability.shopping

import android.util.Log
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import uk.co.siland.culvery.core.plugin.ApplicationScope
import uk.co.siland.culvery.core.plugin.Startable
import uk.co.siland.culvery.core.plugin.WallClock

private const val TAG = "ShoppingDrainLoop"

/** The shortest wait between two drains, so a head due "now" can't spin the loop. */
private const val MIN_WAIT_MS = 1_000L

/**
 * Sends the queue when Culvery starts, whenever something is queued or connected ([wake]), and when the head of the
 * queue comes due, so an offline add goes out as soon as Google answers (10 §4.5).
 */
@Singleton
class ShoppingDrainLoop internal constructor(
    private val drain: suspend () -> Long?,
    private val nowMillis: () -> Long,
    private val scope: CoroutineScope,
) : Startable {
    @Inject
    constructor(sync: ShoppingSync, clock: WallClock, @ApplicationScope scope: CoroutineScope) :
        this(sync::drain, clock::nowMillis, scope)

    private val wake = Channel<Unit>(Channel.CONFLATED)

    /** Drains as soon as possible; requests made during a drain add up to one more. */
    fun wake() {
        wake.trySend(Unit)
    }

    override fun start() {
        scope.launch {
            var wait: Long? = 0
            while (true) {
                if (wait == null) wake.receive() else withTimeoutOrNull(wait) { wake.receive() }
                wait = pass()
            }
        }
    }

    /** How long until the next drain: null until woken when the queue is empty. */
    private suspend fun pass(): Long? = try {
        drain()?.let { (it - nowMillis()).coerceAtLeast(MIN_WAIT_MS) }
    } catch (e: CancellationException) {
        throw e
    } catch (e: Exception) {
        Log.w(TAG, "Shopping drain failed (${e::class.simpleName})")
        SHOPPING_BACKOFF_MS.first()
    }
}
