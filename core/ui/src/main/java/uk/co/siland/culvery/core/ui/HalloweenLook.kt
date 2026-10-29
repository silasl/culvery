package uk.co.siland.culvery.core.ui

import androidx.compose.ui.graphics.Color

/** Plum-black with a pumpkin accent and a pink-red danger that stays clear of the orange (14 §2.2). */
val HalloweenNight = HhColors(
    bg = Color(0xFF120E15),
    surf = Color(0xFF1D1722),
    surf2 = Color(0xFF29212F),
    surf3 = Color(0xFF352B3C),
    line = Color(0xFF221B27),
    ink = Color(0xFFF3EEF4),
    mute = Color(0xFFA69AAD),
    accent = Color(0xFFF28C28),
    accentInk = Color(0xFF1A0C02),
    accentOnInk = Color(0xFFA84B0A),
    accentSoft = Color(0xFF3B2414),
    danger = Color(0xFFF27A9B),
    dangerSoft = Color(0xFF3B1A26),
    dangerInk = Color(0xFF1A0710),
    weather = DarkColors.weather,
    rain = DarkColors.rain,
    border = Color.Transparent,
)

val HalloweenDay = HhColors(
    bg = Color(0xFFF3ECE3),
    surf = Color(0xFFFFFEFC),
    surf2 = Color(0xFFF6F0E8),
    surf3 = Color(0xFFE4D8CA),
    line = Color(0xFFE3D7C9),
    ink = Color(0xFF120C15),
    mute = Color(0xFF5E5263),
    accent = Color(0xFFA84B0A),
    accentInk = Color(0xFFFFFFFF),
    accentOnInk = Color(0xFFF28C28),
    accentSoft = Color(0xFFFAE9DA),
    danger = Color(0xFFB0284F),
    dangerSoft = Color(0xFFF8DDE5),
    dangerInk = Color(0xFFFFFFFF),
    weather = LightColors.weather,
    rain = LightColors.rain,
    border = Color(0xFFDDCFBF),
)

val HalloweenLook = SeasonalLook(
    night = HalloweenNight,
    day = HalloweenDay,
    clockFace = CreepsterClock,
    mark = { width -> PumpkinMark(width) },
    cardCorner = { colors -> drawCobweb(HalloweenInks.web(colors), SeasonalDimens.webSpan) },
    sky = { drifting, modifier -> HalloweenSky(drifting, modifier) },
    sound = SeasonalSound.HAUNTED_ORGAN,
)

/** The drawings' colours, here so ContrastTest checks what can sit over them (14 §4.3). */
object HalloweenInks {
    /** `surf2` is lighter than the day `bg` and would vanish. */
    val DAY_BAT = Color(0xFFDDD0C0)
    val PUMPKIN_STEM = Color(0xFF5E8C31)
    val PUMPKIN_FACE = Color(0xFF1A0C02)

    fun moon(c: HhColors): Color = c.accentSoft
    fun bat(c: HhColors): Color = if (c.isLight) DAY_BAT else c.surf2
    fun web(c: HhColors): Color = c.surf3
}
