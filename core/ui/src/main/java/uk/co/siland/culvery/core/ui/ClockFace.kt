package uk.co.siland.culvery.core.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.wrapContentWidth
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.hideFromAccessibility
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.text
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.sp

val Creepster = FontFamily(Font(R.font.creepster))

/** Halloween's clock: the normal clock's size and line height in Creepster, with no tabular figures to ask for (14 §4.4). */
val CreepsterClock: TextStyle = HhType.clock.copy(
    fontFamily = Creepster,
    fontWeight = FontWeight.Normal,
    fontFeatureSettings = null,
    letterSpacing = 0.sp,
)

/** Each character in a box as wide as the widest digit, so the time never changes width (14 §4.4). */
@Composable
fun SlotClock(text: String, style: TextStyle, color: Color, modifier: Modifier = Modifier) {
    val measurer = rememberTextMeasurer()
    val density = LocalDensity.current
    val digitWidth = remember(style, density) {
        "0123456789".maxOf { measurer.measure(it.toString(), style, softWrap = false, maxLines = 1).size.width }
    }
    val colonWidth = remember(style, density) { measurer.measure(":", style, softWrap = false, maxLines = 1).size.width }
    Row(modifier = modifier.semantics { this.text = AnnotatedString(text) }) {
        text.forEach { char ->
            val width = with(density) { (if (char.isDigit()) digitWidth else colonWidth).toDp() }
            Box(Modifier.width(width), contentAlignment = Alignment.Center) {
                Text(
                    char.toString(), style = style, color = color, softWrap = false, maxLines = 1,
                    modifier = Modifier.wrapContentWidth(unbounded = true).testTag("clock_slot").semantics { hideFromAccessibility() },
                )
            }
        }
    }
}
