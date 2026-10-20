package uk.co.siland.culvery.provider.music_sonos

import java.io.IOException
import java.io.StringReader
import java.net.URI
import java.net.URISyntaxException
import javax.xml.parsers.DocumentBuilder
import javax.xml.parsers.DocumentBuilderFactory
import javax.xml.parsers.ParserConfigurationException
import org.w3c.dom.Element
import org.xml.sax.InputSource
import org.xml.sax.SAXException
import uk.co.siland.culvery.capability.music.PlayState

internal data class ZoneMember(val uuid: String, val name: String, val address: String)

/** A Sonos group: its id, its leader's id, and its visible rooms with the leader first. */
internal data class ZoneGroupInfo(val id: String, val coordinator: String, val members: List<ZoneMember>)

/** The group a speaker says it is in, and every player in it, hidden ones too. */
internal data class GroupAttributes(val groupId: String, val players: Set<String>)

internal data class TrackInfo(val uri: String?, val title: String?, val artist: String?, val album: String?, val artUri: String?, val programme: String?)

internal data class MediaInfo(val uri: String?, val title: String?, val artUri: String?, val kind: String?) {
    /** Line-in or Bluetooth: Sonos classes both object.item.audioItem.linein[.…]. */
    val isInput: Boolean get() = kind?.contains(".linein") == true || uri?.startsWith(LINE_IN_PREFIX) == true
}

/** One DIDL-Lite item from a browse; [metadata] is its `r:resMD`, which Sonos wants back when it is played. */
internal data class DidlItem(val id: String, val title: String, val artUri: String?, val uri: String?, val metadata: String?, val metadataClass: String?)

internal data class BrowseResult(val items: List<DidlItem>, val total: Int)

/** An answer that isn't the shape asked for. No cause and no text: either could quote the answer. */
internal class SonosXmlException : Exception("A Sonos answer couldn't be read")

/** ("192.168.1.20", 1400) from a speaker's description URL; null without a host and port. */
internal fun hostAndPort(url: String): Pair<String, Int>? = try {
    URI(url).takeIf { it.host != null && it.port > 0 }?.let { it.host to it.port }
} catch (e: URISyntaxException) {
    null
}

/** Sonos's SOAP answers and their DIDL-Lite (6 §3.2). Plain JVM: no Android, so its tests need no emulator. */
internal object SonosXml {
    private const val NOTHING = "NOT_IMPLEMENTED"
    private const val PLACEHOLDER = "ZPSTR_"
    private const val UNESCAPE_DEPTH = 3
    private const val MAX_DEPTH = 32

    /** Each group's visible rooms, leader first; a member [accept] refuses is dropped, and so is a group without its leader (6 §6). */
    fun zoneGroups(answer: String, accept: (String, Int) -> Boolean = ::isSpeakerAuthority): List<ZoneGroupInfo> =
        parse(text(answer, "ZoneGroupState")).all("ZoneGroup").mapNotNull { group ->
            val leader = group.getAttribute("Coordinator")
            val members = group.children("ZoneGroupMember")
                .filter { it.getAttribute("Invisible") != "1" && it.getAttribute("IsZoneBridge") != "1" }
                .mapNotNull { m ->
                    hostAndPort(m.getAttribute("Location"))
                        ?.takeIf { (host, port) -> accept(host, port) }
                        ?.let { (host, port) -> ZoneMember(m.getAttribute("UUID"), m.getAttribute("ZoneName"), "$host:$port") }
                }
                .sortedBy { it.uuid != leader }
            if (members.none { it.uuid == leader }) null else ZoneGroupInfo(group.getAttribute("ID"), leader, members)
        }

    fun householdId(answer: String): String = text(answer, "CurrentHouseholdID")

    fun groupAttributes(answer: String): GroupAttributes {
        val root = parse(answer)
        val id = root.text("CurrentZoneGroupID") ?: throw SonosXmlException()
        val players = root.text("CurrentZonePlayerUUIDsInGroup") ?: throw SonosXmlException()
        return GroupAttributes(id, players.split(',').map(String::trim).toSet())
    }

    fun transportState(answer: String): PlayState = when (text(answer, "CurrentTransportState")) {
        "PLAYING" -> PlayState.PLAYING
        "PAUSED_PLAYBACK" -> PlayState.PAUSED
        "TRANSITIONING" -> PlayState.TRANSITIONING
        else -> PlayState.STOPPED
    }

