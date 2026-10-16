package uk.co.siland.culvery.provider.lights_hue

import com.google.common.truth.Truth.assertThat
import java.io.InterruptedIOException
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.ConscryptMode
import uk.co.siland.culvery.provider.lights_hue.HueFixtures.BRIDGE_ID
import uk.co.siland.culvery.provider.lights_hue.HueFixtures.KEY

/** 7 §3.2, §4.2, §4.3, §6: each read and command's path, body and key; pairing; the name checks; failures; the stream. */
@RunWith(RobolectricTestRunner::class)
@ConscryptMode(ConscryptMode.Mode.OFF)
class HueClientTest {
    private val bridges = mutableListOf<FakeBridge>()
    private val bridge = fake()
    private val captured = mutableListOf<Pair<String, String>>()
    private val client = client()
    private val call = BridgeCall(bridge.address, BRIDGE_ID, KEY)

    private fun fake(keystore: String = "bridge") = FakeBridge(keystore).also { bridges += it }

    private fun client(
        accept: (String, Int) -> Boolean = onThisMachine,
        pacer: HuePacer = HuePacer(now = System::currentTimeMillis, groupGapMs = 0, lightGapMs = 0),
    ) = HueClient(
        HueTrust(TestCerts.testCa()),
        pacer,
        accept = accept,
        timeoutMs = TIMEOUT_MS,
        capture = { what, answer -> captured += what to answer },
    )

    @After
    fun tearDown() = bridges.forEach { it.close() }

    /** Fails if a read goes to the wrong resource, leaves out the key, or isn't parsed as its resource. */
    @Test
    fun eachReadIsItsResourcesPathWithTheKey() = runTest {
        assertThat(client.rooms(call).map { it.name }).containsExactly("Lounge", "Kitchen", "Garden").inOrder()
        assertThat(client.groupedLights(call)).hasSize(4)
        assertThat(client.lights(call)).hasSize(5)
        assertThat(client.scenes(call).map { it.name }).containsExactly("Relax", "Bright")
        assertThat(client.behaviors(call)).hasSize(6)
        assertThat(client.bridgeId(call)).isEqualTo(BRIDGE_ID)
        assertThat(bridge.received.map { it.method to it.path }).containsExactly(
            "GET" to "/clip/v2/resource/room",
            "GET" to "/clip/v2/resource/grouped_light",
            "GET" to "/clip/v2/resource/light",
            "GET" to "/clip/v2/resource/scene",
            "GET" to "/clip/v2/resource/behavior_instance",
            "GET" to "/clip/v2/resource/bridge",
        ).inOrder()
        assertThat(bridge.received.map { it.key }.distinct()).containsExactly(KEY)
    }

    /** Fails if a command goes to the wrong resource or id, sends the wrong body, or leaves out the key (7 §4.3, §4.5). */
    @Test
    fun eachCommandsPathAndBody() = runTest {
        assertThat(client.setOn(call, HueFixtures.LOUNGE_GROUP, true)).isTrue()
        assertThat(client.setBrightness(call, HueFixtures.LOUNGE_GROUP, 40, turnOn = true)).isTrue()
        assertThat(client.setBrightness(call, HueFixtures.KITCHEN_GROUP, 55, turnOn = false)).isTrue()
        assertThat(client.recall(call, HueFixtures.RELAX)).isTrue()
        assertThat(client.setLightOn(call, "light-1", false)).isTrue()
        assertThat(client.setEnabled(call, HueFixtures.SCHEDULE_OFF, true)).isTrue()
        assertThat(client.trigger(call, HueFixtures.PM_STOPPED, start = true)).isTrue()
        assertThat(client.trigger(call, HueFixtures.PM_STARTED, start = false)).isTrue()
        val resource = "/clip/v2/resource"
        assertThat(bridge.received.map { Triple(it.method, it.path, it.body) }).containsExactly(
            Triple("PUT", "$resource/grouped_light/${HueFixtures.LOUNGE_GROUP}", """{"on":{"on":true}}"""),
            Triple("PUT", "$resource/grouped_light/${HueFixtures.LOUNGE_GROUP}", """{"on":{"on":true},"dimming":{"brightness":40}}"""),
            Triple("PUT", "$resource/grouped_light/${HueFixtures.KITCHEN_GROUP}", """{"dimming":{"brightness":55}}"""),
            Triple("PUT", "$resource/scene/${HueFixtures.RELAX}", """{"recall":{"action":"active"}}"""),
            Triple("PUT", "$resource/light/light-1", """{"on":{"on":false}}"""),
            Triple("PUT", "$resource/behavior_instance/${HueFixtures.SCHEDULE_OFF}", """{"enabled":true}"""),
            Triple("PUT", "$resource/behavior_instance/${HueFixtures.PM_STOPPED}", """{"trigger":{"start":true}}"""),
            Triple("PUT", "$resource/behavior_instance/${HueFixtures.PM_STARTED}", """{"trigger":{"stop":true}}"""),
        ).inOrder()
        assertThat(bridge.received.map { it.key }.distinct()).containsExactly(KEY)
    }

