package uk.co.siland.culvery.capability.calendar.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.Dp
import uk.co.siland.culvery.core.ui.ButtonSize
import uk.co.siland.culvery.core.ui.ButtonSurface
import uk.co.siland.culvery.core.ui.ButtonTone
import uk.co.siland.culvery.core.ui.HhButton
import uk.co.siland.culvery.core.ui.Icons

/** The person-colour bar at the start of an event row; fills the row's intrinsic height. */
@Composable
internal fun ColourBar(color: Color, width: Dp) {
    Box(
        Modifier
            .width(width)
            .fillMaxHeight()
            .clip(RoundedCornerShape(width / 2))
            .background(color),
    )
}

/** A sheet's Delete (hand-off §7): the danger tone, large, on the sheet's `bg`. Disabled, grey like every button. */
@Composable
internal fun DeleteButton(enabled: Boolean, tag: String, onClick: () -> Unit) = HhButton(
    "Delete", onClick, Modifier.testTag(tag),
    tone = ButtonTone.Danger, size = ButtonSize.Large, icon = Icons.DELETE, enabled = enabled, on = ButtonSurface.Bg,
)
