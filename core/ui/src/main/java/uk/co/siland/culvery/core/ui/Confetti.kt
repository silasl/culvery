package uk.co.siland.culvery.core.ui

import androidx.compose.foundation.layout.Spacer
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.withFrameMillis
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import kotlin.math.PI
import kotlin.math.sin
import kotlin.random.Random

/** A burst is over when its last piece has left the bottom. */
const val CONFETTI_MS = 2_500L

/** Past this nothing is left to draw. */
const val CONFETTI_GONE_MS = 3_000L

const val CONFETTI_COUNT = 60

private const val FALL_SHORTEST_MS = 1_800L
private const val SPIN_SLOWEST = 180f
private const val SPIN_FASTEST = 540f
private const val SWAY_HERTZ_SLOWEST = 0.4f
private const val SWAY_HERTZ_FASTEST = 1.2f
private const val START_ABOVE = 0.3f

object ConfettiDimens {
    val pieceWidth = 8.dp
    val pieceHeight = 12.dp

    /** How far past the bottom a piece ends: more than its half-diagonal, so it is wholly out of sight. */
    val exitMargin = 12.dp
    val sway = 24.dp
}

/** One piece at one moment: where its centre is, how it is turned and what colour. */
data class ConfettiPiece(val centre: Offset, val angleDegrees: Float, val colour: Color)

/** What one piece of a burst is, apart from the box and the moment: the draws from its seed, as fractions of the box where they place it. */
internal class ConfettiParams(
    private val x: Float,
    private val above: Float,
    private val fallMs: Long,
    private val swayPhase: Float,
    private val swayHertz: Float,
    private val angle: Float,
    private val spin: Float,
) {
    fun at(elapsedMillis: Long, size: Size, sway: Float, bottom: Float, colour: Color): ConfettiPiece {
        val seconds = elapsedMillis / 1_000f
        val left = x * size.width
        val top = -START_ABOVE * size.height * above
        val progress = (elapsedMillis / fallMs.toFloat()).coerceAtMost(1f)
        val fallen = 0.4f * progress + 0.6f * progress * progress
        return ConfettiPiece(
            centre = Offset(left + sway * sin(swayPhase + 2f * PI.toFloat() * swayHertz * seconds), top + (bottom - top) * fallen),
            angleDegrees = angle + spin * seconds,
            colour = colour,
        )
    }
}

/** The [CONFETTI_COUNT] pieces of the burst [seed], the same for the same seed. */
internal fun confettiParams(seed: Long): List<ConfettiParams> {
    val random = Random(seed)
    return List(CONFETTI_COUNT) {
        ConfettiParams(
            x = random.nextFloat(),
            above = random.nextFloat(),
            fallMs = FALL_SHORTEST_MS + random.nextLong(CONFETTI_MS - FALL_SHORTEST_MS + 1),
            swayPhase = random.nextFloat() * 2f * PI.toFloat(),
            swayHertz = SWAY_HERTZ_SLOWEST + random.nextFloat() * (SWAY_HERTZ_FASTEST - SWAY_HERTZ_SLOWEST),
            angle = random.nextFloat() * 360f,
            spin = (SPIN_SLOWEST + random.nextFloat() * (SPIN_FASTEST - SPIN_SLOWEST)) * if (random.nextBoolean()) 1f else -1f,
        )
    }
}

/** Where [params]' pieces are [elapsedMillis] into the burst in a box of [size]; none from [CONFETTI_GONE_MS]. */
internal fun confettiPieces(params: List<ConfettiParams>, elapsedMillis: Long, size: Size, colours: List<Color>, density: Density): List<ConfettiPiece> {
    if (elapsedMillis >= CONFETTI_GONE_MS || colours.isEmpty()) return emptyList()
    val sway = with(density) { ConfettiDimens.sway.toPx() }
    val bottom = size.height + with(density) { ConfettiDimens.exitMargin.toPx() }
    return params.mapIndexed { i, p -> p.at(elapsedMillis, size, sway, bottom, colours[i % colours.size]) }
}

/**
 * The 60 pieces of the burst [seed] at [elapsedMillis] in a box of [size] (16 §2.3). Each starts between 0.3 of the height
 * above the top and the top, speeds up as it falls, and is below the bottom by [CONFETTI_MS]; none is left from
 * [CONFETTI_GONE_MS]. The same arguments give the same pieces.
 */
fun confettiPieces(seed: Long, elapsedMillis: Long, size: Size, colours: List<Color>, density: Density): List<ConfettiPiece> =
    confettiPieces(confettiParams(seed), elapsedMillis, size, colours, density)

/**
 * Confetti over the whole room it is given for [CONFETTI_MS], then [onDone] once. It frames only while it is composed, so
 * nothing runs once it is dropped. It takes no touch and TalkBack finds nothing in it.
 */
@Composable
fun ConfettiBurst(seed: Long, onDone: () -> Unit, modifier: Modifier = Modifier) {
    val accent = Culvery.colors.accent
    val colours = remember(accent) { PersonPalette.colors.map(::Color) + accent }
    val density = LocalDensity.current
    val elapsed = remember(seed) { mutableLongStateOf(0L) }
    val params = remember(seed) { confettiParams(seed) }
    val done by rememberUpdatedState(onDone)
    LaunchedEffect(seed) {
        var first = -1L
        while (elapsed.longValue < CONFETTI_MS) {
            withFrameMillis { now ->
                if (first < 0) first = now
                elapsed.longValue = now - first
            }
        }
        done()
    }
    Spacer(
        modifier
            .testTag("confetti")
            .clearAndSetSemantics {}
            .graphicsLayer { clip = true }
            .drawBehind {
                val width = ConfettiDimens.pieceWidth.toPx()
                val height = ConfettiDimens.pieceHeight.toPx()
                confettiPieces(params, elapsed.longValue, size, colours, density).forEach { piece ->
                    rotate(piece.angleDegrees, piece.centre) {
                        drawRect(piece.colour, piece.centre - Offset(width / 2f, height / 2f), Size(width, height))
                    }
                }
            },
    )
}
