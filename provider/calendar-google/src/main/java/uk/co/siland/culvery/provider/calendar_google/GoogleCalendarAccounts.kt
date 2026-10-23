package uk.co.siland.culvery.provider.calendar_google

import javax.inject.Inject
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import uk.co.siland.culvery.capability.calendar.CONFIG_ACCOUNT
import uk.co.siland.culvery.capability.calendar.CalendarStore
import uk.co.siland.culvery.capability.calendar.StoredConnection
import uk.co.siland.culvery.core.google.GoogleAccounts

/** The accounts of the calendar's Google connections (10 §4.1). */
class GoogleCalendarAccounts internal constructor(connections: Flow<List<StoredConnection>>) : GoogleAccounts {
    @Inject constructor(store: CalendarStore) : this(store.connections())

    override val connected: Flow<List<String>> = connections.map { stored ->
        stored.filter { it.connection.providerId == GOOGLE_PROVIDER_ID }
            .mapNotNull { it.connection.config[CONFIG_ACCOUNT] }
            .distinct()
    }
}
