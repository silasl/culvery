package uk.co.siland.culvery.capability.calendar

import java.util.concurrent.atomic.AtomicInteger
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.sync.Mutex

/**
 * The one lock around calendar writes (3a design §3.12): the editor holds it while it reads the queue and writes or
 * queues a change, and the drain while it drops a create with the changes behind it, so an edit being queued behind
 * that create either lands first (and goes with it) or finds no create. It wraps a Mutex rather than being one, so
 * nothing delegates Mutex's deprecated onLock.
 */
@Singleton
class CalendarWriteLock @Inject constructor() {
    private val mutex = Mutex()

    private val queued = AtomicInteger()

    suspend fun <T> withLock(block: suspend () -> T): T {
        queued.incrementAndGet()
        try {
            mutex.lock()
        } finally {
            queued.decrementAndGet()
        }
        try {
            return block()
        } finally {
            mutex.unlock()
        }
    }

    internal val held: Boolean get() = mutex.isLocked

    /** How many callers are waiting for the lock, for tests. */
    internal val waiting: Int get() = queued.get()
}