    fun position(answer: String): TrackInfo {
        val root = parse(answer)
        val item = didlItem(root.text("TrackMetaData"))
        return TrackInfo(
            uri = root.text("TrackURI"),
            title = item?.text("title"),
            artist = item?.text("creator") ?: item?.text("artist"),
            album = item?.text("album"),
            artUri = item?.text("albumArtURI"),
            programme = item?.let(::programme),
        )
    }

    fun media(answer: String): MediaInfo {
        val root = parse(answer)
        val item = didlItem(root.text("CurrentURIMetaData"))
        return MediaInfo(root.text("CurrentURI"), item?.text("title"), item?.text("albumArtURI"), item?.text("class"))
    }

    fun volume(answer: String): Int = text(answer, "CurrentVolume").toIntOrNull()?.coerceIn(0, 100) ?: throw SonosXmlException()

    fun browse(answer: String): BrowseResult {
        val root = parse(answer)
        val total = root.text("TotalMatches")?.toIntOrNull() ?: throw SonosXmlException()
        val result = root.text("Result") ?: return BrowseResult(emptyList(), total)
        val items = parse(result).all("item").map { item ->
            val metadata = item.text("resMD")
            DidlItem(item.getAttribute("id"), item.text("title").orEmpty(), item.text("albumArtURI"), item.text("res"), metadata, didlItem(metadata)?.text("class"))
        }
        return BrowseResult(items, total)
    }

    fun faultCode(answer: String): Int? = try {
        parse(answer).text("errorCode")?.toIntOrNull()
    } catch (e: SonosXmlException) {
        null
    }

    private val ENTITIES = listOf("&lt;" to "<", "&gt;" to ">", "&quot;" to "\"", "&apos;" to "'", "&amp;" to "&")
    private val SPOKEN_TEXT = Regex(
        """<((?:\w+:)?(?:title|creator|album|artist|albumArtist|streamContent|radioShowMd|description|desc|albumArtURI|res|""" +
            """CurrentURI|NextURI|TrackURI|AVTransportURI|NextAVTransportURI|EnqueuedTransportURI|CurrentZoneGroupName))(\s[^>]*)?>[^<]*</\1>""",
    )
    // A closing quote is ["]: a raw string ending in four quotes would end early for the copy scan.
    private val NAME_ATTRIBUTE = Regex("""(\w*Name)="[^"]*["]""")
    private val LOCATION_ATTRIBUTE = Regex("""(Location)="[^"]*["]""")
    // A library item's id is its file's path; Sonos's own ids (FV:2/5, A:TRACKS, SQ:3) have no "://" or "%".
    private val PATH_ID = Regex("""(?<![\w:])(id|parentID)="[^"]*(?:://|%)[^"]*["]""")
    private val HOUSEHOLD = Regex("""Sonos_[A-Za-z0-9._-]+""")
    private val SPEAKER_ID = Regex("""RINCON_[0-9A-Fa-f]+""")
    private val IPV4 = Regex("""\b\d{1,3}(?:\.\d{1,3}){3}\b""")

    /**
     * An answer's shape for a capture (6 §8.2): unescaped down to its DIDL, then every room name, title, artist, link,
     * service descriptor, file path, speaker id, household and address replaced.
     */
    fun redact(answer: String): String {
        var text = answer
        repeat(UNESCAPE_DEPTH) { text = ENTITIES.fold(text) { t, (from, to) -> t.replace(from, to) } }
        return text
            .replace(SPOKEN_TEXT) { "<${it.groupValues[1]}${it.groupValues[2]}>[text]</${it.groupValues[1]}>" }
            .replace(NAME_ATTRIBUTE) { "${it.groupValues[1]}=\"[room]\"" }
            .replace(LOCATION_ATTRIBUTE) { "${it.groupValues[1]}=\"[address]\"" }
            .replace(PATH_ID) { "${it.groupValues[1]}=\"[path]\"" }
            .replace(HOUSEHOLD, "Sonos_[household]")
            .replace(SPEAKER_ID, "RINCON_[id]")
            .replace(IPV4, "[address]")
    }

