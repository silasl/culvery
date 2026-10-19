package uk.co.siland.culvery.display.ui

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/** The night clock: large and very dim on black, the same in both themes (8 §2.2). Provisional until the design walk-through. */
object NightClockTokens {
    val size = 160.sp
    val ink = Color(0xFF5A5A5A)
}

/** The camera mark's size and its distance from the top edge, where it sits centred (8 §4.3), and Settings › Display's spacing. */
object DisplayDimens {
    val markSize = 20.dp
    val markTop = 12.dp
    val settingsGap = 16.dp
    val sliderNameWidth = 200.dp
    val sliderValueWidth = 56.dp
}
