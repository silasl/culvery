package uk.co.siland.culvery.provider.music_sonos

import androidx.test.ext.junit.runners.AndroidJUnit4
import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.withContext
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import uk.co.siland.culvery.capability.music.FavouriteKind
import uk.co.siland.culvery.capability.music.MusicLink
import uk.co.siland.culvery.capability.music.MusicProblem
import uk.co.siland.culvery.capability.music.MusicRefusedException
import uk.co.siland.culvery.capability.music.MusicUnreachableException
import uk.co.siland.culvery.capability.music.NoLibraryException
import uk.co.siland.culvery.capability.music.NowPlaying
import uk.co.siland.culvery.capability.music.PlayState
import uk.co.siland.culvery.capability.music.SpeakerRoom
import uk.co.siland.culvery.core.plugin.WallClock
import uk.co.siland.culvery.provider.music_sonos.SonosFixtures.DINING
import uk.co.siland.culvery.provider.music_sonos.SonosFixtures.HOUSEHOLD
import uk.co.siland.culvery.provider.music_sonos.SonosFixtures.KITCHEN
import uk.co.siland.culvery.provider.music_sonos.SonosFixtures.LOUNGE

/**
 * 6 §4, §6, §7: three speakers on localhost. Kitchen plays a library track on its own; the Lounge leads the Dining room,
 * paused on a station.
 */
@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(AndroidJUnit4::class)
class SonosSystemTest {
    private val kitchen = FakeSpeaker()
    private val lounge = FakeSpeaker()
    private val dining = FakeSpeaker()
    private val client = SonosClient(testHttp(), onThisMachine) { _, _ -> }
    private var now = 1_000_000L
    private var online = true
    private var heard = listOf<String>()
    private var scans = 0
    private val moved = mutableListOf<MusicLink>()
    private val home get() = MusicLink(HOUSEHOLD, kitchen.address)

    /** Nothing listens on port 1: the saved speaker is gone. */
    private val gone = MusicLink(HOUSEHOLD, "127.0.0.1:1")

    @Before
    fun setUp() {
        val topology = SonosFixtures.topology(kitchen.address, lounge.address, dining.address)
        listOf(kitchen, lounge, dining).forEach {
            it.answer("GetHouseholdID", SonosFixtures.household())
            it.answer("GetZoneGroupState", topology)
            it.acceptCommands()
        }
        kitchen.answer("GetVolume", SonosFixtures.volume(30))
        lounge.answer("GetVolume", SonosFixtures.volume(40))
        dining.answer("GetVolume", SonosFixtures.volume(35))
        kitchen.answer("GetTransportInfo", SonosFixtures.transport("PLAYING"))
        kitchen.answer("GetPositionInfo", SonosFixtures.position())
        kitchen.answer("GetMediaInfo", SonosFixtures.media(SonosFixtures.queue(KITCHEN)))
        kitchen.answer("GetGroupVolume", SonosFixtures.groupVolume(30))
        lounge.answer("GetTransportInfo", SonosFixtures.transport("PAUSED_PLAYBACK"))
        lounge.answer("GetPositionInfo", SonosFixtures.position(SonosFixtures.RADIO_URI, SonosFixtures.RADIO_TRACK))
        lounge.answer("GetMediaInfo", SonosFixtures.media(SonosFixtures.RADIO_URI, SonosFixtures.RADIO_STATION))
        lounge.answer("GetGroupVolume", SonosFixtures.groupVolume(38))
        kitchen.answer("Browse", SonosFixtures.library(total = 1_234))
    }

    @After
    fun tearDown() {
        listOf(kitchen, lounge, dining).forEach { it.close() }
    }

    private fun system(link: MusicLink = home) =
        SonosSystem(client, { scans++; heard }, { online }, link, { moved += it }, WallClock { now })

    private suspend fun checked() = system().apply {
        checkLayout()
        checkPlayback()
    }

    private fun unreachable(block: suspend () -> Unit): MusicProblem = throws<MusicUnreachableException>(block).problem

