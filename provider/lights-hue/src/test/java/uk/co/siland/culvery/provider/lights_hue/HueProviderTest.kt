package uk.co.siland.culvery.provider.lights_hue

import com.google.common.truth.Truth.assertThat
import java.io.File
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.ConscryptMode
import uk.co.siland.culvery.capability.lights.BridgeFound
import uk.co.siland.culvery.capability.lights.LightsLink
import uk.co.siland.culvery.capability.lights.LightsProblem
import uk.co.siland.culvery.capability.lights.LightsUnreachableException
import uk.co.siland.culvery.core.plugin.WallClock
import uk.co.siland.culvery.provider.lights_hue.HueFixtures.BRIDGE_ID
import uk.co.siland.culvery.provider.lights_hue.HueFixtures.KEY

private const val ANOTHER_ID = "001788fffe00ffff"

/** 7 §3.2, §4.1: finding, checking and pairing the bridge, only once its certificate and its config agree on its id. */
@RunWith(RobolectricTestRunner::class)
@ConscryptMode(ConscryptMode.Mode.OFF)
class HueProviderTest {
    private val bridges = mutableListOf<FakeBridge>()
    private val bridge = fake()
    private var searches = 0

    private fun fake(keystore: String = "bridge") = FakeBridge(keystore).also { bridges += it }

    private fun provider(found: List<BridgeFound> = emptyList(), online: Boolean = true) = HueProvider(
        HueClient(HueTrust(TestCerts.testCa()), HuePacer(now = { 0L }), accept = onThisMachine, capture = { _, _ -> }),
        search = {
            searches++
            found
        },
        online = { online },
        clock = WallClock { 0L },
    )

    private fun FakeBridge.posts() = received.filter { it.method == "POST" }

    @After
    fun tearDown() = bridges.forEach { it.close() }

    /** Fails if find takes a later bridge, or doesn't confirm the first one's config before giving it. */
    @Test
    fun findIsTheSearchsFirstBridge() = runBlocking {
        val second = fake()
        val provider = provider(listOf(BridgeFound(BRIDGE_ID, bridge.address), BridgeFound(BRIDGE_ID, second.address)))

        assertThat(provider.find()).isEqualTo(BridgeFound(BRIDGE_ID, bridge.address))
        assertThat(bridge.received.map { it.path }).containsExactly("/api/config")
        assertThat(second.requestCount).isEqualTo(0)
    }

    /** Fails if check sends a key, asks anything but the config, or answers without the config's id. */
    @Test
    fun checkAsksTheConfigWithoutAKey() = runBlocking {
        assertThat(provider().check(bridge.address)).isEqualTo(BridgeFound(BRIDGE_ID, bridge.address))

        val asked = bridge.received.single()
        assertThat(asked.method).isEqualTo("GET")
        assertThat(asked.path).isEqualTo("/api/config")
        assertThat(asked.key).isNull()
    }

    /** Fails if a pairing that succeeds doesn't give the bridge's id, its address and the key it answered. */
    @Test
    fun pairingSavesTheKey() = runBlocking {
        val link = provider().pair(BridgeFound(BRIDGE_ID, bridge.address))

        assertThat(link).isEqualTo(LightsLink(BRIDGE_ID, bridge.address, KEY))
        assertThat(link?.key).isEqualTo(KEY)
        assertThat(bridge.posts().single().body).contains("culvery#tablet")
    }

    /** Fails if the key is asked of a bridge whose certificate names another id than its config. */
    @Test
    fun aConfigNamingAnotherBridgeIsNotPaired() = runBlocking {
        val other = fake("other")

        assertThat(provider().pair(BridgeFound(BRIDGE_ID, other.address))).isNull()
        assertThat(other.posts()).isEmpty()
    }

    /** Fails if a bridge whose certificate differs from the id mDNS reported is found or asked for a key. */
    @Test
    fun aSearchIdThatDiffersFromTheCertificateIsNotPaired() = runBlocking {
        val provider = provider(listOf(BridgeFound(ANOTHER_ID, bridge.address)))

        assertThat(provider.find()).isNull()
        assertThat(provider.pair(BridgeFound(ANOTHER_ID, bridge.address))).isNull()
        assertThat(bridge.posts()).isEmpty()
    }

    /** Fails if the bridge's "link button not pressed" is anything but null. */
    @Test
    fun theButtonNotPressedIsNull() = runBlocking {
        bridge.buttonPressed = false

        assertThat(provider().pair(BridgeFound(BRIDGE_ID, bridge.address))).isNull()
        assertThat(bridge.posts()).hasSize(1)
    }

    /** Fails if find, check or pair contact anything offline, or don't say the tablet is offline. */
    @Test
    fun offlineSaysSo() {
        val provider = provider(listOf(BridgeFound(BRIDGE_ID, bridge.address)), online = false)

        assertThat(throws<LightsUnreachableException> { provider.find() }.problem).isEqualTo(LightsProblem.OFFLINE)
        assertThat(throws<LightsUnreachableException> { provider.check(bridge.address) }.problem).isEqualTo(LightsProblem.OFFLINE)
        assertThat(throws<LightsUnreachableException> { provider.pair(BridgeFound(BRIDGE_ID, bridge.address)) }.problem)
            .isEqualTo(LightsProblem.OFFLINE)
        assertThat(searches).isEqualTo(0)
        assertThat(bridge.requestCount).isEqualTo(0)
    }

    /** Fails if any shipped source names Hue's cloud discovery (7 §3.2). Pin. */
    @Test
    fun noCloudDiscovery() {
        // Unit tests run in the module's directory.
        val repo = File("../..").canonicalFile
        val sources = repo.walkTopDown()
            .onEnter { it.name != "build" && !it.name.startsWith(".") }
            .filter { it.isFile && it.extension in setOf("kt", "xml") && "${File.separator}src${File.separator}main${File.separator}" in it.path }
            .toList()

        assertThat(sources.map { it.name }).contains("HueClient.kt")
        assertThat(sources.filter { "meethue.com" in it.readText(Charsets.UTF_8) }).isEmpty()
    }
}
