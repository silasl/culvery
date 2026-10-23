package uk.co.siland.culvery.capability.door.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import java.time.ZoneId
import uk.co.siland.culvery.capability.door.DoorEvent
import uk.co.siland.culvery.capability.door.DoorKind
import uk.co.siland.culvery.capability.door.NEW
import uk.co.siland.culvery.capability.door.NEW_SPOKEN
import uk.co.siland.culvery.capability.door.SHOW_FULL_SIZE
import uk.co.siland.culvery.capability.door.SNAPSHOT
import uk.co.siland.culvery.capability.door.eventLine
import uk.co.siland.culvery.capability.door.isNotice
import uk.co.siland.culvery.capability.door.subLine
import uk.co.siland.culvery.core.ui.Culvery
import uk.co.siland.culvery.core.ui.HhIcon
import uk.co.siland.culvery.core.ui.HhType
import uk.co.siland.culvery.core.ui.Icons
import uk.co.siland.culvery.core.ui.ListRowTokens
import uk.co.siland.culvery.core.ui.hhClickable

/** One ring, movement or notice in the Door tab (9 §4.6): the kind's icon circle, what and when, NEW, and its picture. */
@Composable
internal fun DoorRow(event: DoorEvent, zone: ZoneId, isNew: Boolean, onOpenSnapshot: (DoorEvent) -> Unit, modifier: Modifier = Modifier) {
    val c = Culvery.colors
    val rang = event.kind == DoorKind.RING && !event.isNotice
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(DoorDimens.rowInnerGap),
        modifier = modifier
            .testTag("door_row_${event.id}")
            .fillMaxWidth()
            .clip(ListRowTokens.shape)
            .background(c.surf)
            .padding(horizontal = DoorDimens.rowPaddingH, vertical = DoorDimens.rowPaddingV),
    ) {
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier.size(DoorDimens.rowCircle).clip(CircleShape).background(if (rang) c.accentSoft else c.surf2),
        ) {
            val icon = when {
                event.kind == DoorKind.MOTION -> Icons.DIRECTIONS_WALK
                event.isNotice -> Icons.HELP
                else -> Icons.DOORBELL
            }
            HhIcon(icon, size = DoorDimens.rowIcon, tint = if (rang) c.accent else c.mute)
        }
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(DoorDimens.rowLineGap)) {
            Text(
                eventLine(event, zone),
                style = HhType.rowTitle.copy(fontSize = DoorDimens.rowTitleSize),
                color = c.ink,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            subLine(event)?.let { Text(it, style = HhType.secondary, color = c.mute, maxLines = 1, overflow = TextOverflow.Ellipsis) }
        }
        if (isNew) NewTag(Modifier.testTag("door_row_${event.id}_new"))
        event.snapshot?.let { file ->
            Box(
                Modifier
                    .testTag("door_thumb_${event.id}")
                    .size(DoorDimens.thumbWidth, DoorDimens.thumbHeight)
                    .clip(RoundedCornerShape(DoorDimens.thumbRadius))
                    .background(c.surf2)
                    .semantics { contentDescription = SNAPSHOT }
                    .hhClickable({ onOpenSnapshot(event) }, label = SHOW_FULL_SIZE),
            ) {
                SnapshotImage(file, Modifier.testTag("door_thumb_image").fillMaxSize(), maxPx = DoorDimens.THUMB_PX)
            }
        }
    }
}

/** The red NEW of a ring new on this visit, matching the rail's badge (9 §6.8). */
@Composable
private fun NewTag(modifier: Modifier) {
    val c = Culvery.colors
    Box(
        contentAlignment = Alignment.Center,
        modifier = Modifier.height(DoorDimens.newTagHeight).clip(CircleShape).background(c.danger).padding(horizontal = DoorDimens.newTagPaddingH),
    ) {
        Text(
            NEW,
            style = HhType.labelSmall.copy(fontSize = DoorDimens.newTagTextSize),
            color = c.dangerInk,
            modifier = modifier.semantics { contentDescription = NEW_SPOKEN },
        )
    }
}