    /** Fails if pairing sends a key, the wrong body or path, or misreads the bridge's answer (7 §3.2). */
    @Test
    fun pairingPostsTheDeviceType() = runTest {
        assertThat(client.pair(bridge.address, BRIDGE_ID)).isEqualTo(PairAnswer.Paired(KEY))
        bridge.buttonPressed = false
        assertThat(client.pair(bridge.address, BRIDGE_ID)).isEqualTo(PairAnswer.ButtonNotPressed)
        val sent = bridge.received.first()
        assertThat(sent.method to sent.path).isEqualTo("POST" to "/api")
        assertThat(sent.body).isEqualTo("""{"devicetype":"culvery#tablet","generateclientkey":true}""")
        assertThat(bridge.received.map { it.key }).containsExactly(null, null)
    }

    /** Fails if config sends a key, or trusts the answer's id without the certificate's name or the expected id agreeing. */
    @Test
    fun configNeedsNoKeyAndChecksTheName() = runTest {
        assertThat(client.config(bridge.address)).isEqualTo(HueConfig(BRIDGE_ID))
        assertThat(client.config(bridge.address, expectedId = BRIDGE_ID.uppercase())).isEqualTo(HueConfig(BRIDGE_ID))
        assertThat(bridge.received.map { it.method to it.path }.distinct()).containsExactly("GET" to "/api/config")
        assertThat(bridge.received.map { it.key }.distinct()).containsExactly(null)
        throws<WrongBridge> { client.config(bridge.address, expectedId = "001788fffe00c3d4") }
        // Its certificate names 001788fffe00c3d4, its answer says BRIDGE_ID.
        val other = fake("other")
        throws<WrongBridge> { client.config(other.address) }
        throws<WrongBridge> { client.config(other.address, expectedId = BRIDGE_ID) }
    }

    /** Fails if a 403 is read as anything but a removed pairing (7 §6). */
    @Test
    fun aForbiddenAnswerIsUnauthorised() = runTest {
        bridge.forbidden = true
        throws<HueUnauthorised> { client.rooms(call) }
        throws<HueUnauthorised> { client.setOn(call, HueFixtures.LOUNGE_GROUP, false) }
    }

    /** Fails if v1's unauthorised error, which comes with a 200, is taken as an answer (7 §6). */
    @Test
    fun aV1UnauthorisedErrorIsUnauthorisedToo() = runTest {
        bridge.v1Unauthorised = true
        throws<HueUnauthorised> { client.scenes(call) }
        throws<HueUnauthorised> { client.recall(call, HueFixtures.RELAX) }
    }

    /** Fails if an error status is read as success, or a refusal loses its code (7 §6). */
    @Test
    fun anErrorAnswerIsRefused() = runTest {
        bridge.failing = true
        assertThat(throws<HueRefused> { client.setOn(call, HueFixtures.LOUNGE_GROUP, true) }.code).isEqualTo(500)
        assertThat(throws<HueRefused> { client.rooms(call) }.code).isEqualTo(500)
    }

    /** Fails if a 2xx answer listing an error counts as done, so a room shows on when the bridge did nothing (7 §6). */
    @Test
    fun anAnswerListingAnErrorIsRefused() = runTest {
        bridge.answersWithErrors = true
        assertThat(throws<HueRefused> { client.setOn(call, HueFixtures.LOUNGE_GROUP, true) }.code).isEqualTo(200)
        assertThat(throws<HueRefused> { client.setBrightness(call, HueFixtures.LOUNGE_GROUP, 40, turnOn = true) }.code).isEqualTo(200)
    }

    /** Fails if switching an automation and triggering it share one pace target, so one replaces the other. */
    @Test
    fun enablingAndTriggeringOneAutomationAreBothSent(): Unit = runBlocking {
        val paced = client(pacer = HuePacer(now = System::currentTimeMillis, groupGapMs = 0, lightGapMs = 300))
        paced.setLightOn(call, "light-1", true)
        val enabled = async { paced.setEnabled(call, HueFixtures.PM_STOPPED, true) }
        val triggered = async { paced.trigger(call, HueFixtures.PM_STOPPED, start = true) }
        assertThat(enabled.await()).isTrue()
        assertThat(triggered.await()).isTrue()
        assertThat(bridge.received.map { it.body }).containsAtLeast("""{"enabled":true}""", """{"trigger":{"start":true}}""")
    }

    /** Fails if idle connections aren't kept, which races HTTP/2, or are kept long enough to outlive the bridge's idle timeout. */
    @Test
    fun idleConnectionsAreKeptBriefly() = runTest {
        assertThat(IDLE_CONNECTIONS).isEqualTo(2)
        assertThat(KEEP_ALIVE_MS).isEqualTo(5_000L)
        client.rooms(call)
        assertThat(client.pool.idleConnectionCount()).isEqualTo(1)
    }

