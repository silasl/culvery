package uk.co.siland.culvery.capability.shopping

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import java.time.Instant
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import uk.co.siland.culvery.core.plugin.Connection
import uk.co.siland.culvery.core.plugin.Feature
import uk.co.siland.culvery.core.plugin.ProviderDescriptor
import uk.co.siland.culvery.core.plugin.UnreachableException
import uk.co.siland.culvery.core.plugin.WallClock
import uk.co.siland.culvery.core.ui.Icons

val SAMPLE_DESCRIPTOR = ProviderDescriptor(
    id = "shopping.sample",
    displayName = "the sample list",
    icon = Icons.SHOPPING_CART,
    features = setOf(Feature.READ, Feature.WRITE),
    userConnectable = false,
)

/**
 * An in-memory list with a history, for tests and the debug build. Ids run "g1", "g2"… in the order made. The failure
 * switches are checked after the call is recorded in [calls].
 */
class SampleShoppingProvider(
    open: List<String> = emptyList(),
    ticked: List<String> = emptyList(),
    private val clock: WallClock,
    override val descriptor: ProviderDescriptor = SAMPLE_DESCRIPTOR,
    override val accounts: Flow<ProviderAccounts> = flowOf(ProviderAccounts.Ready(emptyList())),
    override val accountGoneLine: String = "The sample account was disconnected.",
) : ShoppingProvider {

    /** The next call fails with this, once. */
    var failNext: Exception? = null

    /** Every call fails with this until [remakeList]. */
    var failAll: Exception? = null

    /** The next add, tick or untick applies, then throws [UnreachableException]. */
    var failAfterWrite: Boolean = false

    /** A tick suspends on this before applying. */
    var tickGate: CompletableDeferred<Unit>? = null

    private val _calls = mutableListOf<String>()

    /** What was called, such as "items", "add", "tick g1", "history". */
    val calls: List<String> get() = _calls

    private var made = 0
    private val openItems = mutableListOf<ShoppingItem>()
    private val history = mutableListOf<ShoppingItem>()

    init {
        open.forEach { openItems += make(it, now()) }
        ticked.forEach { history += make(it, now()) }
    }

    private fun now(): Instant = Instant.ofEpochMilli(clock.nowMillis())

    private fun make(text: String, at: Instant) = ShoppingItem("g${++made}", text, at)

    /** An item already ticked at [at], as if ticked on a phone. */
    fun tickedOn(text: String, at: Instant) {
        history += make(text, at)
    }

    /** An item added on a phone: first on the list, with no call recorded. */
    fun addFromAPhone(text: String) {
        openItems.add(0, make(text, now()))
    }

    private fun begin(call: String) {
        _calls += call
        failAll?.let { throw it }
        failNext?.let {
            failNext = null
            throw it
        }
    }

    private fun afterWrite() {
        if (failAfterWrite) {
            failAfterWrite = false
            throw UnreachableException("the sample write was cut off")
        }
    }

    @Composable
    override fun ConnectScreen(account: String?, existing: Connection?, onConnected: (Connection) -> Unit, onCancel: () -> Unit) {
        LaunchedEffect(Unit) {
            val config = if (account != null) mapOf(SHOPPING_CONFIG_ACCOUNT to account) else emptyMap()
            onConnected(Connection("c1", descriptor.id, descriptor.displayName, config))
        }
    }

    override suspend fun remakeList(conn: Connection): Connection {
        _calls += "remakeList"
        failAll = null
        openItems.clear()
        history.clear()
        return conn.copy(config = conn.config + ("list" to "new"))
    }

    override suspend fun items(conn: Connection): List<ShoppingItem> {
        begin("items")
        return openItems.toList()
    }

    override suspend fun add(conn: Connection, text: String, clientKey: String): ShoppingItem {
        begin("add")
        val item = make(text, now())
        openItems.add(0, item)
        afterWrite()
        return item
    }

    override suspend fun tick(conn: Connection, id: String) {
        begin("tick $id")
        tickGate?.await()
        val item = openItems.firstOrNull { it.id == id } ?: throw ItemGoneException()
        openItems.remove(item)
        history += item.copy(changedAt = now())
        afterWrite()
    }

    override suspend fun untick(conn: Connection, id: String) {
        begin("untick $id")
        val item = history.firstOrNull { it.id == id } ?: throw ItemGoneException()
        history.remove(item)
        openItems.add(0, item.copy(changedAt = now()))
        afterWrite()
    }

    override suspend fun history(conn: Connection, since: Instant): List<ShoppingItem> {
        begin("history")
        return history.filter { it.changedAt >= since }
    }
}
