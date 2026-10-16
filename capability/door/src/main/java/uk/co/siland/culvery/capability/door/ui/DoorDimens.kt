package uk.co.siland.culvery.capability.door.ui

import androidx.compose.ui.unit.dp
import uk.co.siland.culvery.core.ui.ShellTokens

/** The door's layout values (5 §5); provisional until the end-of-v1 design walk-through. */
internal object DoorDimens {
    // The card: `surf` with a `line` border, Home's cards' radius.
    val cardRadius = ShellTokens.homeCardRadius
    val cardBorder = 1.dp
    val cardPadding = 22.dp
    val cardGap = 16.dp
    val cardIcon = 34.dp
    val cardIconGap = 14.dp

    const val SNAPSHOT_RATIO = 16f / 9f
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
    const val QUIET_STEP_MINUTES = 30L
    const val VOLUME_STEPS = 9
}
