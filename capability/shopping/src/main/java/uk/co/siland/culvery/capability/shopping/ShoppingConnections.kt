package uk.co.siland.culvery.capability.shopping

import android.util.Log
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import uk.co.siland.culvery.core.access.AccessControl
import uk.co.siland.culvery.core.access.mayManageConnections
import uk.co.siland.culvery.core.plugin.AccountUse
import uk.co.siland.culvery.core.plugin.ApplicationScope
import uk.co.siland.culvery.core.plugin.Connection
import uk.co.siland.culvery.core.plugin.Toaster
import uk.co.siland.culvery.core.plugin.UnreachableException
import uk.co.siland.culvery.core.plugin.attempt
import uk.co.siland.culvery.core.plugin.couldNotConnect

private const val TAG = "ShoppingConnections"

/**
 * Turning the shopping list on and off (10 §4.10, §4.11): the Admin check, and finishing what a provider's screen
 * connected. Connecting, stopping and clearing what was learned each need an Admin's fresh PIN.
 */
@Singleton
class ShoppingConnections @Inject constructor(
    private val store: ShoppingStore,
    private val providers: Set<@JvmSuppressWildcards ShoppingProvider>,
    private val sync: ShoppingSync,
    private val lock: ShoppingLock,
    private val loop: ShoppingDrainLoop,
    private val learning: ShoppingLearning,
    private val access: AccessControl,
    private val toaster: Toaster,
    @ApplicationScope private val scope: CoroutineScope,
) {
    /** The one provider Settings offers: the debug sample when one is bound, else the first (10 §5, ruling 24). */
    val offered: ShoppingProvider? = providers.firstOrNull { !it.descriptor.userConnectable } ?: providers.firstOrNull()

    /** connections.manage: an Admin's fresh PIN every time. */
    suspend fun mayConnect(): Boolean = access.mayManageConnections()

    /**
     * Stores what the provider's screen connected, on the application scope so leaving Settings can't cancel it, then
     * reads the list once, which seeds the favourites. The check waits for the lock to be free: it takes the lock itself.
     */
    fun connected(connection: Connection) {
        scope.launch {
            lock.withLock { store.connect(connection) }
            readAfterConnecting()
        }
    }

    // A failed first read is made again by the next check.
    private suspend fun readAfterConnecting() {
        attempt({ sync.check() }) { e -> Log.w(TAG, "The first read after connecting failed (${e::class.simpleName})") }
    }

    /** The permission is given again for the same connection: the queue is untouched and drains. */
    fun reallowed(connection: Connection) {
        scope.launch { replace(connection) }
    }

    /** Makes the list again (it was deleted where it lives), keeping what is queued. False if the PIN was refused or Google could not be reached. */
    suspend fun remakeList(): Boolean {
        if (!mayConnect()) return false
        val stored = store.connectionNow() ?: return false
        val provider = providers.forConnection(stored.connection) ?: return false
        return attempt({
            replace(provider.remakeList(stored.connection))
            true
        }) { e ->
            Log.w(TAG, "Couldn't make the list again (${e::class.simpleName})")
            toaster.show(couldNotConnect(provider.descriptor.displayName, unreachable = e is UnreachableException), urgent = true)
            false
        }
    }

    private suspend fun replace(connection: Connection) {
        val replaced = lock.withLock {
            // Stopped since the screens opened: nothing to put back.
            val present = store.connectionNow()?.connection?.id == connection.id
            if (present) {
                store.replaceConnection(connection)
                store.setHealth(ShoppingHealth.OK)
                // What waited out a backoff for the missing permission or list can go now.
                store.makeQueueDue()
            }
            present
        }
        if (replaced) loop.wake()
    }

    /** How many unsent changes stopping would lose. */
    suspend fun queued(): Int = store.queueNow().size

    /** The connection, the last read and the queue go; what was learned stays. */
    suspend fun stop(): Boolean {
        if (!mayConnect()) return false
        val service = store.connectionNow()?.let { stored ->
            providers.forConnection(stored.connection)?.descriptor?.displayName ?: stored.connection.label
        }
        lock.withLock { store.stop() }
        if (service != null) toaster.show("Stopped using $service.")
        return true
    }

    suspend fun forget(item: Learned): Boolean {
        if (!mayConnect()) return false
        learning.forget(item.key)
        toaster.show("Forgot ${item.text}.")
        return true
    }

    suspend fun clearSuggestions(): Boolean {
        if (!mayConnect()) return false
        learning.clear()
        toaster.show("Suggestions cleared.")
        return true
    }
}

/** Shopping uses the account its stored connection names (10 §4.10). */
class ShoppingAccountUse @Inject constructor(private val store: ShoppingStore) : AccountUse {
    override suspend fun uses(account: String): Boolean =
        store.connectionNow()?.connection?.config?.get(SHOPPING_CONFIG_ACCOUNT)?.equals(account, ignoreCase = true) == true
}
