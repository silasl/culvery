package uk.co.siland.culvery.provider.music_sonos

import androidx.test.ext.junit.runners.AndroidJUnit4
import com.google.common.truth.Truth.assertThat
import java.io.IOException
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Test
import org.junit.runner.RunWith
import uk.co.siland.culvery.capability.music.PlayState
import uk.co.siland.culvery.core.net.BodyTooLarge
import uk.co.siland.culvery.provider.music_sonos.SonosFixtures.KITCHEN

private val AV = SonosService.AV_TRANSPORT.urn

/** 6 §3.2, §7: each command's path, SOAPACTION and body; faults; the 3-second timeouts; the capture. */
@RunWith(AndroidJUnit4::class)
class SonosClientTest {
    private val speaker = FakeSpeaker()
    private val captured = mutableListOf<Pair<String, String>>()
    private val http = testHttp()
    private val client = SonosClient(http, onThisMachine) { action, answer -> captured += action to answer }

    @After
    fun tearDown() = speaker.close()

    private fun sent(action: String): FakeSpeaker.Received = speaker.received.last { it.action == action }

    /** Fails if a command goes to the wrong service, names itself wrongly, or loses an argument or its order. */
    @Test
    fun eachCommandSendsItsPathActionAndArguments() = runTest {
        speaker.acceptCommands()
        val a = speaker.address
        client.play(a)
        client.pause(a)
        client.next(a)
        client.removeAllTracks(a)
        client.addToQueue(a, "x-rincon-playlist:RINCON_1#A:TRACKS", "")
        client.setPlayMode(a, SHUFFLE_NOREPEAT)
        client.setTransportUri(a, queueUri("RINCON_1"), "")
        client.setVolume(a, 35)
        val expected = mapOf(
            "Play" to "<u:Play xmlns:u=\"$AV\"><InstanceID>0</InstanceID><Speed>1</Speed></u:Play>",
            "Pause" to "<u:Pause xmlns:u=\"$AV\"><InstanceID>0</InstanceID></u:Pause>",
            "Next" to "<u:Next xmlns:u=\"$AV\"><InstanceID>0</InstanceID></u:Next>",
            "RemoveAllTracksFromQueue" to "<u:RemoveAllTracksFromQueue xmlns:u=\"$AV\"><InstanceID>0</InstanceID></u:RemoveAllTracksFromQueue>",
            "AddURIToQueue" to "<u:AddURIToQueue xmlns:u=\"$AV\"><InstanceID>0</InstanceID><EnqueuedURI>x-rincon-playlist:RINCON_1#A:TRACKS</EnqueuedURI>" +
                "<EnqueuedURIMetaData></EnqueuedURIMetaData><DesiredFirstTrackNumberEnqueued>0</DesiredFirstTrackNumberEnqueued><EnqueueAsNext>0</EnqueueAsNext></u:AddURIToQueue>",
            "SetPlayMode" to "<u:SetPlayMode xmlns:u=\"$AV\"><InstanceID>0</InstanceID><NewPlayMode>SHUFFLE_NOREPEAT</NewPlayMode></u:SetPlayMode>",
            "SetAVTransportURI" to "<u:SetAVTransportURI xmlns:u=\"$AV\"><InstanceID>0</InstanceID><CurrentURI>x-rincon-queue:RINCON_1#0</CurrentURI><CurrentURIMetaData></CurrentURIMetaData></u:SetAVTransportURI>",
            "SetVolume" to "<u:SetVolume xmlns:u=\"urn:schemas-upnp-org:service:RenderingControl:1\"><InstanceID>0</InstanceID><Channel>Master</Channel><DesiredVolume>35</DesiredVolume></u:SetVolume>",
        )
        expected.forEach { (action, body) ->
            assertThat(sent(action).body).contains(body)
            assertThat(sent(action).body).startsWith("<?xml version=\"1.0\" encoding=\"utf-8\"?><s:Envelope")
        }
        assertThat(sent("Play").path).isEqualTo("/MediaRenderer/AVTransport/Control")
        assertThat(sent("Play").soapAction).isEqualTo("\"$AV#Play\"")
        assertThat(sent("SetVolume").path).isEqualTo("/MediaRenderer/RenderingControl/Control")
    }

    /**
     * Sonos asks for a snapshot first, so the rooms keep their ratio. Pins a request shape taken from SoCo and node-sonos,
     * not Sonos's documentation, until it is tried on real speakers.
     */
    @Test
    fun setGroupVolumeSnapshotsTheGroupFirst() = runTest {
        speaker.acceptCommands()
        client.setGroupVolume(speaker.address, 12)
        assertThat(speaker.actions()).containsExactly("SnapshotGroupVolume", "SetGroupVolume").inOrder()
        assertThat(sent("SetGroupVolume").path).isEqualTo("/MediaRenderer/GroupRenderingControl/Control")
        assertThat(sent("SetGroupVolume").body).contains("<DesiredVolume>12</DesiredVolume>")
    }

