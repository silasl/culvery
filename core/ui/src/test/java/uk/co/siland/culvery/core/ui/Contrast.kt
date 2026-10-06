package uk.co.siland.culvery.core.ui

import androidx.compose.ui.graphics.Color
import kotlin.math.pow
import kotlin.math.roundToInt

/** WCAG 2.x contrast ratio of two opaque colours, 1 to 21. */
internal fun contrast(fg: Color, bg: Color): Double {
    val a = relativeLuminance(fg)
    val b = relativeLuminance(bg)
    return (maxOf(a, b) + 0.05) / (minOf(a, b) + 0.05)
}

/** This colour at [alpha] over [surface], rounded to 8 bits a channel as the screen blends it. */
internal fun Color.over(alpha: Float, surface: Color): Color = Color(
    red = blend(red, surface.red, alpha),
    green = blend(green, surface.green, alpha),
    blue = blend(blue, surface.blue, alpha),
)

private fun blend(fg: Float, bg: Float, alpha: Float): Int = (fg * 255 * alpha + bg * 255 * (1 - alpha)).roundToInt()

private fun relativeLuminance(c: Color): Double = 0.2126 * linear(c.red) + 0.7152 * linear(c.green) + 0.0722 * linear(c.blue)

private fun linear(channel: Float): Double {
    val s = (channel * 255).roundToInt() / 255.0
    return if (s <= 0.03928) s / 12.92 else ((s + 0.055) / 1.055).pow(2.4)
}
