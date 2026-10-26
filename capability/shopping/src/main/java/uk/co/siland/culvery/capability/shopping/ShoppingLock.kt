package uk.co.siland.culvery.capability.shopping

import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/** Held by everything that changes the queue, the connection or the read; never across a check's read of the list (10 §4.4). */
@Singleton
class ShoppingLock @Inject constructor() {
    private val mutex = Mutex()

    suspend fun <T> withLock(block: suspend () -> T): T = mutex.withLock { block() }
}
