package uk.co.siland.culvery.capability.calendar

import android.util.Log
import javax.inject.Inject
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.launch
import uk.co.siland.culvery.core.plugin.ApplicationScope
import uk.co.siland.culvery.core.plugin.Startable

const val SYNC_INTERVAL_MS = 5 * 60_000L

/** Syncs on start, whenever a connection is added or removed, and then every [intervalMillis]. */
class CalendarSyncLoop internal constructor(
    private val syncAll: suspend () -> Unit,
    private val connectionIds: Flow<List<String>>,
    private val scope: CoroutineScope,
    private val intervalMillis: Long = SYNC_INTERVAL_MS,
) : Startable {
    @Inject
    constructor(sync: CalendarSync, store: CalendarStore, @ApplicationScope scope: CoroutineScope) :
        this(sync::syncAll, store.connectionIds(), scope)

    override fun start() {
        scope.launch {
            connectionIds.distinctUntilChanged().collectLatest {
                while (true) {
                    try {
                        syncAll()
                    } catch (e: CancellationException) {
                        // Stops the loop only if it was really cancelled; a stray one (an internal timeout) must not.
                        currentCoroutineContext().ensureActive()
                        Log.w(TAG, "Calendar sync was cancelled internally", e)
                    } catch (e: Exception) {
                        Log.w(TAG, "Calendar sync failed", e)
                    }
                    delay(intervalMillis)
                }
            }
        }
    }

    private companion object {
        const val TAG = "CalendarSync"
    }
}
