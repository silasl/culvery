package uk.co.siland.culvery.capability.music.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertContentDescriptionEquals
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.runBlocking
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import uk.co.siland.culvery.capability.music.HomePick
import uk.co.siland.culvery.capability.music.MusicActions
import uk.co.siland.culvery.capability.music.MusicChecks
import uk.co.siland.culvery.capability.music.MusicFixture
import uk.co.siland.culvery.capability.music.MusicLink
import uk.co.siland.culvery.capability.music.MusicView
import uk.co.siland.culvery.capability.music.PlayState
import uk.co.siland.culvery.capability.music.SONG
import uk.co.siland.culvery.capability.music.STATION
import uk.co.siland.culvery.capability.music.Speakers
import uk.co.siland.culvery.capability.music.TestMusicProvider
import uk.co.siland.culvery.capability.music.group
import uk.co.siland.culvery.capability.music.recordingGroupActions
import uk.co.siland.culvery.core.ui.CulveryTheme
import uk.co.siland.culvery.core.ui.test.assertAccessible
import uk.co.siland.culvery.core.ui.test.assertClickablesHaveRoleAndLabel
import uk.co.siland.culvery.core.ui.test.waitFor

/** 6 §5.1, §5.5: the Home card's states, its controls and their words; it watches the speakers while it shows. */
@RunWith(AndroidJUnit4::class)
class MusicCardTest {
    @get:Rule val compose = createComposeRule()
    @get:Rule val music = MusicFixture()
    private val calls = mutableListOf<String>()
    private val actions = recordingGroupActions(calls)
    /** The right-hand Home cell; app's HomeGridTest pins the same size. */
    private val rightHalfSize = DpSize(701.dp, 285.5.dp)
    private val playing = group("k", "Kitchen", state = PlayState.PLAYING, nowPlaying = SONG, volume = 30)

    private fun show(pick: HomePick?, status: String? = null, line: String? = null) = compose.setContent {
        CulveryTheme(dark = true) { Box(Modifier.size(rightHalfSize)) { MusicCard(pick, status, line, actions) } }
    }

    @Test
    fun playingSaysWhatAndOffersPauseNextAndTheVolume() {
        show(HomePick(playing, nothingPlaying = false))
        compose.onNodeWithContentDescription("Kitchen, playing Song by Artist").assertExists()
        compose.onNodeWithTag("music_card_pause").assertContentDescriptionEquals("Pause Kitchen").performClick()
        compose.onNodeWithTag("music_card_next").assertIsEnabled().assertContentDescriptionEquals("Next track in Kitchen")
        compose.onNodeWithTag("music_card_volume_slider")
            .assert(SemanticsMatcher.expectValue(SemanticsProperties.ContentDescription, listOf("Kitchen volume")))
            .assert(SemanticsMatcher.expectValue(SemanticsProperties.StateDescription, "30 percent"))
        compose.onNodeWithTag("music_card_volume_slider").performSemanticsAction(SemanticsActions.SetProgress) { it(45f) }
        assertThat(calls).containsExactly("pause k", "volume k 45").inOrder()
        compose.assertAccessible()
    }

    /** Each volume sent is a command to the group; one per drag keeps them from arriving out of order. Fails if it sends at each step. */
    @Test
    fun aDragSendsTheVolumeOnceWhenLetGo() {
        show(HomePick(playing, nothingPlaying = false))
        compose.onNodeWithTag("music_card_volume_slider").performTouchInput {
            down(Offset(width * 0.1f, centerY))
            repeat(10) { moveBy(Offset(width * 0.05f, 0f)) }
            up()
        }
        compose.waitForIdle()
        assertThat(calls).hasSize(1)
        assertThat(calls.single()).startsWith("volume k ")
    }

