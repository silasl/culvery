package uk.co.siland.culvery.capability.door

import android.util.Log
import javax.inject.Inject
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.launch
import uk.co.siland.culvery.core.plugin.AlertHost
import uk.co.siland.culvery.core.plugin.ApplicationScope
import uk.co.siland.culvery.core.plugin.Startable
import uk.co.siland.culvery.core.plugin.WallClock

internal const val DAY_MS = 24 * 60 * 60 * 1_000L

/**
 * Prunes at start, daily, and each time Culvery comes back to the front (5 §4.5): a tablet asleep or behind another app
 * through its daily prune still loses old pictures within the 48 hours.
 */
class DoorPruneLoop internal constructor(
    private val prune: suspend (Long) -> Unit,
    private val clock: WallClock,
    private val inFront: StateFlow<Boolean>,
    private val scope: CoroutineScope,
) : Startable {
    @Inject
    constructor(store: DoorStore, clock: WallClock, host: AlertHost, @ApplicationScope scope: CoroutineScope) :
        this(store::prune, clock, host.inFront, scope)

    override fun start() {
        scope.launch {
            while (true) {
                pruneNow()
                delay(DAY_MS)
            }
        }
        scope.launch { inFront.drop(1).filter { it }.collect { pruneNow() } }
    }

    private suspend fun pruneNow() {
        try {
            prune(clock.nowMillis())
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.w(TAG, "Couldn't prune the door's history (${e::class.simpleName}); trying again later")
        }
    }

    private companion object {
        const val TAG = "DoorPruneLoop"
    }
}
