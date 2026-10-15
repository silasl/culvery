package uk.co.siland.culvery.provider.music_sonos

import androidx.test.ext.junit.runners.AndroidJUnit4
import com.google.common.truth.Truth.assertThat
import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.InetAddress
import java.net.InetSocketAddress
import kotlin.concurrent.thread
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.withContext
import org.junit.Test
import org.junit.runner.RunWith

/** 6 §3.2: the search goes out under the multicast lock, and a speaker's answer is heard; a loopback socket plays the speaker. */
@RunWith(AndroidJUnit4::class)
class SonosDiscoveryTest {
    @Test
    fun aScanSearchesUnderTheLockAndHearsTheSpeaker() = runTest {
        DatagramSocket(0, InetAddress.getLoopbackAddress()).use { speaker ->
            var search = ""
            val answering = thread {
                val packet = DatagramPacket(ByteArray(2_048), 2_048)
                speaker.receive(packet)
                search = String(packet.data, 0, packet.length, Charsets.US_ASCII)
                val answer = ("HTTP/1.1 200 OK\r\nLOCATION: http://127.0.0.1:1400/xml/device_description.xml\r\n" +
                    "ST: urn:schemas-upnp-org:device:ZonePlayer:1\r\n\r\n").toByteArray(Charsets.US_ASCII)
                speaker.send(DatagramPacket(answer, answer.size, packet.socketAddress))
            }
            val events = mutableListOf<String>()
            val discovery = SonosDiscovery(
                lock = {
                    events += "lock"
                    AutoCloseable { events += "unlock" }
                },
                target = InetSocketAddress(InetAddress.getLoopbackAddress(), speaker.localPort),
                listenMillis = 1_000,
                accept = onThisMachine,
            )
            val found = withContext(Dispatchers.IO) { discovery.scan() }
            answering.join()
            assertThat(found).containsExactly("127.0.0.1:1400")
            assertThat(search).startsWith("M-SEARCH * HTTP/1.1\r\n")
            assertThat(search).contains("MAN: \"ssdp:discover\"\r\n")
            assertThat(search).contains("ST: urn:schemas-upnp-org:device:ZonePlayer:1\r\n")
            assertThat(events).containsExactly("lock", "unlock").inOrder()
        }
    }
}
