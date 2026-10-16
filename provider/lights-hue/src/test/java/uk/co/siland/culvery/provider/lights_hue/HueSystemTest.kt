package uk.co.siland.culvery.provider.lights_hue

import com.google.common.truth.Truth.assertThat
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.runInterruptible
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.ConscryptMode
import uk.co.siland.culvery.capability.lights.LightRoom
import uk.co.siland.culvery.capability.lights.LightScene
import uk.co.siland.culvery.capability.lights.LightsLink
import uk.co.siland.culvery.capability.lights.LightsRefusedException
import uk.co.siland.culvery.core.plugin.WallClock
import uk.co.siland.culvery.provider.lights_hue.HueFixtures.BRIDGE_ID
import uk.co.siland.culvery.provider.lights_hue.HueFixtures.BRIGHT
import uk.co.siland.culvery.provider.lights_hue.HueFixtures.GARDEN
import uk.co.siland.culvery.provider.lights_hue.HueFixtures.KEY
import uk.co.siland.culvery.provider.lights_hue.HueFixtures.KITCHEN
import uk.co.siland.culvery.provider.lights_hue.HueFixtures.KITCHEN_GROUP
import uk.co.siland.culvery.provider.lights_hue.HueFixtures.LOUNGE
import uk.co.siland.culvery.provider.lights_hue.HueFixtures.LOUNGE_GROUP
import uk.co.siland.culvery.provider.lights_hue.HueFixtures.RELAX

private const val RESOURCE = "/clip/v2/resource"

/** 7 §4.2, §4.3, §6: the rooms from a full read and the stream, and the room commands. */
@RunWith(RobolectricTestRunner::class)
@ConscryptMode(ConscryptMode.Mode.OFF)
class HueSystemTest {
    private val bridges = mutableListOf<FakeBridge>()
    private val bridge = fake()

    private fun fake() = FakeBridge().also { bridges += it }

    private fun TestScope.system(address: String = bridge.address) = HueSystem(
        HueClient(
            HueTrust(TestCerts.testCa()),
            HuePacer(now = { testScheduler.currentTime }),
            accept = onThisMachine,
            capture = { _, _ -> },
        ),
        search = { emptyList() },
        online = { true },
        link = LightsLink(BRIDGE_ID, address, KEY),
        onMoved = {},
        clock = WallClock { testScheduler.currentTime },
    )

    @After
    fun tearDown() = bridges.forEach { it.close() }

    private fun HueSystem.room(id: String): LightRoom = rooms.value.single { it.id == id }

    private fun FakeBridge.puts() = received.filter { it.method == "PUT" }.map { it.path to it.body }

    private fun FakeBridge.reads() = received.filter { it.method == "GET" && it.path.startsWith(RESOURCE) }.map { it.path.removePrefix("$RESOURCE/") }

    /** Fails if the rooms leave Hue's order, a brightness or scene is misread, or a room with no dimming light can dim (7 §4.2). */
    @Test
    fun aFullReadBuildsTheRoomsInHuesOrder() = runTest {
        val s = system()
        s.readAll()
        assertThat(s.rooms.value).containsExactly(
            LightRoom(LOUNGE, "Lounge", on = true, brightness = 70, listOf(LightScene(RELAX, "Relax"), LightScene(BRIGHT, "Bright")), RELAX, dimmable = true),
            LightRoom(KITCHEN, "Kitchen", on = false, brightness = 0, emptyList(), null, dimmable = true),
            LightRoom(GARDEN, "Garden", on = true, brightness = 0, emptyList(), null, dimmable = false),
        ).inOrder()
        assertThat(bridge.reads()).containsExactly("room", "grouped_light", "light", "scene", "bridge")
        assertThat(s.problem.value).isNull()
    }

    /** Fails if a stream event isn't applied to its room, or is applied by reading everything again (7 §4.2). */
    @Test
    fun theStreamUpdatesARoomWithinTheEvent() = runTest {
        val s = system()
        s.readAll()
        bridge.received.clear()
        bridge.events = """
            data: [{"data":[{"id":"$LOUNGE_GROUP","dimming":{"brightness":30.2},"type":"grouped_light"},{"id":"$KITCHEN_GROUP","on":{"on":true},"type":"grouped_light"},{"id":"$BRIGHT","status":{"active":"static"},"type":"scene"}],"type":"update"}]
        """.trimIndent()
        assertThat(s.stream().toList()).containsExactly(Unit)
        assertThat(s.room(LOUNGE).brightness).isEqualTo(30)
        assertThat(s.room(LOUNGE).on).isTrue()
        assertThat(s.room(LOUNGE).activeSceneId).isEqualTo(BRIGHT)
        assertThat(s.room(KITCHEN).on).isTrue()
        assertThat(bridge.reads()).isEmpty()
    }

