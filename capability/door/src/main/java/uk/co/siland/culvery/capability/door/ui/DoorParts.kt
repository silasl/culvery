package uk.co.siland.culvery.capability.door.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import uk.co.siland.culvery.core.ui.Culvery
import uk.co.siland.culvery.core.ui.HhIcon
import uk.co.siland.culvery.core.ui.HhType
import uk.co.siland.culvery.core.ui.Icons

/** The one line saying what is wrong and how to fix it (5 §6), on the Door tab and in Settings › Door. */
@Composable
internal fun DoorStatusLine(text: String) {
    val c = Culvery.colors
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(DoorDimens.statusGap),
        modifier = Modifier.testTag("door_status").semantics(mergeDescendants = true) { liveRegion = LiveRegionMode.Polite },
    ) {
        HhIcon(Icons.INFO, size = DoorDimens.statusIcon, tint = c.danger)
        Text(text, style = HhType.body, color = c.danger)
    }
}
