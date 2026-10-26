package uk.co.siland.culvery.capability.shopping

import android.util.Log
import java.time.Instant
import java.time.ZoneOffset
import java.util.concurrent.atomic.AtomicLong
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
import uk.co.siland.culvery.core.plugin.TOAST_WITH_ACTION_MS
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
 * what the pass itself adds and ticks, and the [fromHistory] ids a retried add looked for. [ticked] are the ids this
 * pass ticked; no add may match one.
 */
private class Pass(val connectionId: String) {
    var open: MutableList<ShoppingItem>? = null
    var historyFrom: Long? = null
    val fromHistory = mutableSetOf<String>()
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

    // Moves on with every send, connection change and kept read, so a read made across one of them is not kept.
    private val writes = AtomicLong()

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

    /** The connection was made, remade or stopped, so a read already out is of the old one. The lock is held. */
    fun connectionChanged() {
        writes.incrementAndGet()
    }

    /**
     * Sends what is due, then reads the list and the items ticked since (10 §4.4). When the read finds the list again
     * after it couldn't, what waited out a backoff goes straight away.
     */
    suspend fun check() {
        drain()
        if (read()) drain()
    }

    /** Deals with the head of the queue if it is due. The lock is held. */
    private suspend fun drainLocked(pass: Pass): Drained {
        val stored = store.connectionNow() ?: return Drained.Done(null)
        val conn = stored.connection
        if (conn.id != pass.connectionId) return Drained.Done(null)
        val provider = providers.forConnection(conn) ?: return Drained.Done(null)
        val queue = store.queueNow()
        val head = queue.firstOrNull() ?: return Drained.Done(null)
        val now = clock.nowMillis()
        if (stored.health == ShoppingHealth.REFUSED && isRefused(head)) {
            // Ticking the refused item off is how the house lets the rest go, once the tick's Undo has gone.
            val tick = queue.firstOrNull { it.kind == QueuedKind.TICK.name && it.clientKey == head.clientKey }
            if (tick != null) {
                val undoGoneAt = tick.madeAtMillis + TOAST_WITH_ACTION_MS
                if (now < undoGoneAt) return Drained.Done(undoGoneAt)
                store.inTransaction {
                    store.drop(tick.seq)
                    store.drop(head.seq)
                    store.setHealth(ShoppingHealth.OK)
                }
                return Drained.More
            }
        }
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
            val fresh = ticked.filter { t -> open.none { it.id == t.id } }
            open += fresh
            pass.fromHistory += fresh.map { it.id }
            pass.step = Step.ADD
        }
        val key = textKey(head.text)
        val never = splitIds(head.excludeIds) + pass.ticked + queue.mapNotNull { if (it.kind == QueuedKind.TICK.name) it.itemId else null }
        val item = open.firstOrNull { it.id !in never && textKey(it.text) == key } ?: provider.add(conn, head.text, head.clientKey)
        // An item a phone has ticked since is the add done, but it isn't on the list.
        applySent(pass, stored, head, added = item, back = item.takeIf { it.id !in pass.fromHistory })
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
     * it; [added] (an add's item) gives later rows its id, is seen and joins the match set; [back] goes on top of the
     * read unless it is there.
     */
    private suspend fun applySent(
        pass: Pass,
        stored: StoredShopping,
        head: QueuedEntity,
        gone: String? = null,
        added: ShoppingItem? = null,
        back: ShoppingItem? = null,
    ) =
        withContext(NonCancellable) {
            writes.incrementAndGet()
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

    /**
     * Reads the list and the items ticked since the last read without the lock, so a tap never waits on the network,
     * then learns from them and keeps the read under it, unless something was sent or the connection changed meanwhile
     * (the next check reads again). True when the read found the list after it couldn't, and the queue was made due.
     */
    private suspend fun read(): Boolean {
        val noted = writes.get()
        val stored = store.connectionNow() ?: return false
        val provider = providers.forConnection(stored.connection) ?: return false
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
            lock.withLock { if (writes.get() == noted) store.setHealth(healthOf(e)) }
            return false
        }
        return lock.withLock {
            if (!writes.compareAndSet(noted, noted + 1)) return@withLock false
            keepRead(items, ticked, seeding, now)
        }
    }

    /** Learns from a read and keeps it; true when it found the list after it couldn't. The lock is held. */
    private suspend fun keepRead(items: List<ShoppingItem>, ticked: List<ShoppingItem>, seeding: Boolean, now: Long): Boolean {
        val stored = store.connectionNow() ?: return false
        val queue = store.queueNow()
        // An item matching a queued add is the add's own, or the add will find it; counting it now would count it twice.
        val queuedAdds = queue.filter { it.kind == QueuedKind.ADD.name }.map { textKey(it.text) }.toSet()
        val refused = stored.health == ShoppingHealth.REFUSED && queue.firstOrNull()?.let(::isRefused) == true
        val found = stored.health != ShoppingHealth.OK && stored.health != ShoppingHealth.REFUSED
        store.inTransaction {
            learning.seen(items.filter { textKey(it.text) !in queuedAdds }, ticked)
            // A year of seed history would date every item's last use today.
            if (!seeding) ticked.forEach { learning.ticked(it.text) }
            store.setHistoryRead(now)
            store.saveRead(items, if (refused) ShoppingHealth.REFUSED else ShoppingHealth.OK, now)
            if (found) store.makeQueueDue()
        }
        learning.pruneIfDue()
        return found
    }
}
