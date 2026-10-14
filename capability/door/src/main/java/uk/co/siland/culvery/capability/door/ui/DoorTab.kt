package uk.co.siland.culvery.capability.door.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.paneTitle
import androidx.compose.ui.semantics.semantics
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import java.time.LocalDate
import java.time.ZoneId
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.launch
import uk.co.siland.culvery.capability.door.DoorDays
import uk.co.siland.culvery.capability.door.DoorEvent
import uk.co.siland.culvery.capability.door.DoorKind
import uk.co.siland.culvery.capability.door.DoorStore
import uk.co.siland.culvery.capability.door.NO_RINGS_TODAY
import uk.co.siland.culvery.capability.door.SHOW_FULL_SIZE
import uk.co.siland.culvery.capability.door.SNAPSHOT
import uk.co.siland.culvery.capability.door.TODAY
import uk.co.siland.culvery.capability.door.YESTERDAY
import uk.co.siland.culvery.capability.door.doorDays
import uk.co.siland.culvery.capability.door.eventLine
import uk.co.siland.culvery.core.plugin.LocalOverlayHost
import uk.co.siland.culvery.core.ui.Culvery
import uk.co.siland.culvery.core.ui.HhCloseButton
import uk.co.siland.culvery.core.ui.HhIcon
import uk.co.siland.culvery.core.ui.HhListRow
import uk.co.siland.culvery.core.ui.HhSectionLabel
import uk.co.siland.culvery.core.ui.HhSheet
import uk.co.siland.culvery.core.ui.HhType
import uk.co.siland.culvery.core.ui.Icons
import uk.co.siland.culvery.core.ui.ListRowTokens
import uk.co.siland.culvery.core.ui.hhClickable

/** The Door tab (5 §5.2): a status line when something is wrong, then Today and Yesterday, newest first. Open to everyone. */
@Composable
fun DoorTab(days: DoorDays, zone: ZoneId, status: String?, onOpenSnapshot: (DoorEvent) -> Unit, modifier: Modifier = Modifier) {
    val c = Culvery.colors
    LazyColumn(verticalArrangement = Arrangement.spacedBy(DoorDimens.rowGap), modifier = modifier.fillMaxSize().testTag("door_tab")) {
        if (status != null) item { DoorStatusLine(status) }
        item { HhSectionLabel(TODAY, Modifier.testTag("door_today")) }
        if (days.today.isEmpty()) item { Text(NO_RINGS_TODAY, style = HhType.body, color = c.mute, modifier = Modifier.testTag("door_empty")) }
        items(days.today, key = { it.id }) { DoorRow(it, zone, onOpenSnapshot) }
        if (days.yesterday.isNotEmpty()) {
            item { HhSectionLabel(YESTERDAY, Modifier.padding(top = DoorDimens.sectionTop).testTag("door_yesterday")) }
            items(days.yesterday, key = { it.id }) { DoorRow(it, zone, onOpenSnapshot) }
        }
    }
}

@Composable
private fun DoorRow(event: DoorEvent, zone: ZoneId, onOpenSnapshot: (DoorEvent) -> Unit) {
    val c = Culvery.colors
    HhListRow(
        title = eventLine(event, zone),
        subtitle = event.device.ifEmpty { null },
        modifier = Modifier.testTag("door_row_${event.id}"),
        leading = { HhIcon(if (event.kind == DoorKind.MOTION) Icons.DIRECTIONS_WALK else Icons.DOORBELL, size = ListRowTokens.icon, tint = c.mute) },
        trailing = event.snapshot?.let { file ->
            {
                // The tap area is 48 dp high around the 36 dp picture: on the SM-T510 Compose does not widen smaller targets.
                Box(
                    contentAlignment = Alignment.Center,
                    modifier = Modifier
                        .testTag("door_thumb_${event.id}")
                        .size(DoorDimens.thumbWidth, DoorDimens.thumbTouchHeight)
                        .semantics { contentDescription = SNAPSHOT }
                        .hhClickable({ onOpenSnapshot(event) }, label = SHOW_FULL_SIZE),
                ) {
                    SnapshotImage(
                        file,
                        Modifier
                            .testTag("door_thumb_image")
                            .size(DoorDimens.thumbWidth, DoorDimens.thumbHeight)
                            .clip(RoundedCornerShape(DoorDimens.thumbRadius))
                            .background(c.surf2),
                        maxPx = DoorDimens.THUMB_PX,
                    )
                }
            }
        },
    )
}

/** A ring's picture full size, in a sheet (5 §5.2). */
@Composable
fun SnapshotSheet(event: DoorEvent, zone: ZoneId, onClose: () -> Unit) {
    val c = Culvery.colors
    val file = event.snapshot ?: return
    HhSheet(
        padding = PaddingValues(horizontal = DoorDimens.sheetPaddingH, vertical = DoorDimens.sheetPaddingV),
        modifier = Modifier.testTag("door_snapshot_sheet").semantics { paneTitle = SNAPSHOT },
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(eventLine(event, zone), style = HhType.sectionTitle, color = c.ink, modifier = Modifier.semantics { heading() })
                if (event.device.isNotEmpty()) Text(event.device, style = HhType.body, color = c.mute)
            }
            HhCloseButton(onClose)
        }
        SnapshotImage(file, Modifier.fillMaxWidth().aspectRatio(DoorDimens.SNAPSHOT_RATIO).clip(RoundedCornerShape(DoorDimens.snapshotRadius)))
    }
}

/** The tab as the rail opens it: the store's events on the household's [today], and its [status]. */
@Composable
internal fun DoorTabHost(store: DoorStore, today: Flow<LocalDate>, zone: Flow<ZoneId>, status: Flow<String?>) {
    val events by store.events.collectAsStateWithLifecycle(emptyList())
    val day by today.collectAsStateWithLifecycle(null)
    val householdZone by zone.collectAsStateWithLifecycle(null)
    val problem by status.collectAsStateWithLifecycle(null)
    val overlay = LocalOverlayHost.current
    // Seen on opening or coming back to the tab (5 §4.7); a ring that arrives while it stays open keeps its badge.
    val scope = rememberCoroutineScope()
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) { scope.launch { store.markAllSeen() } }
    val d = day ?: return
    val z = householdZone ?: return
    val days = remember(events, d, z) { doorDays(events, d, z) }
    DoorTab(days, z, problem, onOpenSnapshot = { event -> overlay.show { SnapshotSheet(event, z, onClose = overlay::dismiss) } })
}
