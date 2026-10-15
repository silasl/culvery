package uk.co.siland.culvery.capability.music.ui

import androidx.compose.ui.unit.dp

/** Music's layout values (6 §5); provisional until the end-of-v1 design walk-through. */
internal object MusicDimens {
    // The Home card: padding 20 × 22, as the forecast's; the radius is ShellTokens.homeCardRadius.
    val cardPaddingV = 20.dp
    val cardPaddingH = 22.dp
    val cardArt = 72.dp
    val artGap = 14.dp
    val artRadius = 12.dp
    val controlsGap = 10.dp

    // Narrower on the card, so Play, Next and a compact Shuffle everything fit 345 dp.
    val cardControlsGap = 6.dp

    // Round controls are the 48 dp touch target, with a 28 dp icon.
    val button = 48.dp
    val buttonIcon = 28.dp
    val statusIcon = 20.dp
    val statusGap = 8.dp

    // The tab's rows: art, the group slider, and each room's slider under the Rooms chip.
    val rowArt = 64.dp
    val rowGap = 10.dp
    val rowSlider = 220.dp
    val roomSlider = 280.dp

    // The Favourites and address sheets, as the calendar's.
    val sheetPaddingH = 30.dp
    val sheetPaddingV = 28.dp
    val favouriteArt = 48.dp

    // Settings › Music: as Settings' other pages; the doorbell's share in 5 % steps, 0–50.
    val settingsGap = 16.dp
    val buttonGap = 12.dp
    const val LOWER_STEPS = 9
}
