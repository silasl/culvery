package uk.co.siland.culvery.core.ui

import androidx.compose.ui.Modifier
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
    layout(placeable.width, boxHeight) { placeable.place(0, offset) }
}
