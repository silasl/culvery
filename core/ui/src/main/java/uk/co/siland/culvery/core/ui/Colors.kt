package uk.co.siland.culvery.core.ui

import androidx.compose.runtime.Immutable
import androidx.compose.ui.graphics.Color

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
)

val LightColors = HhColors(
    bg = Color(0xFFEDF0EE),
    surf = Color(0xFFFFFFFF),
    surf2 = Color(0xFFF1F4F2),
    surf3 = Color(0xFFDDE3E0),
    line = Color(0xFFDAE0DD),
    ink = Color(0xFF111514),
    mute = Color(0xFF5A6461),
    accent = Color(0xFF2E8A64),
    accentInk = Color(0xFFFFFFFF),
    accentSoft = Color(0xFFD3EDE1),
    danger = Color(0xFFB83A28),
    dangerSoft = Color(0xFFF7DFDA),
    dangerInk = Color(0xFFFFFFFF),
)