    /** The programme from a station's metadata: radioShowMd without its id, else the stream's words unless a placeholder. */
    private fun programme(item: Element): String? {
        val show = item.text("radioShowMd")?.substringBeforeLast(',')?.takeIf { it.isNotBlank() }
        val stream = item.text("streamContent")?.takeIf { !it.startsWith(PLACEHOLDER) }
        return show ?: stream
    }

    private fun didlItem(didl: String?): Element? = didl?.let(::parse)?.let { it.first("item") ?: it.first("container") }

    private fun text(answer: String, name: String): String = parse(answer).text(name) ?: throw SonosXmlException()

    private val FACTORY: DocumentBuilderFactory = DocumentBuilderFactory.newInstance().apply {
        isNamespaceAware = true
        isExpandEntityReferences = false
        try {
            setFeature("http://apache.org/xml/features/disallow-doctype-decl", true)
        } catch (e: ParserConfigurationException) {
            // Android's parser refuses the feature; it never fetches a DOCTYPE's outside parts anyway.
        }
    }

    // A builder isn't thread-safe, and neither is the factory that makes one. Never reset(): Android's turns namespace
    // awareness off, and nothing here sets a resolver or handler that would need clearing.
    private val BUILDER: ThreadLocal<DocumentBuilder> = ThreadLocal.withInitial { synchronized(FACTORY) { FACTORY.newDocumentBuilder() } }

    private val DECLARATIONS = Regex("""<!(DOCTYPE|ENTITY)""", RegexOption.IGNORE_CASE)
    private val SKIPPED = listOf("<!--" to "-->", "<![CDATA[" to "]]>", "<?" to "?>", "<!" to ">")

    /**
     * Whether [xml] nests deeper than any answer does; checked before the tree is built, as a deep one overflows the stack.
     * One loop, no regex: java.util.regex recurses per character of a long tag.
     */
    private fun tooDeep(xml: String): Boolean {
        var depth = 0
        var i = xml.indexOf('<')
        while (i >= 0) {
            val skip = SKIPPED.firstOrNull { xml.startsWith(it.first, i) }
            i = when {
                skip != null -> xml.indexOf(skip.second, i).let { if (it < 0) return false else it + skip.second.length }
                xml.startsWith("</", i) -> { depth--; xml.indexOf('>', i) }
                else -> {
                    val close = tagEnd(xml, i)
                    if (close < 0) return false
                    if (xml[close - 1] != '/' && ++depth > MAX_DEPTH) return true
                    close
                }
            }
            if (i < 0) return false
            i = xml.indexOf('<', i)
        }
        return false
    }

    /** The index of the `>` ending the tag at [start], skipping quoted attribute values; -1 if it never ends. */
    private fun tagEnd(xml: String, start: Int): Int {
        var quote: Char? = null
        for (j in start + 1 until xml.length) {
            val c = xml[j]
            when {
                quote != null -> if (c == quote) quote = null
                c == '"' || c == '\'' -> quote = c
                c == '>' -> return j
            }
        }
        return -1
    }

    /**
     * Refuses a DOCTYPE or an entity (no speaker sends one) and a too-deep answer before parsing, here and in the DIDL
     * parsed a second time.
     */
    private fun parse(xml: String): Element {
        if (DECLARATIONS.containsMatchIn(xml) || tooDeep(xml)) throw SonosXmlException()
        return try {
            BUILDER.get().parse(InputSource(StringReader(xml))).documentElement
        } catch (e: SAXException) {
            throw SonosXmlException()
        } catch (e: IOException) {
            throw SonosXmlException()
        }
    }

    private fun Element.all(localName: String): List<Element> =
        getElementsByTagNameNS("*", localName).let { nodes -> (0 until nodes.length).map { nodes.item(it) as Element } }

    private fun Element.first(localName: String): Element? = getElementsByTagNameNS("*", localName).item(0) as Element?

    private fun Element.children(localName: String): List<Element> =
        (0 until childNodes.length).map(childNodes::item).filterIsInstance<Element>().filter { it.localName == localName }

    /** The first [localName]'s text, trimmed; null when missing, empty or Sonos's NOT_IMPLEMENTED. */
    private fun Element.text(localName: String): String? =
        first(localName)?.textContent?.trim()?.takeIf { it.isNotEmpty() && it != NOTHING }
}
