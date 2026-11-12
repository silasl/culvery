package uk.co.siland.culvery.core.ui

import androidx.compose.foundation.Image
import androidx.compose.runtime.Composable
import androidx.compose.runtime.MutableLongState
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.graphics.vector.addPathNodes
import androidx.compose.ui.graphics.vector.toPath
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.hideFromAccessibility
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin
import kotlin.random.Random

object ChristmasDimens {
    val flakeRadiusMin = 1.5.dp
    val flakeRadiusMax = 3.dp

    /** How far a flake drifts either side of its column. */
    val snowSway = 10.dp

    /** The lights: the gap between bulbs along the wire, a bulb's size and the wire's thickness. */
    val bulbPitch = 26.dp
    val bulbRadius = 4.dp
    val wireStroke = 1.5.dp

    /** The holly sprig's drawing unit (a leaf is 28 long), and how far in from the card's top-right corner its berries sit. */
    val hollyUnit = 1.3.dp
    val hollyInset = 3.dp
}

/** How many flakes fall, and how long each takes to cross the header and come back in at the top. */
internal const val SNOW_FLAKES = 9
internal const val SNOW_FALL_MS = 24_000L

/** How long a bulb's twinkle takes, and how few bulbs make a string. */
internal const val TWINKLE_MS = 4_000L
internal const val TWINKLE_GROUPS = 4
internal const val BULBS_MIN = 3

class Flake(val centre: Offset, val radius: Float)

/**
 * A fixed scatter, so the snow looks random but draws the same on every run. Each flake has its own slot across the width and
 * its own slot down the fall, shuffled apart and jittered within the slot, so no two line up; and its own size, sway and sway phase.
 */
private object SnowScatter {
    private val random = Random(232)
    private fun slots() = (0 until SNOW_FLAKES).shuffled(random).map { (it + 0.15f + 0.7f * random.nextFloat()) / SNOW_FLAKES }.toFloatArray()

    val column = slots()
    val start = slots()
    val size = FloatArray(SNOW_FLAKES) { random.nextFloat() }
    val sway = FloatArray(SNOW_FLAKES) { 0.4f + 0.6f * random.nextFloat() }
    val swayPhase = FloatArray(SNOW_FLAKES) { random.nextFloat() }
}

/** Each flake's column and radius in [area], worked out once per size so a step allocates nothing. */
private class SnowField(private val area: Rect, density: Density) {
    private val sway = with(density) { ChristmasDimens.snowSway.toPx() }
    private val columns = FloatArray(SNOW_FLAKES) { i ->
        area.left + minOf(sway, area.width / 2) + maxOf(area.width - 2 * sway, 0f) * SnowScatter.column[i]
    }
    val radii = with(density) {
        val min = ChristmasDimens.flakeRadiusMin.toPx()
        val max = ChristmasDimens.flakeRadiusMax.toPx()
        FloatArray(SNOW_FLAKES) { i -> min + (max - min) * SnowScatter.size[i] }
    }

    fun fall(t: Float, i: Int) = (SnowScatter.start[i] + t) % 1f
    fun x(fall: Float, i: Int) = columns[i] + sway * SnowScatter.sway[i] * sin(2f * PI.toFloat() * (fall + SnowScatter.swayPhase[i]))
    fun y(fall: Float) = area.top + fall * area.height
}

private fun fallAt(elapsedMillis: Long) = (elapsedMillis % SNOW_FALL_MS).toFloat() / SNOW_FALL_MS

/**
 * Where the snow is [elapsedMillis] into the drift in [area]. Every flake falls the area's height every [SNOW_FALL_MS], coming
 * back in at the top, and sways once in that time about its column (21 §4.4).
 */
fun snowFlakes(elapsedMillis: Long, area: Rect, density: Density): List<Flake> {
    val field = SnowField(area, density)
    val t = fallAt(elapsedMillis)
    return List(SNOW_FLAKES) { i ->
        val fall = field.fall(t, i)
        Flake(Offset(field.x(fall, i), field.y(fall)), field.radii[i])
    }
}

/** The bulbs along the wire, a [ChristmasDimens.bulbPitch] apart and centred in [band], each hanging its radius below the wire. */
fun bulbCentres(band: Rect, density: Density): List<Offset> = with(density) {
    hungRow(band, ChristmasDimens.bulbPitch.toPx(), BULBS_MIN).map { x -> Offset(x, hangingY(band, x, density) + ChristmasDimens.bulbRadius.toPx()) }
}

/** How lit bulb [index] is [elapsedMillis] in, between [ChristmasInks.BULB_DIM] and 1; each of [TWINKLE_GROUPS] groups runs a step behind the last. */
fun bulbAlpha(elapsedMillis: Long, index: Int): Float {
    val t = (elapsedMillis % TWINKLE_MS).toFloat() / TWINKLE_MS
    val phase = (t - (index % TWINKLE_GROUPS) / TWINKLE_GROUPS.toFloat() + 1f) % 1f
    return ChristmasInks.BULB_DIM + (1f - ChristmasInks.BULB_DIM) * (1f + cos(2f * PI.toFloat() * phase)) / 2f
}

/**
 * Snow across Home's whole header, behind the clock, and the lights where the birthday bunting hangs. Nothing in it is read by
 * TalkBack. While [drifting] the snow falls and the bulbs twinkle; still, it runs no frames and the bulbs stay lit (21 §4.4).
 */
