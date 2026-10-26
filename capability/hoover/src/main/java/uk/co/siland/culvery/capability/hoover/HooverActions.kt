package uk.co.siland.culvery.capability.hoover

import javax.inject.Inject
import javax.inject.Singleton
import uk.co.siland.culvery.core.access.AccessControl

/** What the Hoover row does (11 §4.4): Start asks for a fresh Adult or Admin PIN each time; 11b's scheduled clean calls the system directly and asks none. */
@Singleton
class HooverActions @Inject constructor(
    private val access: AccessControl,
    private val systems: Set<@JvmSuppressWildcards HooverSystem>,
) {
    /** Null when the PIN pad was cancelled or refused, or no hoover is chosen; nothing was started then. */
    suspend fun start(): HooverResult? {
        val system = systems.firstOrNull() ?: return null
        access.authorise(HooverPermissions.START) ?: return null
        return system.start()
    }
}
