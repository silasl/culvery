package uk.co.siland.culvery.capability.music.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import uk.co.siland.culvery.capability.music.MusicActions
import uk.co.siland.culvery.capability.music.PLAY_LAST
import uk.co.siland.culvery.capability.music.SHUFFLE_EVERYTHING
import uk.co.siland.culvery.capability.music.SpeakerGroup
import uk.co.siland.culvery.capability.music.SpeakerRoom
import uk.co.siland.culvery.capability.music.groupSpoken
import uk.co.siland.culvery.capability.music.nextSpoken
import uk.co.siland.culvery.capability.music.nowPlayingLines
import uk.co.siland.culvery.capability.music.pauseSpoken
import uk.co.siland.culvery.capability.music.percentWords
import uk.co.siland.culvery.capability.music.playLastSpoken
import uk.co.siland.culvery.capability.music.playSpoken
import uk.co.siland.culvery.capability.music.shuffleSpoken
import uk.co.siland.culvery.capability.music.volumeName
import uk.co.siland.culvery.core.ui.ButtonSurface
import uk.co.siland.culvery.core.ui.ButtonTone
import uk.co.siland.culvery.core.ui.Culvery
import uk.co.siland.culvery.core.ui.HhButton
import uk.co.siland.culvery.core.ui.HhIcon
import uk.co.siland.culvery.core.ui.HhPercentSlider
import uk.co.siland.culvery.core.ui.HhRoundButton
import uk.co.siland.culvery.core.ui.HhStatusLine
import uk.co.siland.culvery.core.ui.HhType
import uk.co.siland.culvery.core.ui.Icons
import uk.co.siland.culvery.core.ui.shownNotSpoken

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

/** The labelled Play that resumes what a quiet room last played; the same [GroupActions.onPlay] as the round Play (9 §4.5). */
@Composable
internal fun PlayLastButton(group: SpeakerGroup, tag: String, actions: GroupActions) = HhButton(
    PLAY_LAST, { actions.onPlay(group) }, Modifier.testTag("${tag}_play"),
    tone = ButtonTone.Primary, icon = Icons.PLAY_ARROW, on = ButtonSurface.Surf, spokenName = playLastSpoken(group.name),
)

@Composable
internal fun PlayPauseButton(group: SpeakerGroup, tag: String, actions: GroupActions, playing: Boolean = group.isPlaying) {
    if (playing) {
        HhRoundButton(Icons.PAUSE, pauseSpoken(group.name), { actions.onPause(group) }, Modifier.testTag("${tag}_pause"), tone = ButtonTone.Primary, on = ButtonSurface.Surf)
    } else {
        HhRoundButton(Icons.PLAY_ARROW, playSpoken(group.name), { actions.onPlay(group) }, Modifier.testTag("${tag}_play"), tone = ButtonTone.Primary, on = ButtonSurface.Surf)
    }
}

/** Greyed for a stream (6 §4.3). */
@Composable
internal fun NextButton(group: SpeakerGroup, tag: String, actions: GroupActions) =
    HhRoundButton(
        Icons.SKIP_NEXT, nextSpoken(group.name), { actions.onNext(group) }, Modifier.testTag("${tag}_next"),
        enabled = group.canSkip, on = ButtonSurface.Surf,
    )

@Composable
internal fun ShuffleButton(group: SpeakerGroup, tag: String, on: ButtonSurface, actions: GroupActions, compact: Boolean = false) = HhButton(
    SHUFFLE_EVERYTHING, { actions.onShuffle(group) }, Modifier.testTag("${tag}_shuffle"),
    icon = Icons.SHUFFLE, on = on, spokenName = shuffleSpoken(group.name), compact = compact,
)

/** Art, the group's name, and what it plays, read by TalkBack as one sentence (6 §5.5). */
@Composable
internal fun NowPlayingBlock(
    group: SpeakerGroup,
    nothingPlaying: Boolean,
    artSize: Dp,
    nameStyle: TextStyle,
    tag: String,
    modifier: Modifier = Modifier,
    firstStyle: TextStyle = HhType.body,
    secondStyle: TextStyle = HhType.secondary,
    artRadius: Dp = MusicDimens.artRadius,
) {
    val c = Culvery.colors
    val spoken = groupSpoken(group, nothingPlaying)
    val (first, second) = nowPlayingLines(group, nothingPlaying)
    val quiet = nothingPlaying || group.nowPlaying == null
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(MusicDimens.artGap),
        modifier = modifier.testTag(tag).clearAndSetSemantics { contentDescription = spoken },
    ) {
        ArtImage(group.artUrl.takeUnless { quiet }, artSize, Modifier.testTag("${tag}_art"), artRadius)
        Column(Modifier.weight(1f)) {
            Text(group.name, style = nameStyle, color = c.ink, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(first, style = firstStyle, color = if (quiet) c.mute else c.ink, maxLines = 1, overflow = TextOverflow.Ellipsis)
            if (second != null) Text(second, style = secondStyle, color = c.mute, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
    }
}

/** The one line saying what is wrong and how to fix it (6 §6), on the card, the tab and Settings › Music. */
@Composable
internal fun MusicStatusLine(text: String) = HhStatusLine(text, "music_status")

/** A group's or room's [volume] in a 48 dp `surf2` pill: the speaker icon, a thick track and the number (9 §4.2). Tagged [tag]; the slider [tag]_slider. */
@Composable
internal fun VolumePill(name: String, volume: Int, tag: String, onLetGo: (Int) -> Unit, modifier: Modifier = Modifier) {
    val c = Culvery.colors
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(MusicDimens.pillGap),
        modifier = modifier
            .testTag(tag)
            .height(MusicDimens.pillHeight)
            .clip(CircleShape)
            .background(c.surf2)
            .padding(start = MusicDimens.pillPaddingStart, end = MusicDimens.pillPaddingEnd),
    ) {
        HhIcon(Icons.VOLUME_UP, size = MusicDimens.pillIcon, tint = c.mute, modifier = Modifier.shownNotSpoken())
        HhPercentSlider(
            name, volume, "${tag}_slider", Modifier.weight(1f),
            spokenName = volumeName(name), spokenState = ::percentWords,
            label = { Text("$it", style = MusicDimens.pillNumber, color = c.ink, maxLines = 1, modifier = Modifier.shownNotSpoken()) },
            trackHeight = MusicDimens.pillTrack, onLetGo = onLetGo,
        )
    }
}
