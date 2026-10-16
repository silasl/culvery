package uk.co.siland.culvery.core.plugin

private const val PARTS = 4
private const val MAX_DIGITS = 3
private const val MAX_PART = 255

/** [host]'s four numbers of one to three digits each, or null; any value, so the caller checks the range. */
internal fun ipv4Parts(host: String, leadingZeros: Boolean): List<Int>? {
    val parts = host.split('.')
    if (parts.size != PARTS) return null
    return parts.map { part ->
        val plain = part.length in 1..MAX_DIGITS && part.all { it in '0'..'9' } && (leadingZeros || part.length == 1 || part[0] != '0')
        if (plain) part.toInt() else return null
    }
}

/**
 * Whether [host] is on the home network as written: four numbers 0–255 without leading zeros, in a private range
 * (10/8, 172.16/12, 192.168/16, 169.254/16). The one rule for what Culvery may contact on the home network (6 §6, 7 §6);
 * a provider adds its port to it.
 */
fun isHomeNetworkHost(host: String): Boolean {
    val n = ipv4Parts(host, leadingZeros = false) ?: return false
    if (n.any { it > MAX_PART }) return false
    return n[0] == 10 || (n[0] == 172 && n[1] in 16..31) || (n[0] == 192 && n[1] == 168) || (n[0] == 169 && n[1] == 254)
}

/**
 * A typed address (6 §4.1) as four plain numbers, or null: spaces round it and leading zeros go, then it must be on the
 * home network ([isHomeNetworkHost]), so the wall never contacts anything off it by mistake.
 */
fun homeNetworkHost(typed: String): String? =
    ipv4Parts(typed.trim(), leadingZeros = true)?.joinToString(".")?.takeIf(::isHomeNetworkHost)
