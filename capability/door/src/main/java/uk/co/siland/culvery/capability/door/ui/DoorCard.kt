package uk.co.siland.culvery.capability.door.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.liveRegion
import java.io.File
import uk.co.siland.culvery.capability.door.GOT_IT
import uk.co.siland.culvery.capability.door.RING_ALERT
import uk.co.siland.culvery.capability.door.SOMEONE_AT_THE_DOOR
import uk.co.siland.culvery.capability.door.cardWords
import uk.co.siland.culvery.core.ui.ButtonSize
import uk.co.siland.culvery.core.ui.ButtonSurface
import uk.co.siland.culvery.core.ui.ButtonTone
import uk.co.siland.culvery.core.ui.Culvery
import uk.co.siland.culvery.core.ui.HhButton
import uk.co.siland.culvery.core.ui.HhIcon
import uk.co.siland.culvery.core.ui.HhType
import uk.co.siland.culvery.core.ui.Icons

/** [device] may be empty. */
data class DoorCardUi(val unrecognised: Boolean, val device: String, val time: String, val snapshot: File?)

/** The door card (5 §5.1). TalkBack says its words at once, as one description in place of the texts. */
@Composable
fun DoorCard(card: DoorCardUi, onGotIt: () -> Unit, modifier: Modifier = Modifier) {
    val c = Culvery.colors
    val shape = RoundedCornerShape(DoorDimens.cardRadius)
    val title = if (card.unrecognised) RING_ALERT else SOMEONE_AT_THE_DOOR
    Column(
        verticalArrangement = Arrangement.spacedBy(DoorDimens.cardGap),
        modifier = modifier
            .testTag("door_card")
            .fillMaxWidth()
            .clip(shape)
            .background(c.surf)
            .border(DoorDimens.cardBorder, c.line, shape)
            .padding(DoorDimens.cardPadding),
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(DoorDimens.cardIconGap),
            modifier = Modifier.testTag("door_card_words").clearAndSetSemantics {
                contentDescription = cardWords(title, card.device, card.time)
                liveRegion = LiveRegionMode.Assertive
            },
        ) {
            HhIcon(Icons.DOORBELL, size = DoorDimens.cardIcon, tint = c.accent)
            Column(Modifier.weight(1f)) {
                Text(title, style = HhType.sectionTitle, color = c.ink)
                Text(listOf(card.device, card.time).filter { it.isNotEmpty() }.joinToString(" · "), style = HhType.body, color = c.mute)
            }
        }
        card.snapshot?.let {
            // Takes what height the words and Got it leave, so a short window (the keyboard up) shrinks the picture, not the button.
            Box(Modifier.weight(1f, fill = false).fillMaxWidth(), contentAlignment = Alignment.Center) {
                SnapshotImage(
                    it,
                    Modifier.testTag("door_snapshot").fillMaxWidth().aspectRatio(DoorDimens.SNAPSHOT_RATIO).clip(RoundedCornerShape(DoorDimens.snapshotRadius)),
                )
            }
        }
        HhButton(GOT_IT, onGotIt, Modifier.fillMaxWidth().testTag("door_got_it"), tone = ButtonTone.Primary, size = ButtonSize.Large, on = ButtonSurface.Surf)
    }
}
