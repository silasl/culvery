package uk.co.siland.culvery.capability.door

import android.util.Log
import javax.inject.Inject
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.launch
import uk.co.siland.culvery.core.plugin.AlertHost
import uk.co.siland.culvery.core.plugin.ApplicationScope
import uk.co.siland.culvery.core.plugin.HouseholdClock
import uk.co.siland.culvery.core.plugin.Startable

/**
 * Prunes at start, at each household midnight, when the keep changes, and each time Culvery comes back to the front
 * (5 §4.5, 15a §3.1): a tablet asleep or behind another app through its midnight prune still loses old pictures within
 * the days it keeps. The start and the midnights are [HouseholdClock.today] changing.
 */
class DoorPruneLoop internal constructor(
    private val prune: suspend (Long) -> Unit,
    private val clock: HouseholdClock,
    private val inFront: StateFlow<Boolean>,
    private val keep: Flow<KeepDays>,
    private val scope: CoroutineScope,
) : Startable {
    @Inject
    constructor(
        store: DoorStore,
        clock: HouseholdClock,
        host: AlertHost,
        settings: DoorSettings,
        @ApplicationScope scope: CoroutineScope,
    ) : this(store::prune, clock, host.inFront, settings.keep, scope)

    override fun start() {
        scope.launch { clock.today.collect { pruneNow() } }
        scope.launch { inFront.drop(1).filter { it }.collect { pruneNow() } }
        scope.launch { keep.drop(1).collect { pruneNow() } }
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
