package uk.co.siland.culvery.core.ui

/**
 * The eight person colours (4a design D12). A colour in use can't be picked again, so eight is also the most people a
 * household can have. Family keeps its amber, which is none of these.
 */
object PersonPalette {
    // The hand-off's green, blue and pink; then violet, lime, red, slate and brown, each at least 60 apart in RGB from
    // every other and from Family's amber (PersonPaletteTest).
    val colors: List<Long> = listOf(
        0xFF4CB387,
        0xFF5B9BE0,
        0xFFE07BA8,
        0xFF9C7CE3,
        0xFF9CC44E,
        0xFFE06666,
        0xFF8B96A8,
        0xFFA1785A,
    )

    /** The first colour nobody in [taken] has; null once all eight are used. */
    fun firstFree(taken: Collection<Long>): Long? = colors.firstOrNull { it !in taken }
}
