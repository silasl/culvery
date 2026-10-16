package uk.co.siland.culvery.capability.music.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import uk.co.siland.culvery.capability.music.MusicActions
import uk.co.siland.culvery.capability.music.SHUFFLE_EVERYTHING
import uk.co.siland.culvery.capability.music.SpeakerGroup
import uk.co.siland.culvery.capability.music.SpeakerRoom
import uk.co.siland.culvery.capability.music.groupSpoken
import uk.co.siland.culvery.capability.music.nextSpoken
import uk.co.siland.culvery.capability.music.nowPlayingLines
import uk.co.siland.culvery.capability.music.pauseSpoken
import uk.co.siland.culvery.capability.music.playSpoken
import uk.co.siland.culvery.capability.music.shuffleSpoken
import uk.co.siland.culvery.core.ui.ButtonSurface
import uk.co.siland.culvery.core.ui.Culvery
import uk.co.siland.culvery.core.ui.HhButton
import uk.co.siland.culvery.core.ui.HhIcon
import uk.co.siland.culvery.core.ui.HhStatusLine
import uk.co.siland.culvery.core.ui.HhType
import uk.co.siland.culvery.core.ui.Icons
import uk.co.siland.culvery.core.ui.hhClickable
import uk.co.siland.culvery.core.ui.quietFill

/** What a group's controls do; the Home card and the tab's rows share them. */
class GroupActions(
    val onPlay: (SpeakerGroup) -> Unit,
    val onPause: (SpeakerGroup) -> Unit,
    val onNext: (SpeakerGroup) -> Unit,
    val onGroupVolume: (SpeakerGroup, Int) -> Unit,
    val onRoomVolume: (SpeakerGroup, SpeakerRoom, Int) -> Unit,
    val onShuffle: (SpeakerGroup) -> Unit,
    val onFavourites: (SpeakerGroup) -> Unit = {},
) {
    companion object {
        /** For screenshots: nothing happens. */
        val None = GroupActions({}, {}, {}, { _, _ -> }, { _, _, _ -> }, {})
    }
}

internal fun MusicActions.forGroups(onFavourites: (SpeakerGroup) -> Unit = {}) =
    GroupActions(this::play, this::pause, this::next, this::setGroupVolume, this::setRoomVolume, this::shuffleEverything, onFavourites)

/** A round 48 dp control named by [spoken]; disabled, its icon is `mute` and TalkBack hears it as unavailable. */
@Composable
internal fun RoundButton(icon: String, spoken: String, tag: String, enabled: Boolean = true, onClick: () -> Unit) {
    val c = Culvery.colors
    Box(
        contentAlignment = Alignment.Center,
        modifier = Modifier
            .testTag(tag)
            .size(MusicDimens.button)
            .clip(CircleShape)
            .background(quietFill(ButtonSurface.Surf, c))
            .semantics { contentDescription = spoken }
            .hhClickable(onClick, enabled = enabled),
    ) {
        HhIcon(icon, size = MusicDimens.buttonIcon, tint = if (enabled) c.ink else c.mute)
    }
}

@Composable
internal fun PlayPauseButton(group: SpeakerGroup, tag: String, actions: GroupActions) {
    if (group.isPlaying) {
        RoundButton(Icons.PAUSE, pauseSpoken(group.name), "${tag}_pause") { actions.onPause(group) }
    } else {
        RoundButton(Icons.PLAY_ARROW, playSpoken(group.name), "${tag}_play") { actions.onPlay(group) }
    }
}

/** Greyed for a stream (6 §4.3). */
@Composable
internal fun NextButton(group: SpeakerGroup, tag: String, actions: GroupActions) =
    RoundButton(Icons.SKIP_NEXT, nextSpoken(group.name), "${tag}_next", enabled = group.canSkip) { actions.onNext(group) }

@Composable
internal fun ShuffleButton(group: SpeakerGroup, tag: String, on: ButtonSurface, actions: GroupActions, compact: Boolean = false) = HhButton(
    SHUFFLE_EVERYTHING, { actions.onShuffle(group) }, Modifier.testTag("${tag}_shuffle"),
    icon = Icons.SHUFFLE, on = on, spokenName = shuffleSpoken(group.name), compact = compact,
)

/** Art, the group's name, and what it plays, read by TalkBack as one sentence (6 §5.5). */
@Composable
internal fun NowPlayingBlock(group: SpeakerGroup, nothingPlaying: Boolean, artSize: Dp, nameStyle: TextStyle, tag: String, modifier: Modifier = Modifier) {
    val c = Culvery.colors
    val spoken = groupSpoken(group, nothingPlaying)
    val (first, second) = nowPlayingLines(group, nothingPlaying)
    val quiet = nothingPlaying || group.nowPlaying == null
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(MusicDimens.artGap),
        modifier = modifier.testTag(tag).clearAndSetSemantics { contentDescription = spoken },
    ) {
        ArtImage(group.artUrl.takeUnless { quiet }, artSize)
        Column(Modifier.weight(1f)) {
            Text(group.name, style = nameStyle, color = c.ink, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(first, style = HhType.body, color = if (quiet) c.mute else c.ink, maxLines = 1, overflow = TextOverflow.Ellipsis)
            if (second != null) Text(second, style = HhType.secondary, color = c.mute, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
    }
}

/** The one line saying what is wrong and how to fix it (6 §6), on the card, the tab and Settings › Music. */
@Composable
internal fun MusicStatusLine(text: String) = HhStatusLine(text, "music_status")
