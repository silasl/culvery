package uk.co.siland.culvery.capability.party.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertContentDescriptionEquals
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertIsOff
import androidx.compose.ui.test.assertIsOn
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.CompletableDeferred
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.GraphicsMode
import uk.co.siland.culvery.capability.party.DINING
import uk.co.siland.culvery.capability.party.FakeMusic
import uk.co.siland.culvery.capability.party.KITCHEN
import uk.co.siland.culvery.capability.party.LOUNGE
import uk.co.siland.culvery.capability.party.ModesWords
import uk.co.siland.culvery.capability.party.MusicChoice
import uk.co.siland.culvery.capability.party.PARTY_MODE
import uk.co.siland.culvery.capability.party.StoredMode
import uk.co.siland.culvery.core.house.HouseMusic
import uk.co.siland.culvery.core.house.HouseRoom
import uk.co.siland.culvery.core.house.MusicOptions
import uk.co.siland.culvery.core.plugin.LocalOverlayHost
import uk.co.siland.culvery.core.plugin.test.TestOverlay
import uk.co.siland.culvery.core.ui.CulveryTheme
import uk.co.siland.culvery.core.ui.test.assertClickablesHaveRoleAndLabel
import uk.co.siland.culvery.core.ui.test.waitFor

/** 15b §4.2: a mode's music and speakers, moved from Party mode's old page (13 §3). */
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class ModeMusicSectionTest {
    @get:Rule val compose = createComposeRule()
    private val overlay = TestOverlay()
    private val music = FakeMusic().apply { offered = MUSIC_OPTIONS }
    private var mode by mutableStateOf(PARTY_MODE)

    /** The section over [mode], which each change it asks for replaces; [withMusic] false is no Sonos at all. */
    private fun section(start: StoredMode = PARTY_MODE, withMusic: Boolean = true, waitFor: String? = "mode_music") {
        mode = start
        val seam: HouseMusic? = music.takeIf { withMusic }
        compose.setContent {
            CulveryTheme(dark = true) {
                CompositionLocalProvider(LocalOverlayHost provides overlay) {
                    Box(Modifier.size(960.dp, 900.dp)) {
                        InSettingsPane { ModeMusicSection(mode, seam) { change -> mode = change(mode) } }
                        overlay.content?.invoke()
                    }
                }
            }
        }
        if (waitFor != null) compose.waitFor { count(waitFor) > 0 }
    }

    private fun count(tag: String) = compose.onAllNodesWithTag(tag).fetchSemanticsNodes().size

    private val sonosLine = "Set up Sonos in Settings › Music to choose the music."

    @Test
    fun musicDefaultsToNone() {
        section()
        compose.onNodeWithTag("mode_music").assertTextEquals("Music", "None")
        compose.assertClickablesHaveRoleAndLabel()
    }

    @Test
    fun pickingMusicSavesIt() {
        section()
        compose.onNodeWithTag("mode_music").performClick()
        compose.onNodeWithTag("mode_music_sheet").assert(SemanticsMatcher.expectValue(SemanticsProperties.PaneTitle, "Music for Party mode"))
        compose.onNodeWithTag("mode_music_0").assertTextEquals("None")
        compose.onNodeWithTag("mode_music_1").assertTextEquals("Sunday morning")
        compose.onNodeWithTag("mode_music_2").assertTextEquals("Party mix")
        compose.assertClickablesHaveRoleAndLabel()

        compose.onNodeWithTag("mode_music_2").performClick()

        assertThat(mode.music).isEqualTo(MusicChoice(MIX.id, MIX.name))
        compose.onAllNodesWithTag("mode_music_sheet").assertCountEquals(0)
        compose.onNodeWithTag("mode_music").assertTextEquals("Music", "Party mix")
    }

    @Test
    fun noneClearsTheMusicAndKeepsTheRooms() {
        section(PARTY_MODE.copy(music = MusicChoice(MIX.id, MIX.name), speakers = listOf(KITCHEN, LOUNGE)))
        compose.onNodeWithTag("mode_music").performClick()

        compose.onNodeWithTag("mode_music_0").performClick()

        assertThat(mode.music).isNull()
        assertThat(mode.speakers).containsExactly(KITCHEN, LOUNGE).inOrder()
    }

    @Test
    fun everySpeakerByDefault() {
        section()
        for ((i, name) in listOf("Kitchen", "Lounge", "Dining").withIndex()) {
            compose.onNodeWithTag("mode_speaker_$i").assertTextEquals(name).assertIsOn().assertIsEnabled()
        }
        compose.assertClickablesHaveRoleAndLabel()
    }

    @Test
    fun switchingARoomOffSavesTheRestAndOnSavesAll() {
        section()
        compose.onNodeWithTag("mode_speaker_2").performClick()
        assertThat(mode.speakers).containsExactly(KITCHEN, LOUNGE).inOrder()
        compose.onNodeWithTag("mode_speaker_2").assertIsOff()

        compose.onNodeWithTag("mode_speaker_2").performClick()

        assertThat(mode.speakers).isNull()
    }

    @Test
    fun theRoomsSaveWithNoMusicPicked() {
        section()
        assertThat(mode.music).isNull()

        compose.onNodeWithTag("mode_speaker_0").performClick()

        assertThat(mode.speakers).containsExactly(LOUNGE, DINING).inOrder()
    }

    @Test
    fun theLastSpeakerCantBeSwitchedOff() {
        section(PARTY_MODE.copy(speakers = listOf(DINING)))
        compose.onNodeWithTag("mode_speaker_0").assertIsOff().assertIsEnabled()
        compose.onNodeWithTag("mode_speaker_2").assertIsOn().assertIsNotEnabled()
        compose.assertClickablesHaveRoleAndLabel()
    }

    /** Ledger 2: fails if switching speakers off one by one can ever save an empty list, which would read "on 0 speakers". */
    @Test
    fun switchingEverySpeakerOffNeverSavesNone() {
        section(PARTY_MODE.copy(music = MusicChoice(MIX.id, MIX.name)))

        compose.onNodeWithTag("mode_speaker_0").performClick()
        compose.onNodeWithTag("mode_speaker_1").performClick()
        compose.onNodeWithTag("mode_speaker_2").performClick()

        assertThat(mode.speakers).containsExactly(DINING)
        compose.onNodeWithTag("mode_speaker_2").assertIsOn().assertIsNotEnabled()
        assertThat(ModesWords.summary(mode, on = false, plugs = 0)).contains("Party mix on 1 speaker")
    }

    @Test
    fun turningOffTheOnlyChosenRoomChangesNothing() {
        val all = listOf(KITCHEN, LOUNGE, DINING)
        val saved = PARTY_MODE.copy(speakers = listOf(DINING))
        assertThat(saved.withSpeaker(all, DINING, on = false)).isEqualTo(saved)
    }

    @Test
    fun switchingOneOnWhenEverySavedRoomHasGoneSavesJustThatRoom() {
        val all = listOf(KITCHEN, LOUNGE, DINING)
        val saved = PARTY_MODE.copy(speakers = listOf(HouseRoom("Gone-id", "Gone")))
        assertThat(saved.withSpeaker(all, LOUNGE, on = true).speakers).containsExactly(LOUNGE)
    }

    @Test
    fun aSavedChoiceNoLongerOfferedStillShows() {
        section(PARTY_MODE.copy(music = MusicChoice("FV:9/9", "Old mix")))
        compose.onNodeWithTag("mode_music").assertTextEquals("Music", "Old mix")
    }

    @Test
    fun unlinkedSystemSaysSetUp() {
        section(withMusic = false, waitFor = "mode_music_line")
        compose.onNodeWithTag("mode_music_line").assertTextEquals(sonosLine)
        compose.onAllNodesWithTag("mode_music").assertCountEquals(0)
        compose.onAllNodesWithTag("mode_speaker_0").assertCountEquals(0)
        compose.assertClickablesHaveRoleAndLabel()
    }

    @Test
    fun aSeamThatIsntReadySaysSetUp() {
        music.readyNow.value = false
        section(waitFor = "mode_music_line")
        compose.onNodeWithTag("mode_music_line").assertTextEquals(sonosLine)
    }

    @Test
    fun aReadStillRunningShowsNoSetUpLine() {
        val gate = CompletableDeferred<Unit>()
        music.optionsGate = gate
        section(waitFor = null)
        compose.waitForIdle()
        compose.onAllNodesWithTag("mode_music_line").assertCountEquals(0)
        compose.onAllNodesWithTag("mode_music").assertCountEquals(0)

        gate.complete(Unit)

        compose.waitFor { count("mode_music") > 0 }
        compose.onAllNodesWithTag("mode_music_line").assertCountEquals(0)
    }

    @Test
    fun aReadThatThrowsShowsTheProblemLine() {
        music.optionsThrow = true
        section(waitFor = "mode_music_line")
        compose.onNodeWithTag("mode_music_line")
            .assertTextEquals("Culvery can’t reach Sonos. Check the speakers are on and the tablet is on the home Wi-Fi.")
    }

    @Test
    fun aReadFailureShowsTheLine() {
        val line = "Culvery can’t reach Sonos. Check the speakers are on and the tablet is on the home Wi-Fi."
        music.offered = MusicOptions(emptyList(), emptyList(), line)
        section(waitFor = "mode_music_line")
        compose.onNodeWithTag("mode_music_line").assertTextEquals(line)
        compose.assertClickablesHaveRoleAndLabel()
    }

    @Test
    fun theRowsSpeakTheirValue() {
        section(PARTY_MODE.copy(music = MusicChoice(MIX.id, MIX.name)))
        compose.onNodeWithTag("mode_music").assertContentDescriptionEquals("Music, Party mix")
        compose.onNodeWithTag("mode_speaker_2").assert(SemanticsMatcher.expectValue(SemanticsProperties.Role, Role.Switch)).assert(hasText("Dining"))
        compose.assertClickablesHaveRoleAndLabel()
    }
}
