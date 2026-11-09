package uk.co.siland.culvery.core.plugin

import android.util.Log
import java.util.concurrent.atomic.AtomicLong
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.retryWhen
import kotlinx.coroutines.launch

private const val FIRST_RETRY_MS = 1_000L
private const val LAST_RETRY_MS = 60_000L
private const val MAX_SHIFT = 30

/** [block]'s result, or [orElse]'s for what it threw, which is where it is logged. Cancellation is never caught. */
inline fun <T> attempt(block: () -> T, orElse: (Exception) -> T): T = try {
    block()
} catch (e: CancellationException) {
    throw e
} catch (e: Exception) {
    orElse(e)
}

/** Runs [body] between the "Polling on" and "Polling off" lines a poller logs, off even when it is cancelled. */
suspend inline fun polling(tag: String, body: () -> Unit) {
    Log.d(tag, "Polling on")
    try {
        body()
    } finally {
        Log.d(tag, "Polling off")
    }
}

/** Waits after an outbox's nth failed send: 30 s, 1 min, 2 min, then 5 min (the calendar's and the shopping list's). */
val OUTBOX_BACKOFF_MS: List<Long> = listOf(30_000L, 60_000L, 120_000L, 300_000L)

/** The wait after failure number [attempts] (from 1); the last step repeats. */
fun backoffMillis(attempts: Int): Long = OUTBOX_BACKOFF_MS[(attempts - 1).coerceIn(0, OUTBOX_BACKOFF_MS.lastIndex)]

/** [firstMs] doubled [doublings] times (fewer than 0 count as 0), at most [capMs]. */
fun doublingMillis(firstMs: Long, capMs: Long, doublings: Int): Long =
    (firstMs shl doublings.coerceIn(0, MAX_SHIFT)).coerceAtMost(capMs)

/** The wait before retrying after failure number [attempt] (from 0): 1 s, doubling each time, at most 60 s. */
fun retryDelayMillis(attempt: Long): Long =
    doublingMillis(FIRST_RETRY_MS, LAST_RETRY_MS, attempt.coerceAtMost(MAX_SHIFT.toLong()).toInt())

/**
 * A run of failures with no success between, and the wait that grows with it (19 §5). The caller logs: [failed] is true
 * for the first of a run, [succeeded] says how many there were. Safe to use from several threads.
 */
class FailureRun {
    private var inRun = 0
    private var sinceReset = 0

    /** Counts a failure; true on the first of a run. */
    @Synchronized
    fun failed(): Boolean {
        inRun++
        sinceReset++
        return inRun == 1
    }

    /** How many failed in the run just ended, 0 if none; clears the run. */
    @Synchronized
    fun succeeded(): Int = inRun.also {
        inRun = 0
        sinceReset = 0
    }

    /** The next [waitMillis] is `everyMs` again; the run itself is kept. */
    @Synchronized
    fun resetWaits() {
        sinceReset = 0
    }

    /** [everyMs] with no failure or one since the last reset, then doubling to [capMs]. */
    @Synchronized
    fun waitMillis(everyMs: Long, capMs: Long): Long = doublingMillis(everyMs, capMs, sinceReset - 1)
}

/**
 * Starts the flow again after a failure, waiting [retryDelayMillis], instead of ending it: a store hiccup must not
 * leave a tab, a card or the sync loop's trigger gone until the app restarts (3a design §3.12). [onFailure] logs it.
 * After a failure, the first value starts a timer as long as the next wait: if the flow hasn't failed again when it
 * ends, the waits start again from 1 s; if it has, they keep growing (4c §7.2), so a read that emits and then fails
 * can't log every second.
 */
fun <T> Flow<T>.retryWithBackoff(onFailure: (Throwable) -> Unit): Flow<T> = flow {
    // Per collection; onEach sits upstream of retryWhen, so a downstream failure is never caught or retried.
    val failures = AtomicLong(0)
    coroutineScope {
        var standing: Job? = null
        emitAll(
            onEach {
                // Left running by later values: a flow that emits more often than the wait must still reset it
                // (retryWhen cancels the timer when the flow fails again).
                if (standing?.isActive != true) {
                    standing = failures.get().takeIf { it > 0 }?.let(::retryDelayMillis)?.let { launch { delay(it); failures.set(0) } }
                }
            }.retryWhen { cause, _ ->
                standing?.cancel()
                onFailure(cause)
                delay(retryDelayMillis(failures.getAndIncrement()))
                true
            },
        )
        standing?.cancel()
    }
}
