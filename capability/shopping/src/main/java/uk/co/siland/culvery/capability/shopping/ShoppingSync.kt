package uk.co.siland.culvery.capability.shopping

import android.util.Log
import java.time.Instant
import java.time.ZoneOffset
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.async
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.supervisorScope
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import uk.co.siland.culvery.capability.shopping.db.QueuedEntity
import uk.co.siland.culvery.core.plugin.NeedsSignInException
import uk.co.siland.culvery.core.plugin.WallClock
import uk.co.siland.culvery.core.plugin.textKey

/** How long a change waits after its 1st, 2nd, 3rd… failure; the last step repeats (10 §4.5). */
val SHOPPING_BACKOFF_MS = listOf(30_000L, 60_000L, 120_000L, 300_000L)

/** A check asks for the ticked items from this long before the last such read, so one ticked while it ran is not missed (10 §4.4). */
const val HISTORY_OVERLAP_MS = 10 * 60_000L

/** The first check after connecting reads this many months of ticked items, to learn from (10 §4.7). */
const val SEED_MONTHS = 12L

private const val TAG = "ShoppingSync"

internal fun healthOf(e: Exception): ShoppingHealth = when (e) {
    is NeedsSignInException -> ShoppingHealth.NEEDS_PERMISSION
    is ListGoneException -> ShoppingHealth.LIST_GONE
    is AccountGoneException -> ShoppingHealth.ACCOUNT_GONE
    // Unreachable and Offline, and anything a provider throws that the seam doesn't name: the change is kept and tried again.
    else -> ShoppingHealth.CANT_REACH
}

internal fun backoffMillis(failures: Int): Long = SHOPPING_BACKOFF_MS[(failures - 1).coerceIn(0, SHOPPING_BACKOFF_MS.lastIndex)]

/** The provider call being made, so a log line names it without naming an item. */
private enum class Step(val label: String) {
    ITEMS("items read"),
    HISTORY("history read"),
    ADD("add"),
    TICK("tick"),
    UNTICK("untick"),
}

private fun QueuedKind.step() = when (this) {
    QueuedKind.ADD -> Step.ADD
    QueuedKind.TICK -> Step.TICK
    QueuedKind.UNTICK -> Step.UNTICK
}

/**
 * One run through the queue. [open] is the match set an add looks in: the list as read at the pass's start, then
 * what the pass itself adds and ticks. [ticked] are the ids this pass ticked; no add may match one.
 */
private class Pass {
    var connectionId: String? = null
    var open: MutableList<ShoppingItem>? = null
    var historyFrom: Long? = null
    val ticked = mutableSetOf<String>()
    var step = Step.ITEMS
}

private sealed interface Drained {
    /** A row was dealt with; more may be queued. */
    data object More : Drained

    /** Nothing more can be sent now: the queue is empty (null) or its head waits until [nextAtMillis]. */
    data class Done(val nextAtMillis: Long?) : Drained
}

/**
 * Sends the queue to the provider in order and reads the list (10 §4.4, §4.5). An add that may already have landed is
 * looked for in the list, and in the items ticked since it was made, before it is sent again, so a retry never adds a
 * second one. Nothing is logged but the step and the exception's class.
 */
