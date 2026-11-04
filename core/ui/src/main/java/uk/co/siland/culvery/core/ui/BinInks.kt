package uk.co.siland.culvery.core.ui

import androidx.compose.ui.graphics.Color

/** A bin lid colour as a stripe ink, with a value for each theme; [nightEdge] outlines a night stripe too dark to see on its own. */
data class BinInk(val id: String, val name: String, val day: Color, val night: Color, val nightEdge: Color? = null)

/** The stripe colours, here so ContrastTest checks them against every set. No grey: the `mute` stripe means unknown. */
object BinInks {
    val all: List<BinInk> = listOf(
        BinInk("blue", "Blue", Color(0xFF1F5FBF), Color(0xFF6FA8F0)),
        BinInk("black", "Black", Color(0xFF1B1D1E), Color(0xFF3A3F42), nightEdge = Color(0xFF80878A)),
        BinInk("brown", "Brown", Color(0xFF7A4A21), Color(0xFFC08A5A)),
        BinInk("green", "Green", Color(0xFF2E7D32), Color(0xFF6CC070)),
        BinInk("yellow", "Yellow", Color(0xFF9A7B00), Color(0xFFE0C440)),
        BinInk("purple", "Purple", Color(0xFF6A3FA0), Color(0xFFB08CE0)),
    )

    fun byId(id: String?): BinInk? = all.firstOrNull { it.id == id }

    /** [HhColors.mute] stands for an unknown colour. */
    fun colour(ink: BinInk?, c: HhColors): Color = when {
        ink == null -> c.mute
        c.isLight -> ink.day
        else -> ink.night
    }

    /** The outline [ink]'s stripe takes in [c], or null for none. */
    fun edge(ink: BinInk?, c: HhColors): Color? = if (c.isLight) null else ink?.nightEdge
}
