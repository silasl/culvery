package uk.co.siland.culvery.capability.shopping

import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import uk.co.siland.culvery.capability.shopping.db.QueuedEntity
import uk.co.siland.culvery.core.plugin.ApplicationScope
import uk.co.siland.culvery.core.plugin.textKey

/** A queued add has no id yet; its row is keyed by its client key after this. */
internal const val QUEUED_KEY_PREFIX = "q:"

/** One row of the list as shown. [key] is the item's id, or "q:" and its client key while its add is queued. */
data class ShownItem(val key: String, val text: String, val ticked: Boolean, val queued: Boolean) {
    override fun toString() = "ShownItem(key=$key, ticked=$ticked, queued=$queued)"
}

/** The latest tick, which Undo can take back for [TOAST_WITH_ACTION_MS][uk.co.siland.culvery.core.plugin.TOAST_WITH_ACTION_MS]. */
internal data class Ticked(
    val key: String,
    val itemId: String?,
    val clientKey: String,
    val text: String,
    val index: Int,
    val atMillis: Long,
) {
    override fun toString() = "Ticked(key=$key, itemId=$itemId, index=$index, atMillis=$atMillis)"
}

/** What the screens show. [open] counts the rows not ticked; [problem] is the health's line or the queue-full line. */
data class ShoppingState(
    val items: List<ShownItem>,
    val open: Int,
    val health: ShoppingHealth,
    val lastReadMillis: Long?,
    val service: String,
    val problem: String?,
    val flashing: String?,
)

/**
 * The last read with what is still queued laid over it (10 §4.4): queued adds first, newest first; a read item the queue
 * is about to match is left out; a row with a queued tick is hidden, except the one [undoable] can still take back,
 * which shows ticked at its place.
 */
internal fun shown(read: List<ShoppingItem>, queue: List<QueuedEntity>, undoable: Ticked?): List<ShownItem> {
    val adds = queue.filter { it.kind == QueuedKind.ADD.name }
    val addKeys = adds.map { textKey(it.text) }.toSet()
    val tickedIds = queue.filter { it.kind == QueuedKind.TICK.name }.mapNotNull { it.itemId }.toSet()
    val tickedClients = queue.filter { it.kind == QueuedKind.TICK.name && it.itemId == null }.map { it.clientKey }.toSet()
    val readIds = read.map { it.id }.toSet()

    val comingBack = queue
        .filter { it.kind == QueuedKind.UNTICK.name && it.itemId !in readIds && it.itemId !in tickedIds }
        .map { ShownItem(it.itemId!!, it.text, ticked = undoable?.itemId == it.itemId, queued = true) }
        .asReversed()
    val queuedAdds = adds.asReversed().mapNotNull { add ->
        val key = QUEUED_KEY_PREFIX + add.clientKey
        val isTicked = add.clientKey in tickedClients
        when {
            !isTicked -> ShownItem(key, add.text, ticked = false, queued = true)
            undoable?.key == key -> ShownItem(key, add.text, ticked = true, queued = true)
            else -> null
        }
    }
    val fromRead = read.mapNotNull { item ->
        when {
            undoable?.itemId == item.id -> ShownItem(item.id, item.text, ticked = true, queued = false)
            textKey(item.text) in addKeys || item.id in tickedIds -> null
            else -> ShownItem(item.id, item.text, ticked = false, queued = false)
        }
    }
    val rows = comingBack + queuedAdds + fromRead
    if (undoable == null || rows.any { it.key == undoable.key || (undoable.itemId != null && it.key == undoable.itemId) }) return rows
    val back = ShownItem(undoable.key, undoable.text, ticked = true, queued = false)
    return rows.toMutableList().apply { add(undoable.index.coerceIn(0, size), back) }
}

/** The list as the Home card and the Shopping tab show it; null until the store answers, and with no list connected. */
@Singleton
class ShoppingView @Inject constructor(
    store: ShoppingStore,
    actions: ShoppingActions,
    providers: Set<@JvmSuppressWildcards ShoppingProvider>,
    @ApplicationScope scope: CoroutineScope,
) {
    val state: StateFlow<ShoppingState?> = combine(
        store.connection,
        store.items,
        store.queue,
        actions.undoable,
        actions.flashing,
    ) { stored, read, queue, undoable, flashing ->
        if (stored == null) return@combine null
        val provider = providers.firstOrNull { it.descriptor.id == stored.connection.providerId }
        val rows = shown(read, queue, undoable)
        val full = queue.size >= QUEUE_MAX && stored.health in UNFIXED_HEALTHS
        ShoppingState(
            items = rows,
            open = rows.count { !it.ticked },
            health = stored.health,
            lastReadMillis = stored.lastReadMillis,
            service = provider?.descriptor?.displayName ?: stored.connection.label,
            problem = if (full) QUEUE_FULL else provider?.let { problemLine(stored.health, it) },
            flashing = flashing,
        )
    }.stateIn(scope, SharingStarted.Eagerly, null)
}
