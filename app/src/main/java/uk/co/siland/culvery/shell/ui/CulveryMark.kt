package uk.co.siland.culvery.shell.ui

import androidx.compose.foundation.Image
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.addPathNodes
import androidx.compose.ui.semantics.hideFromAccessibility
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Dp
import uk.co.siland.culvery.core.ui.Culvery

/** The launcher icon's house (ic_launcher_foreground.xml), in its 108-unit square. */
internal const val MARK_HOUSE =
    "M31.06,53.92 A3.22,3.22 0 0,1 26.86,49.04 L49.4,29.72 A3.22,3.22 0 0,1 53.6,29.72 L76.14,49.04 A3.22,3.22 0 0,1 71.94,53.92 L70.82,52.96 L70.82,74.48 A2.76,2.76 0 0,1 68.06,77.24 L57.94,77.24 L57.94,68.96 A6.44,6.44 0 0,0 45.06,68.96 L45.06,77.24 L34.94,77.24 A2.76,2.76 0 0,1 32.18,74.48 L32.18,52.96 L31.06,53.92 Z"

/** The launcher icon's dove on the right eave. */
internal const val MARK_DOVE =
    "M71.67,42.8 C74.32,42.58 76.52,40.15 77.19,37.28 C77.41,36.17 77.63,35.29 77.96,34.63 L79.84,33.96 L78.18,33.3 C77.85,31.76 76.52,30.87 75.31,30.87 C73.87,30.87 72.99,31.98 72.77,33.3 C71.67,34.19 69.24,34.41 67.25,33.96 L61.51,31.09 C60.63,30.65 59.96,31.76 60.63,32.42 L67.03,38.16 C68.35,40.15 69.68,42.36 71.67,42.8 Z"

// The two paths' bounds in the 108-unit square, rounded out: the mark without the icon's safe zone.
private const val LEFT = 26f
private const val TOP = 29f
private const val WIDTH = 54f
private const val HEIGHT = 49f

/** The Culvery mark, [width] wide: the house in `ink`, the dove in `weather`'s gold. A picture only; TalkBack skips it. */
@Composable
internal fun CulveryMark(width: Dp, modifier: Modifier = Modifier) {
    val c = Culvery.colors
    val mark = remember(c.ink, c.weather, width) {
        ImageVector.Builder(name = "CulveryMark", defaultWidth = width, defaultHeight = width * (HEIGHT / WIDTH), viewportWidth = WIDTH, viewportHeight = HEIGHT)
            .addGroup(translationX = -LEFT, translationY = -TOP)
            .addPath(addPathNodes(MARK_HOUSE), fill = SolidColor(c.ink))
            .addPath(addPathNodes(MARK_DOVE), fill = SolidColor(c.weather))
            .clearGroup()
            .build()
    }
    Image(mark, contentDescription = null, modifier = modifier.semantics { hideFromAccessibility() })
}
