package uk.co.siland.culvery.capability.music.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextOverflow
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import uk.co.siland.culvery.capability.music.FAVOURITES_LABEL
import uk.co.siland.culvery.capability.music.LOOKING_FOR_SPEAKERS
import uk.co.siland.culvery.capability.music.MusicActions
import uk.co.siland.culvery.capability.music.MusicChecks
import uk.co.siland.culvery.capability.music.MusicView
import uk.co.siland.culvery.capability.music.ROOMS
import uk.co.siland.culvery.capability.music.SpeakerGroup
import uk.co.siland.culvery.capability.music.favouritesSpoken
import uk.co.siland.culvery.capability.music.problemLine
import uk.co.siland.culvery.capability.music.roomsAction
import uk.co.siland.culvery.capability.music.tabOrder
import uk.co.siland.culvery.core.plugin.LocalOverlayHost
import uk.co.siland.culvery.core.ui.ButtonSurface
import uk.co.siland.culvery.core.ui.Culvery
import uk.co.siland.culvery.core.ui.HhButton
import uk.co.siland.culvery.core.ui.HhChoiceChip
import uk.co.siland.culvery.core.ui.HhType
import uk.co.siland.culvery.core.ui.Icons
import uk.co.siland.culvery.core.ui.ListRowTokens

/**
 * The Music tab (6 §5.2): a status line only when something is wrong, then one row per group as given (playing first,
 * [tabOrder]). Open to everyone; grouping stays in the Sonos app (6 §2.4).
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
    LazyColumn(verticalArrangement = Arrangement.spacedBy(MusicDimens.rowGap), modifier = modifier.fillMaxSize().testTag("music_tab")) {
        if (status != null) item { MusicStatusLine(status) }
        if (groups.isEmpty() && status == null) item { Text(LOOKING_FOR_SPEAKERS, style = HhType.body, color = c.mute) }
        items(groups, key = { it.id }) { group -> MusicRow(group, lines[group.id], group.id in roomsShown, { onRooms(group.id) }, actions) }
    }
}

/** A group: what it plays and its controls; Rooms on a group of several shows each room's slider (6 §5.2). */
@Composable
private fun MusicRow(group: SpeakerGroup, line: String?, roomsShown: Boolean, onRooms: () -> Unit, actions: GroupActions) {
    val c = Culvery.colors
    val tag = "music_row_${group.id}"
    Column(
        verticalArrangement = Arrangement.spacedBy(ListRowTokens.belowGap),
        modifier = Modifier
            .fillMaxWidth()
            .testTag(tag)
            .clip(RoundedCornerShape(ListRowTokens.radius))
            .background(c.surf)
            .padding(horizontal = ListRowTokens.paddingH, vertical = ListRowTokens.paddingV),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(MusicDimens.controlsGap)) {
            NowPlayingBlock(group, nothingPlaying = false, MusicDimens.rowArt, HhType.rowTitle, "${tag}_now", Modifier.weight(1f))
            PlayPauseButton(group, tag, actions)
            NextButton(group, tag, actions)
            VolumeSlider(group.name, group.volume, "${tag}_volume", Modifier.width(MusicDimens.rowSlider)) { actions.onGroupVolume(group, it) }
        }
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(MusicDimens.controlsGap)) {
            if (group.rooms.size > 1) {
                HhChoiceChip(ROOMS, selected = roomsShown, tag = "${tag}_rooms", onClick = onRooms, role = Role.Button, clickLabel = roomsAction(roomsShown))
            }
            HhButton(
                FAVOURITES_LABEL, { actions.onFavourites(group) }, Modifier.testTag("${tag}_favourites"),
                icon = Icons.STAR, on = ButtonSurface.Surf, spokenName = favouritesSpoken(group.name),
            )
            ShuffleButton(group, tag, ButtonSurface.Surf, actions)
        }
        if (roomsShown) {
            group.rooms.forEach { room ->
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(MusicDimens.controlsGap)) {
                    Text(room.name, style = HhType.body, color = c.ink, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
                    VolumeSlider(room.name, room.volume, "${tag}_room_${room.id}_volume", Modifier.width(MusicDimens.roomSlider)) {
                        actions.onRoomVolume(group, room, it)
                    }
                }
            }
        }
        if (line != null) Text(line, style = HhType.secondary, color = c.danger, modifier = Modifier.testTag("${tag}_line"))
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
    var roomsShown by remember { mutableStateOf(emptySet<String>()) }
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
        onRooms = { id -> roomsShown = if (id in roomsShown) roomsShown - id else roomsShown + id },
        actions = groupActions,
    )
}
