package uk.co.siland.culvery.core.ui

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.offset
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.MutableLongState
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.withFrameMillis
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.addPathNodes
import androidx.compose.ui.graphics.vector.toPath
import androidx.compose.ui.layout.layout
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.hideFromAccessibility
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.round
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.roundToInt
import kotlin.math.sin
import kotlinx.coroutines.delay

object HalloweenDimens {
    /** How far the Today card's cobweb reaches from the card's top-right corner. */
    val webSpan = 88.dp
    val webStroke = 1.dp

    /** A bat's width, wing tip to wing tip; its height is half. */
    val batSpan = 26.dp

    /** The sky's room is Home's header, from this far right of the clock and date to this far left of the header items. */
    val skyGap = 24.dp

    /** The moon's size, and how far its edge sits inside the sky's top and right edges. */
    val moonRadius = 30.dp
    val moonEnd = 12.dp
    val moonTop = 8.dp

    /** The tops of the two lanes the bats fly in, from the sky's top. */
    val batLanes = listOf(14.dp, 84.dp)
}

/** How long a bat takes to cross the sky and come back in at the left. */
internal const val BAT_LAP_MS = 45_000L

/** The sky moves at most this often, about 15 frames a second. */
internal const val SKY_FRAME_MS = 66L

// The pumpkin's viewport has the Culvery mark's aspect, so it takes the mark's place without moving anything.
private const val PUMPKIN_W = 54f
private const val PUMPKIN_H = 49f

private const val PUMPKIN_BODY =
    "M1,29 A13,18 0 1,0 27,29 A13,18 0 1,0 1,29 Z " +
        "M13,28 A14,20 0 1,0 41,28 A14,20 0 1,0 13,28 Z " +
        "M27,29 A13,18 0 1,0 53,29 A13,18 0 1,0 27,29 Z"
private const val PUMPKIN_STEM_PATH = "M24,10 L24.5,3 C26,2 29,2 30.5,3.5 L29.5,10 Z"
private const val PUMPKIN_EYES = "M14,25 L22,25 L18,17 Z M32,25 L40,25 L36,17 Z M25.5,31 L28.5,31 L27,27 Z"
private const val PUMPKIN_GRIN = "M12,34 L17,37 L22,34 L27,37 L32,34 L37,37 L42,34 L38,41 L33,43 L27,41 L21,43 L16,41 Z"

/** A jack-o'-lantern, [width] wide, taking the Culvery mark's place. A picture only; TalkBack skips it (14 §4.3). */
@Composable
fun PumpkinMark(width: Dp, modifier: Modifier = Modifier) {
    val body = Culvery.colors.accent
    val pumpkin = remember(body, width) {
        ImageVector.Builder(name = "PumpkinMark", defaultWidth = width, defaultHeight = width * (PUMPKIN_H / PUMPKIN_W), viewportWidth = PUMPKIN_W, viewportHeight = PUMPKIN_H)
            .addPath(addPathNodes(PUMPKIN_BODY), fill = SolidColor(body))
            .addPath(addPathNodes(PUMPKIN_STEM_PATH), fill = SolidColor(HalloweenInks.PUMPKIN_STEM))
            .addPath(addPathNodes(PUMPKIN_EYES), fill = SolidColor(HalloweenInks.PUMPKIN_FACE))
            .addPath(addPathNodes(PUMPKIN_GRIN), fill = SolidColor(HalloweenInks.PUMPKIN_FACE))
            .build()
    }
    Image(pumpkin, contentDescription = null, modifier = modifier.testTag("pumpkin_mark").semantics { hideFromAccessibility() })
}

private const val WEB_THREADS = 5
private val WEB_RINGS = floatArrayOf(0.28f, 0.52f, 0.76f, 1f)

// How far a ring's thread sags toward the corner between two radials.
private const val WEB_SAG = 0.86f

/** A cobweb hanging from the top-right corner: straight threads fanned over the quarter-turn, and rings between them, [span] at most from the corner (14 §4.3). */
fun DrawScope.drawCobweb(color: Color, span: Dp) {
    val reach = span.toPx()
    val corner = Offset(size.width, 0f)
    val stroke = Stroke(HalloweenDimens.webStroke.toPx())

    // 0 points left along the top edge, 90 degrees straight down the side.
    fun at(turn: Float, radius: Float) = Offset(corner.x - radius * cos(turn), corner.y + radius * sin(turn))
    val step = (PI / 2).toFloat() / (WEB_THREADS - 1)

    val web = Path()
    repeat(WEB_THREADS) { i ->
        web.moveTo(corner.x, corner.y)
        val end = at(step * i, reach)
        web.lineTo(end.x, end.y)
    }
    WEB_RINGS.forEach { ring ->
        val radius = reach * ring
        val start = at(0f, radius)
        web.moveTo(start.x, start.y)
        for (i in 1 until WEB_THREADS) {
            val pull = at(step * (i - 0.5f), radius * WEB_SAG)
            val to = at(step * i, radius)
            web.quadraticTo(pull.x, pull.y, to.x, to.y)
        }
    }
    drawPath(web, color, style = stroke)
}

private const val BATS = 4

// A bat in a 26 x 13 box, wing tip to wing tip: ears, scalloped wings, a pointed tail.
private const val BAT_W = 26f
private const val BAT_PATH =
    "M13,3.5 L11.6,0.5 L10.6,3.2 C8.5,2.4 4.5,2 0,1.2 C1.6,4 2.2,7 3,9 C4.6,7.4 6,7.2 7.6,8.6 C8.6,7.4 9.6,7.6 10.6,9 " +
        "L13,12.5 L15.4,9 C16.4,7.6 17.4,7.4 18.4,8.6 C20,7.2 21.4,7.4 23,9 C23.8,7 24.4,4 26,1.2 C21.5,2 17.5,2.4 15.4,3.2 L14.4,0.5 Z"

