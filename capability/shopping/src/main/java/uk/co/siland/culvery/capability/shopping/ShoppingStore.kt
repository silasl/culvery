package uk.co.siland.culvery.capability.shopping

import androidx.room.withTransaction
import java.time.Instant
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import org.json.JSONObject
import uk.co.siland.culvery.capability.shopping.db.ConnectionEntity
import uk.co.siland.culvery.capability.shopping.db.ItemEntity
import uk.co.siland.culvery.capability.shopping.db.LearnedEntity
import uk.co.siland.culvery.capability.shopping.db.SeenEntity
import uk.co.siland.culvery.capability.shopping.db.QueuedEntity
import uk.co.siland.culvery.capability.shopping.db.ShoppingDatabase
import uk.co.siland.culvery.core.plugin.Connection
import uk.co.siland.culvery.core.plugin.textKey

/** The stored connection with how it stands and what has been read. */
data class StoredShopping(
    val connection: Connection,
    val health: ShoppingHealth,
    val lastReadMillis: Long?,
    val lastHistoryMillis: Long?,
    val seeded: Boolean,
) {
    // The connection's config holds the account's email.
    override fun toString() = "StoredShopping(id=${connection.id}, health=$health, seeded=$seeded)"
}

/** The only writer of shopping.db. Callers hold [ShoppingLock] (10 §4.5). */
@Singleton
class ShoppingStore @Inject constructor(private val db: ShoppingDatabase) {
    private val dao = db.shoppingDao()

    val connection: Flow<StoredShopping?> = dao.connection().map { it?.toStored() }

    suspend fun connectionNow(): StoredShopping? = dao.connectionNow()?.toStored()

    /** Replaces any connection, healthy and not yet seeded. The old read goes, and so does any queue left over: off means an empty queue. */
    suspend fun connect(connection: Connection) = db.withTransaction {
        dao.putConnection(
            ConnectionEntity(
                id = connection.id,
                providerId = connection.providerId,
                label = connection.label,
                configJson = encodeConfig(connection.config),
                health = ShoppingHealth.OK.name,
                lastReadMillis = null,
                lastHistoryMillis = null,
                seeded = false,
            ),
        )
        dao.deleteItems()
        dao.deleteQueue()
    }

    /** The same connection with new settings (a remade list); the read, the health and the queue stay. */
    suspend fun replaceConnection(connection: Connection) {
        val count = dao.updateConnection(connection.id, connection.providerId, connection.label, encodeConfig(connection.config))
        check(count == 1) { "replaceConnection: no stored connection with that id" }
    }

    /** Stops using the list: the connection, what was read and the queue go; what was learned stays. */
    suspend fun stop() = db.withTransaction {
        dao.deleteConnection()
        dao.deleteItems()
        dao.deleteQueue()
    }

    /** The open items as last read, in the provider's order. */
    val items: Flow<List<ShoppingItem>> = dao.items().map { rows -> rows.map { it.toItem() } }

    /** The open items as the provider listed them, replacing the last read in one transaction. */
    suspend fun saveRead(items: List<ShoppingItem>, health: ShoppingHealth, atMillis: Long) = db.withTransaction {
        dao.deleteItems()
        dao.insertItems(items.mapIndexed { position, item -> ItemEntity(item.id, item.text, position, item.changedAt.toEpochMilli()) })
        dao.setRead(health.name, atMillis)
    }

    suspend fun setHealth(health: ShoppingHealth) = dao.setHealth(health.name)

    suspend fun setHistoryRead(atMillis: Long) = dao.setHistoryRead(atMillis)

    suspend fun markSeeded() = dao.markSeeded()

    val queue: Flow<List<QueuedEntity>> = dao.queue()

    suspend fun queueNow(): List<QueuedEntity> = dao.queueNow()

    suspend fun enqueue(
        kind: QueuedKind,
        clientKey: String,
        itemId: String?,
        text: String,
        atMillis: Long,
        excludeIds: String? = null,
    ): Long = dao.insertQueued(
        QueuedEntity(
            kind = kind.name,
            clientKey = clientKey,
            itemId = itemId,
            text = text,
            madeAtMillis = atMillis,
            attempts = 0,
            nextAtMillis = 0,
            excludeIds = excludeIds,
        ),
    )

    /** The row went through. An add's [itemId] reaches every later row of the same client key, and is kept for [idOfSentAdd]. */
    suspend fun sent(seq: Long, itemId: String?) = db.withTransaction {
        val row = dao.queued(seq)
        dao.deleteQueued(seq)
        if (row != null && itemId != null) {
            dao.setItemIdAfter(row.clientKey, seq, itemId)
            if (row.kind == QueuedKind.ADD.name) rememberSentAdd(row.clientKey, itemId)
        }
    }

    /** Runs [block] as one transaction, so a row leaving the queue and the read following it are seen together. */
    suspend fun <T> inTransaction(block: suspend () -> T): T = db.withTransaction { block() }

