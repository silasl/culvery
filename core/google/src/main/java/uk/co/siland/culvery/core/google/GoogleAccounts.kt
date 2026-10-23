package uk.co.siland.culvery.core.google

import kotlinx.coroutines.flow.Flow

/** The Google accounts the household has connected, so a capability can offer them and notice one disconnected (10 §4.1). */
interface GoogleAccounts {
    /** The account emails of the calendar's Google connections, as the calendar store lists them, distinct. */
    val connected: Flow<List<String>>
}
