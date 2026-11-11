package uk.co.siland.culvery.core.ui

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
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.unit.Density
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
