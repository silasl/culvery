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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeJoin
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
import kotlin.math.cos
import kotlin.math.sin

object ChristmasDimens {
    val flakeRadiusMin = 1.5.dp
    val flakeRadiusMax = 3.dp

    /** How far a flake drifts either side of its column. */
    val snowSway = 10.dp

    /** The lights: the gap between bulbs along the wire, a bulb's size and the wire's thickness. */
    val bulbPitch = 26.dp
    val bulbRadius = 4.dp
    val wireStroke = 1.5.dp

    /** The Today card's holly: its width from the card's top-right corner; its height is this times 25 / 40. */
    val hollySpan = 40.dp
}

/** How many flakes fall, and how long each takes to cross the header and come back in at the top. */
internal const val SNOW_FLAKES = 18
internal const val SNOW_FALL_MS = 24_000L

/** How long a bulb's twinkle takes, and how few bulbs make a string. */
internal const val TWINKLE_MS = 4_000L
internal const val BULBS_MIN = 3

class Flake(val centre: Offset, val radius: Float)

// Each flake's own fixed place in the fall, its column and its size, spread by steps coprime with SNOW_FLAKES.
private fun flakeFraction(i: Int, step: Int) = (i * step % SNOW_FLAKES + 0.5f) / SNOW_FLAKES

/**
 * Where the snow is [elapsedMillis] into the drift in [area]. Every flake falls the area's height every [SNOW_FALL_MS], coming
 * back in at the top, and sways once in that time about its column (21 §4.4).
 */
fun snowFlakes(elapsedMillis: Long, area: Rect, density: Density): List<Flake> = with(density) {
    val sway = ChristmasDimens.snowSway.toPx()
    val radiusMin = ChristmasDimens.flakeRadiusMin.toPx()
    val radiusMax = ChristmasDimens.flakeRadiusMax.toPx()
    val room = maxOf(area.width - 2 * sway, 0f)
    val t = (elapsedMillis % SNOW_FALL_MS).toFloat() / SNOW_FALL_MS
    List(SNOW_FLAKES) { i ->
        val fall = (flakeFraction(i, 7) + t) % 1f
        val column = area.left + minOf(sway, area.width / 2) + room * flakeFraction(i, 11)
        Flake(
            Offset(column + sway * sin(2f * PI.toFloat() * fall), area.top + fall * area.height),
            radiusMin + (radiusMax - radiusMin) * flakeFraction(i, 5),
        )
    }
}

/** The bulbs along the wire, a [ChristmasDimens.bulbPitch] apart and centred in [band], each hanging its radius below the wire. */
fun bulbCentres(band: Rect, density: Density): List<Offset> = with(density) {
    val pitch = ChristmasDimens.bulbPitch.toPx()
    val n = fitCount(band.width, pitch, BULBS_MIN)
    val first = band.left + (band.width - n * pitch) / 2 + pitch / 2
    List(n) { i ->
        val x = first + i * pitch
        Offset(x, hangingY(band, x, BirthdayDimens.buntingTop, BirthdayDimens.buntingSag, density) + ChristmasDimens.bulbRadius.toPx())
    }
}

/** How lit bulb [index] is [elapsedMillis] in, between [ChristmasInks.BULB_DIM] and 1; each of four groups runs a quarter period behind the last. */
fun bulbAlpha(elapsedMillis: Long, index: Int): Float {
    val t = (elapsedMillis % TWINKLE_MS).toFloat() / TWINKLE_MS
    val phase = (t - (index % 4) / 4f + 1f) % 1f
    return ChristmasInks.BULB_DIM + (1f - ChristmasInks.BULB_DIM) * (1f + cos(2f * PI.toFloat() * phase)) / 2f
}

@Composable
fun ChristmasSky(drifting: Boolean, modifier: Modifier = Modifier, elapsed: MutableLongState = remember { mutableLongStateOf(0L) }) {
    val colors = Culvery.colors
    SkyFrames(drifting, elapsed)
    val measured = LocalHomeSkyBand.current
    Box(modifier) {
        Spacer(
            Modifier
                .inHomeHeader(measured)
                .testTag("christmas_sky")
                .clearAndSetSemantics {}
                .graphicsLayer { clip = true }
                .drawWithCache {
                    val density = this
                    val band = skyBand(measured, size, density)
                    val wire = hangingPath(band, BirthdayDimens.buntingTop, BirthdayDimens.buntingSag, density)
                    val bulbs = bulbCentres(band, density)
                    val area = Rect(Offset.Zero, size)
                    val snow = ChristmasInks.snow(colors)
                    val bulbInks = ChristmasInks.bulbs(colors)
                    val outline = ChristmasInks.bulbOutline(colors)
                    val bulbRadius = ChristmasDimens.bulbRadius.toPx()
                    val stroke = Stroke(ChristmasDimens.wireStroke.toPx())
                    onDrawBehind {
                        if (size.width <= 0f || size.height <= 0f) return@onDrawBehind
                        val now = elapsed.longValue
                        snowFlakes(now, area, density).forEach { drawCircle(snow, it.radius, it.centre) }
                        if (bulbs.isEmpty()) return@onDrawBehind
                        drawPath(wire, ChristmasInks.wire(colors), style = stroke)
                        bulbs.forEachIndexed { i, at ->
                            val alpha = if (drifting) bulbAlpha(now, i) else 1f
                            drawCircle(bulbInks[i % bulbInks.size], bulbRadius, at, alpha)
                            outline?.let { drawCircle(it, bulbRadius, at, alpha, stroke) }
                        }
                    }
                },
        )
    }
}

