package uk.co.siland.househub.core.ui

import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.text.ExperimentalTextApi
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontVariation
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

@OptIn(ExperimentalTextApi::class)
private fun symbols(fill: Float) = FontFamily(
    Font(
        R.font.material_symbols_rounded,
        FontWeight.W400,
        variationSettings = FontVariation.Settings(
            FontWeight.W400,
            FontStyle.Normal,
            FontVariation.Setting("FILL", fill),
        ),
    ),
)

private val SymbolsOutline = symbols(0f)
private val SymbolsFilled = symbols(1f)

/** Renders a Material Symbols Rounded glyph by ligature name, e.g. "lightbulb". */
@Composable
fun HhIcon(
    name: String,
    size: Dp = 24.dp,
    filled: Boolean = false,
    tint: Color = HouseHub.colors.ink,
    modifier: Modifier = Modifier,
) {
    val sp = with(LocalDensity.current) { size.toSp() }
    Text(
        text = name,
        style = TextStyle(
            fontFamily = if (filled) SymbolsFilled else SymbolsOutline,
            fontSize = sp,
            lineHeight = sp,
            color = tint,
        ),
        maxLines = 1,
        modifier = modifier.clearAndSetSemantics { },
    )
}
