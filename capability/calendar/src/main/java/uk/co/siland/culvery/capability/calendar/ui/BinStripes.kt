package uk.co.siland.culvery.capability.calendar.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import uk.co.siland.culvery.capability.calendar.CalendarRepository
import uk.co.siland.culvery.capability.calendar.EventUi
import uk.co.siland.culvery.capability.calendar.binInkFor
import uk.co.siland.culvery.capability.calendar.orderedBins
import uk.co.siland.culvery.core.ui.BinInks
import uk.co.siland.culvery.core.ui.Culvery

/** The family's ink per kind of bin; empty means every kind in its default. */
internal val LocalBinInks = staticCompositionLocalOf<Map<String, String>> { emptyMap() }

/** One stripe colour per bin, in [orderedBins] order, in the current theme's variant of each ink. */
@Composable
internal fun binStripes(bins: List<String>): List<Color> {
    val choices = LocalBinInks.current
    val colours = Culvery.colors
    return orderedBins(bins).map { BinInks.colour(binInkFor(choices, it), colours) }
}

/** The edge colours of an event's row: a stripe per bin on a bin day, else one per person. */
@Composable
internal fun EventUi.stripes(): List<Color> = bins?.let { binStripes(it) } ?: people.map { Color(it.color) }

/** Shows [content] with the bins in the colours the family chose. Overlays are composed elsewhere, so each host provides its own. */
@Composable
internal fun WithBinInks(repo: CalendarRepository, content: @Composable () -> Unit) {
    val inks by repo.binInks.collectAsState()
    CompositionLocalProvider(LocalBinInks provides inks, content = content)
}
