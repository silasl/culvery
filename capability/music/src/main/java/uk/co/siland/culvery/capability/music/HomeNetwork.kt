package uk.co.siland.culvery.capability.music

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
 * (10/8, 172.16/12, 192.168/16, 169.254/16). The one rule for what Music may contact (6 §6 privacy); the provider adds
 * its port to it.
 */
fun isHomeNetworkHost(host: String): Boolean {
    val n = ipv4Parts(host, leadingZeros = false) ?: return false
    if (n.any { it > MAX_PART }) return false
    return n[0] == 10 || (n[0] == 172 && n[1] in 16..31) || (n[0] == 192 && n[1] == 168) || (n[0] == 169 && n[1] == 254)
}
