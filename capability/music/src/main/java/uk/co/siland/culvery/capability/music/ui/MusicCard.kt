package uk.co.siland.culvery.capability.music.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import uk.co.siland.culvery.capability.music.HomePick
import uk.co.siland.culvery.capability.music.LOOKING_FOR_SPEAKERS
import uk.co.siland.culvery.capability.music.MUSIC
import uk.co.siland.culvery.capability.music.MusicActions
import uk.co.siland.culvery.capability.music.MusicChecks
import uk.co.siland.culvery.capability.music.MusicView
import uk.co.siland.culvery.capability.music.problemLine
import uk.co.siland.culvery.core.ui.ButtonSurface
import uk.co.siland.culvery.core.ui.Culvery
import uk.co.siland.culvery.core.ui.HhCard
import uk.co.siland.culvery.core.ui.HhType
import uk.co.siland.culvery.core.ui.ShellTokens

/**
 * The REGULAR Home card (6 §5.1): the room Home follows and what it plays, Play/Pause, Next and the group's volume;
 * Shuffle everything only while that group isn't playing, so a tap mid-song can't replace the queue. With a problem,
 * the §6 line and no controls; before the first check, that it is looking.
 */
@Composable
fun MusicCard(pick: HomePick?, status: String?, line: String?, actions: GroupActions, modifier: Modifier = Modifier) {
    val c = Culvery.colors
    HhCard(
        modifier = modifier.fillMaxSize().testTag("music_card"),
        radius = ShellTokens.homeCardRadius,
        padding = PaddingValues(horizontal = MusicDimens.cardPaddingH, vertical = MusicDimens.cardPaddingV),
    ) {
        if (status != null || pick == null) {
            Text(MUSIC, style = HhType.cardTitle, color = c.ink, modifier = Modifier.semantics { heading() })
            Spacer(Modifier.height(MusicDimens.controlsGap))
            if (status != null) MusicStatusLine(status) else Text(LOOKING_FOR_SPEAKERS, style = HhType.body, color = c.mute)
            return@HhCard
        }
        val group = pick.group
        NowPlayingBlock(group, pick.nothingPlaying, MusicDimens.cardArt, HhType.cardTitle, "music_card_now")
        Spacer(Modifier.weight(1f))
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(MusicDimens.controlsGap)) {
            PlayPauseButton(group, "music_card", actions)
            NextButton(group, "music_card", actions)
            if (!group.isPlaying) ShuffleButton(group, "music_card", ButtonSurface.Surf, actions)
        }
        VolumeSlider(group.name, group.volume, "music_card_volume", Modifier.fillMaxWidth()) { actions.onGroupVolume(group, it) }
        if (line != null) Text(line, style = HhType.secondary, color = c.danger, maxLines = 2, modifier = Modifier.testTag("music_card_line"))
    }
}

/** The card over the view; while it shows, the speakers are checked (6 §4.2). */
@Composable
internal fun MusicCardHost(view: MusicView, actions: MusicActions, checks: MusicChecks) {
    DisposableEffect(checks) {
        val stop = checks.watch()
        onDispose { stop() }
    }
    val pick by view.home.collectAsStateWithLifecycle()
    val problem by view.problem.collectAsStateWithLifecycle(null)
    val lines by actions.lines.collectAsStateWithLifecycle()
    val groupActions = remember(actions) { actions.forGroups(onFavourites = {}) }
    MusicCard(pick, problem?.let(::problemLine), pick?.let { lines[it.group.id] }, groupActions)
}
