package uk.co.siland.culvery.provider.music_sonos

import com.google.common.truth.Truth.assertThat
import org.junit.Assert.assertThrows
import org.junit.Test
import uk.co.siland.culvery.capability.music.PlayState
import uk.co.siland.culvery.provider.music_sonos.SonosFixtures.DINING
import uk.co.siland.culvery.provider.music_sonos.SonosFixtures.KITCHEN
import uk.co.siland.culvery.provider.music_sonos.SonosFixtures.LOUNGE

/** 6 §7: Sonos's answers read on the plain JVM, from the documented fixtures. */
class SonosXmlTest {
    private val topology = SonosFixtures.topology("192.168.1.20:1400", "192.168.1.21:1400", "192.168.1.22:1400")

    /** Fails if the hidden sub shows as a room, the leader isn't first, or an address keeps its path. */
    @Test
    fun theTopologyGivesEachGroupItsVisibleRoomsLeaderFirst() {
        assertThat(SonosXml.zoneGroups(topology)).containsExactly(
            ZoneGroupInfo("$KITCHEN:12", KITCHEN, listOf(ZoneMember(KITCHEN, "Kitchen", "192.168.1.20:1400"))),
            ZoneGroupInfo(
                "$LOUNGE:34", LOUNGE,
                listOf(ZoneMember(LOUNGE, "Lounge", "192.168.1.21:1400"), ZoneMember(DINING, "Dining room", "192.168.1.22:1400")),
            ),
        ).inOrder()
    }

    /** A room named with "&", "’" and "<" survives the two levels of escaping. */
    @Test
    fun anEscapedRoomNameIsReadAsWritten() {
        val odd = SonosFixtures.topology("192.168.1.20:1400", "192.168.1.21:1400", "192.168.1.22:1400", kitchenName = "Tom & Jo’s <den>")
        assertThat(SonosXml.zoneGroups(odd).first().members.single().name).isEqualTo("Tom & Jo’s <den>")
    }

    /**
     * One builder per thread is reused: a second answer on the same thread is read as the first was. Pin: on the JVM it
     * already is; it guards against a reuse step that drops namespace awareness, as Android's reset() does.
     */
    @Test
    fun aReusedBuilderStillReadsNamespacedAnswers() {
        repeat(2) {
            assertThat(SonosXml.householdId(SonosFixtures.household())).isEqualTo(SonosFixtures.HOUSEHOLD)
            assertThat(SonosXml.position(SonosFixtures.position()).title).isEqualTo("Song")
        }
    }

    @Test
    fun theHouseholdId() {
        assertThat(SonosXml.householdId(SonosFixtures.household())).isEqualTo(SonosFixtures.HOUSEHOLD)
    }

    @Test
    fun transportStates() {
        val states = listOf("PLAYING", "PAUSED_PLAYBACK", "STOPPED", "TRANSITIONING", "NO_MEDIA_PRESENT")
        assertThat(states.map { SonosXml.transportState(SonosFixtures.transport(it)) })
            .containsExactly(PlayState.PLAYING, PlayState.PAUSED, PlayState.STOPPED, PlayState.TRANSITIONING, PlayState.STOPPED).inOrder()
    }

    @Test
    fun aLibraryTrack() {
        assertThat(SonosXml.position(SonosFixtures.position())).isEqualTo(
            TrackInfo(
                SonosFixtures.TRACK_URI, "Song", "Artist", "Album",
                "/getaa?s=1&u=x-file-cifs%3a%2f%2fnas%2fmusic%2fArtist%2fAlbum%2f01%2520Song.flac", programme = null,
            ),
        )
    }

    /** The station is GetMediaInfo's; the programme radioShowMd's without its id; ZPSTR_ placeholders are nothing. */
    @Test
    fun aBbcRadioStream() {
        val track = SonosXml.position(SonosFixtures.position(SonosFixtures.RADIO_URI, SonosFixtures.RADIO_TRACK))
        assertThat(track.programme).isEqualTo("The Breakfast Show")
        assertThat(track.artUri).isEqualTo("https://art.example/p0abc123.jpg")
        assertThat(SonosXml.media(SonosFixtures.media(SonosFixtures.RADIO_URI, SonosFixtures.RADIO_STATION)))
            .isEqualTo(MediaInfo(SonosFixtures.RADIO_URI, "Culvery FM", null))
    }

