package uk.co.siland.culvery.core.ui

import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.AlignmentLine
import androidx.compose.ui.layout.FirstBaseline
import androidx.compose.ui.layout.LastBaseline
import androidx.compose.ui.layout.layout
import androidx.compose.ui.unit.TextUnit

/**
 * Makes a single line of text [height] tall, centred on its natural box, with baselines moving with the text. Compose
 * won't shrink one line below the font's ascent and descent through the text style's line height, so the box is set here;
 * glyphs may draw outside it.
 */
fun Modifier.lineBox(height: TextUnit): Modifier = layout { measurable, constraints ->
    val placeable = measurable.measure(constraints)
    val boxHeight = height.roundToPx()
    val offset = (boxHeight - placeable.height) / 2
    val lines = mapOf<AlignmentLine, Int>(FirstBaseline to placeable[FirstBaseline] + offset, LastBaseline to placeable[LastBaseline] + offset)
    layout(placeable.width, boxHeight, lines) { placeable.place(0, offset) }
}
