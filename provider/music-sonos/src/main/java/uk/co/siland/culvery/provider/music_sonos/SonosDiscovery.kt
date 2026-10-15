package uk.co.siland.culvery.provider.music_sonos

import android.content.Context
import android.net.wifi.WifiManager
import android.util.Log
import java.io.IOException
import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.InetSocketAddress
import java.net.SocketTimeoutException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext

internal const val SSDP_HOST = "239.255.255.250"
internal const val SSDP_PORT = 1900

/** How long a scan listens (6 §3.2). */
internal const val SCAN_MS = 5_000L

private const val TAG = "SonosDiscovery"
private const val ZONE_PLAYER = "urn:schemas-upnp-org:device:ZonePlayer:1"
private const val M_SEARCH = "M-SEARCH * HTTP/1.1\r\nHOST: 239.255.255.250:1900\r\nMAN: \"ssdp:discover\"\r\nMX: 1\r\nST: urn:schemas-upnp-org:device:ZonePlayer:1\r\n\r\n"

// UDP can drop the search; two cost nothing.
private const val SEARCHES = 2
private const val RECEIVE_MS = 250
private const val ANSWER_BYTES = 2_048
private const val NANOS_PER_MILLI = 1_000_000L
private const val LOCK_TAG = "Culvery:SonosScan"

/** One SSDP answer: the address of the machine that sent it, and its text. */
internal data class SsdpAnswer(val from: String, val text: String)

/**
 * Each ZonePlayer's address from SSDP's answers, once each, in the order heard: its LOCATION's host and port, only when
 * that host is the answer's sender and [accept] allows it (6 §6), so an answer can't point Culvery elsewhere.
 */
internal fun ssdpAddresses(answers: List<SsdpAnswer>, accept: (String, Int) -> Boolean = ::isSpeakerAuthority): List<String> = answers
    .filter { answer -> answer.text.lineSequence().any { it.startsWith("ST:", ignoreCase = true) && ZONE_PLAYER in it } }
    .mapNotNull { answer ->
        answer.text.lineSequence().firstOrNull { it.startsWith("LOCATION:", ignoreCase = true) }
            ?.substringAfter(':')?.trim()?.let(::hostAndPort)
            ?.takeIf { (host, port) -> host == answer.from && accept(host, port) }
            ?.let { (host, port) -> "$host:$port" }
    }
    .distinct()

/** Held for one scan: without it some Wi-Fi drivers drop multicast traffic to save power (6 §3.2). */
internal fun multicastLock(context: Context): () -> AutoCloseable = {
    val lock = context.applicationContext.getSystemService(WifiManager::class.java)?.createMulticastLock(LOCK_TAG)?.apply {
        setReferenceCounted(false)
        acquire()
    }
    AutoCloseable { lock?.release() }
}

/**
 * Finds Sonos speakers on the home Wi-Fi (6 §4.1): SSDP's search for ZonePlayers, then about 5 seconds of answers. A
 * network that can't send finds nothing rather than failing; the caller says nothing answered.
 */
internal class SonosDiscovery(
    private val lock: () -> AutoCloseable,
    private val target: InetSocketAddress = InetSocketAddress(SSDP_HOST, SSDP_PORT),
    private val listenMillis: Long = SCAN_MS,
    private val accept: (String, Int) -> Boolean = ::isSpeakerAuthority,
) {
    suspend fun scan(): List<String> = withContext(Dispatchers.IO) {
        val answers = mutableListOf<SsdpAnswer>()
        try {
            lock().use {
                DatagramSocket().use { socket ->
                    socket.soTimeout = RECEIVE_MS
                    val search = M_SEARCH.toByteArray(Charsets.US_ASCII)
                    repeat(SEARCHES) { socket.send(DatagramPacket(search, search.size, target)) }
                    listen(socket, answers)
                }
            }
        } catch (e: IOException) {
            Log.w(TAG, "The speaker scan stopped (${e::class.simpleName})")
        }
        ssdpAddresses(answers, accept)
    }

    /** Adds each answer heard on [socket] to [answers] until [listenMillis] have passed. */
    private suspend fun listen(socket: DatagramSocket, answers: MutableList<SsdpAnswer>) {
        val until = System.nanoTime() + listenMillis * NANOS_PER_MILLI
        val buffer = ByteArray(ANSWER_BYTES)
        while (System.nanoTime() < until) {
            currentCoroutineContext().ensureActive()
            val packet = DatagramPacket(buffer, buffer.size)
            try {
                socket.receive(packet)
            } catch (e: SocketTimeoutException) {
                continue
            }
            answers += SsdpAnswer(packet.address.hostAddress.orEmpty(), String(packet.data, packet.offset, packet.length, Charsets.US_ASCII))
        }
    }
}
