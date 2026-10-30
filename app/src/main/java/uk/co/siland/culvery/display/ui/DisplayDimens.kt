package uk.co.siland.culvery.display.ui

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/** The night clock and the camera glyph: very dim on black, the same in both themes (9 §4.10). Provisional until the design walk-through. */
object NightClockTokens {
    val size = 200.sp
    val tracking = (-8).sp
    val ink = Color(0xFF5A5A5A)
    val glyph = Color(0xFF2E3331)
}

/** The camera mark's size and its distance from the top edge, where it sits centred (8 §4.3), and Settings › Display's spacing. */
object DisplayDimens {
    val markSize = 20.dp
    val markTop = 12.dp
    /** Where the glyph sits before the top bar has reported the pill's spot: the bar's right end, centred in its height. */
    val glyphFallbackEnd = 28.dp
    val glyphFallbackTop = 14.dp
    val settingsGap = 16.dp
    val sheetPaddingH = 30.dp
    val sheetPaddingV = 28.dp
}
