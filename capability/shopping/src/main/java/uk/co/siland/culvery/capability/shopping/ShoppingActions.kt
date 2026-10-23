package uk.co.siland.culvery.capability.shopping

import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import uk.co.siland.culvery.core.plugin.ApplicationScope
import uk.co.siland.culvery.core.plugin.TOAST_WITH_ACTION_MS
import uk.co.siland.culvery.core.plugin.ToastAction
import uk.co.siland.culvery.core.plugin.Toaster
import uk.co.siland.culvery.core.plugin.WallClock
import uk.co.siland.culvery.core.plugin.textKey

/** The longest an item's text may be (10 §4.8). */
const val ADD_MAX_CHARS = 100

/** Adds past either rate are refused, so a stuck key or a bored child can't fill the list (10 §4.11). */
const val ADDS_PER_SECOND = 3
const val ADDS_PER_MINUTE = 20

/** How long the row an add matched shows in the accent colour (10 §4.6). */
const val FLASH_MS = 1_200L

private const val ONE_SECOND_MS = 1_000L
private const val ONE_MINUTE_MS = 60_000L

/** Healths in which nothing queued can be sent until someone fixes it in Settings. */
internal val UNFIXED_HEALTHS = setOf(ShoppingHealth.ACCOUNT_GONE, ShoppingHealth.LIST_GONE)

internal fun newShoppingKey(): String = UUID.randomUUID().toString().replace("-", "")

/** What an add did. */
sealed interface AddResult {
    data object Added : AddResult

    /** An open row of [key] already has this text; it flashes instead. */
    data class AlreadyThere(val key: String) : AddResult

    data object Empty : AddResult

    data object TooFast : AddResult

    /** The queue is full while the list can't be reached for good. */
    data object Full : AddResult
}

/** Adding, ticking and taking a tick back (10 §4.6, §4.8). Each change is queued, then sent by [ShoppingDrainLoop]. */
@Singleton
class ShoppingActions @Inject constructor(
    private val store: ShoppingStore,
    private val learning: ShoppingLearning,
    private val lock: ShoppingLock,
    private val loop: ShoppingDrainLoop,
    private val checks: ShoppingChecks,
    private val toaster: Toaster,
    private val clock: WallClock,
    @ApplicationScope private val scope: CoroutineScope,
) {
    private val adding = Mutex()
    private val recentAdds = ArrayDeque<Long>()

    private val _undoable = MutableStateFlow<Ticked?>(null)

    /** The latest tick while Undo can still take it back. */
    internal val undoable: StateFlow<Ticked?> = _undoable.asStateFlow()
    private var undoClear: Job? = null

    private val _flashing = MutableStateFlow<String?>(null)

    /** The row key an add just matched, for [FLASH_MS]. */
    internal val flashing: StateFlow<String?> = _flashing.asStateFlow()
    private var flashClear: Job? = null

    /**
     * Queues [text] unless it is empty, already on the list, too fast, or the queue is full. One at a time, and the queue
     * is read under the lock, so two taps at once queue one add.
     */
    suspend fun add(text: String): AddResult = adding.withLock { lock.withLock { addLocked(text) } }

    private suspend fun addLocked(text: String): AddResult {
        val cleaned = text.trim().take(ADD_MAX_CHARS).trimEnd()
        if (cleaned.isEmpty()) return AddResult.Empty
        val key = textKey(cleaned)
        val queue = store.queueNow()
        val rows = shown(store.items.first(), queue, _undoable.value)
        rows.firstOrNull { !it.ticked && textKey(it.text) == key }?.let { there ->
            flash(there.key)
            checks.askNow()
            return AddResult.AlreadyThere(there.key)
        }
        val now = clock.nowMillis()
        while (recentAdds.isNotEmpty() && now - recentAdds.first() >= ONE_MINUTE_MS) recentAdds.removeFirst()
        if (recentAdds.count { now - it < ONE_SECOND_MS } >= ADDS_PER_SECOND || recentAdds.size >= ADDS_PER_MINUTE) return AddResult.TooFast
        if (queue.size >= QUEUE_MAX && store.connectionNow()?.health in UNFIXED_HEALTHS) return AddResult.Full

        // This add must never be matched to an item the house just ticked off (10 §4.6).
        val ticking = queue.filter { it.kind == QueuedKind.TICK.name && textKey(it.text) == key }.mapNotNull { it.itemId }
        val undoing = _undoable.value?.takeIf { textKey(it.text) == key }?.let { it.itemId ?: store.idOfSentAdd(it.clientKey) }
        store.enqueue(QueuedKind.ADD, newShoppingKey(), null, cleaned, now, joinIds((ticking + listOfNotNull(undoing)).toSet()))
        recentAdds.addLast(now)
        learning.added(cleaned)
        loop.wake()
        return AddResult.Added
    }

    /** Ticks [row] off: queued at once, with an Undo for [TOAST_WITH_ACTION_MS]. A newer tick takes its place. */
    suspend fun tick(row: ShownItem) {
        val latest = lock.withLock { tickLocked(row) } ?: return
        toaster.show(ticked(latest.text), action = ToastAction(UNDO) { scope.launch { undo(latest) } })
    }

    private suspend fun tickLocked(row: ShownItem): Ticked? {
        val queue = store.queueNow()
        val rows = shown(store.items.first(), queue, _undoable.value)
        val index = rows.indexOfFirst { it.key == row.key }
        // The list moved under the tap, or the row is already ticked.
        if (index < 0 || rows[index].ticked) return null
        val queuedAdd = row.key.startsWith(QUEUED_KEY_PREFIX)
        val clientKey = if (queuedAdd) row.key.removePrefix(QUEUED_KEY_PREFIX) else newShoppingKey()
        val itemId = if (queuedAdd) null else row.key
        val now = clock.nowMillis()
        store.enqueue(QueuedKind.TICK, clientKey, itemId, row.text, now)
        learning.ticked(row.text)
        val latest = Ticked(row.key, itemId, clientKey, row.text, index, now)
        _undoable.value = latest
        undoClear?.cancel()
        undoClear = scope.launch {
            delay(TOAST_WITH_ACTION_MS)
            _undoable.update { if (it === latest) null else it }
        }
        loop.wake()
        return latest
    }

    /** Takes back [latest]: its tick is dropped if still queued, else an untick is queued. Only the latest tick can be taken back. */
    internal suspend fun undo(latest: Ticked) = lock.withLock {
        if (_undoable.value != latest) return@withLock
        _undoable.value = null
        val queue = store.queueNow()
        val queued = queue.firstOrNull { it.kind == QueuedKind.TICK.name && it.clientKey == latest.clientKey }
        // A tick on a queued add had no id until the add was sent.
        val itemId = latest.itemId ?: queued?.itemId ?: store.idOfSentAdd(latest.clientKey)
        // Putting it back as it was: an add made after the tick would leave a second one.
        if (itemId != null) {
            queue.filter { it.kind == QueuedKind.ADD.name && itemId in splitIds(it.excludeIds) }.forEach { store.drop(it.seq) }
        }
        if (queued != null) {
            store.drop(queued.seq)
        } else if (itemId != null) {
            store.enqueue(QueuedKind.UNTICK, newShoppingKey(), itemId, latest.text, clock.nowMillis())
            loop.wake()
        }
    }

    private fun flash(key: String) {
        _flashing.value = key
        flashClear?.cancel()
        flashClear = scope.launch {
            delay(FLASH_MS)
            _flashing.update { if (it == key) null else it }
        }
    }
}
