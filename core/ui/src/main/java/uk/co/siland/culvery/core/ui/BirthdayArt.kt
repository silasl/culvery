package uk.co.siland.culvery.core.ui

import androidx.compose.foundation.Image
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.addPathNodes
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.hideFromAccessibility
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Dp

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
