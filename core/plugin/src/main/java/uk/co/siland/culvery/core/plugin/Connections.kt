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
)

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
    data object NeedsSignIn : ConnectionHealth
    data class Error(val message: String) : ConnectionHealth
}
