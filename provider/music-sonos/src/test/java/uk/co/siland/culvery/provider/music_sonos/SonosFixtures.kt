package uk.co.siland.culvery.provider.music_sonos

/**
 * Sonos's answers as its UPnP services give them, built from Sonos's published service descriptions and SoCo's and
 * node-sonos's examples (6 §7); replaced by the household's redacted captures (6 §8.2). Rooms are generic; the ids and
 * the household are made up.
 */
internal object SonosFixtures {
    const val HOUSEHOLD = "Sonos_aBcDeFgHiJkLmNoP"
    const val KITCHEN = "RINCON_000E58AAAAAA01400"
    const val LOUNGE = "RINCON_000E58BBBBBB01400"
    const val DINING = "RINCON_000E58CCCCCC01400"
    const val SUB = "RINCON_000E58DDDDDD01400"

    private const val DIDL_OPEN =
        """<DIDL-Lite xmlns:dc="http://purl.org/dc/elements/1.1/" xmlns:upnp="urn:schemas-upnp-org:metadata-1-0/upnp/" xmlns:r="urn:schemas-rinconnetworks-com:metadata-1-0/" xmlns="urn:schemas-upnp-org:metadata-1-0/DIDL-Lite/">"""

    fun escape(text: String) = text.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;").replace("\"", "&quot;")

    /** A SOAP answer to [action] with [values], each escaped as Sonos escapes them. */
    fun soap(action: String, vararg values: Pair<String, String>): String =
        """<?xml version="1.0"?><s:Envelope xmlns:s="http://schemas.xmlsoap.org/soap/envelope/" s:encodingStyle="http://schemas.xmlsoap.org/soap/encoding/"><s:Body>""" +
            """<u:${action}Response xmlns:u="urn:schemas-upnp-org:service:AVTransport:1">""" +
            values.joinToString("") { (name, value) -> "<$name>${escape(value)}</$name>" } +
            """</u:${action}Response></s:Body></s:Envelope>"""

    fun fault(code: Int) =
        """<?xml version="1.0"?><s:Envelope xmlns:s="http://schemas.xmlsoap.org/soap/envelope/" s:encodingStyle="http://schemas.xmlsoap.org/soap/encoding/"><s:Body><s:Fault><faultcode>s:Client</faultcode><faultstring>UPnPError</faultstring><detail><UPnPError xmlns="urn:schemas-upnp-org:control-1-0"><errorCode>$code</errorCode></UPnPError></detail></s:Fault></s:Body></s:Envelope>"""

    /** Kitchen alone; Lounge leading Dining room (listed first), with a sub bonded to the Lounge that the app hides. */
    fun topology(kitchen: String, lounge: String, dining: String, kitchenName: String = "Kitchen") = soap(
        "GetZoneGroupState",
        "ZoneGroupState" to
            """<ZoneGroupState><ZoneGroups>""" +
            """<ZoneGroup Coordinator="$KITCHEN" ID="$KITCHEN:12"><ZoneGroupMember UUID="$KITCHEN" Location="http://$kitchen/xml/device_description.xml" ZoneName="${escape(kitchenName)}" Icon="x-rincon-roomicon:kitchen" Invisible="0"/></ZoneGroup>""" +
            """<ZoneGroup Coordinator="$LOUNGE" ID="$LOUNGE:34"><ZoneGroupMember UUID="$DINING" Location="http://$dining/xml/device_description.xml" ZoneName="Dining room"/>""" +
            """<ZoneGroupMember UUID="$LOUNGE" Location="http://$lounge/xml/device_description.xml" ZoneName="Lounge"><Satellite UUID="$SUB" Location="http://$lounge/xml/device_description.xml" ZoneName="Lounge" Invisible="1"/></ZoneGroupMember>""" +
            """<ZoneGroupMember UUID="$SUB" Location="http://$lounge/xml/device_description.xml" ZoneName="Lounge" Invisible="1"/></ZoneGroup>""" +
            """</ZoneGroups><VanishedDevices/></ZoneGroupState>""",
    )

    private fun member(uuid: String, address: String, name: String) =
        """<ZoneGroupMember UUID="$uuid" Location="http://$address/xml/device_description.xml" ZoneName="$name"/>"""

    private fun zoneGroupState(groups: String) =
        soap("GetZoneGroupState", "ZoneGroupState" to "<ZoneGroupState><ZoneGroups>$groups</ZoneGroups><VanishedDevices/></ZoneGroupState>")

    /** The Kitchen joined to the Lounge's group in the Sonos app. */
    fun grouped(kitchen: String, lounge: String, dining: String) = zoneGroupState(
        """<ZoneGroup Coordinator="$LOUNGE" ID="$LOUNGE:34">""" + member(LOUNGE, lounge, "Lounge") + member(DINING, dining, "Dining room") +
            member(KITCHEN, kitchen, "Kitchen") + "</ZoneGroup>",
    )

    /** The Dining room taken out of the Lounge's group: every room on its own. */
    fun apart(kitchen: String, lounge: String, dining: String) = zoneGroupState(
        """<ZoneGroup Coordinator="$KITCHEN" ID="$KITCHEN:12">""" + member(KITCHEN, kitchen, "Kitchen") + "</ZoneGroup>" +
            """<ZoneGroup Coordinator="$LOUNGE" ID="$LOUNGE:34">""" + member(LOUNGE, lounge, "Lounge") + "</ZoneGroup>" +
            """<ZoneGroup Coordinator="$DINING" ID="$DINING:56">""" + member(DINING, dining, "Dining room") + "</ZoneGroup>",
    )

    /**
     * GetZoneGroupAttributes as ZoneGroupTopology documents it: the group the asked speaker is in, and every player in it
     * (hidden ones too), comma-separated.
     */
    fun groupAttributes(groupId: String, vararg players: String, name: String = "Kitchen") = soap(
        "GetZoneGroupAttributes",
        "CurrentZoneGroupName" to name,
        "CurrentZoneGroupID" to groupId,
        "CurrentZonePlayerUUIDsInGroup" to players.joinToString(","),
        "CurrentMuseHouseholdId" to HOUSEHOLD,
    )

    fun household(id: String = HOUSEHOLD) = soap("GetHouseholdID", "CurrentHouseholdID" to id)

    /** An answer that declares an entity: no speaker sends one, so it is refused before parsing. */
    const val HOUSEHOLD_WITH_ENTITY =
        """<?xml version="1.0"?><!DOCTYPE s:Envelope [<!ENTITY room "Kitchen">]><s:Envelope xmlns:s="http://schemas.xmlsoap.org/soap/envelope/"><s:Body>""" +
            """<u:GetHouseholdIDResponse xmlns:u="urn:schemas-upnp-org:service:DeviceProperties:1"><CurrentHouseholdID>&room;</CurrentHouseholdID></u:GetHouseholdIDResponse></s:Body></s:Envelope>"""

    fun transport(state: String) = soap("GetTransportInfo", "CurrentTransportState" to state, "CurrentTransportStatus" to "OK", "CurrentSpeed" to "1")

    fun volume(volume: Int) = soap("GetVolume", "CurrentVolume" to "$volume")

    fun groupVolume(volume: Int) = soap("GetGroupVolume", "CurrentVolume" to "$volume")

    /** A FLAC from the NAS's music library, its art on the speaker. */
    const val LIBRARY_TRACK = DIDL_OPEN +
        """<item id="-1" parentID="-1" restricted="true"><res protocolInfo="x-file-cifs:*:audio/flac:*" duration="0:04:12">x-file-cifs://nas/music/Artist/Album/01%20Song.flac</res>""" +
        """<r:streamContent></r:streamContent><upnp:albumArtURI>/getaa?s=1&amp;u=x-file-cifs%3a%2f%2fnas%2fmusic%2fArtist%2fAlbum%2f01%2520Song.flac</upnp:albumArtURI>""" +
        """<dc:title>Song</dc:title><upnp:class>object.item.audioItem.musicTrack</upnp:class><dc:creator>Artist</dc:creator><upnp:album>Album</upnp:album></item></DIDL-Lite>"""

    /** A BBC station from BBC Sounds: the programme in radioShowMd, the stream's own art on the internet. */
    const val RADIO_TRACK = DIDL_OPEN +
        """<item id="-1" parentID="-1" restricted="true"><res protocolInfo="sonos.com-http:*:application/x-mpegURL:*">x-sonosapi-hls:stations%7eBBC6%7eBBC6?sid=325&amp;flags=8224&amp;sn=1</res>""" +
        """<r:streamContent>ZPSTR_CONNECTING</r:streamContent><r:radioShowMd>The Breakfast Show,p0abc123</r:radioShowMd>""" +
        """<upnp:albumArtURI>https://art.example/p0abc123.jpg</upnp:albumArtURI><dc:title>x-sonosapi-hls:stations%7eBBC6%7eBBC6?sid=325&amp;flags=8224&amp;sn=1</dc:title>""" +
        """<upnp:class>object.item</upnp:class></item></DIDL-Lite>"""

    /** A stream that names no programme: [stream] is its only words, a ZPSTR_ placeholder while it connects. */
    fun radioStream(stream: String) = DIDL_OPEN +
        """<item id="-1" parentID="-1" restricted="true"><res protocolInfo="x-rincon-mp3radio:*:*:*">x-rincon-mp3radio://radio.example/stream</res>""" +
        """<r:streamContent>${escape(stream)}</r:streamContent><upnp:class>object.item</upnp:class></item></DIDL-Lite>"""

    /** An answer wrapped in [depth] [open] elements around its body's value. */
    fun nested(depth: Int, open: String = "<a>") = soap("GetHouseholdID", "CurrentHouseholdID" to HOUSEHOLD).replace(
        "<CurrentHouseholdID>", open.repeat(depth) + "<CurrentHouseholdID>",
    ).replace("</CurrentHouseholdID>", "</CurrentHouseholdID>" + "</a>".repeat(depth))

    const val RADIO_STATION = DIDL_OPEN +
        """<item id="-1" parentID="-1" restricted="true"><dc:title>Culvery FM</dc:title><upnp:class>object.item.audioItem.audioBroadcast</upnp:class>""" +
        """<desc id="cdudn" nameSpace="urn:schemas-rinconnetworks-com:metadata-1-0/">SA_RINCON83207_</desc></item></DIDL-Lite>"""

    const val RADIO_URI = "x-sonosapi-hls:stations%7eBBC6%7eBBC6?sid=325&flags=8224&sn=1"
    const val TRACK_URI = "x-file-cifs://nas/music/Artist/Album/01%20Song.flac"

    fun position(uri: String = TRACK_URI, metadata: String = LIBRARY_TRACK) =
        soap("GetPositionInfo", "Track" to "1", "TrackDuration" to "0:04:12", "TrackMetaData" to metadata, "TrackURI" to uri, "RelTime" to "0:01:02")

    fun media(uri: String, metadata: String = "") =
        soap("GetMediaInfo", "NrTracks" to "12", "MediaDuration" to "NOT_IMPLEMENTED", "CurrentURI" to uri, "CurrentURIMetaData" to metadata, "NextURI" to "")

    /** Stopped with nothing loaded: Sonos answers NOT_IMPLEMENTED for the metadata. */
    val emptyPosition = soap("GetPositionInfo", "Track" to "0", "TrackMetaData" to "NOT_IMPLEMENTED", "TrackURI" to "")

    fun queue(leader: String) = "x-rincon-queue:$leader#0"

    /** The household's Favourites (FV:2): a station, a NAS playlist, and a single track. */
    val favourites = browse(
        total = 3,
        """<item id="FV:2/5" parentID="FV:2" restricted="false"><dc:title>Culvery FM</dc:title><upnp:class>object.itemobject.item.sonos-favorite</upnp:class>""" +
            """<upnp:albumArtURI>https://art.example/station.png</upnp:albumArtURI><r:type>instantPlay</r:type><res protocolInfo="x-rincon-mp3radio:*:*:*">${escape(RADIO_URI)}</res>""" +
            """<r:resMD>${escape(RADIO_STATION)}</r:resMD></item>""",
        """<item id="FV:2/9" parentID="FV:2" restricted="false"><dc:title>Sunday &amp; slow</dc:title><upnp:class>object.itemobject.item.sonos-favorite</upnp:class>""" +
            """<upnp:albumArtURI>/getaa?s=1&amp;u=x-rincon-playlist</upnp:albumArtURI><r:type>instantPlay</r:type><res protocolInfo="file:*:audio/mpegurl:*">file:///jffs/settings/savedqueues.rsq#3</res>""" +
            """<r:resMD>${escape(DIDL_OPEN + """<item id="SQ:3" parentID="SQ:" restricted="true"><dc:title>Sunday &amp; slow</dc:title><upnp:class>object.container.playlistContainer</upnp:class></item></DIDL-Lite>""")}</r:resMD></item>""",
        """<item id="FV:2/12" parentID="FV:2" restricted="false"><dc:title>One song</dc:title><upnp:class>object.itemobject.item.sonos-favorite</upnp:class>""" +
            """<res protocolInfo="x-file-cifs:*:audio/flac:*">$TRACK_URI</res><r:resMD>${escape(LIBRARY_TRACK)}</r:resMD></item>""",
    )

    fun library(total: Int) = browse(total = total, *(if (total == 0) emptyArray() else arrayOf(
        """<item id="S://nas/music/Artist/Album/01%20Song.flac" parentID="A:TRACKS" restricted="true"><res protocolInfo="x-file-cifs:*:audio/flac:*">$TRACK_URI</res><dc:title>Song</dc:title><upnp:class>object.item.audioItem.musicTrack</upnp:class></item>""",
    )))

    fun browse(total: Int, vararg items: String) = soap(
        "Browse",
        "Result" to DIDL_OPEN + items.joinToString("") + "</DIDL-Lite>",
        "NumberReturned" to "${items.size}",
        "TotalMatches" to "$total",
        "UpdateID" to "1",
    )
}
