package uk.co.siland.culvery.capability.calendar

import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * The one lock around calendar writes (3a design §3.12): the editor holds it while it reads the queue and writes or
 * queues a change, and the drain while it drops a create with the changes behind it, so an edit being queued behind
 * that create either lands first (and goes with it) or finds no create. It wraps a Mutex rather than being one, so
 * nothing delegates Mutex's deprecated onLock.
 */
@Singleton
class CalendarWriteLock @Inject constructor() {
    private val mutex = Mutex()

    suspend fun <T> withLock(block: suspend () -> T): T = mutex.withLock { block() }
}
