package uk.co.siland.culvery.capability.holiday.ui

import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import uk.co.siland.culvery.core.ui.HhType

/** The sheet's date (11 §5.2); the rest is HouseModeDimens. Provisional until the end-of-v1 design walk-through. */
internal object HolidayDimens {
    val dateStyle = HhType.body.copy(fontSize = 28.sp, fontWeight = FontWeight.W700)
}
