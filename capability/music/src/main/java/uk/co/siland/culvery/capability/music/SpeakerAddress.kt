package uk.co.siland.culvery.capability.music

private const val PARTS = 4
private const val MAX_DIGITS = 3

/**
 * A typed speaker address (6 §4.1) as four plain numbers, or null: spaces round it and leading zeros go, then it must be
 * on the home network ([isHomeNetworkHost]), so the wall never contacts anything off it by mistake (6 §6).
 */
fun speakerHost(typed: String): String? {
    val parts = typed.trim().split('.')
    if (parts.size != PARTS || parts.any { p -> p.isEmpty() || p.length > MAX_DIGITS || p.any { it !in '0'..'9' } }) return null
    return parts.joinToString(".") { it.toInt().toString() }.takeIf(::isHomeNetworkHost)
}
