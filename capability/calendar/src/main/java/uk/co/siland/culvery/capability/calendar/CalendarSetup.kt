package uk.co.siland.culvery.capability.calendar

import javax.inject.Inject
import javax.inject.Singleton
import uk.co.siland.culvery.core.plugin.Connection

/** Adds a provider connection with its sources. Used by the debug seed now and by setup/settings in Plan 4. */
@Singleton
class CalendarSetup @Inject constructor(
    private val store: CalendarStore,
    private val providers: Set<@JvmSuppressWildcards CalendarProvider>,
) {
    suspend fun hasConnections(): Boolean = store.connectionsNow().isNotEmpty()

    /** Sources missing from [mapping] show as Family. */
    suspend fun connect(connection: Connection, mapping: Map<String, SourceMapping>) {
        val provider = providers.firstOrNull { it.descriptor.id == connection.providerId }
            ?: throw IllegalArgumentException("No calendar provider ${connection.providerId}")
        store.addConnection(connection, provider.sources(connection), mapping)
    }
}
