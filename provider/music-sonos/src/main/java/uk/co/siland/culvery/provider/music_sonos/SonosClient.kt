package uk.co.siland.culvery.provider.music_sonos

import java.io.IOException
import java.io.InterruptedIOException
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.withContext
import okhttp3.ConnectionPool
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import uk.co.siland.culvery.capability.music.PlayState
import uk.co.siland.culvery.core.net.Answer
import uk.co.siland.culvery.core.net.BodyTooLarge
import uk.co.siland.culvery.core.net.await
import uk.co.siland.culvery.core.net.homeHttpClient

/** A check or command gives up after this (6 §6). */
internal const val SONOS_TIMEOUT_MS = 3_000L

/** Favourites with their metadata run to a few hundred KB; past this isn't a Sonos answer. */
internal const val MAX_ANSWER_BYTES = 4L * 1024 * 1024
internal const val MAX_ART_BYTES = 2L * 1024 * 1024

internal const val LIBRARY = "A:TRACKS"
internal const val FAVOURITES = "FV:2"
internal const val PLAYLISTS = "SQ:"
internal const val FAVOURITES_PAGE = 100
internal const val SHUFFLE_NOREPEAT = "SHUFFLE_NOREPEAT"
internal const val QUEUE_PREFIX = "x-rincon-queue:"
internal const val RINCON_PREFIX = "x-rincon:"
internal const val LIBRARY_PREFIX = "x-rincon-playlist:"

/** Line-in has this scheme; Bluetooth is an x-sonos-vli URI, so inputs are told by their UPnP class (MediaInfo.isInput). */
internal const val LINE_IN_PREFIX = "x-rincon-stream:"

/** A group's own queue, played from its leader (6 §4.4). */
internal fun queueUri(leader: String) = "$QUEUE_PREFIX$leader#0"

private const val INSTANCE = "InstanceID"
private const val CHANNEL = "Master"
private val SOAP_TYPE = "text/xml; charset=utf-8".toMediaType()

/** Sonos answered with a fault, or an HTTP error: [code] is UPnP's when given. */
internal class SonosFault(val code: Int?) : Exception("Sonos answered with a fault (${code ?: "no code"})")

/** An address that isn't a speaker on the home network: refused before anything is sent (6 §6). I/O, so it leads to a rescan. */
internal class NotASpeaker : IOException("Not a speaker on the home network")

internal fun sonosHttp(): OkHttpClient = homeHttpClient(SONOS_TIMEOUT_MS)

/**
 * SOAP to one speaker at `host:port` (6 §3.2), only where [accept] allows: a speaker on the home network, on port 1400.
 * Each call throws IOException when the speaker doesn't answer (or isn't allowed), [SonosFault] when it refuses, and
 * [SonosXmlException] when the answer isn't the shape asked for. Answers are decoded and parsed off the main thread.
 */
