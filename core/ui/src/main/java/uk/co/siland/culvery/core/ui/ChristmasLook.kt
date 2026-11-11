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

val ChristmasLook = SeasonalLook(night = ChristmasNight, day = ChristmasDay)
