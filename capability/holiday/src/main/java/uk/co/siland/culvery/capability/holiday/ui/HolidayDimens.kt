package uk.co.siland.culvery.capability.holiday.ui

import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import uk.co.siland.culvery.core.ui.HhType

/** The Holiday tile's and sheet's layout values (11 §5.2); provisional until the end-of-v1 design walk-through. */
internal object HolidayDimens {
    val radius = 17.dp
    val paddingV = 10.dp
    val paddingH = 12.dp
    val gap = 12.dp
    val icon = 24.dp
    val secondStyle = HhType.secondary.copy(fontSize = 12.sp)
    val lineGap = 6.dp

    val pillHeight = 24.dp
    val pillPaddingH = 10.dp
    val onStyle = HhType.labelSmall

    val sheetPaddingH = 30.dp
    val sheetPaddingV = 28.dp
    val dateStyle = HhType.body.copy(fontSize = 28.sp, fontWeight = FontWeight.W700)
    val stepGap = 8.dp
    val buttonGap = 12.dp
}