internal class SonosClient(
    http: OkHttpClient,
    private val accept: (String, Int) -> Boolean = ::isSpeakerAuthority,
    private val capture: (String, String) -> Unit = SonosCapture::log,
) {
    // Whatever client it is given: a redirect could lead off the home network. No idle connection is kept: a speaker
    // drops idle keep-alives within seconds, and with no retry (a command is never sent twice) a dropped one fails the call.
    private val http = http.newBuilder()
        .followRedirects(false)
        .followSslRedirects(false)
        .connectionPool(ConnectionPool(0, 1, TimeUnit.SECONDS))
        .build()

    private suspend fun <T> call(address: String, service: SonosService, action: String, vararg args: Pair<String, String>, parse: (String) -> T): T {
        if (!isSpeakerAddress(address, accept)) throw NotASpeaker()
        val request = Request.Builder()
            .url("http://$address${service.path}")
            .header("SOAPACTION", soapAction(service, action))
            .post(envelope(service, action, args.toList()).toRequestBody(SOAP_TYPE))
            .build()
        val answer = send(request, MAX_ANSWER_BYTES, retry = action.startsWith("Get") || action == "Browse")
        return withContext(Dispatchers.Default) {
            val text = answer.text
            capture(action, text)
            if (answer.code !in 200..299) throw SonosFault(SonosXml.faultCode(text))
            parse(text)
        }
    }

    /**
     * A busy network drops the odd connection, so a read ([retry]) is asked once more after a dropped connection. Never a
     * command (it may have taken effect before its answer was lost), a timeout (a dead speaker would take twice the 3 s)
     * or an answer too large to be Sonos's.
     */
    private suspend fun send(request: Request, maxBytes: Long, retry: Boolean): Answer = try {
        http.newCall(request).await(maxBytes)
    } catch (e: IOException) {
        if (!retry || e is InterruptedIOException || e is BodyTooLarge) throw e
        http.newCall(request).await(maxBytes)
    }

    /** The picture at [url], http or https only, no redirect; null for anything else or any failure. Which URLs may be asked is the caller's rule (6 §4.2). */
    suspend fun bytes(url: String): ByteArray? {
        if (!url.startsWith("http://") && !url.startsWith("https://")) return null
        return try {
            send(Request.Builder().url(url).build(), MAX_ART_BYTES, retry = true).takeIf { it.code in 200..299 }?.bytes
        } catch (e: IOException) {
            null
        } catch (e: IllegalArgumentException) {
            null
        }
    }

    suspend fun play(address: String) = av(address, "Play", "Speed" to "1")

    suspend fun pause(address: String) = av(address, "Pause")

    suspend fun next(address: String) = av(address, "Next")

    suspend fun transportInfo(address: String): PlayState = av(address, "GetTransportInfo", parse = SonosXml::transportState)

    suspend fun positionInfo(address: String): TrackInfo = av(address, "GetPositionInfo", parse = SonosXml::position)

    suspend fun mediaInfo(address: String): MediaInfo = av(address, "GetMediaInfo", parse = SonosXml::media)

    suspend fun removeAllTracks(address: String) = av(address, "RemoveAllTracksFromQueue")

    suspend fun addToQueue(address: String, uri: String, metadata: String) =
        av(address, "AddURIToQueue", "EnqueuedURI" to uri, "EnqueuedURIMetaData" to metadata, "DesiredFirstTrackNumberEnqueued" to "0", "EnqueueAsNext" to "0")

    suspend fun setPlayMode(address: String, mode: String) = av(address, "SetPlayMode", "NewPlayMode" to mode)

    suspend fun setTransportUri(address: String, uri: String, metadata: String) =
        av(address, "SetAVTransportURI", "CurrentURI" to uri, "CurrentURIMetaData" to metadata)

    /** Makes the speaker at [address] join the group [coordinatorUuid] leads (13 §4.1). */
    suspend fun join(address: String, coordinatorUuid: String) = setTransportUri(address, "$RINCON_PREFIX$coordinatorUuid", "")

    /** Takes the speaker at [address] out of its group, to lead itself alone (13 §4.1). */
    suspend fun standAlone(address: String) = av(address, "BecomeCoordinatorOfStandaloneGroup")

    suspend fun groupVolume(address: String): Int =
        call(address, SonosService.GROUP_RENDERING, "GetGroupVolume", INSTANCE to "0", parse = SonosXml::volume)

    /** The group slider's: a snapshot first, so the rooms keep their ratio. */
    suspend fun setGroupVolume(address: String, percent: Int) {
        call(address, SonosService.GROUP_RENDERING, "SnapshotGroupVolume", INSTANCE to "0") {}
        call(address, SonosService.GROUP_RENDERING, "SetGroupVolume", INSTANCE to "0", "DesiredVolume" to "$percent") {}
    }

    suspend fun volume(address: String): Int =
        call(address, SonosService.RENDERING, "GetVolume", INSTANCE to "0", "Channel" to CHANNEL, parse = SonosXml::volume)

    suspend fun setVolume(address: String, percent: Int) {
        call(address, SonosService.RENDERING, "SetVolume", INSTANCE to "0", "Channel" to CHANNEL, "DesiredVolume" to "$percent") {}
    }

    suspend fun zoneGroups(address: String): List<ZoneGroupInfo> =
        call(address, SonosService.TOPOLOGY, "GetZoneGroupState") { SonosXml.zoneGroups(it, accept) }

    suspend fun groupAttributes(address: String): GroupAttributes =
        call(address, SonosService.TOPOLOGY, "GetZoneGroupAttributes", parse = SonosXml::groupAttributes)

    suspend fun householdId(address: String): String = call(address, SonosService.DEVICE, "GetHouseholdID", parse = SonosXml::householdId)

    suspend fun browse(address: String, objectId: String, start: Int, count: Int): BrowseResult = call(
        address, SonosService.CONTENT, "Browse",
        "ObjectID" to objectId, "BrowseFlag" to "BrowseDirectChildren", "Filter" to "*",
        "StartingIndex" to "$start", "RequestedCount" to "$count", "SortCriteria" to "",
        parse = SonosXml::browse,
    )

    private suspend fun <T> av(address: String, action: String, vararg args: Pair<String, String>, parse: (String) -> T): T =
        call(address, SonosService.AV_TRANSPORT, action, INSTANCE to "0", *args, parse = parse)

    private suspend fun av(address: String, action: String, vararg args: Pair<String, String>) = av(address, action, *args) {}
}

/** [block]'s answer, or null when the speaker didn't answer, refused, or answered in a shape that isn't Sonos's. */
internal inline fun <T> quietly(block: () -> T): T? = try {
    block()
} catch (e: IOException) {
    null
} catch (e: SonosFault) {
    null
} catch (e: SonosXmlException) {
    null
}

/** The household of the speaker at [address], or null when nothing there answers as a Sonos speaker on the home network. */
internal suspend fun SonosClient.householdAt(address: String): String? = quietly { householdId(address) }

/** Each of [addresses] that answers as a speaker, with its household: all asked at once, kept in the given order. */
internal suspend fun SonosClient.householdsAt(addresses: List<String>): List<Pair<String, String>> = coroutineScope {
    addresses.map { address -> async { householdAt(address)?.let { address to it } } }.awaitAll().filterNotNull()
}
