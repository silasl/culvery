package uk.co.siland.culvery.core.ui

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance

/** A bin lid colour as a stripe ink, with a value for each theme. */
data class BinInk(val id: String, val name: String, val day: Color, val night: Color)

/** The stripe colours, here so ContrastTest checks them against every set. */
object BinInks {
    val all: List<BinInk> = listOf(
        BinInk("blue", "Blue", Color(0xFF1F5FBF), Color(0xFF6FA8F0)),
        BinInk("black", "Black", Color(0xFF1B1D1E), Color(0xFF9BA1A4)),
        BinInk("brown", "Brown", Color(0xFF7A4A21), Color(0xFFC08A5A)),
        BinInk("green", "Green", Color(0xFF2E7D32), Color(0xFF6CC070)),
        BinInk("grey", "Grey", Color(0xFF6B7275), Color(0xFFB4BABD)),
        BinInk("yellow", "Yellow", Color(0xFF9A7B00), Color(0xFFE0C440)),
        BinInk("purple", "Purple", Color(0xFF6A3FA0), Color(0xFFB08CE0)),
    )

    fun byId(id: String?): BinInk? = all.firstOrNull { it.id == id }

    /** [HhColors.mute] stands for an unknown colour. */
    fun colour(ink: BinInk?, c: HhColors): Color = when {
        ink == null -> c.mute
        c.bg.luminance() < 0.5f -> ink.night
        else -> ink.day
    }
}
