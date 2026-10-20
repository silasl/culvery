package uk.co.siland.culvery.capability.music.ui

import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import uk.co.siland.culvery.core.ui.HhType

/** Music's layout values (6 §5); provisional until the end-of-v1 design walk-through. */
internal object MusicDimens {
    // The Home card: padding 20 × 21 as Today's, art 88 (9 §4.2); the radius is ShellTokens.homeCardRadius.
    val cardPaddingV = 20.dp
    val cardPaddingH = 21.dp
    val cardArt = 88.dp
    val artGap = 14.dp
    val artRadius = 12.dp
    val cardArtRadius = 13.dp
    val cardName: TextStyle = HhType.cardTitle.copy(fontSize = 20.sp)
    val cardTrack: TextStyle = HhType.rowTitle.copy(fontWeight = FontWeight.W400)
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
    val pillNumber: TextStyle = HhType.body.copy(fontSize = 15.sp, fontWeight = FontWeight.W600, fontFeatureSettings = "tnum")

    // The tab's room cards: art, name 21 sp, track 17 sp, then the 48 dp buttons and the 280 dp volume pill (9 §4.5).
    val roomRadius = 24.dp
    val roomPaddingV = 17.dp
    val roomPaddingH = 20.dp
    val roomArt = 80.dp
    val roomGap = 15.dp
    val roomPill = 280.dp

    // The opened group chip's grid: two columns, rows 48 dp, the name a fixed column so the pills line up.
    const val ROOM_COLUMNS = 2
    val roomCellHeight = 48.dp
    val roomNameWidth = 120.dp
    val chipIcon = 20.dp
    val roomName: TextStyle = HhType.cardTitle.copy(fontSize = 21.sp)
    val roomTrack: TextStyle = HhType.rowTitle.copy(fontWeight = FontWeight.W400)

    // The Favourites and address sheets, as the calendar's.
    val sheetPaddingH = 30.dp
    val sheetPaddingV = 28.dp
    val favouriteArt = 48.dp

    // Settings › Music: as Settings' other pages; the doorbell's share in 5 % steps, 0–50.
    val settingsGap = 16.dp
    val buttonGap = 12.dp
    const val LOWER_STEPS = 9
}