    @Test
    fun theLayoutGivesEachGroupItsRoomsAndTheirVolumes() = runTest {
        val s = system()
        s.checkLayout()
        assertThat(s.groups.value.map { it.name }).containsExactly("Kitchen", "Lounge + Dining room").inOrder()
        assertThat(s.groups.value[1].rooms).containsExactly(SpeakerRoom(LOUNGE, "Lounge", 40), SpeakerRoom(DINING, "Dining room", 35)).inOrder()
        assertThat(s.problem.value).isNull()
    }

    /** 6 §3.1, §4.2: what each leader plays; Next only from a queue; the speaker's art made whole; only leaders asked. */
    @Test
    fun aCheckReadsEachLeadersPlayback() = runTest {
        val (k, l) = checked().groups.value
        assertThat(k.state).isEqualTo(PlayState.PLAYING)
        assertThat(k.nowPlaying).isEqualTo(NowPlaying.Track("Song", "Artist", "Album"))
        assertThat(k.canSkip).isTrue()
        assertThat(k.volume).isEqualTo(30)
        assertThat(k.artUrl).startsWith("http://${kitchen.address}/getaa?s=1&u=")
        assertThat(k.startedAtMillis).isEqualTo(now)
        assertThat(l.state).isEqualTo(PlayState.PAUSED)
        assertThat(l.nowPlaying).isEqualTo(NowPlaying.Radio("Culvery FM", "The Breakfast Show"))
        assertThat(l.canSkip).isFalse()
        assertThat(l.artUrl).isEqualTo("https://art.example/p0abc123.jpg")
        assertThat(dining.actions()).doesNotContain("GetTransportInfo")
    }

    /** 6 §2.2: when a group last started playing. Fails if a group already playing gets a new time at each check. */
    @Test
    fun aGroupThatStartsPlayingRecordsWhen() = runTest {
        val s = checked()
        lounge.answer("GetTransportInfo", SonosFixtures.transport("PLAYING"))
        val started = now + 60_000
        now = started
        s.checkPlayback()
        now += 2_000
        s.checkPlayback()
        assertThat(s.groups.value.map { it.startedAtMillis }).containsExactly(1_000_000L, started).inOrder()
    }

    /**
     * 6 §4.2: GetMediaInfo is asked again only when the track or the transport state changes. Fails if each check asks
     * it, or if a new track keeps the old one's.
     */
    @Test
    fun mediaIsReadAgainOnlyWhenTheTrackChanges() = runTest {
        val s = checked()
        s.checkPlayback()
        assertThat(kitchen.actions().count { it == "GetMediaInfo" }).isEqualTo(1)
        kitchen.answer("GetPositionInfo", SonosFixtures.position(SonosFixtures.RADIO_URI, SonosFixtures.RADIO_TRACK))
        s.checkPlayback()
        assertThat(kitchen.actions().count { it == "GetMediaInfo" }).isEqualTo(2)
    }

    /** 6 §4.3: to the leader, and that group checked at once. Fails if the Dining room gets the command. */
    @Test
    fun aCommandGoesToTheLeaderAndTheGroupIsCheckedAtOnce() = runTest {
        val s = checked()
        lounge.received.clear()
        s.play(s.groups.value[1])
        assertThat(lounge.actions().first()).isEqualTo("Play")
        assertThat(lounge.actions()).contains("GetTransportInfo")
        assertThat(dining.actions()).doesNotContain("Play")
    }

    /** 6 §4.3: the screen changes at once; the check after the command puts back what the speaker says. */
    @Test
    fun theScreenChangesAtOnceAndTheCheckCorrectsIt() = runTest {
        val s = checked()
        kitchen.answerAfter("Pause", SonosFixtures.soap("Pause"), millis = 300)
        val pausing = launch { s.pause(s.groups.value[0]) }
        runCurrent()
        assertThat(s.groups.value[0].state).isEqualTo(PlayState.PAUSED)
        pausing.join()
        // The fake speaker still says PLAYING.
        assertThat(s.groups.value[0].state).isEqualTo(PlayState.PLAYING)
    }

