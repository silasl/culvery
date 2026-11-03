package uk.co.siland.culvery.core.ui

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Spacer
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.addPathNodes
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.hideFromAccessibility
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import kotlin.math.floor

// The cake's viewport has the Culvery mark's aspect, so it takes the mark's place without moving anything.
private const val CAKE_W = 54f
private const val CAKE_H = 49f

private const val CAKE_PLATE = "M3,44 L51,44 L51,48 L3,48 Z"
private const val CAKE_TIERS = "M6,30 L48,30 L48,44 L6,44 Z M14,18 L40,18 L40,30 L14,30 Z"
private const val CAKE_ICING =
    "M14,18 L40,18 L40,22 L38,22 L38,25 L35.5,25 L35.5,22 L30,22 L30,26 L27,26 L27,22 L22,22 L22,24.5 L19.5,24.5 L19.5,22 L14,22 Z " +
        "M6,30 L48,30 L48,34 L45,34 L45,38 L42,38 L42,34 L33,34 L33,37 L30,37 L30,34 L20,34 L20,39 L17,39 L17,34 L9,34 L9,36 L6,36 Z"
private const val CAKE_CANDLE = "M26,8 L28,8 L28,18 L26,18 Z"
private const val CAKE_FLAME = "M27,1 C29.5,4 29.5,7 27,8 C24.5,7 24.5,4 27,1 Z"

/** A two-tier cake with one lit candle, [width] wide, taking the Culvery mark's place. A picture only; TalkBack skips it (16 §4.2). */
@Composable
fun CakeMark(width: Dp, modifier: Modifier = Modifier) {
    val c = Culvery.colors
    val cake = remember(c, width) {
        ImageVector.Builder(name = "CakeMark", defaultWidth = width, defaultHeight = width * (CAKE_H / CAKE_W), viewportWidth = CAKE_W, viewportHeight = CAKE_H)
            .addPath(addPathNodes(CAKE_PLATE), fill = SolidColor(c.accentSoft))
            .addPath(addPathNodes(CAKE_TIERS), fill = SolidColor(c.accent))
            .addPath(addPathNodes(CAKE_ICING), fill = SolidColor(BirthdayInks.ICING))
            .addPath(addPathNodes(CAKE_CANDLE), fill = SolidColor(c.ink))
            .addPath(addPathNodes(CAKE_FLAME), fill = SolidColor(c.weather))
            .build()
    }
    Image(cake, contentDescription = null, modifier = modifier.testTag("cake_mark").semantics { hideFromAccessibility() })
}

object BirthdayDimens {
    /** How far the bunting's string hangs below the band's top, and how far it sags at the middle below that. */
    val buntingTop = 6.dp
    val buntingSag = 16.dp
    val buntingStroke = 1.5.dp
    val flagWidth = 18.dp
    val flagHeight = 22.dp
    val flagPitch = 28.dp
}

private const val BUNTING_MIN_FLAGS = 3

/** How many flags fit [bandWidthPx] a pitch apart; none for a band narrower than three pitches. */
internal fun flagCount(bandWidthPx: Float, density: Density): Int {
    val pitch = with(density) { BirthdayDimens.flagPitch.toPx() }
    return if (bandWidthPx < BUNTING_MIN_FLAGS * pitch) 0 else floor(bandWidthPx / pitch).toInt()
}

/** The string's height at [x]: a parabola from [BirthdayDimens.buntingTop] at both ends to the sag at the middle. */
private fun stringY(band: Rect, x: Float, density: Density): Float = with(density) {
    val t = (x - band.left) / band.width
    band.top + BirthdayDimens.buntingTop.toPx() + BirthdayDimens.buntingSag.toPx() * 4f * t * (1f - t)
}

/** Where each flag hangs from the string (the middle of its top edge), the row centred in [band]. */
internal fun flagAnchors(band: Rect, density: Density): List<Offset> {
    val n = flagCount(band.width, density)
    val pitch = with(density) { BirthdayDimens.flagPitch.toPx() }
    val first = band.left + (band.width - n * pitch) / 2 + pitch / 2
    return List(n) { i -> (first + i * pitch).let { x -> Offset(x, stringY(band, x, density)) } }
}

/** The bottom of the lowest flag's point, or of the string's middle where there are no flags. */
internal fun lowestFlagTip(band: Rect, density: Density): Float = with(density) {
    val anchors = flagAnchors(band, density)
    if (anchors.isEmpty()) stringY(band, band.center.x, density) else anchors.maxOf { it.y } + BirthdayDimens.flagHeight.toPx()
}

/** A [string]-coloured line sagging across [band], with a triangular flag in each person colour, in order, hung from it (16 §4.2). A band too narrow for three flags gets nothing. */
fun DrawScope.drawBunting(band: Rect, string: Color, density: Density) {
    val anchors = flagAnchors(band, density)
    if (anchors.isEmpty()) return
    val dip = with(density) { BirthdayDimens.buntingSag.toPx() }
    val y = band.top + with(density) { BirthdayDimens.buntingTop.toPx() }
    val line = Path().apply {
        moveTo(band.left, y)
        quadraticTo(band.center.x, y + 2 * dip, band.right, y)
    }
    drawPath(line, string, style = Stroke(with(density) { BirthdayDimens.buntingStroke.toPx() }))
    val half = with(density) { BirthdayDimens.flagWidth.toPx() } / 2
    val height = with(density) { BirthdayDimens.flagHeight.toPx() }
    anchors.forEachIndexed { i, at ->
        val flag = Path().apply {
            moveTo(at.x - half, at.y)
            lineTo(at.x + half, at.y)
            lineTo(at.x, at.y + height)
            close()
        }
        drawPath(flag, BirthdayInks.flag(PersonPalette.colors[i % PersonPalette.colors.size]))
    }
}

/**
 * The bunting across Home's open header band. Nothing in it is read by TalkBack. [drifting] is for the balloons that
 * will rise through it; the bunting itself is still. It is as big as the band, so [modifier] gives the room the band sits in (16 §4.2).
 */
@Composable
fun BirthdaySky(drifting: Boolean, modifier: Modifier = Modifier) {
    val string = Culvery.colors.mute
    val measured = LocalHomeSkyBand.current
    Box(modifier) {
        Spacer(
            Modifier
                .inSkyBand(measured)
                .testTag("birthday_sky")
                .clearAndSetSemantics {}
                .graphicsLayer { clip = true }
                .drawWithCache {
                    val band = Rect(Offset.Zero, size)
                    onDrawBehind { if (size.width > 0f && size.height > 0f) drawBunting(band, string, this) }
                },
        )
    }
}
