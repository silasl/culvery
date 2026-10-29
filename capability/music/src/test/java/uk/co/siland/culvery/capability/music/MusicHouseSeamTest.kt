package uk.co.siland.culvery.capability.music

import androidx.test.ext.junit.runners.AndroidJUnit4
import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.shadows.ShadowLog
import uk.co.siland.culvery.core.house.GroupBefore
import uk.co.siland.culvery.core.house.HouseLines
import uk.co.siland.culvery.core.house.HouseStep
import uk.co.siland.culvery.core.house.MusicStart
import uk.co.siland.culvery.core.house.PartyGroup

/** 13 §2.4, §2.10, §2.11, §2.13, §4.1, §4.2: Party's music, and leaving a group the family changed. */
@RunWith(AndroidJUnit4::class)
class MusicHouseSeamTest {
    private val apart = listOf(group("Kitchen-id", "Kitchen"), group("Lounge-id", "Lounge"), group("Dining-id", "Dining"))
    private val system = TestMusicSystem(apart)
    private val seam = MusicHouseSeam({ system }, flowOf(true))
    private val startLine = HouseStep.Failed(HouseLines.MUSIC_DIDNT_START)
    private val stopLine = HouseStep.Failed(HouseLines.MUSIC_DIDNT_STOP)
    private val all = setOf("Kitchen-id", "Lounge-id", "Dining-id")
    private val party = PartyGroup("Kitchen-id", all)
    private val partyMix = Favourite("FV:2/3", "Party mix", null, FavouriteKind.QUEUED)
    private val regroupLine = "regroup Kitchen-id:Kitchen-id,Lounge-id:Lounge-id,Dining-id:Dining-id"
    private val groupLine = "group Kitchen-id Dining-id,Kitchen-id,Lounge-id"

    init {
        system.favouriteList = listOf(Favourite("FV:2/2", "Sunday morning", null, FavouriteKind.STREAM), partyMix)
    }

    private fun partyPlaying(track: NowPlaying = SONG) =
        group("Kitchen-id", "Kitchen", "Lounge", "Dining", state = PlayState.PLAYING, nowPlaying = track)

    /** The layout shows Party's group playing; [answer] is what the speakers say is playing when asked afresh. */
    private fun partyIsUp(answer: List<SpeakerGroup> = listOf(partyPlaying())) {
        system.groups.value = listOf(partyPlaying())
        system.playingNowAnswer = answer
        system.calls.clear()
    }

    private fun groupingAndPlaying() = system.calls.filter { it.startsWith("group") || it.startsWith("playFavourite") }

    @Test
    fun beforeReadsTheLayoutAfresh() = runTest {
        system.groups.value = listOf(group("Lounge-id", "Lounge", "Dining"), group("Kitchen-id", "Kitchen"))
        assertThat(seam.before()).containsExactly(
            GroupBefore("Lounge-id", listOf("Lounge-id", "Dining-id")),
            GroupBefore("Kitchen-id", listOf("Kitchen-id")),
        ).inOrder()
        assertThat(system.calls.first()).isEqualTo("checkLayout")
    }

    @Test
    fun startGroupsTheChosenRoomsAndPlays() = runTest {
        val result = seam.start("FV:2/3", null, seam.before())
        assertThat(system.calls).containsExactly("checkLayout", "checkLayout", "favourites", "playlists", groupLine, "playFavourite Kitchen-id FV:2/3").inOrder()
        assertThat(result).isEqualTo(MusicStart(party, HouseStep.Done))
    }

    @Test
    fun aPlaylistIsFoundToo() = runTest {
        system.favouriteList = emptyList()
        system.playlistList = listOf(partyMix)
        val result = seam.start("FV:2/3", null, seam.before())
        assertThat(system.calls).contains("playFavourite Kitchen-id FV:2/3")
        assertThat(result.step).isEqualTo(HouseStep.Done)
    }

    @Test
    fun onlyChosenRoomsJoin() = runTest {
        val result = seam.start("FV:2/3", setOf("Kitchen-id", "Lounge-id", "Gone-id"), seam.before())
        assertThat(system.calls).contains("group Kitchen-id Kitchen-id,Lounge-id")
        assertThat(result.made).isEqualTo(PartyGroup("Kitchen-id", setOf("Kitchen-id", "Lounge-id")))
    }

    @Test
    fun noChosenRoomLeftFailsTheMusic() = runTest {
        assertThat(seam.start("FV:2/3", setOf("Gone-id"), seam.before())).isEqualTo(MusicStart(null, startLine))
        assertThat(groupingAndPlaying()).isEmpty()
    }

