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
import uk.co.siland.culvery.capability.shopping.db.QueuedEntity
import uk.co.siland.culvery.capability.shopping.db.ShoppingDatabase
import uk.co.siland.culvery.core.plugin.Connection

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

    /** Replaces any connection, healthy and not yet seeded. The old read goes; the queue stays, so nothing added is lost. */
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

    /** The row went through. An add's [itemId] reaches every later row of the same client key. */
    suspend fun sent(seq: Long, itemId: String?) = db.withTransaction {
        val row = dao.queued(seq)
        dao.deleteQueued(seq)
        if (row != null && itemId != null) dao.setItemIdAfter(row.clientKey, seq, itemId)
    }

    suspend fun failed(seq: Long, nextAtMillis: Long) = dao.markFailed(seq, nextAtMillis)

    suspend fun drop(seq: Long) = dao.deleteQueued(seq)
}

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
