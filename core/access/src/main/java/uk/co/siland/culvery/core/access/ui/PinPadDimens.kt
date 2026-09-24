package uk.co.siland.culvery.core.access.ui

import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import uk.co.siland.culvery.core.ui.HhType

/** Hand-off §7 "PIN pad". */
internal object PinPadDimens {
    // Card: 400 dp, `surf`, radius 30, padding 26×28, 14 dp between blocks; 6 dp between title and reason.
    val cardWidth = 400.dp
    val cardRadius = 30.dp
    val cardPaddingV = 26.dp
    val cardPaddingH = 28.dp
    val gap = 14.dp
    val titleReasonGap = 6.dp

    // 56 dp `accentSoft` lock badge with a 30 dp `accent` lock.
    val badge = 56.dp
    val badgeIcon = 30.dp

    // Four 18 dp dots 16 apart; empty ones have a 2 dp border.
    val dot = 18.dp
    val dotGap = 16.dp
    val dotBorder = 2.dp

    // The error line is always 18 dp high, so the keypad never moves.
    val errorLine = 18.dp

    // Keypad: 76 dp circles, 12 dp between rows, 20 dp between columns.
    val key = 76.dp
    val keyRowGap = 12.dp
    val keyColumnGap = 20.dp
    val backspaceIcon = 30.dp
}

internal object PinPadType {
    /** 24 sp / 700: "Who's this?". */
    val title = HhType.dateNumber

    /** 15 sp / 400: the reason line. */
    val reason = HhType.secondary.copy(fontSize = 15.sp)

    /** 14 sp / 700: the error line. */
    val error = HhType.secondary.copy(fontWeight = FontWeight.W700)

    /** 16 sp / 600: the Cancel key. */
    val cancel = HhType.body.copy(fontWeight = FontWeight.W600)

    /** 30 sp / 600, tabular: digits. */
    val digit = HhType.pinDigit
}
