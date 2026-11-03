package uk.co.siland.culvery.core.ui

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.compositeOver

private fun c(rgb: Long) = Color(0xFF000000 or rgb)

/** Day: white on the accent; the toast action takes the person's colour. */
private fun day(bg: Long, surf2: Long, line: Long, accent: Long, soft: Long, danger: Long? = null): HhColors =
    LightColors.copy(
        bg = c(bg), surf2 = c(surf2), line = c(line), accent = c(accent), accentSoft = c(soft),
        accentInk = Color.White,
        danger = danger?.let(::c) ?: LightColors.danger,
    )

/** Night: near-black on the accent; the toast action takes the day accent. */
private fun night(dayAccent: Long, bg: Long, surf2: Long, line: Long, accent: Long, soft: Long, danger: Long? = null): HhColors =
    DarkColors.copy(
        bg = c(bg), surf2 = c(surf2), line = c(line), accent = c(accent), accentSoft = c(soft),
        accentInk = DarkColors.bg, accentOnInk = c(dayAccent),
        danger = danger?.let(::c) ?: DarkColors.danger,
    )

private fun look(person: Long, night: HhColors, day: HhColors): Pair<Long, SeasonalLook> =
    person to SeasonalLook(
        night, day.copy(accentOnInk = Color(person)),
        clockFace = CherryBombClock,
        mark = { width -> CakeMark(width) },
        sky = { drifting, m -> BirthdaySky(drifting, m) },
    )

private val Looks: Map<Long, SeasonalLook> = mapOf(
    look(
        PersonPalette.colors[0],
        night(0x2F6F54, 0x111817, 0x263030, 0x232F2C, 0x4CB387, 0x22342E),
        day(0xE7F0EC, 0xE7EFEB, 0xBBD6CA, 0x2F6F54, 0xC9E8DB),
    ),
    look(
        PersonPalette.colors[1],
        night(0x2165AF, 0x12171B, 0x272F35, 0x242D33, 0x5B9BE0, 0x24303B),
        day(0xE8EEF4, 0xE8EDF2, 0xBED1DE, 0x2165AF, 0xD1E3F6),
    ),
    look(
        PersonPalette.colors[2],
        night(0xB42A68, 0x181519, 0x2F2D32, 0x2E2A2F, 0xE07BA8, 0x382B33, danger = 0xEE8A5A),
        day(0xF4EBEF, 0xF2EBEE, 0xDBCAD1, 0xB42A68, 0xF6D9E6, danger = 0xB8461A),
    ),
    look(
        PersonPalette.colors[3],
        night(0x546D24, 0x151914, 0x2B312C, 0x293027, 0x9CC44E, 0x2E3625),
        day(0xEEF2E7, 0xEDF0E7, 0xCCDABE, 0x546D24, 0xDAE9BE),
    ),
    look(
        PersonPalette.colors[4],
        night(0xB3371A, 0x181312, 0x2F2A2A, 0x2E2624, 0xE66E51, 0x38241F, danger = 0xEE6FB0),
        day(0xF4EAE7, 0xF5ECEA, 0xDBBFB5, 0xB3371A, 0xF8D9D1, danger = 0xA8236A),
    ),
    look(
        PersonPalette.colors[5],
        night(0x6D660D, 0x181911, 0x2E3129, 0x2D3023, 0xCEC018, 0x35351D),
        day(0xF2F1E2, 0xF0EFE4, 0xD7D9B2, 0x6D660D, 0xECE6A5),
    ),
)

/** The look for [colour], one of [PersonPalette.colors]; null for any other, which keeps the normal colours (16 §3.2). */
fun birthdayLook(colour: Long): SeasonalLook? = Looks[colour]

/** The drawings' colours, here so ContrastTest checks them against every set (16 §4.2). */
object BirthdayInks {
    /** Cream: visible on the cake's tiers in both themes and on the greeting pill, where an `accentSoft` icing would vanish. */
    val ICING = Color(0xFFFFF4E8)
    const val BALLOON_ALPHA = 0.85f

    fun balloon(colour: Color, c: HhColors): Color = colour.copy(alpha = BALLOON_ALPHA).compositeOver(c.bg)
    fun balloon(argb: Long, c: HhColors): Color = balloon(Color(argb), c)
    fun flag(colour: Color): Color = colour
    fun flag(argb: Long): Color = flag(Color(argb))
}