    @Test
    fun volumesGoWhereTheyBelong() = runTest {
        val s = checked()
        s.setRoomVolume(SpeakerRoom(DINING, "Dining room", 35), 20)
        assertThat(dining.bodyOf("SetVolume")).contains("<DesiredVolume>20</DesiredVolume>")
        assertThat(lounge.actions()).doesNotContain("SetVolume")
        assertThat(s.groups.value[1].rooms[1].volume).isEqualTo(20)
        s.setGroupVolume(s.groups.value[1], 150)
        assertThat(lounge.bodyOf("SetGroupVolume")).contains("<DesiredVolume>100</DesiredVolume>")
        assertThat(s.roomVolume(SpeakerRoom(LOUNGE, "Lounge", 0))).isEqualTo(40)
    }

    /**
     * 6 §4.4: the library checked, then its five steps in order, on the leader, with its own queue; the queue before the
     * play mode, which Sonos refuses (UPnP 712) while a stream is the source. The URI, its empty metadata and the browse
     * of one come from SoCo and node-sonos, not Sonos's documentation: pinned until the walk-through tries them on the
     * household's speakers.
     */
    @Test
    fun shuffleEverythingRunsItsStepsInOrder() = runTest {
        val s = checked()
        kitchen.received.clear()
        s.shuffleEverything(s.groups.value[0])
        assertThat(kitchen.actions().take(6)).containsExactly(
            "Browse", "RemoveAllTracksFromQueue", "AddURIToQueue", "SetAVTransportURI", "SetPlayMode", "Play",
        ).inOrder()
        assertThat(kitchen.bodyOf("Browse")).contains("<ObjectID>A:TRACKS</ObjectID>")
        assertThat(kitchen.bodyOf("Browse")).contains("<RequestedCount>1</RequestedCount>")
        assertThat(kitchen.bodyOf("AddURIToQueue")).contains("<EnqueuedURI>x-rincon-playlist:$KITCHEN#A:TRACKS</EnqueuedURI><EnqueuedURIMetaData></EnqueuedURIMetaData>")
        assertThat(kitchen.bodyOf("SetPlayMode")).contains("<NewPlayMode>SHUFFLE_NOREPEAT</NewPlayMode>")
        assertThat(kitchen.bodyOf("SetAVTransportURI")).contains("<CurrentURI>x-rincon-queue:$KITCHEN#0</CurrentURI>")
    }

    /** 6 §4.4, §6: an empty library stops before step 1, so the queue is untouched. */
    @Test
    fun anEmptyLibraryStopsBeforeTheQueue() = runTest {
        val s = checked()
        kitchen.answer("Browse", SonosFixtures.library(total = 0))
        throws<NoLibraryException> { s.shuffleEverything(s.groups.value[0]) }
        assertThat(kitchen.actions()).doesNotContain("RemoveAllTracksFromQueue")
    }

    /** 6 §4.5: a station is set and played; a playlist replaces the queue without touching the play mode. */
    @Test
    fun favouritesPlayByTheirKind() = runTest {
        val s = checked()
        kitchen.answer("Browse", SonosFixtures.favourites)
        val favourites = s.favourites()
        assertThat(favourites.map { it.name to it.kind }).containsExactly(
            "Culvery FM" to FavouriteKind.STREAM, "Sunday & slow" to FavouriteKind.QUEUED, "One song" to FavouriteKind.STREAM,
        ).inOrder()
        assertThat(favourites[0].artUrl).isEqualTo("https://art.example/station.png")
        assertThat(favourites[1].artUrl).isEqualTo("http://${kitchen.address}/getaa?s=1&u=x-rincon-playlist")
        val k = s.groups.value[0]
        kitchen.received.clear()
        s.playFavourite(k, favourites[0])
        assertThat(kitchen.actions().take(2)).containsExactly("SetAVTransportURI", "Play").inOrder()
        assertThat(kitchen.bodyOf("SetAVTransportURI")).contains("<CurrentURI>x-sonosapi-hls:stations%7eBBC6%7eBBC6?sid=325&amp;flags=8224&amp;sn=1</CurrentURI>")
        assertThat(kitchen.bodyOf("SetAVTransportURI")).contains("&lt;dc:title&gt;Culvery FM&lt;/dc:title&gt;")
        kitchen.received.clear()
        s.playFavourite(k, favourites[1])
        assertThat(kitchen.actions().take(4)).containsExactly("RemoveAllTracksFromQueue", "AddURIToQueue", "SetAVTransportURI", "Play").inOrder()
        assertThat(kitchen.actions()).doesNotContain("SetPlayMode")
        assertThat(kitchen.bodyOf("AddURIToQueue")).contains("<EnqueuedURI>file:///jffs/settings/savedqueues.rsq#3</EnqueuedURI>")
    }

