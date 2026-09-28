package uk.co.siland.culvery.provider.calendar_google

import kotlin.coroutines.cancellation.CancellationException

/** What [block] threw, or null if it returned; a cancellation is never swallowed. Shared by this module's tests. */
internal suspend fun failureOf(block: suspend () -> Unit): Throwable? =
    try {
        block()
        null
    } catch (e: CancellationException) {
        throw e
    } catch (e: Exception) {
        e
    }