@Singleton
class ShoppingSync @Inject constructor(
    private val store: ShoppingStore,
    private val providers: Set<@JvmSuppressWildcards ShoppingProvider>,
    private val learning: ShoppingLearning,
    private val lock: ShoppingLock,
    private val clock: WallClock,
) {
    // One pass at a time: a pass's match set lives across lock releases, so another pass's writes would leave it stale.
    private val passes = Mutex()

    /**
     * Sends what is due, one row at a time under the lock so a tap waits for at most one call. Null when the queue is
     * empty, else when its head is next due.
     */
    suspend fun drain(): Long? = passes.withLock {
        val pass = Pass()
        var drained: Drained
        do {
            drained = lock.withLock { drainLocked(pass) }
        } while (drained !is Drained.Done)
        drained.nextAtMillis
    }

    /** Sends what is due, then reads the list and the items ticked since, holding the lock so no write is read past (10 §4.4). */
    suspend fun check() {
        drain()
        lock.withLock { readLocked() }
    }

    private fun providerOf(stored: StoredShopping) = providers.firstOrNull { it.descriptor.id == stored.connection.providerId }

    /** Deals with the head of the queue if it is due. The lock is held. */
    private suspend fun drainLocked(pass: Pass): Drained {
        val stored = store.connectionNow() ?: return Drained.Done(null)
        val conn = stored.connection
        if (pass.connectionId == null) pass.connectionId = conn.id
        if (conn.id != pass.connectionId) return Drained.Done(null)
        val provider = providerOf(stored) ?: return Drained.Done(null)
        val queue = store.queueNow()
        val head = queue.firstOrNull() ?: return Drained.Done(null)
        val now = clock.nowMillis()
        if (head.nextAtMillis > now) return Drained.Done(head.nextAtMillis)
        val kind = QueuedKind.valueOf(head.kind)
        val itemId = head.itemId
        if (kind != QueuedKind.ADD && itemId == null) return unplaced(head, queue)

        pass.step = Step.ITEMS
        try {
            if (pass.open == null && queue.any { it.kind == QueuedKind.ADD.name }) pass.open = provider.items(conn).toMutableList()
            pass.step = kind.step()
            when (kind) {
                QueuedKind.ADD -> sendAdd(pass, stored, provider, head, queue)
                QueuedKind.TICK -> {
                    provider.tick(conn, itemId!!)
                    withContext(NonCancellable) {
                        pass.ticked += itemId
                        pass.open?.removeAll { it.id == itemId }
                        store.inTransaction {
                            store.sent(head.seq, null)
                            store.excludeFromLaterAdds(head.seq, head.text, itemId)
                            succeeded(stored) { read -> read.filter { it.id != itemId } }
                        }
                    }
                }
                QueuedKind.UNTICK -> {
                    provider.untick(conn, itemId!!)
                    withContext(NonCancellable) {
                        val back = ShoppingItem(itemId, head.text, Instant.ofEpochMilli(now))
                        store.inTransaction {
                            store.sent(head.seq, null)
                            succeeded(stored) { read -> if (read.any { it.id == itemId }) read else listOf(back) + read }
                        }
                    }
                }
            }
            return Drained.More
        } catch (e: CancellationException) {
            // The add may have landed with no answer seen; its retry will look in the ticked items too.
            if (pass.step == Step.ADD) withContext(NonCancellable) { store.failed(head.seq, now) }
            throw e
        } catch (e: Exception) {
            return failed(head, kind, e, pass.step)
        }
    }

    private suspend fun sendAdd(
        pass: Pass,
        stored: StoredShopping,
        provider: ShoppingProvider,
        head: QueuedEntity,
        queue: List<QueuedEntity>,
    ) {
        val conn = stored.connection
        val open = pass.open!!
        if (head.attempts > 0 && head.madeAtMillis < (pass.historyFrom ?: Long.MAX_VALUE)) {
            pass.step = Step.HISTORY
            val ticked = provider.history(conn, Instant.ofEpochMilli(head.madeAtMillis))
            pass.historyFrom = head.madeAtMillis
            open += ticked.filter { t -> open.none { it.id == t.id } }
            pass.step = Step.ADD
        }
        val key = textKey(head.text)
        val never = splitIds(head.excludeIds) + pass.ticked + queue.mapNotNull { if (it.kind == QueuedKind.TICK.name) it.itemId else null }
        val item = open.firstOrNull { it.id !in never && textKey(it.text) == key }
            ?: provider.add(conn, head.text, head.clientKey).also { open += it }
        withContext(NonCancellable) {
            store.inTransaction {
                store.sent(head.seq, item.id)
                learning.markSeen(item.id)
                succeeded(stored) { read -> if (read.any { it.id == item.id }) read else listOf(item) + read }
            }
        }
    }

    /** Health is fine again, and the last read follows the change, so the list doesn't flicker until the next read. */
    private suspend fun succeeded(stored: StoredShopping, change: (List<ShoppingItem>) -> List<ShoppingItem>) {
        if (stored.health != ShoppingHealth.OK) store.setHealth(ShoppingHealth.OK)
        val readAt = stored.lastReadMillis ?: return
        store.saveRead(change(store.itemsNow()), ShoppingHealth.OK, readAt)
    }

    /** A tick or untick whose add is gone from the queue can never be sent; one whose add is queued waits for it. */
    private suspend fun unplaced(head: QueuedEntity, queue: List<QueuedEntity>): Drained {
        if (queue.none { it.kind == QueuedKind.ADD.name && it.clientKey == head.clientKey }) {
            store.drop(head.seq)
            return Drained.More
        }
        val next = clock.nowMillis() + backoffMillis(head.attempts + 1)
        store.failed(head.seq, next)
        return Drained.Done(next)
    }

    private suspend fun failed(head: QueuedEntity, kind: QueuedKind, e: Exception, what: Step): Drained {
        val now = clock.nowMillis()
        val own = what == kind.step()
        when {
            // A deleted list took its items with it, so a tick or untick for one has nothing left to do (10 §4.5).
            kind != QueuedKind.ADD && e is ListGoneException -> {
                Log.w(TAG, "Shopping drain: the ${what.label} is dropped, the list has gone")
                store.drop(head.seq)
                store.setHealth(ShoppingHealth.LIST_GONE)
                return Drained.More
            }
            kind != QueuedKind.ADD && own && (e is ItemGoneException || e is ShoppingRejectedException) -> {
                Log.w(TAG, "Shopping drain: the ${what.label} is dropped, it can't be sent (${e::class.simpleName})")
                store.drop(head.seq)
                return Drained.More
            }
            kind == QueuedKind.ADD && own && e is ShoppingRejectedException -> {
                Log.w(TAG, "Shopping drain: the ${what.label} waits (${e::class.simpleName})")
                val next = now + SHOPPING_BACKOFF_MS.last()
                store.failed(head.seq, next)
                return Drained.Done(next)
            }
        }
        Log.w(TAG, "Shopping drain: the ${what.label} waits (${e::class.simpleName})")
        val next = now + backoffMillis(head.attempts + 1)
        store.failed(head.seq, next)
        store.setHealth(healthOf(e))
        return Drained.Done(next)
    }

    /** Reads the list and the items ticked since the last read, learns from them, and keeps the read. The lock is held. */
    private suspend fun readLocked() {
        val stored = store.connectionNow() ?: return
        val provider = providerOf(stored) ?: return
        val now = clock.nowMillis()
        val seeding = !stored.seeded
        val since = if (seeding) {
            Instant.ofEpochMilli(now).atZone(ZoneOffset.UTC).minusMonths(SEED_MONTHS).toInstant().toEpochMilli()
        } else {
            (stored.lastHistoryMillis ?: now) - HISTORY_OVERLAP_MS
        }
        var what = Step.ITEMS
        val (items, ticked) = try {
            // Both reads go at once; a failure is named as the one-after-the-other reads would name it.
            supervisorScope {
                val reading = async { provider.items(stored.connection) }
                val history = async { provider.history(stored.connection, Instant.ofEpochMilli(since)) }
                try {
                    val open = reading.await()
                    what = Step.HISTORY
                    open to history.await()
                } finally {
                    history.cancel()
                }
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.w(TAG, "Shopping check: the ${what.label} failed (${e::class.simpleName})")
            store.setHealth(healthOf(e))
            return
        }
        // An item matching a queued add is the add's own, or the add will find it; counting it now would count it twice.
        val queuedAdds = store.queueNow().filter { it.kind == QueuedKind.ADD.name }.map { textKey(it.text) }.toSet()
        store.inTransaction {
            learning.seen(items.filter { textKey(it.text) !in queuedAdds }, ticked)
            // A year of seed history would date every item's last use today.
            if (!seeding) ticked.forEach { learning.ticked(it.text) }
            store.setHistoryRead(now)
            store.saveRead(items, ShoppingHealth.OK, now)
        }
        learning.pruneIfDue()
    }
}
