package uk.co.siland.culvery.core.ui

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Spacer
import androidx.compose.runtime.Composable
import androidx.compose.runtime.MutableLongState
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
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
import kotlin.math.PI
import kotlin.math.sin

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
    val cake = remember(width) {
        ImageVector.Builder(name = "CakeMark", defaultWidth = width, defaultHeight = width * (CAKE_H / CAKE_W), viewportWidth = CAKE_W, viewportHeight = CAKE_H)
            .addPath(addPathNodes(CAKE_PLATE), fill = SolidColor(BirthdayInks.PLATE))
            .addPath(addPathNodes(CAKE_TIERS), fill = SolidColor(BirthdayInks.SPONGE))
            .addPath(addPathNodes(CAKE_ICING), fill = SolidColor(BirthdayInks.ICING))
            .addPath(addPathNodes(CAKE_CANDLE), fill = SolidColor(BirthdayInks.CANDLE))
            .addPath(addPathNodes(CAKE_FLAME), fill = SolidColor(BirthdayInks.FLAME))
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
    val balloonWidth = 30.dp
    val balloonHeight = 38.dp
    val balloonString = 26.dp
    val balloonSway = 6.dp
}

internal const val BALLOON_LAP_MS = 30_000L
internal const val BALLOONS = 4

/** Where each balloon's body hangs (its centre x, top y) in [band] [elapsedMillis] into the drift. */
fun balloonPositions(elapsedMillis: Long, band: Rect, density: Density): List<Offset> =
    with(density) {
        val travel = band.height + BirthdayDimens.balloonHeight.toPx() + BirthdayDimens.balloonString.toPx()
        val sway = BirthdayDimens.balloonSway.toPx()
        List(BALLOONS) { i ->
            val lap = ((elapsedMillis % BALLOON_LAP_MS).toFloat() / BALLOON_LAP_MS + i / BALLOONS.toFloat()) % 1f
            Offset(band.left + band.width * (i + 0.5f) / BALLOONS + sway * sin(2f * PI.toFloat() * lap), band.bottom - lap * travel)
        }
    }

private const val BUNTING_MIN_FLAGS = 3

/** Where each flag hangs from the string (the middle of its top edge), the row centred in [band]. */
internal fun flagAnchors(band: Rect, density: Density): List<Offset> {
    val pitch = with(density) { BirthdayDimens.flagPitch.toPx() }
    val n = fitCount(band.width, pitch, BUNTING_MIN_FLAGS)
    val first = band.left + (band.width - n * pitch) / 2 + pitch / 2
    return List(n) { i -> (first + i * pitch).let { x -> Offset(x, hangingY(band, x, BirthdayDimens.buntingTop, BirthdayDimens.buntingSag, density)) } }
}

/** The bottom of the lowest flag's point, or of the string's middle where there are no flags. */
internal fun lowestFlagTip(band: Rect, density: Density): Float = with(density) {
    val anchors = flagAnchors(band, density)
    if (anchors.isEmpty()) hangingY(band, band.center.x, BirthdayDimens.buntingTop, BirthdayDimens.buntingSag, density) else anchors.maxOf { it.y } + BirthdayDimens.flagHeight.toPx()
}

/** The string and a triangular flag in each person colour, in order, hung from it, built once for a band size (16 §4.2). */
private class Bunting(val string: Path, val stroke: Stroke, val flags: List<Pair<Path, Color>>)

/** The bunting across [band]; null for a band too narrow for three flags. */
private fun buntingIn(band: Rect, density: Density): Bunting? = with(density) {
    val anchors = flagAnchors(band, density)
    if (anchors.isEmpty()) return null
    val string = hangingPath(band, BirthdayDimens.buntingTop, BirthdayDimens.buntingSag, density)
    val half = BirthdayDimens.flagWidth.toPx() / 2
    val height = BirthdayDimens.flagHeight.toPx()
    val flags = anchors.mapIndexed { i, at ->
        val flag = Path().apply {
            moveTo(at.x - half, at.y)
            lineTo(at.x + half, at.y)
            lineTo(at.x, at.y + height)
            close()
        }
        flag to BirthdayInks.flag(PersonPalette.colourAt(i))
    }
    Bunting(string, Stroke(BirthdayDimens.buntingStroke.toPx()), flags)
}

private fun DrawScope.drawBunting(bunting: Bunting, string: Color) {
    drawPath(bunting.string, string, style = bunting.stroke)
    bunting.flags.forEach { (flag, colour) -> drawPath(flag, colour) }
}

/** Four balloons, one per lane, each with its string, under whatever is drawn after them (16 §4.2). */
private fun DrawScope.drawBalloons(elapsedMillis: Long, band: Rect, c: HhColors, density: Density) = with(density) {
    val w = BirthdayDimens.balloonWidth.toPx()
    val h = BirthdayDimens.balloonHeight.toPx()
    val string = BirthdayDimens.balloonString.toPx()
    val stroke = 1.dp.toPx()
    balloonPositions(elapsedMillis, band, density).forEachIndexed { i, at ->
        drawLine(c.mute, Offset(at.x, at.y + h), Offset(at.x, at.y + h + string), stroke)
        drawOval(BirthdayInks.balloon(PersonPalette.colourAt(i), c), Offset(at.x - w / 2, at.y), Size(w, h))
    }
}

/**
 * Balloons rising and bunting across Home's open header band. Nothing in it is read by TalkBack. While [drifting] it advances
 * [elapsed] about every [SKY_FRAME_MS]; the time is read only when drawing. Still, it runs no frames and draws the balloons once.
 * It is as big as the band, so [modifier] gives the room the band sits in (16 §4.2).
 */
@Composable
fun BirthdaySky(drifting: Boolean, modifier: Modifier = Modifier, elapsed: MutableLongState = remember { mutableLongStateOf(0L) }) {
    val colors = Culvery.colors
    SkyFrames(drifting, elapsed)
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
                    val bunting = buntingIn(band, this)
                    onDrawBehind {
                        if (size.width <= 0f || size.height <= 0f) return@onDrawBehind
                        drawBalloons(elapsed.longValue, band, colors, this)
                        bunting?.let { drawBunting(it, colors.mute) }
                    }
                },
        )
    }
}
