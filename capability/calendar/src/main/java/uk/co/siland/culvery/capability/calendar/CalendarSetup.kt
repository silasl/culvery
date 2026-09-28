package uk.co.siland.culvery.capability.calendar

import android.util.Log
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.coroutines.CoroutineContext
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import uk.co.siland.culvery.core.household.HouseholdRepository
import uk.co.siland.culvery.core.household.Person
import uk.co.siland.culvery.core.plugin.Connection
import uk.co.siland.culvery.core.plugin.ConnectionHealth
import uk.co.siland.culvery.core.plugin.Feature
import uk.co.siland.culvery.core.plugin.Toaster
import uk.co.siland.culvery.core.plugin.WallClock

/** 3a design §3.3 toasts. */
fun connected(service: String): String = "$service connected"

fun reconnected(service: String): String = "$service reconnected"

fun couldNotConnect(service: String): String = "Couldn't connect to $service — try again"

/**
 * Adds and reconnects provider connections and chooses the master calendar: Settings, the Connect card and the
 * reconnect chip (3a design §3.3), and the debug seed. Every read of a provider goes through [callReader], on [io]
 * under [timeoutMillis].
 */
@Singleton
class CalendarSetup(
    private val store: CalendarStore,
    private val providers: Set<@JvmSuppressWildcards CalendarProvider>,
    private val people: suspend () -> List<Person>,
    private val toaster: Toaster,
    private val clock: WallClock,
    private val io: CoroutineContext = Dispatchers.IO,
    private val timeoutMillis: Long = PROVIDER_TIMEOUT_MS,
    private val requestSync: () -> Unit = {},
) {
    @Inject
    constructor(
        store: CalendarStore,
        providers: Set<@JvmSuppressWildcards CalendarProvider>,
        household: HouseholdRepository,
        toaster: Toaster,
        clock: WallClock,
        loop: CalendarSyncLoop,
    ) : this(store, providers, { household.people.first() }, toaster, clock, Dispatchers.IO, PROVIDER_TIMEOUT_MS, loop::requestSync)

    suspend fun hasConnections(): Boolean = store.connectionsNow().isNotEmpty()

    /** Every connection's id, as it changes. */
    fun connectionIds(): Flow<List<String>> = store.connectionIds()

    /** A connection with everything it holds (3a design D5: the debug sample once a real calendar is connected). */
    suspend fun removeConnection(connectionId: String) = store.removeConnection(connectionId)

    /** Sources missing from [mapping] show as Family. */
    suspend fun connect(connection: Connection, mapping: Map<String, SourceMapping>) {
        val provider = providerFor(connection.providerId)
        store.addConnection(connection, callProvider { provider.sources(connection) }, mapping)
    }

    /**
     * Connects with automatic setup (3a design D4): every source, mapped by [defaultMapping] from the household's
     * people, and the primary as the master; then a sync. Toasts the outcome. A failure stores nothing. Returns whether
     * the connection was stored. The same provider and account already connected (a second tap on Connect, while the
     * first was still setting up) is reconnected instead, never added twice (review M2); CalendarConnections runs one
     * connect at a time, so the second sees the first.
     */
    suspend fun connectWithDefaults(connection: Connection): Boolean {
        alreadyConnected(connection)?.let { return reconnect(it) }
        val provider = providerFor(connection.providerId)
        val service = provider.descriptor.displayName
        val stored = try {
            val sources = callProvider { provider.sources(connection) }
            val household = people()
            val master = sources.firstOrNull { it.primary && it.writable }?.id
            store.addConnection(connection, sources, sources.associate { it.id to defaultMapping(it, household) }, master)
            true
        } catch (e: CancellationException) {
            throw e
        } catch (e: Throwable) {
            Log.w(TAG, "Couldn't connect ${connection.providerId}", e)
            false
        }
        if (stored) {
            requestSync()
            toaster.show(connected(service))
        } else {
            toaster.show(couldNotConnect(service))
        }
        return stored
    }

    /**
     * Reconnect (3a design D6): the same connection, healthy again, so its sign-in pause ends (D16); its queued
     * changes are due now (m4) and a sync is asked for. Returns whether it was recorded.
     */
    suspend fun reconnect(connection: Connection): Boolean {
        val service = providerFor(connection.providerId).descriptor.displayName
        return try {
            val now = clock.nowMillis()
            store.setHealth(connection.id, ConnectionHealth.Ok, now)
            store.makeDue(connection.id, now)
            requestSync()
            toaster.show(reconnected(service))
            true
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.w(TAG, "Couldn't record ${connection.id} as reconnected", e)
            toaster.show(couldNotConnect(service))
            false
        }
    }

    /** The stored connection for [connection]'s provider and account, if there is one. */
    private suspend fun alreadyConnected(connection: Connection): Connection? {
        val account = connection.config[CONFIG_ACCOUNT] ?: return null
        return store.connectionsNow().map { it.connection }.firstOrNull {
            it.providerId == connection.providerId && it.config[CONFIG_ACCOUNT].equals(account, ignoreCase = true)
        }
    }

    /** The household's master calendar; null until one is chosen. */
    suspend fun master(): StoredSource? = store.master().first()

    /**
     * Makes [sourceId] the master calendar, the only one the tablet writes to (spec §6). The provider must declare
     * WRITE and report the source as writable now. That is recorded too, because an install from before 2b-1
     * stored every source's writability as it was then.
     */
    suspend fun setMaster(connectionId: String, sourceId: String) {
        val connection = store.connectionsNow().firstOrNull { it.connection.id == connectionId }?.connection
            ?: throw IllegalArgumentException("No connection $connectionId")
        val provider = providerFor(connection.providerId)
        require(Feature.WRITE in provider.descriptor.features) { "${provider.descriptor.displayName} can't write" }
        val source = callProvider { provider.sources(connection) }.firstOrNull { it.id == sourceId }
            ?: throw IllegalArgumentException("No source $sourceId in ${connection.label}")
        require(source.writable) { "${source.name} is read-only" }
        store.setMaster(connectionId, sourceId)
        requestSync()
    }

    /** Asks the sync loop for a pass now. */
    fun syncSoon() = requestSync()

    private fun providerFor(providerId: String): CalendarProvider =
        providers.firstOrNull { it.descriptor.id == providerId } ?: throw IllegalArgumentException("No calendar provider $providerId")

    private suspend fun <T> callProvider(block: suspend () -> T): T = callReader(io, timeoutMillis, block).getOrThrow()

    private companion object {
        const val TAG = "CalendarSetup"
    }
}
