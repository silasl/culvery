package uk.co.siland.culvery.capability.calendar

import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import uk.co.siland.culvery.core.access.AccessControl
import uk.co.siland.culvery.core.access.CorePermissions
import uk.co.siland.culvery.core.plugin.ApplicationScope
import uk.co.siland.culvery.core.plugin.Connection
import uk.co.siland.culvery.core.plugin.ConnectionHealth
import uk.co.siland.culvery.core.ui.Icons

/** A connection as Settings lists it (3a design §4.1): the service's name and icon, and its health. */
data class CalendarRow(
    val connection: Connection,
    val service: String,
    val icon: String,
    val health: ConnectionHealth,
    val lastSyncMillis: Long?,
)

/**
 * Connecting and reconnecting (3a design §3.3), for the Connect card, Settings and the reconnect chip: which providers
 * can be connected, the Admin check, and finishing what a provider's screen connected.
 */
@Singleton
class CalendarConnections @Inject constructor(
    private val store: CalendarStore,
    private val setup: CalendarSetup,
    private val access: AccessControl,
    private val providers: Set<@JvmSuppressWildcards CalendarProvider>,
    @ApplicationScope private val scope: CoroutineScope,
) {
    /** Offered as "Connect {name}": providers a household may connect (not the debug sample) and hasn't yet. */
    val connectable: Flow<List<CalendarProvider>> = store.connections().map { stored ->
        providers
            .filter { p -> p.descriptor.userConnectable && stored.none { it.connection.providerId == p.descriptor.id } }
            .sortedBy { it.descriptor.displayName }
    }

    val rows: Flow<List<CalendarRow>> = store.connections().map { stored ->
        stored.map { s ->
            val icon = provider(s.connection.providerId)?.descriptor?.icon ?: DEFAULT_ICON
            CalendarRow(s.connection, serviceNameOf(s.connection, providers::displayNameOf), icon, s.health, s.lastSyncMillis)
        }
    }

    // One connect or reconnect at a time: a second tap on Connect (the card closes before setup ends) waits, then finds
    // the first connection and reconnects it rather than adding a second (review M2).
    private val finishing = Mutex()

    fun provider(providerId: String): CalendarProvider? = providers.firstOrNull { it.descriptor.id == providerId }

    /** connections.manage: an Admin's fresh PIN every time (4c design D5); the wizard's setup session passes it. */
    suspend fun mayConnect(): Boolean = access.authorise(CorePermissions.CONNECTIONS_MANAGE) != null

    /** Stores what the provider's screen connected, on the application scope, so closing the card can't cancel it. */
    fun finish(connection: Connection, reconnecting: Boolean) {
        scope.launch {
            finishing.withLock { if (reconnecting) setup.reconnect(connection) else setup.connectWithDefaults(connection) }
        }
    }

    private companion object {
        const val DEFAULT_ICON = Icons.CALENDAR_MONTH
    }
}
