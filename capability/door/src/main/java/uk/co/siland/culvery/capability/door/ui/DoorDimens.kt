package uk.co.siland.culvery.capability.door.ui

import androidx.compose.ui.unit.dp
import uk.co.siland.culvery.core.ui.ShellTokens

/** The door's layout values (5 §5); provisional until the end-of-v1 design walk-through. */
internal object DoorDimens {
    // The card: `surf` with a 1 dp `line` border, Home's cards' radius; padding 22, 16 between blocks; a 34 dp doorbell 14 from its words.
    val cardRadius = ShellTokens.homeCardRadius
    val cardBorder = 1.dp
    val cardPadding = 22.dp
    val cardGap = 16.dp
    val cardIcon = 34.dp
    val cardIconGap = 14.dp

    // A snapshot at 16:9, radius 16.
    const val SNAPSHOT_RATIO = 16f / 9f
    val snapshotRadius = 16.dp

    // The tab: rows 10 apart, 18 above a day's label; thumbnails 64 × 36 (16:9), radius 10, read at 128 px.
    val rowGap = 10.dp
    val sectionTop = 18.dp
    val thumbWidth = 64.dp
    val thumbHeight = 36.dp
    val thumbRadius = 10.dp
    val thumbTouchHeight = 48.dp
    const val THUMB_PX = 128

    // The status line: a 20 dp icon 8 from its words.
    val statusIcon = 20.dp
    val statusGap = 8.dp

    // The full-size sheet's padding, as the calendar's sheets.
    val sheetPaddingH = 30.dp
    val sheetPaddingV = 28.dp
}
