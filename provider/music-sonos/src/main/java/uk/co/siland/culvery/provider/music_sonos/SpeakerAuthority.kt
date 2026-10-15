package uk.co.siland.culvery.provider.music_sonos

import uk.co.siland.culvery.capability.music.isHomeNetworkHost

internal const val SONOS_PORT = 1400

/** Whether Culvery may contact [host]:[port]: a speaker on the home network, on Sonos's port (6 §6 privacy). */
internal fun isSpeakerAuthority(host: String, port: Int): Boolean = port == SONOS_PORT && isHomeNetworkHost(host)

/** The same for "host:port"; tests that run speakers on this machine pass their own [accept]. */
internal fun isSpeakerAddress(address: String, accept: (String, Int) -> Boolean = ::isSpeakerAuthority): Boolean {
    val host = address.substringBeforeLast(':', "")
    val port = address.substringAfterLast(':', "").toIntOrNull() ?: return false
    return host.isNotEmpty() && accept(host, port)
}
