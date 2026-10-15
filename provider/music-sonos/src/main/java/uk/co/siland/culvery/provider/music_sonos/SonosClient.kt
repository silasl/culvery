package uk.co.siland.culvery.provider.music_sonos

import java.io.IOException
import java.time.Duration
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import uk.co.siland.culvery.capability.music.PlayState

/** A check or command gives up after this (6 §6). */
internal const val SONOS_TIMEOUT_MS = 3_000L

/** Favourites with their metadata run to a few hundred KB; past this isn't a Sonos answer. */
internal const val MAX_ANSWER_BYTES = 4L * 1024 * 1024
internal const val MAX_ART_BYTES = 2L * 1024 * 1024

internal const val LIBRARY = "A:TRACKS"
internal const val FAVOURITES = "FV:2"
internal const val FAVOURITES_PAGE = 100
internal const val SHUFFLE_NOREPEAT = "SHUFFLE_NOREPEAT"
internal const val QUEUE_PREFIX = "x-rincon-queue:"
internal const val LIBRARY_PREFIX = "x-rincon-playlist:"

/** A group's own queue, played from its leader (6 §4.4). */
internal fun queueUri(leader: String) = "$QUEUE_PREFIX$leader#0"

private const val INSTANCE = "InstanceID"
private const val CHANNEL = "Master"
private val SOAP_TYPE = "text/xml; charset=utf-8".toMediaType()

/** Sonos answered with a fault, or an HTTP error: [code] is UPnP's when given. */
internal class SonosFault(val code: Int?) : Exception("Sonos answered with a fault (${code ?: "no code"})")

/** An address that isn't a speaker on the home network: refused before anything is sent (6 §6). I/O, so it leads to a rescan. */
internal class NotASpeaker : IOException("Not a speaker on the home network")

internal fun sonosHttp(): OkHttpClient = OkHttpClient.Builder()
    .connectTimeout(Duration.ofMillis(SONOS_TIMEOUT_MS))
    .readTimeout(Duration.ofMillis(SONOS_TIMEOUT_MS))
    .callTimeout(Duration.ofMillis(SONOS_TIMEOUT_MS))
    .retryOnConnectionFailure(false)
    // A redirect could lead off the home network.
    .followRedirects(false)
    .followSslRedirects(false)
    .build()

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
    // Whatever client it is given: a redirect could lead off the home network.
    private val http = http.newBuilder().followRedirects(false).followSslRedirects(false).build()

    suspend fun call(address: String, service: SonosService, action: String, vararg args: Pair<String, String>): String {
        if (!isSpeakerAddress(address, accept)) throw NotASpeaker()
        val request = Request.Builder()
            .url("http://$address${service.path}")
            .header("SOAPACTION", soapAction(service, action))
            .post(envelope(service, action, args.toList()).toRequestBody(SOAP_TYPE))
            .build()
        val answer = http.newCall(request).await(MAX_ANSWER_BYTES)
        return withContext(Dispatchers.Default) {
            val text = answer.text
            capture(action, text)
            if (answer.code !in 200..299) throw SonosFault(SonosXml.faultCode(text))
            text
        }
    }

    /** The picture at [url], http or https only, no redirect; null for anything else or any failure. Which URLs may be asked is the caller's rule (6 §4.2). */
    suspend fun bytes(url: String): ByteArray? {
        if (!url.startsWith("http://") && !url.startsWith("https://")) return null
        return try {
            http.newCall(Request.Builder().url(url).build()).await(MAX_ART_BYTES).takeIf { it.code in 200..299 }?.bytes
        } catch (e: IOException) {
            null
        } catch (e: IllegalArgumentException) {
            null
        }
    }

    suspend fun play(address: String) = av(address, "Play", "Speed" to "1")

    suspend fun pause(address: String) = av(address, "Pause")

    suspend fun next(address: String) = av(address, "Next")

    suspend fun transportInfo(address: String): PlayState = read(av(address, "GetTransportInfo"), SonosXml::transportState)

    suspend fun positionInfo(address: String): TrackInfo = read(av(address, "GetPositionInfo"), SonosXml::position)

    suspend fun mediaInfo(address: String): MediaInfo = read(av(address, "GetMediaInfo"), SonosXml::media)

    suspend fun removeAllTracks(address: String) = av(address, "RemoveAllTracksFromQueue")

    suspend fun addToQueue(address: String, uri: String, metadata: String) =
        av(address, "AddURIToQueue", "EnqueuedURI" to uri, "EnqueuedURIMetaData" to metadata, "DesiredFirstTrackNumberEnqueued" to "0", "EnqueueAsNext" to "0")

    suspend fun setPlayMode(address: String, mode: String) = av(address, "SetPlayMode", "NewPlayMode" to mode)

    suspend fun setTransportUri(address: String, uri: String, metadata: String) =
        av(address, "SetAVTransportURI", "CurrentURI" to uri, "CurrentURIMetaData" to metadata)

    suspend fun groupVolume(address: String): Int =
        read(call(address, SonosService.GROUP_RENDERING, "GetGroupVolume", INSTANCE to "0"), SonosXml::volume)

    /** The group slider's: a snapshot first, so the rooms keep their ratio. */
    suspend fun setGroupVolume(address: String, percent: Int) {
        call(address, SonosService.GROUP_RENDERING, "SnapshotGroupVolume", INSTANCE to "0")
        call(address, SonosService.GROUP_RENDERING, "SetGroupVolume", INSTANCE to "0", "DesiredVolume" to "$percent")
    }

    suspend fun volume(address: String): Int =
        read(call(address, SonosService.RENDERING, "GetVolume", INSTANCE to "0", "Channel" to CHANNEL), SonosXml::volume)

    suspend fun setVolume(address: String, percent: Int) {
        call(address, SonosService.RENDERING, "SetVolume", INSTANCE to "0", "Channel" to CHANNEL, "DesiredVolume" to "$percent")
    }

    suspend fun zoneGroups(address: String): List<ZoneGroupInfo> =
        read(call(address, SonosService.TOPOLOGY, "GetZoneGroupState")) { SonosXml.zoneGroups(it, accept) }

    suspend fun householdId(address: String): String = read(call(address, SonosService.DEVICE, "GetHouseholdID"), SonosXml::householdId)

    suspend fun browse(address: String, objectId: String, start: Int, count: Int): BrowseResult = read(
        call(
            address, SonosService.CONTENT, "Browse",
            "ObjectID" to objectId, "BrowseFlag" to "BrowseDirectChildren", "Filter" to "*",
            "StartingIndex" to "$start", "RequestedCount" to "$count", "SortCriteria" to "",
        ),
        SonosXml::browse,
    )

    private suspend fun av(address: String, action: String, vararg args: Pair<String, String>): String =
        call(address, SonosService.AV_TRANSPORT, action, INSTANCE to "0", *args)

    private suspend fun <T> read(text: String, parse: (String) -> T): T = withContext(Dispatchers.Default) { parse(text) }
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
