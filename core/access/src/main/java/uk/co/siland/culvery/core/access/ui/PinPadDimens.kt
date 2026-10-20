package uk.co.siland.culvery.core.access.ui

import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import uk.co.siland.culvery.core.ui.HhType

/** Hand-off §7 "PIN pad". */
internal object PinPadDimens {
    // Card: 413 dp, `surf` with a 1 dp `surf3` edge, radius 29, padding 26×28, 14 dp between blocks; 6 dp between title and reason.
    val cardWidth = 413.dp
    val cardRadius = 29.dp
    val cardEdge = 1.dp
    val cardPaddingV = 26.dp
    val cardPaddingH = 28.dp
    val gap = 14.dp
    val titleReasonGap = 6.dp

    // 59 dp lock badge, `accentSoft`/`accent` (locked: `dangerSoft`/`danger`) with a 33 dp icon.
    val badge = 59.dp
    val badgeIcon = 33.dp

    // Four 18 dp dots 16 apart; empty ones have a 2 dp border.
    val dot = 18.dp
    val dotGap = 16.dp
    val dotBorder = 2.dp

    // The lock's countdown over a 173 × 5 dp bar, 8 dp apart, in the dots' place.
    val barWidth = 173.dp
    val barHeight = 5.dp
    val countdownBarGap = 8.dp

    // Keypad: 77 dp circles, 12 dp between rows, 20 dp between columns.
    val key = 77.dp
    val keyRowGap = 12.dp
    val keyColumnGap = 20.dp
    val backspaceIcon = 30.dp
}

/** Every key but Cancel while the pad is locked. */
internal const val DISABLED_KEY_ALPHA = 0.3f

internal object PinPadType {
    /** 27 sp / 700: "Who's this?" and "Too many tries". */
    val title = HhType.dateNumber.copy(fontSize = 27.sp)

    /** 15 sp / 400: the reason line. */
    val reason = HhType.secondary.copy(fontSize = 15.sp)

    /** 14 sp / 700: the error line. */
    val error = HhType.secondary.copy(fontWeight = FontWeight.W700)

    /** 29 sp / 700, tabular: the lock's countdown. */
    val countdown = HhType.pinDigit.copy(fontSize = 29.sp, fontWeight = FontWeight.W700)

    /** 16 sp / 600: the Cancel key. */
    val cancel = HhType.body.copy(fontWeight = FontWeight.W600)

    /** 30 sp / 600, tabular: digits. */
    val digit = HhType.pinDigit
}
