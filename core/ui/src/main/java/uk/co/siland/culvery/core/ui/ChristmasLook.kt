package uk.co.siland.culvery.core.ui

import androidx.compose.ui.graphics.Color

/** Pine green with a holly-red accent; warnings are amber and the weather ice blue (21 §3). */
val ChristmasNight = HhColors(
    bg = Color(0xFF0F1A14),
    surf = Color(0xFF16241C),
    surf2 = Color(0xFF203126),
    surf3 = Color(0xFF2B3E31),
    line = Color(0xFF1B2A21),
    ink = Color(0xFFF1F4EF),
    mute = Color(0xFF9FB0A4),
    accent = Color(0xFFEE6A6E),
    accentInk = Color(0xFF1A0405),
    accentOnInk = Color(0xFFB3262C),
    accentSoft = Color(0xFF3A1A1B),
    danger = Color(0xFFF2A33A),
    dangerSoft = Color(0xFF3A2A14),
    dangerInk = Color(0xFF1A0F02),
    weather = Color(0xFF9FD3EA),
    rain = Color(0xFF9C9EF2),
    border = Color.Transparent,
)

val ChristmasDay = HhColors(
    bg = Color(0xFFF4EFE6),
    surf = Color(0xFFFFFEFB),
    surf2 = Color(0xFFF1EBE1),
    surf3 = Color(0xFFE4DCCD),
    line = Color(0xFFE3DACB),
    ink = Color(0xFF0B130F),
    mute = Color(0xFF4F5E55),
    accent = Color(0xFFB3262C),
    accentInk = Color(0xFFFFFFFF),
    accentOnInk = Color(0xFFEE6A6E),
    accentSoft = Color(0xFFF8DEDE),
    danger = Color(0xFF8A5300),
    dangerSoft = Color(0xFFFAEBD3),
    dangerInk = Color(0xFFFFFFFF),
    weather = Color(0xFF2A6A86),
    rain = Color(0xFF4A4FB8),
    border = Color(0xFFDCD2C2),
)

val ChristmasLook = SeasonalLook(night = ChristmasNight, day = ChristmasDay, clockFace = MountainsOfChristmasClock, sky = { drifting, m -> ChristmasSky(drifting, m) })

object ChristmasInks {
    val NIGHT_SNOW = Color(0xFF2C4034)
    val DAY_SNOW = Color(0xFFD3DCD4)

    /** Red, gold, green and white; by day each is deeper so it stands off the cream. */
    val NIGHT_BULBS = listOf(Color(0xFFEE6A6E), Color(0xFFF2C14E), Color(0xFF5FBF7A), Color(0xFFF4F1E8))
    val DAY_BULBS = listOf(Color(0xFFB3262C), Color(0xFFB8860B), Color(0xFF2E7D46), Color(0xFFFFFFFF))

    /** How dim a bulb gets in its twinkle, as an alpha. */
    const val BULB_DIM = 0.5f

    fun snow(c: HhColors): Color = if (c.isLight) DAY_SNOW else NIGHT_SNOW
    fun wire(c: HhColors): Color = c.mute
    fun bulbs(c: HhColors): List<Color> = if (c.isLight) DAY_BULBS else NIGHT_BULBS

    /** By day every bulb is outlined so the white one shows on cream; by night none is. */
    fun bulbOutline(c: HhColors): Color? = if (c.isLight) c.mute else null
}
