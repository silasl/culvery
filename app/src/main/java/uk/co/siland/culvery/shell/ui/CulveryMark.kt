package uk.co.siland.culvery.shell.ui

import androidx.compose.foundation.Image
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.addPathNodes
import androidx.compose.ui.semantics.hideFromAccessibility
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Dp
import uk.co.siland.culvery.core.ui.Culvery
import uk.co.siland.culvery.core.ui.MARK_DOVE
import uk.co.siland.culvery.core.ui.MARK_HOUSE
import uk.co.siland.culvery.core.ui.markVector

/** The Culvery mark, [width] wide: the house in `ink`, the dove in `weather`'s gold. A picture only; TalkBack skips it. */
@Composable
internal fun CulveryMark(width: Dp, modifier: Modifier = Modifier) {
    val c = Culvery.colors
    val mark = remember(c.ink, c.weather, width) {
        markVector("CulveryMark", width) {
            addPath(addPathNodes(MARK_HOUSE), fill = SolidColor(c.ink))
            addPath(addPathNodes(MARK_DOVE), fill = SolidColor(c.weather))
        }
    }
    Image(mark, contentDescription = null, modifier = modifier.semantics { hideFromAccessibility() })
}
