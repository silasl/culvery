package uk.co.siland.culvery.core.ui

import androidx.compose.foundation.Image
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.addPathNodes
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.hideFromAccessibility
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Dp
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

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
