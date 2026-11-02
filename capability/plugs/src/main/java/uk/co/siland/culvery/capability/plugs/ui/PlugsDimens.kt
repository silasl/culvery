package uk.co.siland.culvery.capability.plugs.ui

import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/** The plug tiles' layout values (15a §4.5, px ÷ 1.5); provisional until the end-of-v1 design walk-through. */
internal object PlugsDimens {
    val lineGap = 10.dp

    // A plug's tile: name over state, the round button at the bottom right. Four to a row.
    val tileGap = 12.dp
    val tileRadius = 19.dp
    val tilePaddingV = 15.dp
    val tilePaddingH = 17.dp
    val tileMinHeight = 100.dp
    val nameSize = 17.sp
    val statusSize = 13.sp
    val textGap = 4.dp
    val lock = 20.dp
    val tilesPerRow = 4

    // The section label's icon.
    val labelIconGap = 8.dp
    val labelIcon = 20.dp
}
