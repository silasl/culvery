package uk.co.siland.culvery.capability.calendar.ui

import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import uk.co.siland.culvery.capability.calendar.EventUi
import uk.co.siland.culvery.core.ui.BinInks
import uk.co.siland.culvery.core.ui.Culvery

/** The edge colours of an event's row: a stripe per bin in the current theme's shade of its ink, else one per person. */
@Composable
internal fun EventUi.stripes(): List<Color> {
    val colours = Culvery.colors
    return bins?.map { BinInks.colour(it.ink, colours) } ?: people.map { Color(it.color) }
}
