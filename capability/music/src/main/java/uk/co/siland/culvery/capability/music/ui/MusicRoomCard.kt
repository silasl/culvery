package uk.co.siland.culvery.capability.music.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextOverflow
import uk.co.siland.culvery.capability.music.FAVOURITES_LABEL
import uk.co.siland.culvery.capability.music.SpeakerGroup
import uk.co.siland.culvery.capability.music.favouritesSpoken
import uk.co.siland.culvery.capability.music.isQuiet
import uk.co.siland.culvery.capability.music.roomsAction
import uk.co.siland.culvery.capability.music.roomsChip
import uk.co.siland.culvery.core.ui.ButtonSurface
import uk.co.siland.culvery.core.ui.Culvery
import uk.co.siland.culvery.core.ui.HhButton
import uk.co.siland.culvery.core.ui.HhCard
import uk.co.siland.culvery.core.ui.HhChoiceChip
import uk.co.siland.culvery.core.ui.HhIcon
import uk.co.siland.culvery.core.ui.HhType
import uk.co.siland.culvery.core.ui.Icons

/** A group: what it plays, the Play/Pause and Next buttons and its volume pill; the group chip on a group of several shows each room's pill (9 §4.5). */
@Composable
internal fun MusicRoomCard(
    group: SpeakerGroup,
    line: String?,
    roomsShown: Boolean,
    onRooms: () -> Unit,
    actions: GroupActions,
    modifier: Modifier = Modifier,
) {
    val c = Culvery.colors
    val tag = "music_row_${group.id}"
    val quiet = group.isQuiet()
    HhCard(
        modifier = modifier.fillMaxWidth().testTag(tag),
        radius = MusicDimens.roomRadius,
        padding = PaddingValues(horizontal = MusicDimens.roomPaddingH, vertical = MusicDimens.roomPaddingV),
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(MusicDimens.controlsGap)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(MusicDimens.controlsGap)) {
                NowPlayingBlock(
                    group, nothingPlaying = quiet, MusicDimens.roomArt, MusicDimens.roomName, "${tag}_now", Modifier.weight(1f),
                    firstStyle = MusicDimens.roomTrack, artRadius = MusicDimens.cardArtRadius,
                )
                if (quiet) {
                    PlayLastButton(group, tag, actions)
                } else {
                    PlayPauseButton(group, tag, actions)
                    NextButton(group, tag, actions)
                }
                VolumePill(group.name, group.volume, "${tag}_volume", { actions.onGroupVolume(group, it) }, Modifier.width(MusicDimens.roomPill))
            }
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(MusicDimens.controlsGap)) {
                if (group.rooms.size > 1) {
                    HhChoiceChip(
                        roomsChip(group.rooms.size), selected = roomsShown, tag = "${tag}_rooms", onClick = onRooms,
                        selectedColor = c.accentSoft, selectedInk = c.accent, unselectedFill = c.accentSoft, unselectedInk = c.accent,
                        role = Role.Button, clickLabel = roomsAction(roomsShown),
                        trailing = { HhIcon(if (roomsShown) Icons.EXPAND_LESS else Icons.EXPAND_MORE, size = MusicDimens.chipIcon, tint = c.accent) },
                    )
                }
                HhButton(
                    FAVOURITES_LABEL, { actions.onFavourites(group) }, Modifier.testTag("${tag}_favourites"),
                    icon = Icons.STAR, on = ButtonSurface.Surf, spokenName = favouritesSpoken(group.name),
                )
                ShuffleButton(group, tag, ButtonSurface.Surf, actions)
            }
            if (roomsShown) {
                Column(verticalArrangement = Arrangement.spacedBy(MusicDimens.roomGap)) {
                    group.rooms.chunked(MusicDimens.ROOM_COLUMNS).forEach { cells ->
                        Row(horizontalArrangement = Arrangement.spacedBy(MusicDimens.roomGap)) {
                            cells.forEach { room ->
                                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f).height(MusicDimens.roomCellHeight)) {
                                    Text(room.name, style = HhType.body, color = c.ink, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.width(MusicDimens.roomNameWidth))
                                    VolumePill(room.name, room.volume, "${tag}_room_${room.id}_volume", { actions.onRoomVolume(group, room, it) }, Modifier.weight(1f))
                                }
                            }
                            repeat(MusicDimens.ROOM_COLUMNS - cells.size) { Spacer(Modifier.weight(1f)) }
                        }
                    }
                }
            }
            if (line != null) Text(line, style = HhType.secondary, color = c.danger, modifier = Modifier.testTag("${tag}_line"))
        }
    }
}
