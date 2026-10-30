package uk.co.siland.culvery.capability.door

import android.util.Log
import java.time.ZoneId
import javax.inject.Inject
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import uk.co.siland.culvery.core.household.HouseholdZone
import uk.co.siland.culvery.core.plugin.AlertHost
import uk.co.siland.culvery.core.plugin.ApplicationScope
import uk.co.siland.culvery.core.plugin.Startable
import uk.co.siland.culvery.core.plugin.WallClock

internal const val DAY_MS = 24 * 60 * 60 * 1_000L

/**
 * Prunes at start, at each household midnight, when the keep changes, and each time Culvery comes back to the front
 * (5 §4.5, 15a §3.1): a tablet asleep or behind another app through its midnight prune still loses old pictures within
 * the days it keeps.
 */
class DoorPruneLoop internal constructor(
    private val prune: suspend (Long) -> Unit,
    private val clock: WallClock,
    private val inFront: StateFlow<Boolean>,
    private val keep: Flow<KeepDays>,
    private val zone: Flow<ZoneId>,
    private val scope: CoroutineScope,
) : Startable {
    @Inject
    constructor(
        store: DoorStore,
        clock: WallClock,
        host: AlertHost,
        settings: DoorSettings,
        zone: HouseholdZone,
        @ApplicationScope scope: CoroutineScope,
    ) : this(store::prune, clock, host.inFront, settings.keep, zone.zone, scope)

    override fun start() {
        scope.launch {
            while (true) {
                pruneNow()
                delay(untilMidnight())
            }
        }
        scope.launch { inFront.drop(1).filter { it }.collect { pruneNow() } }
        scope.launch { keep.drop(1).collect { pruneNow() } }
    }

    /** Until the household's next midnight; a day when the zone can't be read, which the return to the front backs up. */
    private suspend fun untilMidnight(): Long = try {
        val now = clock.nowMillis()
        val z = zone.first()
        dateIn(now, z).plusDays(1).atStartOfDay(z).toInstant().toEpochMilli() - now
    } catch (e: CancellationException) {
        throw e
    } catch (e: Exception) {
        Log.w(TAG, "Couldn't read the household's time zone (${e::class.simpleName}); pruning again in a day")
        DAY_MS
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
