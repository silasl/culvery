package uk.co.siland.culvery.core.plugin

import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.retryWhen

private const val FIRST_RETRY_MS = 1_000L
private const val LAST_RETRY_MS = 60_000L
private const val MAX_DOUBLINGS = 6L

/** The wait before retrying after failure number [attempt] (from 0): 1 s, doubling each time, at most 60 s. */
fun retryDelayMillis(attempt: Long): Long =
    (FIRST_RETRY_MS shl attempt.coerceAtMost(MAX_DOUBLINGS).toInt()).coerceAtMost(LAST_RETRY_MS)

/**
 * Starts the flow again after a failure, waiting [retryDelayMillis], instead of ending it: a store hiccup must not
 * leave a tab, a card or the sync loop's trigger gone until the app restarts (3a design §3.12). [onFailure] logs it.
 */
fun <T> Flow<T>.retryWithBackoff(onFailure: (Throwable) -> Unit): Flow<T> = retryWhen { cause, attempt ->
    onFailure(cause)
    delay(retryDelayMillis(attempt))
    true
}