    /** 6 §6: a fault is "Sonos didn’t do that", and the group is checked again. */
    @Test
    fun aFaultIsRefusedAndTheGroupIsCheckedAgain() = runTest {
        val s = checked()
        kitchen.refuse("Pause", 701)
        kitchen.received.clear()
        assertThat(throws<MusicRefusedException> { s.pause(s.groups.value[0]) }.code).isEqualTo(701)
        assertThat(kitchen.actions()).contains("GetTransportInfo")
        assertThat(s.problem.value).isNull()
    }

    /** A group regrouped in the Sonos app since the screen drew it is refused, not "can't reach Sonos". */
    @Test
    fun aGroupGoneFromTheLayoutIsRefused() = runTest {
        val s = checked()
        // The next layout check redraws the rows.
        throws<MusicRefusedException> { s.pause(s.groups.value[0].copy(id = "RINCON_000E58EEEEEE01400:9")) }
        assertThat(s.problem.value).isNull()
        // Reading the layout again was enough: no scan.
        assertThat(scans).isEqualTo(0)
    }

    /**
     * 6 §4.2: the playback and layout checks run on their own timers and can overlap; together they still leave every group
     * with its rooms, volumes and playback. Pin: a lost update between them is a race one run rarely shows.
     */
    @Test
    fun overlappingChecksLeaveTheWholePicture() = runTest {
        val expected = checked().groups.value
        val s = system()
        withContext(Dispatchers.Default) {
            repeat(10) {
                launch { s.checkLayout() }
                launch { s.checkPlayback() }
            }
        }
        assertThat(s.groups.value).isEqualTo(expected)
        assertThat(s.problem.value).isNull()
    }

    /** 6 §4.2, §6 privacy: a speaker's own /getaa art, or https; never another http address or path. */
    @Test
    fun onlyASpeakersOwnArtOrHttpsArtIsFetched() = runTest {
        val s = checked()
        kitchen.answer("", "picture")
        assertThat(s.art("http://${kitchen.address}/getaa?s=1&u=x")).isEqualTo("picture".toByteArray())
        kitchen.received.clear()
        assertThat(s.art("http://${kitchen.address}/elsewhere.jpg")).isNull()
        assertThat(s.art("http://8.8.8.8/art.jpg")).isNull()
        assertThat(s.art("ftp://art.example/x.jpg")).isNull()
        assertThat(kitchen.received).isEmpty()
    }

    /** 6 §6 privacy: a saved address that isn't a home speaker is never asked; the scan finds the household instead. */
    @Test
    fun aSavedAddressOffTheHomeNetworkIsNeverContacted() = runTest {
        heard = listOf(lounge.address)
        val s = system(MusicLink(HOUSEHOLD, "8.8.8.8:1400"))
        s.checkLayout()
        assertThat(moved).containsExactly(MusicLink(HOUSEHOLD, lounge.address))
        assertThat(s.groups.value).hasSize(2)
    }

    /** 6 §4.1: the router moved the speakers; one scan finds the household, saves it, and nothing is reported. */
    @Test
    fun aMovedSpeakerIsFoundAndSavedWithoutAProblem() = runTest {
        heard = listOf(lounge.address)
        val s = system(gone)
        s.checkLayout()
        assertThat(s.groups.value).hasSize(2)
        assertThat(moved).containsExactly(MusicLink(HOUSEHOLD, lounge.address))
        assertThat(s.problem.value).isNull()
        assertThat(scans).isEqualTo(1)
    }

