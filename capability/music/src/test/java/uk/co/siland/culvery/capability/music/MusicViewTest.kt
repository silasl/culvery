package uk.co.siland.culvery.capability.music

import androidx.test.ext.junit.runners.AndroidJUnit4
import app.cash.turbine.test
import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.takeWhile
import kotlinx.coroutines.test.runTest
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class MusicViewTest {
    @get:Rule val music = MusicFixture()
    private val scope = music.scope
    private val kitchen = group("k", "Kitchen")
    private val bedroom = group("b", "Bedroom")

    /**
     * 6 §2.2: Home's memory outlives the card (Settings over Home, another tab, then back): the card's
     * second collection still sees the room it paused. Fails if `home`'s memory starts afresh when nothing collects it
     * (a cold flow, or one shared only while subscribed).
     */
    @Test
    fun theRoomPausedFromHomeSurvivesTheCardLeaving() = runTest {
        val settings = music.settings
        val provider = TestMusicProvider()
        val speakers = Speakers(setOf(provider), settings, scope)
        settings.connect("music.sonos", MusicLink("Sonos_home", "192.168.1.20:1400"))
        settings.setUsualRoom("Kitchen-id")
        // The work runs on Dispatchers.IO, which the test scheduler's virtual time doesn't wait for.
        soon { speakers.system.first { it != null } }
        val system = provider.systems.single()
        val view = MusicView(speakers, settings, scope)
        val paused = bedroom.copy(state = PlayState.PAUSED, nowPlaying = SONG)
        system.groups.value = listOf(kitchen, bedroom.copy(state = PlayState.PLAYING, nowPlaying = SONG))
        soon {
            view.home.takeWhile { it?.group?.state != PlayState.PAUSED }.collect { pick ->
                if (pick?.group?.id == "b") system.groups.value = listOf(kitchen, paused)
            }
        }
        // While the card is away a check reads a new volume, so the card's return can't be answered from a cached pick.
        val turned = paused.copy(volume = 40)
        system.groups.value = listOf(kitchen, turned)
        val back = soon { view.home.first { it?.group in system.groups.value } }
        assertThat(back).isEqualTo(HomePick(turned, nothingPlaying = false))
    }

    /** 6 §2.2: Pause on Home, then Play, is the same room. Fails if homePicks forgets what it showed. */
    @Test
    fun pausingTheShownGroupKeepsItOnHome() = runTest {
        val groups = MutableStateFlow(listOf(kitchen, bedroom.copy(state = PlayState.PLAYING, nowPlaying = SONG)))
        homePicks(groups, MutableStateFlow("Kitchen-id")).test {
            assertThat(awaitItem()?.group?.id).isEqualTo("b")
            groups.value = listOf(kitchen, bedroom.copy(state = PlayState.PAUSED, nowPlaying = SONG))
            assertThat(awaitItem()).isEqualTo(HomePick(bedroom.copy(state = PlayState.PAUSED, nowPlaying = SONG), nothingPlaying = false))
            groups.value = listOf(kitchen, bedroom.copy(state = PlayState.STOPPED))
            assertThat(awaitItem()).isEqualTo(HomePick(kitchen, nothingPlaying = true))
        }
    }

    /** A paused usual room that Home never showed playing is "Nothing playing" (6 §5.1). */
    @Test
    fun aGroupPausedBeforeHomeShowedItIsNothingPlaying() = runTest {
        val paused = kitchen.copy(state = PlayState.PAUSED, nowPlaying = SONG)
        homePicks(MutableStateFlow(listOf(paused, bedroom)), MutableStateFlow("Kitchen-id")).test {
            assertThat(awaitItem()).isEqualTo(HomePick(paused, nothingPlaying = true))
        }
    }
}