/**
 * The open band in Home's header where the sky is drawn: right of the clock and date, left of the header items, from Home's
 * top to the header's bottom. With nothing measured the band is all of [size] (14 §4.3).
 */
fun skyBand(measured: HomeSkyBand?, size: Size, density: Density): Rect {
    if (measured == null) return Rect(Offset.Zero, size)
    val gap = with(density) { HalloweenDimens.skyGap.toPx() }
    val right = if (measured.itemsWidth > 0) measured.headerWidth - measured.itemsWidth - gap else measured.headerWidth.toFloat()
    return Rect(maxOf(measured.clockWidth, measured.dateWidth) + gap, 0f, right, measured.headerHeight.toFloat())
}

class Moon(val centre: Offset, val radius: Float)

/** The moon near the band's right end, shrunk to fit a band shorter than it. */
fun moonIn(band: Rect, density: Density): Moon = with(density) {
    val radius = minOf(HalloweenDimens.moonRadius.toPx(), band.width / 2, band.height / 2)
    val x = band.right - HalloweenDimens.moonEnd.toPx() - radius
    val y = minOf(band.top + HalloweenDimens.moonTop.toPx() + radius, band.bottom - radius)
    Moon(Offset(x, y), radius)
}

/** Where the bats sit in [band]: a bat's width, the lap length across the band, and each lane's top. */
private class BatTrack(band: Rect, density: Density) {
    val span = with(density) { HalloweenDimens.batSpan.toPx() }
    val left = band.left - span
    val cycle = band.width + span
    val tops = FloatArray(BATS) { i ->
        val lanes = HalloweenDimens.batLanes
        band.top + minOf(with(density) { lanes[i % lanes.size].toPx() }, band.height - span / 2)
    }

    fun left(lap: Double, i: Int): Float = left + ((lap + (i + 0.5) / BATS) % 1.0 * cycle).toFloat()
}

private fun lapAt(elapsedMillis: Long): Double = (elapsedMillis % BAT_LAP_MS).toDouble() / BAT_LAP_MS

/**
 * Where each bat's box starts (left edge, top), [elapsedMillis] into the drift. The bats share two lanes, start a quarter of
 * a lap apart and cross from a bat's width left of [band] to its right edge once every [BAT_LAP_MS] (14 §4.3).
 */
fun batPositions(elapsedMillis: Long, band: Rect, density: Density): List<Offset> {
    val track = BatTrack(band, density)
    val lap = lapAt(elapsedMillis)
    return List(BATS) { i -> Offset(track.left(lap, i), track.tops[i]) }
}

/** What the sky draws for one size, worked out once rather than at every step. */
private class SkyFrame(size: Size, density: Density) {
    val band = Rect(Offset.Zero, size)
    val moon = moonIn(band, density)
    val track = BatTrack(band, density)
    val fit = track.span / BAT_W
}

/**
 * The moon and the bats in Home's open header band. Nothing in it is read by TalkBack. While [drifting] it advances [elapsed]
 * about every [SKY_FRAME_MS]; the time is read only when drawing, so a step redraws this small layer and nothing else (14 §4.3).
 * It is as big as the band, so [modifier] gives the room the band sits in.
 */
@Composable
fun HalloweenSky(drifting: Boolean, modifier: Modifier = Modifier, elapsed: MutableLongState = remember { mutableLongStateOf(0L) }) {
    val colors = Culvery.colors
    val moon = HalloweenInks.moon(colors)
    val bat = HalloweenInks.bat(colors)
    val measured = LocalHomeSkyBand.current
    val batShape = remember { addPathNodes(BAT_PATH).toPath() }
    LaunchedEffect(drifting) {
        if (!drifting) return@LaunchedEffect
        var last = -1L
        while (true) {
            withFrameMillis { now ->
                if (last >= 0) elapsed.longValue += now - last
                last = now
            }
            delay(SKY_FRAME_MS)
        }
    }
    // The box gives the band its room; the band's own node is placed inside it, so the box's size can't stretch it.
    Box(modifier) {
        Spacer(
            Modifier
                .offset { measured?.let { skyBand(it, Size.Zero, this).topLeft.round() } ?: IntOffset.Zero }
                .layout { measurable, constraints ->
                    val band = skyBand(measured, Size(constraints.maxWidth.toFloat(), constraints.maxHeight.toFloat()), this)
                    val width = band.width.roundToInt().coerceAtLeast(0)
                    val height = band.height.roundToInt().coerceAtLeast(0)
                    val placeable = measurable.measure(Constraints.fixed(width, height))
                    layout(width, height) { placeable.place(0, 0) }
                }
                .testTag("halloween_sky")
                .clearAndSetSemantics {}
                .graphicsLayer { clip = true }
                .drawWithCache {
                    val frame = SkyFrame(size, this)
                    val bats = frame.track
                    onDrawBehind {
                        if (size.width <= 0f || size.height <= 0f) return@onDrawBehind
                        drawCircle(moon, frame.moon.radius, frame.moon.centre)
                        val lap = lapAt(elapsed.longValue)
                        for (i in 0 until BATS) {
                            translate(bats.left(lap, i), bats.tops[i]) { scale(frame.fit, frame.fit, pivot = Offset.Zero) { drawPath(batShape, bat) } }
                        }
                    }
                },
        )
    }
}
