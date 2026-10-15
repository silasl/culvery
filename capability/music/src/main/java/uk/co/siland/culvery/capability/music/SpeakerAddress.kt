package uk.co.siland.culvery.capability.music

/**
 * A typed speaker address (6 §4.1) as four plain numbers, or null: spaces round it and leading zeros go, then it must be
 * on the home network ([isHomeNetworkHost]), so the wall never contacts anything off it by mistake (6 §6).
 */
fun speakerHost(typed: String): String? =
    ipv4Parts(typed.trim(), leadingZeros = true)?.joinToString(".")?.takeIf(::isHomeNetworkHost)