@Composable
fun ChristmasSky(drifting: Boolean, modifier: Modifier = Modifier, elapsed: MutableLongState = remember { mutableLongStateOf(0L) }) {
    val colors = Culvery.colors
    SkyLayer("christmas_sky", drifting, elapsed, modifier, { inHomeHeader(it) }) { measured ->
        val density = this
        val band = skyBand(measured, size, density)
        val wire = hangingPath(band, density)
        val bulbs = bulbCentres(band, density)
        val field = SnowField(Rect(Offset.Zero, size), density)
        val snow = ChristmasInks.snow(colors)
        val wireInk = ChristmasInks.wire(colors)
        val bulbInks = ChristmasInks.bulbs(colors)
        val outline = ChristmasInks.bulbOutline(colors)
        val bulbRadius = ChristmasDimens.bulbRadius.toPx()
        val stroke = Stroke(ChristmasDimens.wireStroke.toPx())
        val alphas = FloatArray(TWINKLE_GROUPS)
        return@SkyLayer { now ->
            val t = fallAt(now)
            for (i in 0 until SNOW_FLAKES) {
                val fall = field.fall(t, i)
                drawCircle(snow, field.radii[i], Offset(field.x(fall, i), field.y(fall)))
            }
            if (bulbs.isNotEmpty()) {
                for (g in alphas.indices) alphas[g] = if (drifting) bulbAlpha(now, g) else 1f
                drawPath(wire, wireInk, style = stroke)
                for (i in bulbs.indices) {
                    val alpha = alphas[i % TWINKLE_GROUPS]
                    drawCircle(bulbInks[i % bulbInks.size], bulbRadius, bulbs[i], alpha)
                    outline?.let { drawCircle(it, bulbRadius, bulbs[i], alpha, stroke) }
                }
            }
        }
    }
}

// The cap lies along the roof's top edge from 12 units either side of the apex, with a scalloped lower edge; the wreath hangs above the door arch.
private const val MARK_CAP =
    "M39.5,38.2 L49.4,29.72 A3.22,3.22 0 0,1 53.6,29.72 L63.5,38.2 " +
        "Q61.5,42 59,39.6 Q57,43.6 54,40.4 Q51.5,44.2 49,40.4 Q46,43.6 44,39.6 Q41.5,42 39.5,38.2 Z"
private const val MARK_WREATH = "M48.5,58 A3,3 0 1,1 54.5,58 A3,3 0 1,1 48.5,58 Z"
private const val MARK_BOW = "M49.6,54.6 L51.5,55.9 L53.4,54.6 L53.4,57.2 L51.5,55.9 L49.6,57.2 Z"
private const val MARK_WREATH_STROKE = 1.8f
private const val MARK_CAP_LINE = 0.9f

/**
 * The Culvery house, snowed in, taking the mark's place: snow over the roof inside the mark's own viewport with a line of the
 * page colour between it and the roof, a wreath and bow above the door, and the dove in robin red (21 §3.4).
 */
@Composable
fun ChristmasMark(width: Dp, modifier: Modifier = Modifier) {
    val c = Culvery.colors
    val mark = remember(c, width) {
        val robin = SolidColor(ChristmasInks.robin(c))
        markVector("ChristmasMark", width) {
            addPath(addPathNodes(MARK_HOUSE), fill = SolidColor(c.ink))
            addPath(addPathNodes(MARK_CAP), fill = SolidColor(ChristmasInks.markSnow(c)), stroke = SolidColor(c.bg), strokeLineWidth = MARK_CAP_LINE, strokeLineJoin = StrokeJoin.Round)
            addPath(addPathNodes(MARK_DOVE), fill = robin)
            addPath(addPathNodes(MARK_WREATH), stroke = SolidColor(ChristmasInks.wreath(c)), strokeLineWidth = MARK_WREATH_STROKE)
            addPath(addPathNodes(MARK_BOW), fill = robin)
        }
    }
    Image(mark, contentDescription = null, modifier = modifier.testTag("christmas_mark").semantics { hideFromAccessibility() })
}

// One holly leaf, 28 units long, pointing along +x from its stalk at the origin, with three spikes a side.
private val HOLLY_LEAF = addPathNodes(
    "M0,0 Q3,-5 6,-4.5 L8,-8 Q10.5,-5 13,-6 L15.5,-9 Q17.5,-5.5 20,-5.5 L22.5,-8 Q24,-4 28,0 " +
        "Q24,4 22.5,8 L20,5.5 Q17.5,5.5 15.5,9 L13,6 Q10.5,5 8,8 L6,4.5 Q3,5 0,0 Z",
).toPath()
private const val HOLLY_RIB_END = 25f
private const val HOLLY_RIB_WIDTH = 0.9f

// One leaf runs along the card's top edge and one down its right side; the berries sit where they meet.
private val HOLLY_LEAF_ANGLES = floatArrayOf(172f, 112f)
private val HOLLY_BERRIES = listOf(Offset(-2f, -1f), Offset(2f, -4f), Offset(2f, 2f))
private const val HOLLY_BERRY_RADIUS = 3.4f

/** A sprig of holly pinned on the card's top-right corner, half over its edge: two spiked leaves and three berries (21 §3.7). */
fun DrawScope.drawHollySprig(c: HhColors) {
    val u = ChristmasDimens.hollyUnit.toPx()
    val inset = ChristmasDimens.hollyInset.toPx()
    val leaf = ChristmasInks.hollyLeaf(c)
    val rib = ChristmasInks.hollyRib(c)
    val berry = ChristmasInks.hollyBerry(c)
    withTransform({
        translate(size.width - inset, inset)
        scale(u, u, Offset.Zero)
    }) {
        HOLLY_LEAF_ANGLES.forEach { angle ->
            rotate(angle, Offset.Zero) {
                drawPath(HOLLY_LEAF, leaf)
                drawLine(rib, Offset(1f, 0f), Offset(HOLLY_RIB_END, 0f), HOLLY_RIB_WIDTH)
            }
        }
        HOLLY_BERRIES.forEach { drawCircle(berry, HOLLY_BERRY_RADIUS, it) }
    }
}