    /** An empty saved choice is no speaker chosen: the music fails with its line rather than naming no rooms. */
    @Test
    fun anEmptyRoomChoiceFailsTheMusic() = runTest {
        assertThat(seam.start("FV:2/3", emptySet(), seam.before())).isEqualTo(MusicStart(null, startLine))
        assertThat(groupingAndPlaying()).isEmpty()
    }

    @Test
    fun aPickNoLongerThereFails() = runTest {
        assertThat(seam.start("FV:9/9", null, seam.before())).isEqualTo(MusicStart(null, startLine))
        assertThat(groupingAndPlaying()).isEmpty()
    }

    @Test
    fun aRoomThatWontJoinStillPlaysTheRestAndSaysSo() = runTest {
        system.groupLeavesOut = setOf("Dining-id")
        val result = seam.start("FV:2/3", null, seam.before())
        assertThat(system.calls).contains("playFavourite Kitchen-id FV:2/3")
        assertThat(result.made?.roomIds).containsExactly("Kitchen-id", "Lounge-id")
        assertThat(result.step).isEqualTo(startLine)
    }

    @Test
    fun aPlayThatFailsPutsTheGroupsBack() = runTest {
        val before = seam.before()
        system.failures["playFavourite Kitchen-id FV:2/3"] = MusicRefusedException()
        assertThat(seam.start("FV:2/3", null, before)).isEqualTo(MusicStart(null, startLine))
        assertThat(system.calls.last()).isEqualTo(regroupLine)
        assertThat(system.groups.value.map { it.rooms.size }).containsExactly(1, 1, 1)
    }

    /** Some joins may have happened before the throw, so the groups go back. */
    @Test
    fun aGroupingThatThrowsPutsTheGroupsBack() = runTest {
        val before = seam.before()
        system.failures[groupLine] = MusicUnreachableException(MusicProblem.UNREACHABLE)
        assertThat(seam.start("FV:2/3", null, before)).isEqualTo(MusicStart(null, startLine))
        assertThat(system.calls.last()).isEqualTo(regroupLine)
        assertThat(system.calls.none { it.startsWith("playFavourite") }).isTrue()
    }

    @Test
    fun theSpeakersDownFailTheStartWithoutThrowing() = runTest {
        val before = seam.before()
        system.failures["checkLayout"] = MusicUnreachableException(MusicProblem.UNREACHABLE)
        assertThat(seam.start("FV:2/3", null, before)).isEqualTo(MusicStart(null, startLine))
    }

    @Test
    fun stopPausesAndPutsTheGroupsBack() = runTest {
        val before = seam.before()
        partyIsUp()
        assertThat(seam.stop(party, before)).isEqualTo(HouseStep.Done)
        assertThat(system.calls).containsExactly("checkLayout", "playingNow", "pause Kitchen-id", regroupLine).inOrder()
    }

    /** 13 §2.11: a new song in the same group is still Party's. */
    @Test
    fun aNewSongStillCountsAsParty() = runTest {
        val before = seam.before()
        partyIsUp(listOf(partyPlaying(NowPlaying.Track("Another", "Someone", null))))
        assertThat(seam.stop(party, before)).isEqualTo(HouseStep.Done)
        assertThat(system.calls).containsAtLeast("pause Kitchen-id", regroupLine)
    }

    @Test
    fun aRegroupedPartyIsLeft() = runTest {
        val before = seam.before()
        partyIsUp(listOf(group("Kitchen-id", "Kitchen", "Lounge", state = PlayState.PLAYING, nowPlaying = SONG)))
        assertThat(seam.stop(party, before)).isEqualTo(HouseStep.Done)
        assertThat(system.calls).containsExactly("checkLayout", "playingNow").inOrder()
    }

    @Test
    fun aPausedPartyIsLeft() = runTest {
        val before = seam.before()
        partyIsUp(emptyList())
        assertThat(seam.stop(party, before)).isEqualTo(HouseStep.Done)
        assertThat(system.calls).containsExactly("checkLayout", "playingNow").inOrder()
    }

    /** The cached groups still show the party playing; only the fresh read counts. */
    @Test
    fun stillPartyReadsAfresh() = runTest {
        val before = seam.before()
        partyIsUp(emptyList())
        assertThat(seam.stop(party, before)).isEqualTo(HouseStep.Done)
        assertThat(system.calls.first()).isEqualTo("checkLayout")
        assertThat(system.calls.indexOf("checkLayout")).isLessThan(system.calls.indexOf("playingNow"))
        assertThat(system.calls.filter { it.startsWith("pause") || it.startsWith("regroup") }).isEmpty()
    }

