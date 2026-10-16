package uk.co.siland.culvery.provider.lights_hue

import android.net.nsd.NsdServiceInfo
import com.google.common.truth.Truth.assertThat
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import uk.co.siland.culvery.capability.lights.BridgeFound

/** 7 §3.2: each bridge mDNS reports, once, from its own record, only on the home network; resolves one at a time. */
@RunWith(RobolectricTestRunner::class)
class HueDiscoveryTest {
    private class Asked(val service: NsdServiceInfo, val onDone: (MdnsAnswer?) -> Unit, val onBusy: () -> Unit)

    private val asked = mutableListOf<Asked>()
    private val queue = ResolveQueue { service, onDone, onBusy -> asked += Asked(service, onDone, onBusy) }

    private fun service(name: String) = NsdServiceInfo().apply { serviceName = name }

    private fun names() = asked.map { it.service.serviceName }

    /** Fails if a bridge heard twice is listed twice, or its id is taken from anywhere but its `bridgeid` record. */
    @Test
    fun eachBridgeOnceWithItsIdFromTheRecord() {
        val answers = listOf(
            MdnsAnswer("001788FFFE00A1B2", "192.168.1.30"),
            MdnsAnswer("001788fffe00a1b2", "192.168.1.30"),
            MdnsAnswer("001788fffe00c3d4", "192.168.1.31"),
        )

        assertThat(bridgesFrom(answers)).containsExactly(
            BridgeFound("001788fffe00a1b2", "192.168.1.30"),
            BridgeFound("001788fffe00c3d4", "192.168.1.31"),
        ).inOrder()
    }

    /** Fails if an answer off the home network, with no address, or with no `bridgeid` record is kept. */
    @Test
    fun anAnswerOffTheHomeNetworkIsDropped() {
        val answers = listOf(
            MdnsAnswer("001788fffe00a1b2", "8.8.8.8"),
            MdnsAnswer("001788fffe00a1b2", "fe80::1"),
            MdnsAnswer("001788fffe00a1b2", null),
            MdnsAnswer(null, "192.168.1.30"),
        )

        assertThat(bridgesFrom(answers)).isEmpty()
    }

    /** Fails if a second resolve starts before the first has finished. */
    @Test
    fun resolvesRunOneAtATime() {
        queue.add(service("a"))
        queue.add(service("b"))
        queue.add(service("c"))
        assertThat(names()).containsExactly("a")

        asked[0].onDone(MdnsAnswer("001788fffe00a1b2", "192.168.1.30"))
        assertThat(names()).containsExactly("a", "b").inOrder()

        asked[1].onDone(null)
        asked[2].onDone(MdnsAnswer("001788fffe00c3d4", "192.168.1.31"))
        assertThat(names()).containsExactly("a", "b", "c").inOrder()
        assertThat(queue.answers()).containsExactly(
            MdnsAnswer("001788fffe00a1b2", "192.168.1.30"),
            MdnsAnswer("001788fffe00c3d4", "192.168.1.31"),
        ).inOrder()
    }

    /** Fails if a resolve refused as busy is dropped, or asked again before the services waiting behind it. */
    @Test
    fun aBusyResolveIsQueuedAgain() {
        queue.add(service("a"))
        queue.add(service("b"))

        asked[0].onBusy()
        asked[1].onDone(null)
        assertThat(names()).containsExactly("a", "b", "a").inOrder()

        asked[2].onDone(MdnsAnswer("001788fffe00a1b2", "192.168.1.30"))
        assertThat(queue.answers()).containsExactly(MdnsAnswer("001788fffe00a1b2", "192.168.1.30"))
    }
}
