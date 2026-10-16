package uk.co.siland.culvery.provider.lights_hue

import uk.co.siland.culvery.core.plugin.isHomeNetworkHost

internal const val HUE_PORT = 443

/** Whether Culvery may contact [host]:[port]: a bridge on the home network, on HTTPS's port (7 §6 privacy). */
internal fun isBridgeAuthority(host: String, port: Int): Boolean = port == HUE_PORT && isHomeNetworkHost(host)

/** The same for a saved address, "host" or "host:port"; tests that run a bridge on this machine pass their own [accept]. */
internal fun isBridgeAddress(address: String, accept: (String, Int) -> Boolean = ::isBridgeAuthority): Boolean =
    bridgeAuthority(address)?.let { (host, port) -> accept(host, port) } ?: false

/** [address]'s host and port, 443 when none is given; null when it isn't one of those two shapes. */
internal fun bridgeAuthority(address: String): Pair<String, Int>? {
    val host = address.substringBeforeLast(':')
    val port = if (':' in address) address.substringAfterLast(':').toIntOrNull() ?: return null else HUE_PORT
    return if (host.isEmpty() || ':' in host) null else host to port
}