    /** Fails if an added or deleted resource leaves the rooms as they were, without a full read. */
    @Test
    fun anAddOrDeleteReadsEverythingAgain() = runTest {
        val s = system()
        s.readAll()
        bridge.received.clear()
        bridge.resources["room"] = """
            {"errors":[],"data":[{"id":"$LOUNGE","children":[],"services":[{"rid":"$LOUNGE_GROUP","rtype":"grouped_light"}],
             "metadata":{"name":"Lounge"},"type":"room"}]}
        """.trimIndent()
        bridge.events = """data: [{"data":[{"id":"$GARDEN","type":"room"}],"type":"delete"}]"""
        s.stream().toList()
        assertThat(s.rooms.value.map { it.id }).containsExactly(LOUNGE)
        assertThat(bridge.reads()).contains("room")

        bridge.received.clear()
        bridge.events = """data: [{"data":[{"id":"$GARDEN","type":"room"}],"type":"add"}]"""
        s.stream().toList()
        assertThat(bridge.reads()).contains("room")
    }

    /** Fails if a command waits for the bridge's answer before the room shows it (7 §4.3). */
    @Test
    fun aCommandShowsAtOnce() = runTest {
        val s = system()
        s.readAll()
        val hold = CountDownLatch(1).also { bridge.holdCommands = it }
        val sent = async { s.setOn(s.room(KITCHEN), true) }
        assertThat(runInterruptible(Dispatchers.IO) { bridge.held.tryAcquire(5, TimeUnit.SECONDS) }).isTrue()
        assertThat(s.room(KITCHEN).on).isTrue()
        hold.countDown()
        sent.await()
        assertThat(bridge.puts()).containsExactly("$RESOURCE/grouped_light/$KITCHEN_GROUP" to """{"on":{"on":true}}""")
        assertThat(s.room(KITCHEN).on).isTrue()
    }

    /** Fails if brightness is sent as dimming alone, which leaves an off room off (7 §4.3). */
    @Test
    fun brightnessOnARoomThatIsOffTurnsItOnAtThatBrightness() = runTest {
        val s = system()
        s.readAll()
        s.setBrightness(s.room(KITCHEN), 40)
        assertThat(bridge.puts()).containsExactly(
            "$RESOURCE/grouped_light/$KITCHEN_GROUP" to """{"on":{"on":true},"dimming":{"brightness":40}}""",
        )
        assertThat(s.room(KITCHEN).on).isTrue()
        assertThat(s.room(KITCHEN).brightness).isEqualTo(40)
    }

    /** Fails if a scene isn't recalled with `action: active`, or the room still shows the scene it had. */
    @Test
    fun aSceneIsRecalledAndMarkedActive() = runTest {
        val s = system()
        s.readAll()
        s.recallScene(s.room(LOUNGE), LightScene(BRIGHT, "Bright"))
        assertThat(bridge.puts()).containsExactly("$RESOURCE/scene/$BRIGHT" to """{"recall":{"action":"active"}}""")
        assertThat(s.room(LOUNGE).activeSceneId).isEqualTo(BRIGHT)
        assertThat(s.room(LOUNGE).on).isTrue()
    }

    /** Fails if a refused command passes as done, or the room keeps showing what the bridge didn't do (7 §6). */
    @Test
    fun aRefusedCommandIsRefusedAndTheRoomReadAgain() = runTest {
        val s = system()
        s.readAll()
        bridge.received.clear()
        bridge.refusingCommands = true
        val failure = runCatching { s.setOn(s.room(KITCHEN), true) }.exceptionOrNull()
        assertThat(failure).isInstanceOf(LightsRefusedException::class.java)
        assertThat(s.room(KITCHEN).on).isFalse()
        assertThat(bridge.received.first().method).isEqualTo("PUT")
        assertThat(bridge.reads()).containsAtLeast("room", "grouped_light")
    }

    /** Fails if a command replaced while waiting its turn throws, or is sent after all. */
    @Test
    fun aSupersededCommandIsNotAnError() = runTest {
        val s = system()
        s.readAll()
        s.setOn(s.room(KITCHEN), true)
        val first = async { s.setOn(s.room(LOUNGE), false) }
        val second = async { s.setOn(s.room(LOUNGE), true) }
        first.await()
        second.await()
        assertThat(bridge.puts().filter { it.first.endsWith(LOUNGE_GROUP) }).containsExactly(
            "$RESOURCE/grouped_light/$LOUNGE_GROUP" to """{"on":{"on":true}}""",
        )
        assertThat(s.room(LOUNGE).on).isTrue()
    }

    /** Fails if a new address is ignored, so reads and commands still go to the old one. */
    @Test
    fun useAddressSendsThereFromNowOn() = runTest {
        val s = system()
        s.readAll()
        val moved = fake()
        val before = bridge.received.size
        s.useAddress(moved.address)
        s.setOn(s.room(LOUNGE), false)
        s.readAll()
        assertThat(bridge.received).hasSize(before)
        assertThat(moved.puts()).containsExactly("$RESOURCE/grouped_light/$LOUNGE_GROUP" to """{"on":{"on":false}}""")
        assertThat(moved.reads()).contains("room")
    }
}
