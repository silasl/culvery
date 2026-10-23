package uk.co.siland.culvery.capability.shopping.ui

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.unit.Dp

/**
 * One line of whole children, [gap] apart: the first that would not fit is dropped, and everything after it, so the row
 * never wraps and never cuts a chip (10 ruling 14). A dropped child is measured but not placed.
 */
@Composable
internal fun FitRow(gap: Dp, modifier: Modifier = Modifier, content: @Composable () -> Unit) {
    Layout(content, modifier) { measurables, constraints ->
        val gapPx = gap.roundToPx()
        val placeables = measurables.map { it.measure(constraints.copy(minWidth = 0, minHeight = 0)) }
        var used = 0
        var fitting = 0
        for (placeable in placeables) {
            val next = if (fitting == 0) placeable.width else used + gapPx + placeable.width
            if (next > constraints.maxWidth) break
            used = next
            fitting++
        }
        val shownChildren = placeables.take(fitting)
        val height = (shownChildren.maxOfOrNull { it.height } ?: 0).coerceIn(constraints.minHeight, constraints.maxHeight)
        layout(used.coerceAtLeast(constraints.minWidth), height) {
            var x = 0
            shownChildren.forEach {
                it.placeRelative(x, (height - it.height) / 2)
                x += it.width + gapPx
            }
        }
    }
}