    /** 6 §4.1: the scan missed the moved speakers; the address typed in Settings › Music is where the next check goes. */
    @Test
    fun aTypedAddressIsWhereTheNextCheckGoes() = runTest {
        val s = system(gone)
        unreachable { s.checkLayout() }
        s.useAddress(lounge.address)
        s.checkLayout()
        assertThat(s.groups.value).hasSize(2)
        assertThat(s.problem.value).isNull()
        assertThat(lounge.actions()).contains("GetZoneGroupState")
    }

    /** A Next that reached the speaker but lost its answer may have skipped already; sent again it would skip two tracks. */
    @Test
    fun aNextWhoseAnswerIsLostIsNotSentAgain() = runTest {
        val s = checked()
        kitchen.dropNext("Next")
        unreachable { s.next(s.groups.value[0]) }
        assertThat(kitchen.actions().count { it == "Next" }).isEqualTo(1)
        assertThat(s.problem.value).isNull()
    }

    /** 6 §4.1: report only after one rescan. */
    @Test
    fun noSpeakerAfterTheRescanIsUnreachable() = runTest {
        val s = system(gone)
        assertThat(unreachable { s.checkLayout() }).isEqualTo(MusicProblem.UNREACHABLE)
        assertThat(s.problem.value).isEqualTo(MusicProblem.UNREACHABLE)
        assertThat(scans).isEqualTo(1)
    }

    /** Fails at 29 999 ms if the gap is shorter, at 30 000 if longer. */
    @Test
    fun theRescanWaitsThirtySecondsBeforeScanningAgain() = runTest {
        val s = system(gone)
        unreachable { s.checkLayout() }
        now += RESCAN_GAP_MS - 1
        unreachable { s.checkLayout() }
        assertThat(scans).isEqualTo(1)
        now += 1
        unreachable { s.checkLayout() }
        assertThat(scans).isEqualTo(2)
    }

    /** 6 §6: Android says there's no network, so it's the tablet; no scan. */
    @Test
    fun offlineIsSaidWithoutAScan() = runTest {
        online = false
        val s = system(gone)
        assertThat(unreachable { s.checkLayout() }).isEqualTo(MusicProblem.OFFLINE)
        assertThat(s.problem.value).isEqualTo(MusicProblem.OFFLINE)
        assertThat(scans).isEqualTo(0)
    }

    @Test
    fun theProblemClearsWhenTheSpeakersAnswerAgain() = runTest {
        val s = system()
        kitchen.silent = true
        unreachable { s.checkLayout() }
        kitchen.silent = false
        s.checkLayout()
        assertThat(s.problem.value).isNull()
    }

    /** 6 §6: a reset system has a new household id; its rooms aren't shown as the household's. */
    @Test
    fun aDifferentHouseholdIsSaidAndItsRoomsAreNotShown() = runTest {
        val s = checked()
        kitchen.answer("GetHouseholdID", SonosFixtures.household("Sonos_someoneElse"))
        s.checkLayout()
        assertThat(s.problem.value).isEqualTo(MusicProblem.OTHER_HOUSEHOLD)
        assertThat(s.groups.value).isEmpty()
        kitchen.answer("GetHouseholdID", SonosFixtures.household())
        s.checkLayout()
        assertThat(s.problem.value).isNull()
        assertThat(s.groups.value).hasSize(2)
    }

    /**
     * 6 §6: once a different household answers, a row still on screen sends nothing and doesn't bring that household's
     * rooms back; Favourites and a room's volume aren't read from it either.
     */
    @Test
    fun nothingIsSentToADifferentHousehold() = runTest {
        val s = checked()
        val stale = s.groups.value
        kitchen.answer("GetHouseholdID", SonosFixtures.household("Sonos_someoneElse"))
        s.checkLayout()
        listOf(kitchen, lounge, dining).forEach { it.received.clear() }
        assertThat(unreachable { s.pause(stale[1]) }).isEqualTo(MusicProblem.OTHER_HOUSEHOLD)
        assertThat(unreachable { s.favourites() }).isEqualTo(MusicProblem.OTHER_HOUSEHOLD)
        assertThat(unreachable { s.roomVolume(stale[1].rooms[0]) }).isEqualTo(MusicProblem.OTHER_HOUSEHOLD)
        assertThat(s.groups.value).isEmpty()
        assertThat(listOf(kitchen, lounge, dining).flatMap { it.actions() }).isEmpty()
    }

