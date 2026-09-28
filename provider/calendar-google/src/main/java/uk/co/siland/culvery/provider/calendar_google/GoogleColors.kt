package uk.co.siland.culvery.provider.calendar_google

/** Google Calendar's 11 event colours (colors.get, "event"): id to background RGB. */
internal val EVENT_COLORS: Map<String, Int> = mapOf(
    "1" to 0xa4bdfc,
    "2" to 0x7ae7bf,
    "3" to 0xdbadff,
    "4" to 0xff887c,
    "5" to 0xfbd75b,
    "6" to 0xffb878,
    "7" to 0x46d6db,
    "8" to 0xe1e1e1,
    "9" to 0x5484ed,
    "10" to 0x51b749,
    "11" to 0xdc2127,
)

private const val BYTE = 0xFF

/** The event colour nearest [argb] (a person's colour) by RGB distance (3a design §3.6). */
internal fun nearestColorId(argb: Long): String {
    val r = (argb shr 16).toInt() and BYTE
    val g = (argb shr 8).toInt() and BYTE
    val b = argb.toInt() and BYTE
    return EVENT_COLORS.minBy { (_, rgb) ->
        val dr = (rgb shr 16 and BYTE) - r
        val dg = (rgb shr 8 and BYTE) - g
        val db = (rgb and BYTE) - b
        dr * dr + dg * dg + db * db
    }.key
}
