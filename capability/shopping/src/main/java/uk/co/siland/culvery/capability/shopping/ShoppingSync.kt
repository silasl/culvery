package uk.co.siland.culvery.capability.shopping

import android.util.Log
import java.time.Instant
import java.time.ZoneOffset
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.async
import kotlinx.coroutines.supervisorScope
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import uk.co.siland.culvery.capability.shopping.db.QueuedEntity
import uk.co.siland.culvery.core.plugin.NeedsSignInException
import uk.co.siland.culvery.core.plugin.OUTBOX_BACKOFF_MS
import uk.co.siland.culvery.core.plugin.WallClock
import uk.co.siland.culvery.core.plugin.attempt
import uk.co.siland.culvery.core.plugin.backoffMillis
import uk.co.siland.culvery.core.plugin.textKey

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
private class Pass(val connectionId: String) {
    var open: MutableList<ShoppingItem>? = null
    var historyFrom: Long? = null
    val ticked = mutableSetOf<String>()
    var step = Step.ITEMS
}

/**
 * Whether [head] is the add that was refused, given the health is REFUSED: only the head is ever tried, and the next
 * try of any row sets the health again, so an add at the head that has been tried is the refused one.
 */
private fun isRefused(head: QueuedEntity) = head.kind == QueuedKind.ADD.name && head.attempts > 0

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
        val pass = Pass(store.connectionNow()?.connection?.id ?: return@withLock null)
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

    /** Deals with the head of the queue if it is due. The lock is held. */
    private suspend fun drainLocked(pass: Pass): Drained {
        val stored = store.connectionNow() ?: return Drained.Done(null)
        val conn = stored.connection
        if (conn.id != pass.connectionId) return Drained.Done(null)
        val provider = providers.forConnection(conn) ?: return Drained.Done(null)
        val queue = store.queueNow()
        val head = queue.firstOrNull() ?: return Drained.Done(null)
        if (stored.health == ShoppingHealth.REFUSED && isRefused(head)) {
            // Ticking the refused item off is how the house lets the rest go.
            val tick = queue.firstOrNull { it.kind == QueuedKind.TICK.name && it.clientKey == head.clientKey }
            if (tick != null) {
                store.inTransaction {
                    store.drop(tick.seq)
                    store.drop(head.seq)
                    store.setHealth(ShoppingHealth.OK)
                }
                return Drained.More
            }
        }
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
                QueuedKind.TICK -> sendTick(pass, stored, provider, head, itemId!!)
                QueuedKind.UNTICK -> sendUntick(pass, stored, provider, head, itemId!!, now)
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
        val item = open.firstOrNull { it.id !in never && textKey(it.text) == key } ?: provider.add(conn, head.text, head.clientKey)
        applySent(pass, stored, head, back = item)
    }

    private suspend fun sendTick(pass: Pass, stored: StoredShopping, provider: ShoppingProvider, head: QueuedEntity, itemId: String) {
        provider.tick(stored.connection, itemId)
        applySent(pass, stored, head, gone = itemId)
    }

    private suspend fun sendUntick(
        pass: Pass,
        stored: StoredShopping,
        provider: ShoppingProvider,
        head: QueuedEntity,
        itemId: String,
        nowMillis: Long,
    ) {
        provider.untick(stored.connection, itemId)
        applySent(pass, stored, head, back = ShoppingItem(itemId, head.text, Instant.ofEpochMilli(nowMillis)))
    }

    /**
     * [head] went through: it leaves the queue, health is fine again, and the last read follows it so the list doesn't
     * flicker until the next read. [gone] (a tick's item) leaves the read and the match set, and no later add may match
     * it; [back] goes on top of the read unless it is there, and into the match set only when it is an add's.
     */
    private suspend fun applySent(pass: Pass, stored: StoredShopping, head: QueuedEntity, gone: String? = null, back: ShoppingItem? = null) =
        withContext(NonCancellable) {
            val added = back?.takeIf { head.kind == QueuedKind.ADD.name }
            if (gone != null) {
                pass.ticked += gone
                pass.open?.removeAll { it.id == gone }
            }
            if (added != null && pass.open?.none { it.id == added.id } == true) pass.open?.add(added)
            store.inTransaction {
                store.sent(head.seq, added?.id)
                if (gone != null) store.excludeFromLaterAdds(head.seq, head.text, gone)
                if (added != null) learning.markSeen(added.id)
                if (stored.health != ShoppingHealth.OK) store.setHealth(ShoppingHealth.OK)
                if (stored.lastReadMillis != null) {
                    if (gone != null) store.dropFromRead(gone)
                    if (back != null) store.putOnTopOfRead(back)
                }
            }
        }

    /** A tick or untick whose add is gone from the queue can never be sent; one whose add is queued waits for it. */
    private suspend fun unplaced(head: QueuedEntity, queue: List<QueuedEntity>): Drained {
        if (queue.none { it.kind == QueuedKind.ADD.name && it.clientKey == head.clientKey }) {
            store.drop(head.seq)
            return Drained.More
        }
        return retryLater(head, backoffMillis(head.attempts + 1))
    }

    private suspend fun failed(head: QueuedEntity, kind: QueuedKind, e: Exception, what: Step): Drained {
        val own = what == kind.step()
        // A deleted list took its items with it, so a tick or untick for one has nothing left to do (10 §4.5).
        if (kind != QueuedKind.ADD && (e is ListGoneException || own && (e is ItemGoneException || e is ShoppingRejectedException))) {
            if (e is ListGoneException) {
                Log.w(TAG, "Shopping drain: the ${what.label} is dropped, the list has gone")
            } else {
                Log.w(TAG, "Shopping drain: the ${what.label} is dropped, it can't be sent (${e::class.simpleName})")
            }
            store.drop(head.seq)
            if (e is ListGoneException) store.setHealth(ShoppingHealth.LIST_GONE)
            return Drained.More
        }
        Log.w(TAG, "Shopping drain: the ${what.label} waits (${e::class.simpleName})")
        if (kind == QueuedKind.ADD && own && e is ShoppingRejectedException) {
            // Kept and tried again, as the refusal may pass; the next row sees whether it was already ticked off.
            retryLater(head, OUTBOX_BACKOFF_MS.last())
            store.setHealth(ShoppingHealth.REFUSED)
            return Drained.More
        }
        val waiting = retryLater(head, backoffMillis(head.attempts + 1))
        store.setHealth(healthOf(e))
        return waiting
    }

    /** [head] counts a failure and waits [millis]. */
    private suspend fun retryLater(head: QueuedEntity, millis: Long): Drained {
        val next = clock.nowMillis() + millis
        store.failed(head.seq, next)
        return Drained.Done(next)
    }

    /** Reads the list and the items ticked since the last read, learns from them, and keeps the read. The lock is held. */
    private suspend fun readLocked() {
        val stored = store.connectionNow() ?: return
        val provider = providers.forConnection(stored.connection) ?: return
        val now = clock.nowMillis()
        val seeding = !stored.seeded
        val since = if (seeding) {
            Instant.ofEpochMilli(now).atZone(ZoneOffset.UTC).minusMonths(SEED_MONTHS).toInstant().toEpochMilli()
        } else {
            (stored.lastHistoryMillis ?: now) - HISTORY_OVERLAP_MS
        }
        var what = Step.ITEMS
        val (items, ticked) = attempt({
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
        }) { e ->
            Log.w(TAG, "Shopping check: the ${what.label} failed (${e::class.simpleName})")
            store.setHealth(healthOf(e))
            return
        }
        val queue = store.queueNow()
        // An item matching a queued add is the add's own, or the add will find it; counting it now would count it twice.
        val queuedAdds = queue.filter { it.kind == QueuedKind.ADD.name }.map { textKey(it.text) }.toSet()
        val refused = stored.health == ShoppingHealth.REFUSED && queue.firstOrNull()?.let(::isRefused) == true
        store.inTransaction {
            learning.seen(items.filter { textKey(it.text) !in queuedAdds }, ticked)
            // A year of seed history would date every item's last use today.
            if (!seeding) ticked.forEach { learning.ticked(it.text) }
            store.setHistoryRead(now)
            store.saveRead(items, if (refused) ShoppingHealth.REFUSED else ShoppingHealth.OK, now)
        }
        learning.pruneIfDue()
    }
}
