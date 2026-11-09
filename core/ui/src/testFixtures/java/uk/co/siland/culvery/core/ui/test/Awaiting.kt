package uk.co.siland.culvery.core.ui.test

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.isActive
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout

/** [block] on a real dispatcher, as DataStore's writes need, failing after [timeoutMillis] with [what] it waited for; a timeout inside [block] passes through. */
suspend fun <T> awaiting(what: String, timeoutMillis: Long = 5_000, block: suspend () -> T): T = withContext(Dispatchers.Default) {
    var fromBlock: TimeoutCancellationException? = null
    try {
        withTimeout(timeoutMillis) {
            try {
                block()
            } catch (e: TimeoutCancellationException) {
                if (isActive) fromBlock = e
                throw e
            }
        }
    } catch (e: TimeoutCancellationException) {
        if (e === fromBlock) throw e
        throw AssertionError("Waited ${timeoutMillis / 1000} s for $what", e)
    }
}