    /**
     * A check that reads a new group volume mid-drag doesn't take the drag away: letting go sends where the finger is.
     * Fails if the drag is reset with the volume, so the release sends nothing.
     */
    @Test
    fun aVolumeReadMidDragKeepsTheDrag() {
        var pick by mutableStateOf(HomePick(playing, nothingPlaying = false))
        compose.setContent { CulveryTheme(dark = true) { Box(Modifier.size(rightHalfSize)) { MusicCard(pick, null, null, actions) } } }
        val volume = compose.onNodeWithTag("music_card_volume_slider")
        volume.performTouchInput {
            down(Offset(width * 0.1f, centerY))
            repeat(10) { moveBy(Offset(width * 0.05f, 0f)) }
        }
        compose.waitForIdle()
        pick = HomePick(playing.copy(volume = 31), nothingPlaying = false)
        compose.waitForIdle()
        volume.performTouchInput { up() }
        compose.waitForIdle()
        assertThat(calls).hasSize(1)
        assertThat(calls.single()).isNotEqualTo("volume k 31")
    }

    /** The thumb stays where it was let go until the group's volume changes, then follows it. Fails if it jumps back to 30. */
    @Test
    fun theVolumeLetGoStaysUntilTheGroupsVolumeChanges() {
        var pick by mutableStateOf(HomePick(playing, nothingPlaying = false))
        compose.setContent { CulveryTheme(dark = true) { Box(Modifier.size(rightHalfSize)) { MusicCard(pick, null, null, actions) } } }
        val volume = compose.onNodeWithTag("music_card_volume_slider")
        volume.performSemanticsAction(SemanticsActions.SetProgress) { it(45f) }
        volume.assert(SemanticsMatcher.expectValue(SemanticsProperties.StateDescription, "45 percent"))
        pick = HomePick(playing.copy(volume = 50), nothingPlaying = false)
        volume.assert(SemanticsMatcher.expectValue(SemanticsProperties.StateDescription, "50 percent"))
    }

    /** 6 §5.1: a tap mid-song can't replace the queue. Fails if Shuffle everything shows while playing. */
    @Test
    fun shuffleEverythingIsHiddenWhilePlaying() {
        show(HomePick(playing, nothingPlaying = false))
        compose.onNodeWithTag("music_card_shuffle").assertDoesNotExist()
    }

    /** 6 §4.3: Next greyed for a stream. */
    @Test
    fun nextIsGreyedForRadio() {
        show(HomePick(group("l", "Lounge", state = PlayState.PLAYING, nowPlaying = STATION), nothingPlaying = false))
        compose.onNodeWithTag("music_card_next").assertIsNotEnabled()
        compose.onNodeWithContentDescription("Lounge, playing Culvery FM, The Breakfast Show").assertExists()
    }

    /** 9 §4.2: with nothing playing Shuffle everything stands where Next would, and Next is hidden. */
    @Test
    fun nothingPlayingHidesNextAndOffersShuffleInItsPlace() {
        show(HomePick(group("k", "Kitchen", state = PlayState.PAUSED, nowPlaying = SONG), nothingPlaying = true))
        compose.onNodeWithContentDescription("Kitchen, nothing playing").assertExists()
        compose.onNodeWithTag("music_card_next").assertDoesNotExist()
        compose.onNodeWithTag("music_card_play").assertContentDescriptionEquals("Play Kitchen").performClick()
        compose.onNodeWithTag("music_card_shuffle").assertContentDescriptionEquals("Shuffle everything in Kitchen").performClick()
        assertThat(calls).containsExactly("play k", "shuffle k").inOrder()
        compose.assertClickablesHaveRoleAndLabel()
    }

    /** 9 §4.2: paused with a queue the row is Play, Next, volume; no Shuffle. */
    @Test
    fun pausedShowsPlayAndNextAndNoShuffle() {
        show(HomePick(playing.copy(state = PlayState.PAUSED), nothingPlaying = false))
        compose.onNodeWithTag("music_card_play").assertExists()
        compose.onNodeWithTag("music_card_next").assertExists()
        compose.onNodeWithTag("music_card_shuffle").assertDoesNotExist()
        compose.assertClickablesHaveRoleAndLabel()
    }

    /** 9 §4.2: Pause, Next and the volume pill share one row. Fails while the volume sits on a row of its own. */
    @Test
    fun oneControlRow() {
        show(HomePick(playing, nothingPlaying = false))
        val centres = listOf("music_card_pause", "music_card_next", "music_card_volume").map {
            val b = compose.onNodeWithTag(it).getUnclippedBoundsInRoot()
            (b.top.value + b.bottom.value) / 2
        }
        assertThat(centres[1]).isWithin(1f).of(centres[0])
        assertThat(centres[2]).isWithin(1f).of(centres[0])
        compose.assertClickablesHaveRoleAndLabel()
    }