    /**
     * 6 §6: a scan that heard only another household says so until the saved one answers again. Pin: the layout check
     * already clears it; this keeps that true once the household is checked wherever the layout is read.
     */
    @Test
    fun theSavedHouseholdAnsweringAgainClearsADifferentHousehold() = runTest {
        lounge.answer("GetHouseholdID", SonosFixtures.household("Sonos_someoneElse"))
        heard = listOf(lounge.address)
        val link = MusicLink(HOUSEHOLD, kitchen.address)
        kitchen.silent = true
        val s = system(link)
        assertThat(unreachable { s.checkLayout() }).isEqualTo(MusicProblem.OTHER_HOUSEHOLD)
        kitchen.silent = false
        s.checkLayout()
        assertThat(s.problem.value).isNull()
        assertThat(s.groups.value).hasSize(2)
    }

    /** 6 §4.2: one leader's fault skips that group; the others are still read and the check throws nothing. */
    @Test
    fun oneLeadersFaultLeavesTheOtherGroupsRead() = runTest {
        val s = system()
        s.checkLayout()
        lounge.refuse("GetTransportInfo", 701)
        s.checkPlayback()
        assertThat(s.groups.value.map { it.state }).containsExactly(PlayState.PLAYING, PlayState.STOPPED).inOrder()
        assertThat(s.problem.value).isNull()
    }

    /** The contract's exceptions only: an answer that isn't Sonos's is a refusal. */
    @Test
    fun anAnswerThatIsNotSonossIsRefused() = runTest {
        val s = checked()
        dining.answer("GetVolume", "<html>router</html>")
        assertThat(throws<MusicRefusedException> { s.roomVolume(SpeakerRoom(DINING, "Dining room", 35)) }.code).isNull()
    }

    @Test
    fun aScanThatHearsOnlyAnotherHouseholdSaysSo() = runTest {
        lounge.answer("GetHouseholdID", SonosFixtures.household("Sonos_someoneElse"))
        heard = listOf(lounge.address)
        assertThat(unreachable { system(gone).checkLayout() }).isEqualTo(MusicProblem.OTHER_HOUSEHOLD)
        assertThat(moved).isEmpty()
    }

    /**
     * 6 §6: when the saved speaker goes silent and the scan hears only another household, the old rooms go too: their
     * addresses may now be that household's, so neither a check nor art reaches them.
     */
    @Test
    fun aScanThatHearsAnotherHouseholdForgetsTheOldRooms() = runTest {
        val s = checked()
        val art = s.groups.value[0].artUrl!!
        kitchen.silent = true
        lounge.answer("GetHouseholdID", SonosFixtures.household("Sonos_someoneElse"))
        heard = listOf(lounge.address)
        assertThat(unreachable { s.checkLayout() }).isEqualTo(MusicProblem.OTHER_HOUSEHOLD)
        assertThat(s.groups.value).isEmpty()
        listOf(kitchen, lounge, dining).forEach { it.received.clear() }
        assertThat(s.art(art)).isNull()
        assertThat(unreachable { s.checkPlayback() }).isEqualTo(MusicProblem.OTHER_HOUSEHOLD)
        assertThat(listOf(lounge, dining).flatMap { it.actions() }).isEmpty()
        assertThat(kitchen.received.map { it.path }.filter { it.startsWith("/getaa") }).isEmpty()
    }

    /** 6 §4.6: the bell asks each leader what plays now, not the last check. */
    @Test
    fun playingNowAsksEachLeaderAfresh() = runTest {
        val s = system()
        s.checkLayout()
        assertThat(s.groups.value.map { it.state }).containsExactly(PlayState.STOPPED, PlayState.STOPPED)
        val playing = s.playingNow()
        assertThat(playing.map { it.id }).containsExactly("$KITCHEN:12")
        assertThat(kitchen.actions()).contains("GetTransportInfo")
    }
}
