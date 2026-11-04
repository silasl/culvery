package uk.co.siland.culvery.capability.calendar.ui

import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import uk.co.siland.culvery.capability.calendar.EventUi
import uk.co.siland.culvery.core.ui.BinInks
import uk.co.siland.culvery.core.ui.Culvery

/** The edge bands of an event's row: a stripe per bin in the current theme's shade of its ink, else one per person. */
@Composable
internal fun EventUi.stripes(): List<Stripe> {
    val colours = Culvery.colors
    return bins?.map { Stripe(BinInks.colour(it.ink, colours), BinInks.edge(it.ink, colours)) } ?: people.map { Stripe(Color(it.color)) }
}
