package uk.co.siland.culvery.capability.music.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import uk.co.siland.culvery.capability.music.LOOKING_FOR_SPEAKERS
import uk.co.siland.culvery.capability.music.MUSIC
import uk.co.siland.culvery.capability.music.MusicActions
import uk.co.siland.culvery.capability.music.MusicChecks
import uk.co.siland.culvery.capability.music.MusicView
import uk.co.siland.culvery.capability.music.SpeakerGroup
import uk.co.siland.culvery.capability.music.musicSummary
import uk.co.siland.culvery.capability.music.problemLine
import uk.co.siland.culvery.capability.music.tabOrder
import uk.co.siland.culvery.core.plugin.LocalOverlayHost
import uk.co.siland.culvery.core.ui.Culvery
import uk.co.siland.culvery.core.ui.HhPageHeader
import uk.co.siland.culvery.core.ui.HhType

/**
 * The Music tab (6 §5.2, 9 §4.5): the title with its room and group counts, a status line only when something is wrong,
 * then one card per group as given (playing first, [tabOrder]). Open to everyone; grouping stays in the Sonos app (6 §2.4).
 */
@Composable
fun MusicTab(
    groups: List<SpeakerGroup>,
    status: String?,
    lines: Map<String, String>,
    roomsShown: Set<String>,
    onRooms: (String) -> Unit,
    actions: GroupActions,
    modifier: Modifier = Modifier,
) {
    val c = Culvery.colors
    LazyColumn(modifier = modifier.fillMaxSize().testTag("music_tab")) {
        item { HhPageHeader(MUSIC, if (groups.isEmpty()) null else musicSummary(groups)) }
        // The header ends with its own gap, so each item after it carries the one below it.
        if (status != null) item { Box(Modifier.padding(bottom = MusicDimens.roomGap)) { MusicStatusLine(status) } }
        if (groups.isEmpty() && status == null) {
            item { Text(LOOKING_FOR_SPEAKERS, style = HhType.body, color = c.mute, modifier = Modifier.padding(bottom = MusicDimens.roomGap)) }
        }
        items(groups, key = { it.id }) { group ->
            Box(Modifier.padding(bottom = MusicDimens.roomGap)) {
                MusicRoomCard(group, lines[group.id], group.id in roomsShown, { onRooms(group.id) }, actions)
            }
        }
    }
}

/** The tab over the view; while it shows the speakers are checked, the layout at once (6 §4.2). */
@Composable
internal fun MusicTabHost(view: MusicView, actions: MusicActions, checks: MusicChecks) {
    DisposableEffect(checks) {
        val stop = checks.watch(layoutNow = true)
        onDispose { stop() }
    }
    val groups by view.groups.collectAsStateWithLifecycle(emptyList())
    val problem by view.problem.collectAsStateWithLifecycle(null)
    val lines by actions.lines.collectAsStateWithLifecycle()
    var openRooms by remember { mutableStateOf(emptySet<String>()) }
    // Drawn from this frame's groups, so none shows for a group that has gone; the raw set is pruned after, so a group that re-forms opens closed.
    val roomsShown = remember(groups, openRooms) { openRooms intersect groups.filter { it.rooms.size > 1 }.map { it.id }.toSet() }
    LaunchedEffect(roomsShown) { if (roomsShown != openRooms) openRooms = roomsShown }
    val overlay = LocalOverlayHost.current
    // The sheet is drawn outside the tab, so it is given the tab's art.
    val art = LocalArtSource.current
    val groupActions = remember(actions, overlay, art) {
        actions.forGroups { group ->
            overlay.show { CompositionLocalProvider(LocalArtSource provides art) { FavouritesSheetHost(group, actions, onClose = overlay::dismiss) } }
        }
    }
    val ordered = remember(groups) { tabOrder(groups) }
    MusicTab(
        ordered, problem?.let(::problemLine), lines, roomsShown,
        onRooms = { id -> openRooms = if (id in roomsShown) roomsShown - id else roomsShown + id },
        actions = groupActions,
    )
}
