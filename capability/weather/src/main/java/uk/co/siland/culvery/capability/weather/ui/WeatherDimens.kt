package uk.co.siland.culvery.capability.weather.ui

import androidx.compose.ui.unit.dp

/** Weather layout numbers (4b design §4); provisional, for the end-of-v1 design review. */
internal object WeatherDimens {
    // Card padding 20 × 22, as the calendar's cards; the radius is ShellTokens.homeCardRadius.
    val cardPaddingV = 20.dp
    val cardPaddingH = 22.dp

    // Title → first row 10; rows 44 tall, so three and the age line fit the 279 dp REGULAR cell; a 30 dp icon (§4.2)
    // 12 from the temperatures, each temperature right-aligned in 44.
    val titleGap = 10.dp
    val rowHeight = 44.dp
    val rowIcon = 30.dp
    val rowIconGap = 12.dp
    val temperatureWidth = 44.dp

    // The prompt's pill 18 below its line, as the Connect card's.
    val promptButtonTop = 18.dp

    // Header item (hand-off §1): a 44 dp icon 12 dp from the temperatures.
    val headerIcon = 44.dp
    val headerIconGap = 12.dp
}