    // Only the latest tick can be undone, so a few recent adds are enough; a restart forgets them with the Undo itself.
    private val sentAdds = object : LinkedHashMap<String, String>() {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, String>) = size > SENT_ADDS_KEPT
    }

    private fun rememberSentAdd(clientKey: String, itemId: String) = synchronized(sentAdds) { sentAdds[clientKey] = itemId }

    /** The id the add of [clientKey] got when it was sent, while that is recent; null if it is not known. */
    fun idOfSentAdd(clientKey: String): String? = synchronized(sentAdds) { sentAdds[clientKey] }

    /** The queued adds of [text]'s key made after [afterSeq] must never match [itemId], which a tick just put off the list. */
    suspend fun excludeFromLaterAdds(afterSeq: Long, text: String, itemId: String) = db.withTransaction {
        val key = textKey(text)
        for (row in dao.queueNow()) {
            if (row.kind != QueuedKind.ADD.name || row.seq <= afterSeq || textKey(row.text) != key) continue
            dao.setExcluded(row.seq, joinIds(splitIds(row.excludeIds) + itemId))
        }
    }

    /** Every queued change is due now, as when what held them back has been fixed. Nothing is dropped. */
    suspend fun makeQueueDue() = dao.makeAllDue()

    suspend fun failed(seq: Long, nextAtMillis: Long) = dao.markFailed(seq, nextAtMillis)

    suspend fun drop(seq: Long) = dao.deleteQueued(seq)

    val learned: Flow<List<Learned>> = dao.learned().map { rows -> rows.map { Learned(it.key, it.text, it.count, it.lastMillis) } }

    /** One use of [text] at [atMillis]: counts unless that key was counted in the 24 h before (10 §4.7). */
    suspend fun learnUse(text: String, atMillis: Long) = db.withTransaction { use(text, atMillis) }

    /** Counts each item whose id was never seen, dated by its own change so a year of history counts per day. */
    suspend fun learnSeen(items: List<ShoppingItem>, nowMillis: Long) = db.withTransaction {
        for (item in items.sortedBy { it.changedAt }) {
            if (dao.insertSeenIfNew(SeenEntity(item.id, nowMillis)) != -1L) use(item.text, item.changedAt.toEpochMilli())
        }
    }

    suspend fun markSeen(id: String, atMillis: Long) {
        dao.insertSeenIfNew(SeenEntity(id, atMillis))
    }

    /** Moves the date of a key already learned; an unknown key stays unknown. */
    suspend fun touchLearned(text: String, atMillis: Long) = dao.touchLearned(textKey(text), atMillis)

    suspend fun forgetLearned(key: String) = dao.deleteLearned(key)

    /** Every learned row goes; `seen` stays, so what is open is not counted again. */
    suspend fun clearLearned() = dao.deleteAllLearned()

    suspend fun pruneUnusedBefore(cutoffMillis: Long) = db.withTransaction {
        dao.pruneLearned(cutoffMillis)
        dao.pruneSeen(cutoffMillis)
    }

    private suspend fun use(text: String, atMillis: Long) {
        val key = textKey(text)
        if (key.isEmpty()) return
        val shown = text.trim()
        val old = dao.learnedRow(key)
        val row = if (old == null) {
            LearnedEntity(key, shown, 1, atMillis, atMillis)
        } else {
            val counts = atMillis - old.countedMillis >= COUNT_ONCE_PER_MS
            LearnedEntity(
                key = key,
                text = betterText(old.text, shown),
                count = if (counts) old.count + 1 else old.count,
                lastMillis = maxOf(old.lastMillis, atMillis),
                countedMillis = if (counts) atMillis else old.countedMillis,
            )
        }
        dao.upsertLearned(row)
        dao.trimLearned(LEARNED_MAX)
    }
}

private const val SENT_ADDS_KEPT = QUEUE_MAX

/** An add's [QueuedEntity.excludeIds], as the ids it holds. */
internal fun splitIds(excludeIds: String?): Set<String> = excludeIds?.split(',')?.filter { it.isNotEmpty() }?.toSet() ?: emptySet()

internal fun joinIds(ids: Set<String>): String? = ids.takeIf { it.isNotEmpty() }?.joinToString(",")

private fun ConnectionEntity.toStored() = StoredShopping(
    connection = Connection(id, providerId, label, decodeConfig(configJson)),
    health = ShoppingHealth.valueOf(health),
    lastReadMillis = lastReadMillis,
    lastHistoryMillis = lastHistoryMillis,
    seeded = seeded,
)

private fun ItemEntity.toItem() = ShoppingItem(id, text, Instant.ofEpochMilli(changedAtMillis))

private fun encodeConfig(config: Map<String, String>): String = JSONObject(config).toString()

private fun decodeConfig(json: String): Map<String, String> {
    val o = JSONObject(json)
    return o.keys().asSequence().associateWith { o.getString(it) }
}
