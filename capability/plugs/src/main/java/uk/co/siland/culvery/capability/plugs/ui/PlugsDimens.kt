package uk.co.siland.culvery.capability.plugs.ui

import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/** The Plugs tab's layout values (12 §4.3); provisional until the end-of-v1 design walk-through. */
internal object PlugsDimens {
    // The tab: the cards fill the left, the mode column is 380 wide, 15 apart.
    val modeColumn = 380.dp
    val gap = 15.dp
    val lineGap = 10.dp

    // A plug's card: padding as Lights' room cards, 180 high; the foot holds the lock and the button.
    val cardRadius = 24.dp
    val cardPaddingV = 20.dp
    val cardPaddingH = 21.dp
    val cardHeight = 180.dp
    val nameSize = 23.sp
    val statusSize = 15.sp
    val textGap = 4.dp
    val footGap = 8.dp
    val lock = 24.dp

    // The mode column: a card with Edit on its first line, then a section per mode of rows 56 high with chips 48 high.
    val modePaddingV = 20.dp
    val modePaddingH = 20.dp
    val sectionGap = 18.dp
    val labelGap = 4.dp
    val rowHeight = 56.dp
    val chipGap = 8.dp

    // Settings › Other integrations › Plugs: a block per plug under the section label, 16 apart as the page's own blocks.
    val settingsGap = 16.dp
    val settingsRowGap = 8.dp
}
