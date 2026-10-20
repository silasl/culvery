package uk.co.siland.culvery.core.ui

import androidx.compose.runtime.Immutable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance

/**
 * The theme's colours. [weather] draws the weather icons: amber at night, a darker gold that reads on the day theme's surfaces.
 * [rain] draws the rain icons; [border] is the cards' hairline edge, light only (9 §4.1).
 */
@Immutable
data class HhColors(
    val bg: Color,
    val surf: Color,
    val surf2: Color,
    val surf3: Color,
    val line: Color,
    val ink: Color,
    val mute: Color,
    val accent: Color,
    val accentInk: Color,
    val accentSoft: Color,
    val danger: Color,
    val dangerSoft: Color,
    val dangerInk: Color,
    val weather: Color,
    val rain: Color,
    val border: Color,
)

val DarkColors = HhColors(
    bg = Color(0xFF0E1011),
    surf = Color(0xFF1A1D1E),
    surf2 = Color(0xFF24282A),
    surf3 = Color(0xFF303537),
    line = Color(0xFF1F2324),
    ink = Color(0xFFF1F4F2),
    mute = Color(0xFF9AA3A0),
    accent = Color(0xFF4CB387),
    accentInk = Color(0xFF08170F),
    accentSoft = Color(0xFF173427),
    danger = Color(0xFFEE7B6A),
    dangerSoft = Color(0xFF3A211D),
    dangerInk = Color(0xFF1A0906),
    weather = Color(0xFFE0B85B),
    rain = Color(0xFF7FB2E5),
    border = Color.Transparent,
)

val LightColors = HhColors(
    bg = Color(0xFFEDF0EE),
    surf = Color(0xFFFFFFFF),
    surf2 = Color(0xFFF1F4F2),
    surf3 = Color(0xFFDDE3E0),
    line = Color(0xFFDAE0DD),
    ink = Color(0xFF111514),
    mute = Color(0xFF5A6461),
    accent = Color(0xFF23704F),
    accentInk = Color(0xFFFFFFFF),
    accentSoft = Color(0xFFD3EDE1),
    danger = Color(0xFFB83A28),
    dangerSoft = Color(0xFFF7DFDA),
    dangerInk = Color(0xFFFFFFFF),
    weather = Color(0xFF8A6400),
    rain = Color(0xFF2F6FAE),
    border = Color(0xFFD5DCD8),
)

/** True for the day theme; while the theme fades, from whichever side `bg` has reached. */
internal val HhColors.isLight: Boolean get() = bg.luminance() > 0.5f

/** Alphas drawn over a surface, kept here so ContrastTest checks the values the screens draw (4d-1 §5). */
object HhAlpha {
    private const val TINT_DARK = 0x2E / 255f
    private const val TINT_LIGHT = 0x26 / 255f
    private const val BAR_DARK = 0x4D / 255f
    private const val BAR_LIGHT = 0x40 / 255f

    /** A past day in the date picker, the whole cell over the picker's `surf`; 60 % would be 4.499:1 in light. */
    const val PAST_DAY = 0.62f

    /** A week chip's tint: the person's colour at this alpha over `surf`. */
    fun tint(colors: HhColors): Float = if (colors.isLight) TINT_LIGHT else TINT_DARK

    /** A multi-day bar's tint: the person's colour at this alpha over the column or the gap under it (9 §4.7). */
    fun bar(colors: HhColors): Float = if (colors.isLight) BAR_LIGHT else BAR_DARK
}