    @Test
    fun readsGoToTheirServicesAndAreParsed() = runTest {
        val a = speaker.address
        speaker.answer("GetTransportInfo", SonosFixtures.transport("PAUSED_PLAYBACK"))
        speaker.answer("GetGroupVolume", SonosFixtures.groupVolume(44))
        speaker.answer("GetVolume", SonosFixtures.volume(21))
        speaker.answer("GetHouseholdID", SonosFixtures.household())
        speaker.answer("GetZoneGroupState", SonosFixtures.topology(a, a, a))
        speaker.answer("Browse", SonosFixtures.library(total = 5))
        speaker.answer("GetZoneGroupAttributes", SonosFixtures.groupAttributes("$KITCHEN:12", KITCHEN))
        assertThat(client.transportInfo(a)).isEqualTo(PlayState.PAUSED)
        assertThat(client.groupVolume(a)).isEqualTo(44)
        assertThat(client.volume(a)).isEqualTo(21)
        assertThat(client.householdId(a)).isEqualTo(SonosFixtures.HOUSEHOLD)
        assertThat(client.zoneGroups(a)).hasSize(2)
        assertThat(client.browse(a, LIBRARY, 0, 1).total).isEqualTo(5)
        assertThat(client.groupAttributes(a)).isEqualTo(GroupAttributes("$KITCHEN:12", setOf(KITCHEN)))
        assertThat(sent("GetZoneGroupAttributes").path).isEqualTo("/ZoneGroupTopology/Control")
        assertThat(sent("GetHouseholdID").path).isEqualTo("/DeviceProperties/Control")
        assertThat(sent("GetZoneGroupState").path).isEqualTo("/ZoneGroupTopology/Control")
        assertThat(sent("Browse").path).isEqualTo("/MediaServer/ContentDirectory/Control")
        assertThat(sent("Browse").body).contains(
            "<ObjectID>A:TRACKS</ObjectID><BrowseFlag>BrowseDirectChildren</BrowseFlag><Filter>*</Filter><StartingIndex>0</StartingIndex>" +
                "<RequestedCount>1</RequestedCount><SortCriteria></SortCriteria>",
        )
    }

    /** Metadata goes inside an element, so its markup and ampersands are escaped once. */
    @Test
    fun argumentsAreEscaped() = runTest {
        speaker.acceptCommands()
        client.setTransportUri(speaker.address, "x-sonosapi-hls:a?b=1&c=2", "<DIDL-Lite><dc:title>Tom & Jo’s</dc:title></DIDL-Lite>")
        assertThat(sent("SetAVTransportURI").body).contains(
            "<CurrentURI>x-sonosapi-hls:a?b=1&amp;c=2</CurrentURI><CurrentURIMetaData>&lt;DIDL-Lite&gt;&lt;dc:title&gt;Tom &amp; Jo’s&lt;/dc:title&gt;&lt;/DIDL-Lite&gt;</CurrentURIMetaData>",
        )
    }

    @Test
    fun aFaultIsASonosFaultWithItsCode() = runTest {
        speaker.refuse("Pause", 701)
        assertThat(throws<SonosFault> { client.pause(speaker.address) }.code).isEqualTo(701)
    }

    /** 6 §6: an HTTP error without a UPnP fault is still a refusal, with no code. Pin: it already is. */
    @Test
    fun anErrorWithoutAFaultIsAFaultWithNoCode() = runTest {
        speaker.refuseWithoutFault("Pause", 503)
        assertThat(throws<SonosFault> { client.pause(speaker.address) }.code).isNull()
    }

    /** Past 4 MB isn't a Sonos answer and past 2 MB isn't art: each is a failed call. Pin: the caps are already kept. */
    @Test
    fun anAnswerOrArtOverItsCapFails() = runTest {
        speaker.answer("GetHouseholdID", "x".repeat(MAX_ANSWER_BYTES.toInt() + 1))
        // An IOException, so it leads to a rescan.
        throws<BodyTooLarge> { client.householdId(speaker.address) }
        val art = "http://${speaker.address}/getaa?s=1"
        speaker.answer("", "x".repeat(MAX_ART_BYTES.toInt()))
        assertThat(client.bytes(art)).hasLength(MAX_ART_BYTES.toInt())
        speaker.answer("", "x".repeat(MAX_ART_BYTES.toInt() + 1))
        assertThat(client.bytes(art)).isNull()
    }

    /** The answer is decoded, captured and checked for a fault off the caller's thread, which may be the main one. */
    @Test
    fun theAnswerIsReadOffTheCallersThread() = runTest {
        val threads = mutableListOf<String>()
        val watched = SonosClient(http, onThisMachine) { _, _ -> threads += Thread.currentThread().name }
        speaker.refuse("Pause", 701)
        throws<SonosFault> { watched.pause(speaker.address) }
        assertThat(threads.single()).startsWith("DefaultDispatcher-worker")
    }

    /** Fails if a silent speaker hangs a check instead of failing as I/O, which starts the rescan (6 §6). */
    @Test
    fun aSilentSpeakerFailsAsIo() = runTest {
        speaker.silent = true
        // The rescan's trigger.
        throws<IOException> { client.transportInfo(speaker.address) }
    }

