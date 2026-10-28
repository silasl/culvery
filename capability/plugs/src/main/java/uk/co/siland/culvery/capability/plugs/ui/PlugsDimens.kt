package uk.co.siland.culvery.capability.plugs.ui

import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/** The Plugs tab's layout values (12 §4.3); provisional until the end-of-v1 design walk-through. */
internal object PlugsDimens {
    val lineGap = 10.dp

    // A plug's row: one card across the tab, name and state on the left, then the lock and button, then a cell per mode.
    val rowGap = 10.dp
    val rowRadius = 20.dp
    val rowPaddingV = 14.dp
    val rowPaddingH = 21.dp
    val rowMinHeight = 92.dp
    val nameSize = 23.sp
    val statusSize = 15.sp
    val textGap = 4.dp
    val footGap = 8.dp
    val lock = 24.dp

    // The button's slot is as wide as "Switch off" so the mode columns line up whether or not a row has a button.
    val switchSlot = 190.dp

    // A mode's column: wide enough for the Ignore, On and Off chips, 8 apart.
    val modeCell = 240.dp
    val chipGap = 8.dp
    val columnGap = 12.dp
    val headingGap = 6.dp

    // Settings › Other integrations › Plugs: a block per plug under the section label, 16 apart as the page's own blocks.
    val settingsGap = 16.dp
    val settingsRowGap = 8.dp
}
