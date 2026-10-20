package uk.co.siland.culvery.capability.door.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.liveRegion
import java.io.File
import uk.co.siland.culvery.capability.door.DOOR_HISTORY
import uk.co.siland.culvery.capability.door.GOT_IT
import uk.co.siland.culvery.capability.door.MUSIC_LOWERED
import uk.co.siland.culvery.capability.door.RING_ALERT
import uk.co.siland.culvery.capability.door.SOMEONE_AT_THE_DOOR
import uk.co.siland.culvery.capability.door.cardLine
import uk.co.siland.culvery.capability.door.cardWords
import uk.co.siland.culvery.core.ui.ButtonSize
import uk.co.siland.culvery.core.ui.ButtonSurface
import uk.co.siland.culvery.core.ui.ButtonTone
import uk.co.siland.culvery.core.ui.Culvery
import uk.co.siland.culvery.core.ui.HhButton
import uk.co.siland.culvery.core.ui.HhIcon
import uk.co.siland.culvery.core.ui.HhType
import uk.co.siland.culvery.core.ui.Icons
import uk.co.siland.culvery.core.ui.quietFill

/** [device] may be empty. */
data class DoorCardUi(val unrecognised: Boolean, val device: String, val time: String, val snapshot: File?)

/**
 * The door card (5 §5.1, 9 §4.3): at most 747 dp wide, the snapshot at its full width, Got it and Door history, and
 * "Music lowered" while [lowered]. TalkBack says its words at once, as one description in place of the texts.
 */
@Composable
fun DoorCard(card: DoorCardUi, onGotIt: () -> Unit, modifier: Modifier = Modifier, lowered: Boolean = false, onHistory: () -> Unit = {}) {
    val c = Culvery.colors
    val shape = RoundedCornerShape(DoorDimens.cardRadius)
    val title = if (card.unrecognised) RING_ALERT else SOMEONE_AT_THE_DOOR
    Column(
        verticalArrangement = Arrangement.spacedBy(DoorDimens.cardGap),
        modifier = modifier
            .testTag("door_card")
            .widthIn(max = DoorDimens.cardWidth)
            .fillMaxWidth()
            .shadow(DoorDimens.cardShadow, shape)
            .clip(shape)
            .background(c.surf)
            .border(DoorDimens.cardEdge, c.surf3, shape)
            .padding(DoorDimens.cardPadding),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(DoorDimens.cardGap)) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(DoorDimens.cardGap),
                modifier = Modifier.weight(1f).testTag("door_card_words").clearAndSetSemantics {
                    contentDescription = cardWords(title, card.device, card.time)
                    liveRegion = LiveRegionMode.Assertive
                },
            ) {
                Box(Modifier.size(DoorDimens.bellCircle).clip(CircleShape).background(c.accentSoft), contentAlignment = Alignment.Center) {
                    HhIcon(Icons.DOORBELL, size = DoorDimens.bellIcon, filled = true, tint = c.accent)
                }
                Column(Modifier.weight(1f)) {
                    Text(title, style = HhType.sectionTitle.copy(fontSize = DoorDimens.titleSize), color = c.ink)
                    Text(cardLine(card.device, card.time), style = HhType.body.copy(fontSize = DoorDimens.lineSize), color = c.mute)
                }
            }
            if (lowered) MusicLoweredChip()
        }
        card.snapshot?.let {
            // Takes what height the words and buttons leave, so a short window (the keyboard up) shrinks the picture, not the buttons.
            Box(Modifier.weight(1f, fill = false).fillMaxWidth(), contentAlignment = Alignment.Center) {
                SnapshotImage(
                    it,
                    Modifier.testTag("door_snapshot").fillMaxWidth().aspectRatio(DoorDimens.SNAPSHOT_RATIO).clip(RoundedCornerShape(DoorDimens.cardSnapshotRadius)),
                )
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(DoorDimens.cardGap)) {
            HhButton(GOT_IT, onGotIt, Modifier.weight(2f).testTag("door_got_it"), tone = ButtonTone.Primary, size = ButtonSize.Large, on = ButtonSurface.Surf)
            HhButton(
                DOOR_HISTORY, onHistory, Modifier.weight(1f).testTag("door_history"),
                size = ButtonSize.Large, on = ButtonSurface.Surf, trailingIcon = Icons.CHEVRON_RIGHT,
            )
        }
    }
}

/** A quiet chip saying the music was turned down for the ring (9 §6.3). */
@Composable
private fun MusicLoweredChip() {
    val c = Culvery.colors
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(DoorDimens.chipIconGap),
        modifier = Modifier
            .testTag("door_music_lowered")
            .height(DoorDimens.chipHeight)
            .clip(CircleShape)
            .background(quietFill(ButtonSurface.Surf, c))
            .padding(horizontal = DoorDimens.chipPaddingH),
    ) {
        HhIcon(Icons.VOLUME_DOWN, size = DoorDimens.chipIcon, tint = c.ink)
        Text(MUSIC_LOWERED, style = HhType.label.copy(fontSize = DoorDimens.chipTextSize), color = c.ink, maxLines = 1)
    }
}
