package uk.co.siland.culvery.capability.calendar

import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.first
import uk.co.siland.culvery.core.plugin.Connection
import uk.co.siland.culvery.core.plugin.Feature

/**
 * Adds provider connections and chooses the master calendar. Used by the debug seed now and by setup and
 * settings in Plan 4.
 */
@Singleton
class CalendarSetup(
    private val store: CalendarStore,
    private val providers: Set<@JvmSuppressWildcards CalendarProvider>,
    private val requestSync: () -> Unit = {},
) {
    @Inject
    constructor(store: CalendarStore, providers: Set<@JvmSuppressWildcards CalendarProvider>, loop: CalendarSyncLoop) :
        this(store, providers, loop::requestSync)

    suspend fun hasConnections(): Boolean = store.connectionsNow().isNotEmpty()

    /** Sources missing from [mapping] show as Family. */
    suspend fun connect(connection: Connection, mapping: Map<String, SourceMapping>) {
        val provider = providers.firstOrNull { it.descriptor.id == connection.providerId }
            ?: throw IllegalArgumentException("No calendar provider ${connection.providerId}")
        store.addConnection(connection, provider.sources(connection), mapping)
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
        val provider = providers.firstOrNull { it.descriptor.id == connection.providerId }
            ?: throw IllegalArgumentException("No calendar provider ${connection.providerId}")
        require(Feature.WRITE in provider.descriptor.features) { "${provider.descriptor.displayName} can't write" }
        val source = provider.sources(connection).firstOrNull { it.id == sourceId }
            ?: throw IllegalArgumentException("No source $sourceId in ${connection.label}")
        require(source.writable) { "${source.name} is read-only" }
        store.setMaster(connectionId, sourceId)
        requestSync()
    }

    /** Asks the sync loop for a pass now. */
    fun syncSoon() = requestSync()
}
