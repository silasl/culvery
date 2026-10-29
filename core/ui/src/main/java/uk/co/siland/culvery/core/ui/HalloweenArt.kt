package uk.co.siland.culvery.core.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.MutableLongState
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.withFrameMillis
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
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
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.hideFromAccessibility
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.Dp
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin
import kotlinx.coroutines.delay

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
    val stroke = Stroke(SeasonalDimens.webStroke.toPx())

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

// A bat in a 26 × 13 box, wing tip to wing tip: ears, scalloped wings, a pointed tail.
private const val BAT_W = 26f
private const val BAT_PATH =
    "M13,3.5 L11.6,0.5 L10.6,3.2 C8.5,2.4 4.5,2 0,1.2 C1.6,4 2.2,7 3,9 C4.6,7.4 6,7.2 7.6,8.6 C8.6,7.4 9.6,7.6 10.6,9 " +
        "L13,12.5 L15.4,9 C16.4,7.6 17.4,7.4 18.4,8.6 C20,7.2 21.4,7.4 23,9 C23.8,7 24.4,4 26,1.2 C21.5,2 17.5,2.4 15.4,3.2 L14.4,0.5 Z"

/**
 * Where each bat's box starts (left edge, top), [elapsedMillis] into the drift. The bats start a quarter of a lap apart, each in
 * its lane, and cross from a bat's width off the left edge to the right edge once every [BAT_LAP_MS] (14 §4.3).
 */
fun batPositions(elapsedMillis: Long, size: Size, headerBottomPx: Float, density: Density): List<Offset> = with(density) {
    val span = SeasonalDimens.batSpan.toPx()
    val cycle = size.width + span
    val lap = (elapsedMillis % BAT_LAP_MS).toDouble() / BAT_LAP_MS
    val tops = listOf(SeasonalDimens.batTopLane.toPx()) + SeasonalDimens.batLanesBelowHeader.map { headerBottomPx + it.toPx() }
    tops.mapIndexed { i, top -> Offset(((lap + (i + 0.5) / BATS) % 1.0 * cycle - span).toFloat(), top) }
}

/**
 * The bats behind Home. Nothing in it is read by TalkBack. While [drifting] it advances [elapsed] about every
 * [SKY_FRAME_MS]; the time is read only when drawing, so a step redraws this layer and nothing else (14 §4.3).
 */
@Composable
fun HalloweenSky(drifting: Boolean, modifier: Modifier = Modifier, elapsed: MutableLongState = remember { mutableLongStateOf(0L) }) {
    val colors = Culvery.colors
    val bat = HalloweenInks.bat(colors)
    val headerBottom = LocalHomeHeaderBottom.current
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
    Canvas(modifier.testTag("halloween_sky").clearAndSetSemantics {}.graphicsLayer()) {
        val fit = SeasonalDimens.batSpan.toPx() / BAT_W
        batPositions(elapsed.longValue, size, (headerBottom?.px ?: 0).toFloat(), this).forEach { at ->
            translate(at.x, at.y) { scale(fit, fit, pivot = Offset.Zero) { drawPath(batShape, bat) } }
        }
    }
}
