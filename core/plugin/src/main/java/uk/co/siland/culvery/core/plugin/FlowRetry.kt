package uk.co.siland.culvery.core.plugin

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
private const val MAX_DOUBLINGS = 6L

/** [block]'s result, or [orElse]'s for what it threw, which is where it is logged. Cancellation is never caught. */
inline fun <T> attempt(block: () -> T, orElse: (Exception) -> T): T = try {
    block()
} catch (e: CancellationException) {
    throw e
} catch (e: Exception) {
    orElse(e)
}

/** Waits after an outbox's nth failed send: 30 s, 1 min, 2 min, then 5 min (the calendar's and the shopping list's). */
val OUTBOX_BACKOFF_MS: List<Long> = listOf(30_000L, 60_000L, 120_000L, 300_000L)

/** The wait after failure number [attempts] (from 1); the last step repeats. */
fun backoffMillis(attempts: Int): Long = OUTBOX_BACKOFF_MS[(attempts - 1).coerceIn(0, OUTBOX_BACKOFF_MS.lastIndex)]

/** The wait before retrying after failure number [attempt] (from 0): 1 s, doubling each time, at most 60 s. */
fun retryDelayMillis(attempt: Long): Long =
    (FIRST_RETRY_MS shl attempt.coerceAtMost(MAX_DOUBLINGS).toInt()).coerceAtMost(LAST_RETRY_MS)

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