    /** 6 §6: "Checks or commands time out (3 s)". The client refuses redirects itself (aRedirectIsNotFollowed). */
    @Test
    fun theSpeakersClientGivesUpAfterThreeSeconds() {
        val sonos = sonosHttp()
        assertThat(listOf(sonos.connectTimeoutMillis, sonos.readTimeoutMillis, sonos.callTimeoutMillis)).containsExactly(3_000, 3_000, 3_000)
        assertThat(sonos.retryOnConnectionFailure).isFalse()
    }

    /** Fails if a call reuses a connection the speaker has closed: with no retry (a command is never sent twice), it would fail as unreachable. */
    @Test
    fun eachCallOpensAFreshConnection() = runTest {
        val sonos = SonosClient(sonosHttp(), onThisMachine) { _, _ -> }
        speaker.answerThenHangUp("GetHouseholdID", SonosFixtures.household())
        repeat(2) { assertThat(sonos.householdId(speaker.address)).isEqualTo(SonosFixtures.HOUSEHOLD) }
    }

    /** A busy network drops the odd connection: a read whose connection drops is asked once more, so a check doesn't fail as unreachable. */
    @Test
    fun aReadWhoseConnectionDropsIsAskedOnceMore() = runTest {
        val a = speaker.address
        speaker.answer("GetTransportInfo", SonosFixtures.transport("PLAYING"))
        speaker.answer("Browse", SonosFixtures.library(total = 5))
        speaker.answer("", "picture")
        speaker.dropNext("GetTransportInfo")
        speaker.dropNext("Browse")
        speaker.dropNext("")
        assertThat(client.transportInfo(a)).isEqualTo(PlayState.PLAYING)
        assertThat(client.browse(a, LIBRARY, 0, 1).total).isEqualTo(5)
        assertThat(client.bytes("http://$a/getaa?s=1")).isEqualTo("picture".toByteArray())
        assertThat(speaker.actions()).containsExactly("GetTransportInfo", "GetTransportInfo", "Browse", "Browse", "", "").inOrder()
    }

    /** A command is never sent twice, even when its connection drops before the answer: it may already have taken effect. */
    @Test
    fun aCommandWhoseConnectionDropsIsNotSentAgain() = runTest {
        speaker.acceptCommands()
        speaker.dropNext("Next")
        throws<IOException> { client.next(speaker.address) }
        assertThat(speaker.actions()).containsExactly("Next")
    }

    /** A read that times out isn't asked again, so a dead speaker still fails within the 3 s. */
    @Test
    fun aReadThatTimesOutIsNotAskedAgain() = runTest {
        speaker.silent = true
        throws<IOException> { client.transportInfo(speaker.address) }
        assertThat(speaker.actions()).containsExactly("GetTransportInfo")
    }

    /** 6 §6 privacy: an address off the home network, or on the wrong port, is refused before anything is sent. */
    @Test
    fun anAddressOffTheHomeNetworkIsNeverContacted() = runTest {
        val strict = SonosClient(http) { _, _ -> }
        listOf("8.8.8.8:1400", "192.168.1.20:80", speaker.address).forEach { address -> throws<NotASpeaker> { strict.householdId(address) } }
        assertThat(speaker.received).isEmpty()
        assertThat(strict.householdAt("8.8.8.8:1400")).isNull()
    }

    /** Art that redirects elsewhere is no art, even from a client built to follow redirects. */
    @Test
    fun aRedirectIsNotFollowed() = runTest {
        val elsewhere = FakeSpeaker()
        try {
            elsewhere.answer("", "picture")
            speaker.redirect("", "http://${elsewhere.address}/art.jpg")
            assertThat(http.followRedirects).isTrue()
            assertThat(client.bytes("http://${speaker.address}/getaa?s=1")).isNull()
            assertThat(elsewhere.received).isEmpty()
        } finally {
            elsewhere.close()
        }
    }

    @Test
    fun householdAtAnswersNullForAnythingButASpeaker() = runTest {
        assertThat(client.householdAt(speaker.address)).isNull()
        speaker.answer("GetHouseholdID", SonosFixtures.household())
        assertThat(client.householdAt(speaker.address)).isEqualTo(SonosFixtures.HOUSEHOLD)
        speaker.answer("GetHouseholdID", "<html>router</html>")
        assertThat(client.householdAt(speaker.address)).isNull()
        speaker.silent = true
        assertThat(client.householdAt(speaker.address)).isNull()
    }

    @Test
    fun artComesAsBytesOrNothing() = runTest {
        assertThat(client.bytes("http://${speaker.address}/getaa?s=1")).isNull()
        assertThat(client.bytes("ftp://example/art")).isNull()
        speaker.answer("", "picture")
        assertThat(client.bytes("http://${speaker.address}/getaa?s=1")).isEqualTo("picture".toByteArray())
    }

    /** 6 §8.2: the capture sees each answer with its action. */
    @Test
    fun theCaptureSeesEachAnswer() = runTest {
        speaker.answer("GetTransportInfo", SonosFixtures.transport("PLAYING"))
        client.transportInfo(speaker.address)
        assertThat(captured.map { it.first }).containsExactly("GetTransportInfo")
        assertThat(captured.single().second).contains("PLAYING")
    }
}
