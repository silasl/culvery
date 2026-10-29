package uk.co.siland.culvery.core.plugin

enum class Feature { READ, WRITE }

/** A provider module's identity. [id] is stable and namespaced by capability, e.g. "calendar.google". */
data class ProviderDescriptor(
    val id: String,
    val displayName: String,
    /** Material Symbols ligature name. */
    val icon: String,
    val features: Set<Feature>,
    /** Offered as "Connect {displayName}" in Settings and on the Connect-a-calendar card; false for the debug sample. */
    val userConnectable: Boolean = true,
    /** The connecting card's second line, e.g. what to choose in the provider's screens; none when it has nothing to add. */
    val connectHint: String? = null,
)

/** The first provider a household may connect to, by its [descriptor], so a debug sample never answers a Find or a typed address. */
fun <T> List<T>.firstConnectable(descriptor: (T) -> ProviderDescriptor): T? = firstOrNull { descriptor(it).userConnectable }

/**
 * One user-configured instance of a provider. [config] holds non-secret settings (Google: only the account email).
 * A SecretStore arrives with the first provider that has a secret; Google needs none (3a design D2).
 */
data class Connection(
    val id: String,
    val providerId: String,
    val label: String,
    val config: Map<String, String>,
)

sealed interface ConnectionHealth {
    data object Ok : ConnectionHealth
    data object Unreachable : ConnectionHealth

    /** The tablet itself couldn't look up or reach any address: its Wi-Fi, not the service (5 §A.3). */
    data object Offline : ConnectionHealth

    data object NeedsSignIn : ConnectionHealth
    data class Error(val message: String) : ConnectionHealth
}

/** The connection's sign-in has expired or been revoked (Google: 401, or a refused token refresh). */
class NeedsSignInException(message: String? = null, cause: Throwable? = null) : Exception(message, cause)

/**
 * The provider couldn't be reached, or asked to be tried later: network errors, and for Google 429, 403
 * rate-limit reasons (rateLimitExceeded, userRateLimitExceeded) and every 5xx. The engine retries with backoff.
 */
open class UnreachableException(message: String? = null, cause: Throwable? = null) : Exception(message, cause)

/** The tablet itself has no network (5 §A.3): what a provider throws when it knows, rather than leaving it to a cause. */
class OfflineException(message: String? = null, cause: Throwable? = null) : UnreachableException(message, cause)

/** Whether a capability uses a connected account; bound into a set, so a disconnect can say who else depends on it. */
fun interface AccountUse {
    suspend fun uses(account: String): Boolean
}
