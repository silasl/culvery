package uk.co.siland.culvery.capability.lights.ui

import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/** Lights' layout values (7 §5); provisional until the end-of-v1 design walk-through. */
internal object LightsDimens {
    // The Home card: padding 20 × 21 as Today's; the name and status lines; the brightness row 15 dp above the buttons.
    val cardPaddingV = 20.dp
    val cardPaddingH = 21.dp
    val blockGap = 12.dp
    val bulb = 31.dp
    val nameSize = 20.sp
    val statusSize = 15.sp
    val trackHeight = 20.dp
    val sliderIcon = 24.dp
    val bottomGap = 15.dp

    // At or below this height (the side-by-side row is 212) a line under the buttons takes the gaps and shows on one line.
    val rowCardMaxHeight = 240.dp

    // A room's row is at least a touch target tall, as the card's › (HhRoundButton's 48 dp).
    val chevron = 48.dp

    // All lights off and Holiday lights side by side: Holiday lights keep their width, All lights off takes the rest.
    val buttonGap = 8.dp
    val lineGap = 10.dp

    // The tab: cards 10 apart as lists are; inside a card, 12 between its lines and 8 between scene chips.
    val tabGap = 10.dp
    val roomRadius = 24.dp
    val roomInnerGap = 12.dp
    val chipGap = 8.dp

    // The grid (9 §4.4): cards 15 apart; from seven rooms rows are 280 high under a 147 fade, the 48 dp pill 15 above the foot.
    val gridGap = 15.dp
    val fixedRow = 280.dp
    val gridFade = 147.dp
    val pillFoot = 15.dp
    val pillShadow = 8.dp

    // A room card: its bulb, name, brightness track and percent (9 §4.4).
    val roomBulb = 35.dp
    val roomNameSize = 23.sp
    val roomTrack = 23.dp
    val percentSize = 15.sp
    val percentWidth = 44.dp

    // Settings › Lights: as Settings' other pages; the sheets as the calendar's.
    val settingsGap = 16.dp
    val settingsButtonGap = 12.dp
    val sheetPaddingH = 30.dp
    val sheetPaddingV = 28.dp
}
