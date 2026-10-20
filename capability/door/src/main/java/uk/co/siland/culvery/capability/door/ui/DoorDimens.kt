package uk.co.siland.culvery.capability.door.ui

import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/** The door's layout values (5 §5); provisional until the end-of-v1 design walk-through. */
internal object DoorDimens {
    // The card (handoff 2a): `surf` with a `surf3` edge and a deep shadow, the bell in an `accentSoft` circle.
    val cardWidth = 747.dp
    val cardEdge = 1.dp
    val cardRadius = 29.dp
    val cardPadding = 24.dp
    val cardGap = 17.dp
    val cardShadow = 27.dp
    val bellCircle = 59.dp
    val bellIcon = 33.dp
    val titleSize = 31.sp
    val lineSize = 17.sp
    val chipHeight = 35.dp
    val chipIcon = 20.dp
    val chipTextSize = 15.sp
    val chipPaddingH = 12.dp
    val chipIconGap = 6.dp
    val cardSnapshotRadius = 19.dp

    const val SNAPSHOT_RATIO = 16f / 9f

    // The full-size sheet's picture.
    val snapshotRadius = 16.dp

    // The tab: its rows, a day's label, and the thumbnails, decoded at twice their width.
    val rowGap = 10.dp
    val sectionTop = 18.dp
    val thumbWidth = 64.dp
    val thumbHeight = 36.dp
    val thumbRadius = 10.dp
    val thumbTouchHeight = 48.dp
    const val THUMB_PX = 128

    // The full-size sheet's padding, as the calendar's sheets.
    val sheetPaddingH = 30.dp
    val sheetPaddingV = 28.dp

    // Settings › Door: blocks spaced as Settings' other pages; the volume in tenths.
    val settingsGap = 16.dp
    const val VOLUME_STEPS = 9
}
