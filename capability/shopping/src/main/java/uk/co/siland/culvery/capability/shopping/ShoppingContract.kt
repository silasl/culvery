package uk.co.siland.culvery.capability.shopping

import androidx.compose.runtime.Composable
import java.time.Instant
import kotlinx.coroutines.flow.Flow
import uk.co.siland.culvery.core.plugin.Connection
import uk.co.siland.culvery.core.plugin.ProviderDescriptor

/** The one shopping capability's id, its rail tab and its alerts. */
const val SHOPPING_ID = "shopping"

/** The [Connection.config] key a provider keeps the chosen account under, when it has accounts. */
const val SHOPPING_CONFIG_ACCOUNT = "account"

/** The most unsent changes the queue holds (10 §4.11). */
const val QUEUE_MAX = 300

/** One item on the list. [changedAt] is when it was last changed; for a history item, when it was ticked. */
data class ShoppingItem(val id: String, val text: String, val changedAt: Instant) {
    override fun toString(): String = "ShoppingItem(id=$id, changedAt=$changedAt)"
}

/** Which accounts a provider can be turned on for, or why it can't yet (10 §4.10). */
sealed interface ProviderAccounts {
    /** An empty list means the provider has no account to pick. */
    data class Ready(val accounts: List<String>) : ProviderAccounts {
        // Accounts are email addresses, which show on Settings only.
        override fun toString() = "Ready(accounts=${accounts.size})"
    }

    /** [line] is what to tell the user, e.g. to connect the calendar first. */
    data class Blocked(val line: String) : ProviderAccounts
}

/** The list was deleted where it lives. */
class ListGoneException(message: String? = null, cause: Throwable? = null) : Exception(message, cause)

/** The account the list belongs to was disconnected, or no longer has the list's permission. */
class AccountGoneException(message: String? = null, cause: Throwable? = null) : Exception(message, cause)

/** A tick or untick named an item the list no longer has. */
class ItemGoneException(message: String? = null, cause: Throwable? = null) : Exception(message, cause)

/** The provider refused an add, tick or untick for good reason, so retrying alone will not help. */
class ShoppingRejectedException(message: String? = null, cause: Throwable? = null) : Exception(message, cause)

/** What an unsent change does. */
enum class QueuedKind { ADD, TICK, UNTICK }

/** How the list stands, worked out by the capability from what the provider throws. */
enum class ShoppingHealth { OK, CANT_REACH, NEEDS_PERMISSION, LIST_GONE, ACCOUNT_GONE, REFUSED }

/**
 * A shopping-list service (Google Tasks, Todoist…). Nothing service-specific crosses this seam: the list's own id
 * lives in [Connection.config], which the capability never reads.
 *
 * Every call is main-safe and cancellable. They throw only [uk.co.siland.culvery.core.plugin.NeedsSignInException],
 * [uk.co.siland.culvery.core.plugin.UnreachableException] (and its [uk.co.siland.culvery.core.plugin.OfflineException]),
 * [ListGoneException], [AccountGoneException], [ItemGoneException] (tick and untick only) and
 * [ShoppingRejectedException]. [items] is the open items in the provider's order. [history] is the items ticked since
 * its `since`, each [ShoppingItem.changedAt] being when. Nothing deletes.
 */
interface ShoppingProvider {
    val descriptor: ProviderDescriptor

    /** Which accounts it can be turned on for, or why it can't yet (10 §4.10). Empty Ready means no account to pick. */
    val accounts: Flow<ProviderAccounts>

    /** The fix shown when [AccountGoneException] is thrown. */
    val accountGoneLine: String

    @Composable
    fun ConnectScreen(account: String?, existing: Connection?, onConnected: (Connection) -> Unit, onCancel: () -> Unit)

    suspend fun remakeList(conn: Connection): Connection

    suspend fun items(conn: Connection): List<ShoppingItem>

    suspend fun add(conn: Connection, text: String, clientKey: String): ShoppingItem

    suspend fun tick(conn: Connection, id: String)

    suspend fun untick(conn: Connection, id: String)

    suspend fun history(conn: Connection, since: Instant): List<ShoppingItem>
}

/** The bound provider [connection] was made with; null when it is gone from the build. */
internal fun Iterable<ShoppingProvider>.forConnection(connection: Connection): ShoppingProvider? =
    firstOrNull { it.descriptor.id == connection.providerId }