    /** Without a radioShowMd the stream's own words are the programme, unless they are a ZPSTR_ placeholder. */
    @Test
    fun aStreamsWordsAreTheProgrammeUnlessAPlaceholder() {
        fun programme(stream: String) = SonosXml.position(SonosFixtures.position(SonosFixtures.RADIO_URI, SonosFixtures.radioStream(stream))).programme
        assertThat(programme("ZPSTR_CONNECTING")).isNull()
        assertThat(programme("Artist - Song")).isEqualTo("Artist - Song")
    }

    @Test
    fun stoppedWithNothingLoaded() {
        assertThat(SonosXml.position(SonosFixtures.emptyPosition)).isEqualTo(TrackInfo(null, null, null, null, null, null))
        assertThat(SonosXml.media(SonosFixtures.media(""))).isEqualTo(MediaInfo(null, null, null))
    }

    @Test
    fun volumes() {
        assertThat(SonosXml.volume(SonosFixtures.volume(27))).isEqualTo(27)
        assertThat(SonosXml.volume(SonosFixtures.groupVolume(100))).isEqualTo(100)
    }

    /** 6 §4.5: each Favourite's name, art, link and metadata; its kind comes from the metadata's class. */
    @Test
    fun favourites() {
        val result = SonosXml.browse(SonosFixtures.favourites)
        assertThat(result.total).isEqualTo(3)
        assertThat(result.items.map { listOf(it.id, it.title, it.uri, it.metadataClass) }).containsExactly(
            listOf("FV:2/5", "Culvery FM", SonosFixtures.RADIO_URI, "object.item.audioItem.audioBroadcast"),
            listOf("FV:2/9", "Sunday & slow", "file:///jffs/settings/savedqueues.rsq#3", "object.container.playlistContainer"),
            listOf("FV:2/12", "One song", SonosFixtures.TRACK_URI, "object.item.audioItem.musicTrack"),
        ).inOrder()
        assertThat(result.items.first().metadata).contains("<dc:title>Culvery FM</dc:title>")
    }

    @Test
    fun theLibraryBrowseCountsItsTracks() {
        assertThat(SonosXml.browse(SonosFixtures.library(total = 23_456)).total).isEqualTo(23_456)
        assertThat(SonosXml.browse(SonosFixtures.library(total = 0))).isEqualTo(BrowseResult(emptyList(), 0))
    }

    @Test
    fun aFaultsCode() {
        assertThat(SonosXml.faultCode(SonosFixtures.fault(701))).isEqualTo(701)
        assertThat(SonosXml.faultCode("not xml")).isNull()
    }

    /** No cause and no text: either could quote the answer into a log. */
    @Test
    fun anAnswerOfTheWrongShapeIsASonosXmlException() {
        assertThrows(SonosXmlException::class.java) { SonosXml.householdId("<html>router login</html>") }
        assertThrows(SonosXmlException::class.java) { SonosXml.transportState("not xml") }
        assertThrows(SonosXmlException::class.java) { SonosXml.volume(SonosFixtures.soap("GetVolume", "CurrentVolume" to "loud")) }
    }

    @Test
    fun aHostAndPortFromALocation() {
        assertThat(hostAndPort("http://192.168.1.20:1400/xml/device_description.xml")).isEqualTo("192.168.1.20" to 1400)
        assertThat(hostAndPort("not a url")).isNull()
        assertThat(hostAndPort("http://192.168.1.20/xml")).isNull()
    }

    /** 6 §6 privacy: a speaker on the home network, on Sonos's port, and nothing else. */
    @Test
    fun onlyAHomeSpeakerOnItsPortMayBeContacted() {
        assertThat(isSpeakerAuthority("192.168.1.20", 1400)).isTrue()
        assertThat(isSpeakerAuthority("192.168.1.20", 80)).isFalse()
        assertThat(isSpeakerAuthority("8.8.8.8", 1400)).isFalse()
        assertThat(isSpeakerAddress("192.168.1.20:1400")).isTrue()
        listOf("192.168.1.20", "localhost:1400", "8.8.8.8:1400", ":1400", "192.168.1.20:port").forEach { assertThat(isSpeakerAddress(it)).isFalse() }
    }

    /** 6 §6 privacy: a topology naming an internet address. Fails if the member is kept; a group whose leader goes, goes. */
    @Test
    fun aMemberOffTheHomeNetworkIsDropped() {
        val groups = SonosXml.zoneGroups(SonosFixtures.topology("192.168.1.20:1400", "8.8.8.8:80", "192.168.1.22:1400"))
        assertThat(groups.map { it.coordinator }).containsExactly(KITCHEN)
    }

