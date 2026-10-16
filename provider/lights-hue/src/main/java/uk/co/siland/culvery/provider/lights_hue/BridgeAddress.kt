package uk.co.siland.culvery.provider.lights_hue

import uk.co.siland.culvery.core.plugin.isHomeNetworkHost
import uk.co.siland.culvery.core.plugin.splitAuthority

internal const val HUE_PORT = 443

/** Whether Culvery may contact [host]:[port]: a bridge on the home network, on HTTPS's port (7 §6 privacy). */
internal fun isBridgeAuthority(host: String, port: Int): Boolean = port == HUE_PORT && isHomeNetworkHost(host)

/** [address]'s host and port, 443 when none is given; null when it isn't "host" or "host:port". */
internal fun bridgeAuthority(address: String): Pair<String, Int>? = splitAuthority(address, HUE_PORT)
