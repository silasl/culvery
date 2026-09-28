package uk.co.siland.culvery.capability.calendar

import android.util.Log
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import uk.co.siland.culvery.core.plugin.ApplicationScope
import uk.co.siland.culvery.core.plugin.Startable
import uk.co.siland.culvery.core.plugin.WallClock
import uk.co.siland.culvery.core.plugin.retryWithBackoff

const val SYNC_INTERVAL_MS = 5 * 60_000L

/** The shortest wait between passes, so an overdue queued change can't spin the loop. */
const val MIN_PASS_GAP_MS = 1_000L

/**
 * Runs a pass (the outbox drain, then a sync) as soon as the first connection list arrives, whenever a
 * connection is added or removed, on [requestSync], when a queued change falls due, and otherwise every
 * [intervalMillis]. A trigger that arrives mid-pass runs one more pass afterwards; it never cancels the running
 * one. [untilNextRetry] is the time until the earliest queued change is due, or null when nothing is queued.
 */
@Singleton
class CalendarSyncLoop internal constructor(
    private val syncAll: suspend () -> Unit,
    private val connectionIds: Flow<List<String>>,
    private val scope: CoroutineScope,
    private val intervalMillis: Long = SYNC_INTERVAL_MS,
    private val untilNextRetry: suspend () -> Long? = { null },
    private val drainBackoff: () -> Long? = { null },
) : Startable {
    @Inject
    constructor(sync: CalendarSync, store: CalendarStore, clock: WallClock, @ApplicationScope scope: CoroutineScope) :
        this(
            sync::syncAll,
            store.connectionIds(),
            scope,
            SYNC_INTERVAL_MS,
            { store.nextAttemptMillis()?.let { it - clock.nowMillis() } },
            sync::drainBackoffMillis,
        )

    private val wake = Channel<Unit>(Channel.CONFLATED)

    /** Runs a pass as soon as possible. Requests made during a pass add up to one more pass. */
    fun requestSync() {
        wake.trySend(Unit)
    }

    override fun start() {
        scope.launch {
            // Every list, the first included, asks for a pass, so a connection added before this collector
            // started is never missed.
            launch {
                connectionIds.distinctUntilChanged()
                    .retryWithBackoff { Log.w(TAG, "Couldn't read the connections; retrying", it) }
                    .collect { wake.trySend(Unit) }
            }
            // The first pass waits only for the first connection list.
            var wait = intervalMillis
            while (true) {
                withTimeoutOrNull(wait) { wake.receive() }
                runPass()
                wait = nextWait()
            }
        }
    }

    private suspend fun runPass() {
        try {
            syncAll()
        } catch (e: CancellationException) {
            // Stops the loop only if it was really cancelled; a stray one (an internal timeout) must not.
            currentCoroutineContext().ensureActive()
            Log.w(TAG, "Calendar sync was cancelled internally", e)
        } catch (e: Throwable) {
            // An Error too (review M1): the loop must outlive it, and the application scope's handler never sees it.
            Log.w(TAG, "Calendar sync failed", e)
        }
    }

    private suspend fun nextWait(): Long {
        val retry = try {
            untilNextRetry()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Throwable) {
            // An Error too (review M1): escaping here would end the loop.
            Log.w(TAG, "Couldn't read the outbox; waiting the full interval", e)
            null
        }
        // After a failed drain, the queue's own times don't count: waiting a second would retry the failure (m2).
        val floor = maxOf(MIN_PASS_GAP_MS, drainBackoff() ?: 0L).coerceAtMost(intervalMillis)
        return (retry ?: intervalMillis).coerceIn(floor, intervalMillis)
    }

    private companion object {
        const val TAG = "CalendarSync"
    }
}
