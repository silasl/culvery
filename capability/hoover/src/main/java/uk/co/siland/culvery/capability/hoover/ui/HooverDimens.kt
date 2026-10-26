package uk.co.siland.culvery.capability.hoover.ui

import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import uk.co.siland.culvery.core.ui.HhType

/** The Hoover row's layout values (11 §4.4); provisional until the end-of-v1 design walk-through. */
internal object HooverDimens {
    val radius = 17.dp
    val paddingV = 10.dp
    val paddingH = 12.dp
    val gap = 12.dp
    val icon = 24.dp
    val statusStyle = HhType.secondary.copy(fontSize = 12.sp)
    const val STATUS_LINES = 2

    val pillHeight = 48.dp
    val pillPaddingH = 20.dp
    val pillEdge = 1.dp
}
