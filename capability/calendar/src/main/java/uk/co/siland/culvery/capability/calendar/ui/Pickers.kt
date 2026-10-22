package uk.co.siland.culvery.capability.calendar.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.paneTitle
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Dp
import uk.co.siland.culvery.core.ui.Culvery

@Composable
internal fun PickerCard(width: Dp, gap: Dp, tag: String, paneTitle: String, content: @Composable ColumnScope.() -> Unit) {
    Column(
        verticalArrangement = Arrangement.spacedBy(gap),
        modifier = Modifier
            .testTag(tag)
            .semantics { this.paneTitle = paneTitle }
            .width(width)
            .clip(RoundedCornerShape(CalendarDimens.pickerRadius))
            .background(Culvery.colors.surf)
            // Taps on the card's own space mustn't reach the scrim underneath, which cancels.
            .pointerInput(Unit) { detectTapGestures { } }
            .padding(CalendarDimens.pickerPadding),
        content = content,
    )
}
