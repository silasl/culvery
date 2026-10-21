package uk.co.siland.culvery.capability.calendar.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.Dp
import uk.co.siland.culvery.core.household.Person
import uk.co.siland.culvery.core.ui.ButtonSize
import uk.co.siland.culvery.core.ui.ButtonSurface
import uk.co.siland.culvery.core.ui.ButtonTone
import uk.co.siland.culvery.core.ui.HhButton
import uk.co.siland.culvery.core.ui.Icons

/** An event's colour bar: [colors] in equal bands along its length, the first at the top (9 §3.4); fills the row's intrinsic height. */
@Composable
internal fun StripedBar(colors: List<Color>, width: Dp, modifier: Modifier = Modifier, radius: Dp = width / 2) {
    Column(
        modifier
            .width(width)
            .fillMaxHeight()
            .clip(RoundedCornerShape(radius))
            .testTag("event_bar"),
    ) {
        colors.forEach { Box(Modifier.fillMaxWidth().weight(1f).background(it)) }
    }
}

/**
 * [people]'s dots in a row, each [CalendarDimens.sharedDotOverlap] over the last and ringed in [ring] (the surface behind) so
 * they read apart; one person is a plain dot. Each dot is tagged [dotTag].
 */
@Composable
internal fun PersonDots(people: List<Person>, dot: Dp, ring: Color, dotTag: String, modifier: Modifier = Modifier) {
    Row(modifier, horizontalArrangement = Arrangement.spacedBy(-CalendarDimens.sharedDotOverlap)) {
        people.forEach {
            val ringed = if (people.size > 1) Modifier.border(CalendarDimens.sharedDotRing, ring, CircleShape) else Modifier
            Box(Modifier.testTag(dotTag).size(dot).clip(CircleShape).background(Color(it.color)).then(ringed))
        }
    }
}

/** A sheet's Delete (hand-off §7): the danger tone, large, on the sheet's `bg`. Disabled, grey like every button. */
@Composable
internal fun DeleteButton(enabled: Boolean, tag: String, onClick: () -> Unit) = HhButton(
    "Delete", onClick, Modifier.testTag(tag),
    tone = ButtonTone.Danger, size = ButtonSize.Large, icon = Icons.DELETE, enabled = enabled, on = ButtonSurface.Bg,
)
