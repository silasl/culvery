package uk.co.siland.culvery.core.ui

import androidx.compose.ui.graphics.Color

/**
 * The eight person colours (4a design D12). A colour in use can't be picked again, so eight is also the most people a
 * household can have. Family keeps its amber, which is none of these.
 */
object PersonPalette {
    // The hand-off's green, blue and pink; then violet, lime, red, yellow (the user's swap for slate, 4e) and brown,
    // each at least 60 apart in RGB from every other and from Family's amber (PersonPaletteTest).
    val colors: List<Long> = listOf(
        0xFF4CB387,
        0xFF5B9BE0,
        0xFFE07BA8,
        0xFF9C7CE3,
        0xFF9CC44E,
        0xFFE06666,
        0xFFCEC018,
        0xFFA1785A,
    )

    /** The first colour nobody in [taken] has; null once all eight are used. */
    fun firstFree(taken: Collection<Long>): Long? = colors.firstOrNull { it !in taken }

    /** Each colour's name, in [colors]' order: what TalkBack says for a swatch and in the week's legend. */
    val names: List<String> = listOf("Green", "Blue", "Pink", "Violet", "Lime", "Red", "Yellow", "Brown")

    /** [argb]'s name; null for a colour that isn't one of these (Family's amber is [FAMILY_COLOUR_NAME]). */
    fun nameOf(argb: Long): String? = colors.indexOf(argb).takeIf { it >= 0 }?.let(names::get)
}

/** Family's colour, by name. */
const val FAMILY_COLOUR_NAME = "Amber"

/** The label and tick on a chip filled with a person's colour: dark in both themes, as every person colour is a mid-tone. */
val PersonChipInk: Color = DarkColors.bg
