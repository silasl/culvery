package uk.co.siland.culvery.capability.music

import androidx.test.ext.junit.runners.AndroidJUnit4
import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.shadows.ShadowLog

/** 6 §4.3, §5.3, §6: commands to the system, the row's line when Sonos refuses, Favourites' outcomes, the logs. */
@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(AndroidJUnit4::class)
class MusicActionsTest {
    private val kitchen = group("k", "Kitchen", state = PlayState.PLAYING, nowPlaying = SONG)
    private val system = TestMusicSystem(listOf(kitchen))
    private val station = Favourite("FV:2/1", "Culvery FM", null, FavouriteKind.STREAM)

    private fun TestScope.actions() = MusicActions({ system }, backgroundScope)

    @Test
    fun eachCommandGoesToTheSystem() = runTest {
        val actions = actions()
        actions.pause(kitchen)
        actions.play(kitchen)
        actions.next(kitchen)
        actions.setGroupVolume(kitchen, 45)
        actions.setRoomVolume(kitchen, kitchen.rooms.single(), 35)
        actions.shuffleEverything(kitchen)
        runCurrent()
        assertThat(system.calls).containsExactly(
            "pause k", "play k", "next k", "setGroupVolume k 45", "setRoomVolume Kitchen-id 35", "shuffleEverything k",
        ).inOrder()
    }

    /** 6 §6: "Sonos didn’t do that. Try again." on that row, until its next command. */
    @Test
    fun aRefusedCommandSaysSoOnItsRowUntilTheNextCommand() = runTest {
        val actions = actions()
        system.failures["pause k"] = MusicRefusedException(701)
        actions.pause(kitchen)
        runCurrent()
        assertThat(actions.lines.value).containsExactly("k", "Sonos didn’t do that. Try again.")
        system.failures.clear()
        actions.play(kitchen)
        assertThat(actions.lines.value).isEmpty()
    }

    @Test
    fun anEmptyLibrarySaysSo() = runTest {
        val actions = actions()
        system.failures["shuffleEverything k"] = NoLibraryException()
        actions.shuffleEverything(kitchen)
        runCurrent()
        assertThat(actions.lines.value).containsExactly("k", "Sonos has no music library. Add it in the Sonos app.")
    }

    /** No speaker answering is the status line's to say, once, not every row's. */
    @Test
    fun anUnreachableSpeakerLeavesTheRowToTheStatusLine() = runTest {
        val actions = actions()
        system.failures["play k"] = MusicUnreachableException(MusicProblem.UNREACHABLE)
        actions.play(kitchen)
        runCurrent()
        assertThat(actions.lines.value).isEmpty()
    }

    @Test
    fun favouritesLoadOrSayWhy() = runTest {
        val actions = actions()
        system.favouriteList = listOf(station)
        assertThat(actions.favourites()).isEqualTo(FavouritesLoad.Ready(listOf(station)))
        system.failures["favourites"] = MusicUnreachableException(MusicProblem.OFFLINE)
        assertThat(actions.favourites()).isEqualTo(FavouritesLoad.Failed("The tablet is offline. Check the Wi-Fi."))
        system.failures["favourites"] = MusicRefusedException()
        assertThat(actions.favourites()).isEqualTo(FavouritesLoad.Failed("Sonos didn’t do that. Try again."))
    }

    /** 6 §6: "Sonos couldn’t play this here." and the sheet stays; null closes it. */
    @Test
    fun aFavouriteThatCanNotPlayHereSaysSo() = runTest {
        val actions = actions()
        assertThat(actions.playFavourite(kitchen, station)).isNull()
        system.failures["playFavourite k FV:2/1"] = MusicRefusedException(714)
        assertThat(actions.playFavourite(kitchen, station)).isEqualTo("Sonos couldn’t play this here.")
        system.failures["playFavourite k FV:2/1"] = MusicUnreachableException(MusicProblem.UNREACHABLE)
        assertThat(actions.playFavourite(kitchen, station)).isEqualTo(
            "Culvery can’t reach Sonos. Check the speakers are on and the tablet is on the home Wi-Fi.",
        )
    }

    /** 6 §6 privacy: the command and its outcome only. Fails if a room's name reaches the log. */
    @Test
    fun theLogNamesTheCommandOnly() = runTest {
        ShadowLog.clear()
        val actions = actions()
        system.failures["pause k"] = MusicRefusedException(701)
        actions.pause(kitchen)
        runCurrent()
        val logs = ShadowLog.getLogs().filter { it.tag == "MusicActions" }
        assertThat(logs.map { it.msg }).containsExactly("PAUSE failed (MusicRefusedException)")
    }

    @Test
    fun theProblemLinesAreTheSpecs() {
        assertThat(MusicProblem.entries.map(::problemLine)).containsExactly(
            "Culvery can’t reach Sonos. Check the speakers are on and the tablet is on the home Wi-Fi.",
            "The tablet is offline. Check the Wi-Fi.",
            "Culvery found a different Sonos system. Find Sonos again in Settings › Music.",
        ).inOrder()
    }
}
