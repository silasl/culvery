package uk.co.siland.culvery.capability.lights.ui

import androidx.compose.ui.unit.dp

/** Lights' layout values (7 §5); provisional until the end-of-v1 design walk-through. */
internal object LightsDimens {
    // The Home card: padding 20 × 22 as the forecast's and Music's.
    val cardPaddingV = 20.dp
    val cardPaddingH = 22.dp
    val blockGap = 12.dp
    val bulb = 32.dp

    // The chevron is a 48 dp touch target with a 28 dp glyph.
    val chevron = 48.dp
    val chevronIcon = 28.dp

    // All lights off and Holiday mode side by side in 301 dp: Holiday mode keeps its width, All lights off takes the rest.
    val buttonGap = 8.dp
    val lineGap = 10.dp

    // The tab: rows 10 apart as lists are; inside a row, 12 between its lines and 8 between chips.
    val tabGap = 10.dp
    val rowInnerGap = 12.dp
    val chipGap = 8.dp

    // Settings › Lights: as Settings' other pages; the sheets as the calendar's.
    val settingsGap = 16.dp
    val settingsButtonGap = 12.dp
    val sheetPaddingH = 30.dp
    val sheetPaddingV = 28.dp
}