// The cap lies along the roof's top edge from 12 units either side of the apex, with a scalloped lower edge; the wreath hangs above the door arch.
private const val MARK_CAP =
    "M39.5,38.2 L49.4,29.72 A3.22,3.22 0 0,1 53.6,29.72 L63.5,38.2 " +
        "Q61.5,42 59,39.6 Q57,43.6 54,40.4 Q51.5,44.2 49,40.4 Q46,43.6 44,39.6 Q41.5,42 39.5,38.2 Z"
private const val MARK_WREATH = "M48.5,58.5 A3,3 0 1,1 54.5,58.5 A3,3 0 1,1 48.5,58.5 Z"
private const val MARK_BOW = "M49.6,55.1 L51.5,56.4 L53.4,55.1 L53.4,57.7 L51.5,56.4 L49.6,57.7 Z"
private const val MARK_WREATH_STROKE = 1.8f
private const val MARK_CAP_LINE = 0.7f

/**
 * The Culvery house, snowed in, taking the mark's place: snow over the roof inside the mark's own viewport with a line of the
 * page colour between it and the roof, a wreath and bow above the door, and the dove in robin red (21 §3.4).
 */
@Composable
fun ChristmasMark(width: Dp, modifier: Modifier = Modifier) {
    val c = Culvery.colors
    val mark = remember(c, width) {
        val robin = SolidColor(ChristmasInks.robin(c))
        ImageVector.Builder(name = "ChristmasMark", defaultWidth = width, defaultHeight = width * (MarkViewport.HEIGHT / MarkViewport.WIDTH), viewportWidth = MarkViewport.WIDTH, viewportHeight = MarkViewport.HEIGHT)
            .addGroup(translationX = -MarkViewport.LEFT, translationY = -MarkViewport.TOP)
            .addPath(addPathNodes(MARK_HOUSE), fill = SolidColor(c.ink))
            .addPath(addPathNodes(MARK_CAP), fill = SolidColor(ChristmasInks.markSnow(c)), stroke = SolidColor(c.bg), strokeLineWidth = MARK_CAP_LINE, strokeLineJoin = StrokeJoin.Round)
            .addPath(addPathNodes(MARK_DOVE), fill = robin)
            .addPath(addPathNodes(MARK_WREATH), stroke = SolidColor(ChristmasInks.wreath(c)), strokeLineWidth = MARK_WREATH_STROKE)
            .addPath(addPathNodes(MARK_BOW), fill = robin)
            .clearGroup()
            .build()
    }
    Image(mark, contentDescription = null, modifier = modifier.testTag("christmas_mark").semantics { hideFromAccessibility() })
}

// The sprig is laid out in a 40 x 25 box against the card's top-right corner; each point is (x from the box's left, y from its top).
private const val HOLLY_BOX_W = 40f
private const val HOLLY_BOX_H = 25f
private val HOLLY_STEM = Offset(28f, 13f)
private val HOLLY_TIPS = listOf(Offset(4f, 6f), Offset(9f, 23f))
private const val HOLLY_LEAF_WIDTH = 4.5f
private val HOLLY_BERRIES = listOf(Offset(27f, 12f), Offset(31f, 11f), Offset(29f, 15.5f))
private const val HOLLY_BERRY_RADIUS = 2.6f

/** Two pointed leaves and three berries within [span] by [span] * 25 / 40 of the top-right corner, drawn flat behind the card's content (21 §3.7). */
fun DrawScope.drawHolly(leaf: Color, berry: Color, span: Dp) {
    val u = span.toPx() / HOLLY_BOX_W
    val origin = Offset(size.width - HOLLY_BOX_W * u, 0f)
    fun at(p: Offset) = origin + p * u
    val stem = at(HOLLY_STEM)
    HOLLY_TIPS.forEach { tipUnits ->
        val tip = at(tipUnits)
        val along = tip - stem
        val side = Offset(-along.y, along.x) / along.getDistance() * (HOLLY_LEAF_WIDTH * u)
        val mid = stem + along * 0.5f
        val shape = Path().apply {
            moveTo(stem.x, stem.y)
            quadraticTo(mid.x + side.x, mid.y + side.y, tip.x, tip.y)
            quadraticTo(mid.x - side.x, mid.y - side.y, stem.x, stem.y)
            close()
        }
        drawPath(shape, leaf)
    }
    HOLLY_BERRIES.forEach { drawCircle(berry, HOLLY_BERRY_RADIUS * u, at(it)) }
}