    /** 9 §4.2: the pill names the speaker, shows the number, and letting go sends the volume. */
    @Test
    fun theVolumePillHasTheSpeakerAndTheNumber() {
        show(HomePick(playing, nothingPlaying = false))
        compose.onNodeWithText("30").assertExists()
        compose.onNodeWithTag("music_card_volume_slider")
            .assert(SemanticsMatcher.expectValue(SemanticsProperties.ContentDescription, listOf("Kitchen volume")))
            .assert(SemanticsMatcher.expectValue(SemanticsProperties.StateDescription, "30 percent"))
        compose.onNodeWithTag("music_card_volume_slider").performTouchInput {
            down(Offset(width * 0.1f, centerY))
            moveBy(Offset(width * 0.4f, 0f))
            up()
        }
        compose.waitForIdle()
        assertThat(calls.single()).startsWith("volume k ")
        compose.assertClickablesHaveRoleAndLabel()
    }

    /** 9 §4.2: the art is 88 dp. */
    @Test
    fun theArtIs88Dp() {
        show(HomePick(playing, nothingPlaying = false))
        val art = compose.onNodeWithTag("music_card_now_art", useUnmergedTree = true).getUnclippedBoundsInRoot()
        assertThat((art.right - art.left).value).isWithin(0.01f).of(88f)
        assertThat((art.bottom - art.top).value).isWithin(0.01f).of(88f)
        compose.assertClickablesHaveRoleAndLabel()
    }

    /** 6 §2.2: Home keeps the group it showed playing (its track shows: `music_card_paused_dark`), and offers Play. */
    @Test
    fun aPausedGroupHomeKeptSaysPaused() {
        show(HomePick(playing.copy(state = PlayState.PAUSED), nothingPlaying = false))
        compose.onNodeWithContentDescription("Kitchen, paused").assertExists()
        compose.onNodeWithTag("music_card_play").assertExists()
        compose.onNodeWithTag("music_card_shuffle").assertDoesNotExist()
    }

    /** 6 §5.1: unreachable, the §6 line and no controls. */
    @Test
    fun aProblemShowsItsLineAndNoControls() {
        show(HomePick(playing, false), status = "The tablet is offline. Check the Wi-Fi.")
        compose.onNodeWithText("The tablet is offline. Check the Wi-Fi.").assertExists()
        compose.onNodeWithTag("music_card_pause").assertDoesNotExist()
        compose.onNodeWithTag("music_card_volume").assertDoesNotExist()
        compose.onNodeWithTag("music_card_volume_slider").assertDoesNotExist()
    }

    /** 6 §6: a refused command says so on the card. */
    @Test
    fun aRefusedCommandShowsItsLine() {
        show(HomePick(playing, false), line = "Sonos didn’t do that. Try again.")
        compose.onNodeWithTag("music_card_line").assertTextEquals("Sonos didn’t do that. Try again.")
    }

    @Test
    fun beforeTheFirstCheckItSaysItIsLooking() {
        show(null)
        compose.onNodeWithText("Looking for the speakers…").assertExists()
    }

    /** 6 §4.2: the speakers are checked while the card shows, and not once it has gone. Fails if the watch outlives the card. */
    @Test
    fun theCardWatchesOnlyWhileItShows() {
        val scope = music.scope
        val settings = music.settings
        val provider = TestMusicProvider()
        val speakers = Speakers(setOf(provider), settings, scope)
        runBlocking { settings.connect("music.sonos", MusicLink("Sonos_home", "192.168.1.20:1400")) }
        val view = MusicView(speakers, settings, scope)
        val checks = MusicChecks(speakers.system, MutableStateFlow(true), scope).also { it.start() }
        var shown by mutableStateOf(true)
        compose.setContent { CulveryTheme(dark = true) { if (shown) MusicCardHost(view, MusicActions({ null }, scope), checks) } }
        compose.waitFor { provider.systems.singleOrNull()?.calls?.contains("checkLayout") == true }
        assertThat(checks.watching).isEqualTo(1)
        shown = false
        compose.waitForIdle()
        assertThat(checks.watching).isEqualTo(0)
    }
}
