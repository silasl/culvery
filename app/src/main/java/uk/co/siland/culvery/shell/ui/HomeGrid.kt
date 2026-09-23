package uk.co.siland.culvery.shell.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.dp
import uk.co.siland.culvery.core.plugin.HomeCardPlacer
import uk.co.siland.culvery.core.plugin.HomePlacement

private val COLUMN_WEIGHTS = floatArrayOf(1.15f, 1f, 1f)

/** The hand-off's 3-column (1.15fr 1fr 1fr) × 2-row Home grid with 14 dp gaps. */
@Composable
fun HomeGrid(placements: List<HomePlacement>, modifier: Modifier = Modifier) {
    Layout(
        modifier = modifier,
        content = { placements.forEach { p -> Box { p.card.content() } } },
    ) { measurables, constraints ->
        val gap = 14.dp.roundToPx()
        val available = constraints.maxWidth - gap * (HomeCardPlacer.COLUMNS - 1)
        val colWidths = COLUMN_WEIGHTS.map { (available * it / COLUMN_WEIGHTS.sum()).toInt() }.toMutableList()
        colWidths[colWidths.lastIndex] += available - colWidths.sum()
        val colX = colWidths.runningFold(0) { x, w -> x + w + gap }
        val rowHeight = (constraints.maxHeight - gap * (HomeCardPlacer.ROWS - 1)) / HomeCardPlacer.ROWS

        val placeables = measurables.mapIndexed { i, m ->
            val p = placements[i]
            val w = (p.col until p.col + p.colSpan).sumOf { colWidths[it] } + gap * (p.colSpan - 1)
            val h = rowHeight * p.rowSpan + gap * (p.rowSpan - 1)
            m.measure(Constraints.fixed(w, h))
        }
        layout(constraints.maxWidth, constraints.maxHeight) {
            placeables.forEachIndexed { i, placeable ->
                val p = placements[i]
                placeable.place(colX[p.col], p.row * (rowHeight + gap))
            }
        }
    }
}