    /** Fails if the injected timeout isn't used, so a dead bridge hangs a call instead of failing as I/O (7 §6). */
    @Test
    fun aSlowAnswerTimesOut() = runTest {
        bridge.delayMs = 50
        assertThat(client.rooms(call)).hasSize(3)
        assertThat(client.setOn(call, HueFixtures.LOUNGE_GROUP, true)).isTrue()
        bridge.delayMs = 400
        throws<InterruptedIOException> { client.rooms(call) }
        throws<InterruptedIOException> { client.setOn(call, HueFixtures.LOUNGE_GROUP, true) }
    }

    /** Pin: 7 §6 "Reads or commands time out (3 s)". */
    @Test
    fun theTimeoutIsThreeSeconds() {
        assertThat(HUE_TIMEOUT_MS).isEqualTo(3_000L)
    }

    /** Fails if a paired call or pairing trusts a certificate on the Hue CA that names another bridge, or sends first. */
    @Test
    fun aCertificateNamingAnotherBridgeIsWrongBridge() = runTest {
        val other = fake("other")
        val there = BridgeCall(other.address, BRIDGE_ID, KEY)
        throws<WrongBridge> { client.rooms(there) }
        throws<WrongBridge> { client.setOn(there, HueFixtures.LOUNGE_GROUP, true) }
        throws<WrongBridge> { client.pair(other.address, BRIDGE_ID) }
        throws<WrongBridge> { client.events(there).toList() }
        assertThat(other.requestCount).isEqualTo(0)
    }

    /** Fails if any call reaches an address that isn't port 443 on the home network (Review Focus 5, 7 §6). */
    @Test
    fun theKeyIsNeverSentToAnAddressOffTheHomeNetwork() = runTest {
        val strict = client(accept = ::isBridgeAuthority)
        listOf("8.8.8.8", "hue.local", "192.168.1.30:8443", bridge.address).forEach { address ->
            val there = BridgeCall(address, BRIDGE_ID, KEY)
            throws<NotABridge> { strict.rooms(there) }
            throws<NotABridge> { strict.setOn(there, HueFixtures.LOUNGE_GROUP, true) }
            throws<NotABridge> { strict.events(there).toList() }
            throws<NotABridge> { strict.pair(address, BRIDGE_ID) }
            throws<NotABridge> { strict.config(address) }
        }
        assertThat(bridge.requestCount).isEqualTo(0)
    }

    /** Fails if a redirect is followed, by a call or the stream: it could lead off the home network. */
    @Test
    fun noRedirectIsFollowed() = runTest {
        bridge.redirectTo = "/clip/v2/resource/scene"
        assertThat(throws<HueRefused> { client.rooms(call) }.code).isEqualTo(302)
        assertThat(throws<HueRefused> { client.events(call).toList() }.code).isEqualTo(302)
        assertThat(bridge.received.map { it.path }).containsExactly("/clip/v2/resource/room", "/eventstream/clip/v2").inOrder()
    }

    /** Fails if the stream asks the wrong path, without the key or SSE's Accept, keeps a comment or id line, or never ends. */
    @Test
    fun theStreamGivesEachEventAndEndsWhenTheBridgeCloses() = runTest {
        val events = client.events(call).toList()
        assertThat(events).containsExactly(
            listOf(HueEvent.GroupedLight(HueFixtures.LOUNGE_GROUP, false, null), HueEvent.Scene(HueFixtures.RELAX, true)),
            listOf(HueEvent.Reread),
        ).inOrder()
        val asked = bridge.received.single()
        assertThat(asked.method to asked.path).isEqualTo("GET" to "/eventstream/clip/v2")
        assertThat(asked.accept).isEqualTo("text/event-stream")
        assertThat(asked.key).isEqualTo(KEY)
    }

    /** Fails if the stream keeps the read or call timeout, so a quiet bridge drops it (7 §4.2). */
    @Test
    fun theStreamWaitsLongerThanTheReadTimeout() = runTest {
        bridge.streamDelayMs = 400
        assertThat(client.events(call).first()).contains(HueEvent.Scene(HueFixtures.RELAX, true))
    }

    /** Fails if leaving the stream waits for the bridge to send or close, so the stream stays open behind (7 §4.2). */
    @Test
    fun leavingTheStreamClosesItAtOnce() = runTest {
        bridge.streamHeld = true
        var arrived = 0L
        assertThat(client.events(call).onEach { arrived = System.nanoTime() }.first()).hasSize(2)
        val tookMs = (System.nanoTime() - arrived) / 1_000_000
        assertThat(tookMs).isLessThan(FakeBridge.STREAM_HOLD_MS / 2)
    }

    /** Fails if an answer isn't passed to the capture with what was asked (7 §8.2). */
    @Test
    fun theCaptureSeesEachAnswer() = runTest {
        client.rooms(call)
        client.config(bridge.address)
        assertThat(captured.map { it.first }).containsExactly("room", "config").inOrder()
        assertThat(captured.first().second).contains("Lounge")
    }

    /** Fails if the key is in a call's text, where a log or a crash report could show it. */
    @Test
    fun aCallsTextLeavesOutTheKey() {
        assertThat(call.toString()).doesNotContain(KEY)
        assertThat(call.toString()).doesNotContain(bridge.address)
    }

    private companion object {
        const val TIMEOUT_MS = 200L
    }
}
