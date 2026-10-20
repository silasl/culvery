package uk.co.siland.culvery.capability.music.ui

import androidx.compose.ui.unit.dp

/** Music's layout values (6 §5); provisional until the end-of-v1 design walk-through. */
internal object MusicDimens {
    // The Home card: padding 20 × 21 as Today's, art 88 (9 §4.2); the radius is ShellTokens.homeCardRadius.
    val cardPaddingV = 20.dp
    val cardPaddingH = 21.dp
    val cardArt = 88.dp
    val artGap = 14.dp
    val artRadius = 12.dp
    val cardArtRadius = 13.dp
    val controlsGap = 10.dp

    // The card's one row: Play/Pause, Next or Shuffle everything, then the volume pill.
    val cardControlsGap = 10.dp

    // The volume pill: 48 dp, the speaker icon, a 9 dp track, then the number.
    val pillHeight = 48.dp
    val pillPaddingStart = 14.dp
    val pillPaddingEnd = 18.dp
    val pillGap = 10.dp
    val pillIcon = 24.dp
    val pillTrack = 9.dp

    // Round controls are the 48 dp touch target, with a 28 dp icon.
    val button = 48.dp
    val buttonIcon = 28.dp

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