    @Test
    fun stopWithTheSpeakersDownSaysSo() = runTest {
        val before = seam.before()
        partyIsUp()
        system.failures["playingNow"] = MusicUnreachableException(MusicProblem.UNREACHABLE)
        assertThat(seam.stop(party, before)).isEqualTo(stopLine)
        system.failures.clear()
        system.failures["checkLayout"] = MusicUnreachableException(MusicProblem.UNREACHABLE)
        assertThat(seam.stop(party, before)).isEqualTo(stopLine)
    }

    @Test
    fun aPauseThatFailsLeavesTheGroups() = runTest {
        val before = seam.before()
        partyIsUp()
        system.failures["pause Kitchen-id"] = MusicRefusedException()
        assertThat(seam.stop(party, before)).isEqualTo(stopLine)
        assertThat(system.calls.none { it.startsWith("regroup") }).isTrue()
    }

    @Test
    fun aRegroupThatFailsSaysSo() = runTest {
        val before = seam.before()
        partyIsUp()
        system.failures[regroupLine] = MusicRefusedException()
        assertThat(seam.stop(party, before)).isEqualTo(HouseStep.Failed(HouseLines.SPEAKERS_NOT_BACK))
    }

    /** A restart that finds the snapshot but no group Party made: the groups go back and nothing is paused. */
    @Test
    fun putBackRegroupsToTheSnapshotWithoutPausing() = runTest {
        val before = seam.before()
        partyIsUp()
        assertThat(seam.putBack(before)).isEqualTo(HouseStep.Done)
        assertThat(system.calls).containsExactly(regroupLine)
        assertThat(system.groups.value.map { it.rooms.size }).containsExactly(1, 1, 1)
    }

    @Test
    fun aPutBackThatFailsSaysSo() = runTest {
        val before = seam.before()
        system.failures[regroupLine] = MusicRefusedException()
        val line = HouseStep.Failed(HouseLines.SPEAKERS_NOT_BACK)
        assertThat(seam.putBack(before)).isEqualTo(line)
        assertThat(MusicHouseSeam({ null }, flowOf(false)).putBack(before)).isEqualTo(line)
    }

    @Test
    fun optionsListFavouritesThenPlaylistsAndTheRooms() = runTest {
        system.playlistList = listOf(Favourite("SQ:1", "Roadtrip", null, FavouriteKind.QUEUED))
        val options = seam.options()
        assertThat(options.picks.map { it.name }).containsExactly("Sunday morning", "Party mix", "Roadtrip").inOrder()
        assertThat(options.picks.map { it.id }).containsExactly("FV:2/2", "FV:2/3", "SQ:1").inOrder()
        assertThat(options.rooms.map { it.name }).containsExactly("Kitchen", "Lounge", "Dining").inOrder()
        assertThat(options.rooms.map { it.id }).containsExactly("Kitchen-id", "Lounge-id", "Dining-id").inOrder()
        assertThat(options.line).isNull()
    }

    @Test
    fun optionsWithSonosDownGiveTheLine() = runTest {
        system.failures["checkLayout"] = MusicUnreachableException(MusicProblem.UNREACHABLE)
        assertThat(seam.options().line).isEqualTo(CANT_REACH_SONOS)
        system.failures.clear()
        system.failures["favourites"] = MusicRefusedException()
        val refused = seam.options()
        assertThat(refused.line).isEqualTo(SONOS_REFUSED)
        assertThat(refused.picks).isEmpty()
    }

    @Test
    fun noSystem() = runTest {
        val none = MusicHouseSeam({ null }, flowOf(false))
        assertThat(none.ready.first()).isFalse()
        assertThat(none.start("FV:2/3", null, emptyList())).isEqualTo(MusicStart(null, startLine))
        assertThat(none.stop(party, emptyList())).isEqualTo(stopLine)
        assertThat(none.options().line).isEqualTo(CANT_REACH_SONOS)
    }

    @Test
    fun nothingPersonalReachesTheLog() = runTest {
        ShadowLog.clear()
        val before = seam.before()
        system.failures["playFavourite Kitchen-id FV:2/3"] = MusicRefusedException()
        seam.start("FV:2/3", null, before)
        system.failures.clear()
        partyIsUp(listOf(group("Kitchen-id", "Kitchen", "Lounge", state = PlayState.PLAYING, nowPlaying = SONG)))
        seam.stop(party, before)
        system.failures["playingNow"] = MusicRefusedException()
        seam.stop(party, before)
        val lines = ShadowLog.getLogs().filter { it.tag == "MusicHouseSeam" }.map { it.msg }
        assertThat(lines).isNotEmpty()
        lines.forEach { line -> listOf("Kitchen", "-id", "Party mix", "FV:").forEach { assertThat(line).doesNotContain(it) } }
    }
}
