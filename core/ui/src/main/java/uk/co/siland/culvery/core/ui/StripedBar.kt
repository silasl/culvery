package uk.co.siland.culvery.core.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/** One band of a [HhStripedBar]: [fill], outlined in [edge] when it has one. */
data class Stripe(val fill: Color, val edge: Color? = null)

/** The outline of a stripe whose ink is too dark to see on its row (night black). */
private val StripeEdge = 1.dp

/** A colour bar: [stripes] in equal bands along its length, the first at the top; fills its row's intrinsic height. */
@Composable
fun HhStripedBar(stripes: List<Stripe>, width: Dp, modifier: Modifier = Modifier, radius: Dp = width / 2) {
    Column(
        modifier
            .width(width)
            .fillMaxHeight()
            .clip(RoundedCornerShape(radius))
            .testTag("event_bar"),
    ) {
        stripes.forEach { stripe ->
            val band = Modifier.fillMaxWidth().weight(1f).background(stripe.fill)
            Box(stripe.edge?.let { band.border(StripeEdge, it) } ?: band)
        }
    }
}
