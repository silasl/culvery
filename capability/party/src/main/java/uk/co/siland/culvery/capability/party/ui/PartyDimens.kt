package uk.co.siland.culvery.capability.party.ui

import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import uk.co.siland.culvery.core.ui.HhType

/** The Party tile's and sheet's layout values, as Holiday's (13 §2.6); provisional until the end-of-v1 design walk-through. */
internal object PartyDimens {
    val radius = 17.dp
    val paddingV = 10.dp
    val paddingH = 12.dp
    val gap = 12.dp
    val icon = 24.dp
    val secondStyle = HhType.secondary.copy(fontSize = 12.sp)
    val lineGap = 6.dp

    val sheetPaddingH = 30.dp
    val sheetPaddingV = 28.dp
    val stepGap = 8.dp
    val buttonGap = 12.dp

    val settingsGap = 16.dp
    val rowGap = 12.dp
}
