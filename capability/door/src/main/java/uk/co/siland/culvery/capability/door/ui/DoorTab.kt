package uk.co.siland.culvery.capability.door.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.paneTitle
import androidx.compose.ui.semantics.semantics
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import java.time.LocalDate
import java.time.ZoneId
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import uk.co.siland.culvery.capability.door.DOOR
import uk.co.siland.culvery.capability.door.DoorDay
import uk.co.siland.culvery.capability.door.DoorEvent
import uk.co.siland.culvery.capability.door.DoorKind
import uk.co.siland.culvery.capability.door.DoorStore
import uk.co.siland.culvery.capability.door.KeepDays
import uk.co.siland.culvery.capability.door.NO_RINGS_TODAY
import uk.co.siland.culvery.capability.door.SNAPSHOT
import uk.co.siland.culvery.capability.door.TODAY
import uk.co.siland.culvery.capability.door.dayLabel
import uk.co.siland.culvery.capability.door.doorDays
import uk.co.siland.culvery.capability.door.doorSubtitle
import uk.co.siland.culvery.capability.door.eventLine
import uk.co.siland.culvery.capability.door.subLine
import uk.co.siland.culvery.core.plugin.LocalOverlayHost
import uk.co.siland.culvery.core.plugin.LocalTabReselected
import uk.co.siland.culvery.core.ui.Culvery
import uk.co.siland.culvery.core.ui.HhCloseButton
import uk.co.siland.culvery.core.ui.HhPageHeader
import uk.co.siland.culvery.core.ui.HhSectionLabel
import uk.co.siland.culvery.core.ui.HhSheet
import uk.co.siland.culvery.core.ui.HhType

/**
 * The Door tab (5 §5.2, 9 §4.6): the title with the devices and the new rings, a status line when something is wrong,
 * then Today and every older kept day under its date, newest first. A ring is NEW when it was unseen as the tab opened ([newIds]) or still is,
 * as one arriving while the tab is open. Open to everyone.
 */
@Composable
fun DoorTab(
    days: List<DoorDay>,
    today: LocalDate,
    zone: ZoneId,
    status: String?,
    onOpenSnapshot: (DoorEvent) -> Unit,
    modifier: Modifier = Modifier,
    newIds: Set<Long> = emptySet(),
) {
    val c = Culvery.colors
    fun isNew(event: DoorEvent) = event.kind == DoorKind.RING && (event.id in newIds || !event.seen)
    val subtitle = remember(days, newIds) {
        val shown = days.flatMap { it.events }
        doorSubtitle(shown, shown.count(::isNew))
    }
    // The header ends with its own gap, so each item after it carries the one below it.
    val gap = Modifier.padding(bottom = DoorDimens.rowGap)
    LazyColumn(modifier = modifier.fillMaxSize().testTag("door_tab")) {
        item { HhPageHeader(DOOR, subtitle) }
        if (status != null) item { Box(gap) { DoorStatusLine(status) } }
        item { HhSectionLabel(TODAY, gap.testTag("door_today")) }
        val todays = days.firstOrNull { it.date == today }?.events.orEmpty()
        if (todays.isEmpty()) item { Text(NO_RINGS_TODAY, style = HhType.body, color = c.mute, modifier = gap.testTag("door_empty")) }
        items(todays, key = { it.id }) { DoorRow(it, zone, isNew(it), onOpenSnapshot, gap) }
        days.filter { it.date != today }.forEach { day ->
            val tag = if (day.date == today.minusDays(1)) "door_yesterday" else "door_day_${day.date}"
            item(key = day.date.toString()) { HhSectionLabel(dayLabel(day.date, today), gap.padding(top = DoorDimens.sectionTop).testTag(tag)) }
            items(day.events, key = { it.id }) { DoorRow(it, zone, isNew(it), onOpenSnapshot, gap) }
        }
    }
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
                subLine(event)?.let { Text(it, style = HhType.body, color = c.mute) }
            }
            HhCloseButton(onClose)
        }
        SnapshotImage(file, Modifier.fillMaxWidth().aspectRatio(DoorDimens.SNAPSHOT_RATIO).clip(RoundedCornerShape(DoorDimens.snapshotRadius)))
    }
}

/** The tab as the rail opens it: the store's events over the [keep] days to the household's [today], and its [status]. */
@Composable
internal fun DoorTabHost(store: DoorStore, today: Flow<LocalDate>, zone: Flow<ZoneId>, status: Flow<String?>, keep: Flow<KeepDays>) {
    val events by store.events.collectAsStateWithLifecycle(emptyList())
    val day by today.collectAsStateWithLifecycle(null)
    val householdZone by zone.collectAsStateWithLifecycle(null)
    val problem by status.collectAsStateWithLifecycle(null)
    val kept by keep.collectAsStateWithLifecycle(null)
    val overlay = LocalOverlayHost.current
    // Seen on opening, coming back to the tab, or tapping it again (5 §4.7); a ring that arrives while it stays open keeps its badge.
    // Each resume is a new visit and replaces the NEW rings; a tap again adds to them (9 §6.8). One take at a time, in order.
    var newIds by remember { mutableStateOf(emptySet<Long>()) }
    val visit = remember { Mutex() }
    val scope = rememberCoroutineScope()
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) { scope.launch { visit.withLock { newIds = store.takeUnseen() } } }
    val reselected = LocalTabReselected.current
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    LaunchedEffect(reselected, lifecycle) {
        reselected.collect {
            // Before the resume there is no visit to add to; the resume takes these rings.
            if (lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED)) visit.withLock { newIds = newIds + store.takeUnseen() }
        }
    }
    val d = day ?: return
    val z = householdZone ?: return
    val k = kept ?: return
    val days = remember(events, d, z, k) { doorDays(events, d, z, k) }
    DoorTab(days, d, z, problem, onOpenSnapshot = { event -> overlay.show { SnapshotSheet(event, z, onClose = overlay::dismiss) } }, newIds = newIds)
}