    /** No speaker sends a DOCTYPE or an entity: either is refused before parsing, in the answer or in the DIDL inside it. */
    @Test
    fun aDoctypeOrAnEntityIsRefused() {
        assertThrows(SonosXmlException::class.java) { SonosXml.householdId(SonosFixtures.HOUSEHOLD_WITH_ENTITY) }
        val inner = """<!DOCTYPE d [<!ENTITY t "x">]>""" + SonosFixtures.LIBRARY_TRACK
        assertThrows(SonosXmlException::class.java) { SonosXml.position(SonosFixtures.position(metadata = inner)) }
    }

    /** An `<!ENTITY` the parser would accept (in a comment, in the DIDL) is still refused: the check doesn't rely on the parser. */
    @Test
    fun anEntityDeclarationTheParserWouldAcceptIsRefused() {
        val inner = """<!-- <!ENTITY t "x"> -->""" + SonosFixtures.LIBRARY_TRACK
        assertThrows(SonosXmlException::class.java) { SonosXml.position(SonosFixtures.position(metadata = inner)) }
    }

    /** No answer nests deeper than a few levels; a deeper one is refused before the tree is built, so it can't overflow the stack. */
    @Test
    fun aDeeplyNestedAnswerIsRefused() {
        assertThat(SonosXml.householdId(SonosFixtures.nested(28))).isEqualTo(SonosFixtures.HOUSEHOLD)
        assertThrows(SonosXmlException::class.java) { SonosXml.householdId(SonosFixtures.nested(29)) }
        assertThrows(SonosXmlException::class.java) { SonosXml.householdId(SonosFixtures.nested(100_000)) }
        // A "/>" or ">" inside an attribute's quotes doesn't end the tag.
        listOf("<a x=\"/>\">", "<a x='/>'>").forEach { open ->
            assertThat(SonosXml.householdId(SonosFixtures.nested(28, open))).isEqualTo(SonosFixtures.HOUSEHOLD)
            assertThrows(SonosXmlException::class.java) { SonosXml.householdId(SonosFixtures.nested(29, open)) }
            assertThrows(SonosXmlException::class.java) { SonosXml.householdId(SonosFixtures.nested(100_000, open)) }
        }
    }

    /** A long tag or an unterminated quote is read or refused as an answer, never overflowing the stack while it is measured. */
    @Test
    fun aLongTagIsMeasuredWithoutOverflowing() {
        fun withValueTag(tag: String) = SonosFixtures.household().replace("<CurrentHouseholdID>", tag)
        val longValue = "x".repeat(100_000)
        assertThat(SonosXml.householdId(withValueTag("<CurrentHouseholdID x=\"$longValue\">"))).isEqualTo(SonosFixtures.HOUSEHOLD)
        assertThat(SonosXml.householdId(withValueTag("<CurrentHouseholdID x='$longValue'>"))).isEqualTo(SonosFixtures.HOUSEHOLD)
        assertThrows(SonosXmlException::class.java) { SonosXml.householdId(withValueTag("<a " + "x".repeat(5_000) + ">")) }
        assertThrows(SonosXmlException::class.java) {
            SonosXml.householdId(withValueTag("<CurrentHouseholdID x=\"" + "x".repeat(4_000_000)))
        }
    }

    /** 6 §8.2: a capture keeps the shape; no room, title, link, id, household or address survives. */
    @Test
    fun theCaptureShapeReplacesNamesTitlesLinksIdsAndAddresses() {
        val answers = listOf(
            topology, SonosFixtures.household(), SonosFixtures.position(), SonosFixtures.favourites,
            SonosFixtures.media(SonosFixtures.RADIO_URI, SonosFixtures.RADIO_STATION), SonosFixtures.library(total = 1),
        )
        val shapes = answers.joinToString("\n", transform = SonosXml::redact)
        listOf("Kitchen", "Lounge", "Dining room", "Song", "Artist", "Album", "Culvery FM", "Sunday", "One song",
            "192.168.1.2", "AAAAAA", SonosFixtures.HOUSEHOLD, "x-file-cifs://nas", "art.example", "S://nas", "nas/music", "SA_RINCON").forEach {
            assertThat(shapes).doesNotContain(it)
        }
        assertThat(shapes).contains("ZoneName=\"[room]\"")
        assertThat(shapes).contains("<dc:title>[text]</dc:title>")
        assertThat(shapes).contains("RINCON_[id]")
        assertThat(shapes).contains("id=\"FV:2/5\"")
    }
}
