package uk.co.siland.culvery.core.ui

import androidx.compose.ui.text.ExperimentalTextApi
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontVariation
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.LineHeightStyle
import androidx.compose.ui.unit.sp

// One variable font file. Font(resId, weight) leaves the variation settings empty, so Android draws
// every weight at the file's default wght (400); each Font has to set its wght explicitly.
@OptIn(ExperimentalTextApi::class)
private fun dmSans(weight: FontWeight) = Font(
    R.font.dm_sans,
    weight,
    variationSettings = FontVariation.Settings(FontVariation.weight(weight.weight)),
)

val DmSans = FontFamily(
    dmSans(FontWeight.W400),
    dmSans(FontWeight.W500),
    dmSans(FontWeight.W600),
    dmSans(FontWeight.W700),
)

private const val TABULAR = "tnum"

private fun style(size: Int, weight: FontWeight, tabular: Boolean = false) = TextStyle(
    fontFamily = DmSans,
    fontWeight = weight,
    fontSize = size.sp,
    fontFeatureSettings = if (tabular) TABULAR else null,
)

object HhType {
    val clock = style(104, FontWeight.W600, tabular = true).copy(
        letterSpacing = (-4).sp,
        lineHeight = 93.6.sp,
        lineHeightStyle = LineHeightStyle(LineHeightStyle.Alignment.Center, LineHeightStyle.Trim.Both),
    )
    val date = style(21, FontWeight.W400)
    val screenTitle = style(34, FontWeight.W700)
    val headerValue = style(34, FontWeight.W600, tabular = true)
    val dateNumber = style(24, FontWeight.W700)
    val sectionTitle = style(22, FontWeight.W700)
    val cardTitle = style(19, FontWeight.W700)
    val rowTitle = style(17, FontWeight.W600)
    val body = style(16, FontWeight.W400)
    val secondary = style(14, FontWeight.W400)
    val label = style(13, FontWeight.W600)
    val status = style(13, FontWeight.W500)
    val labelSmall = style(12, FontWeight.W700)
    val buttonLabel = style(15, FontWeight.W700)
    val pinDigit = style(30, FontWeight.W600, tabular = true)
}
