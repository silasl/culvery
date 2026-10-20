package uk.co.siland.culvery.provider.lights_hue

import com.google.common.truth.Truth.assertThat
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.launch
import kotlinx.coroutines.runInterruptible
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.ConscryptMode
import uk.co.siland.culvery.capability.lights.AllOffResult
import uk.co.siland.culvery.capability.lights.Automation
import uk.co.siland.culvery.capability.lights.AutomationResult
import uk.co.siland.culvery.capability.lights.BridgeFound
import uk.co.siland.culvery.capability.lights.LightRoom
import uk.co.siland.culvery.capability.lights.LightScene
import uk.co.siland.culvery.capability.lights.LightsLink
import uk.co.siland.culvery.capability.lights.LightsProblem
import uk.co.siland.culvery.capability.lights.LightsRefusedException
import uk.co.siland.culvery.capability.lights.LightsUnreachableException
import uk.co.siland.culvery.core.plugin.WallClock
import uk.co.siland.culvery.provider.lights_hue.HueFixtures.BEHAVIORS
import uk.co.siland.culvery.provider.lights_hue.HueFixtures.BRIDGE_ID
import uk.co.siland.culvery.provider.lights_hue.HueFixtures.BRIGHT
import uk.co.siland.culvery.provider.lights_hue.HueFixtures.GARDEN
import uk.co.siland.culvery.provider.lights_hue.HueFixtures.GARDEN_GROUP
import uk.co.siland.culvery.provider.lights_hue.HueFixtures.HOME_GROUP
import uk.co.siland.culvery.provider.lights_hue.HueFixtures.KEY
import uk.co.siland.culvery.provider.lights_hue.HueFixtures.KITCHEN
import uk.co.siland.culvery.provider.lights_hue.HueFixtures.KITCHEN_GROUP
import uk.co.siland.culvery.provider.lights_hue.HueFixtures.LOUNGE
import uk.co.siland.culvery.provider.lights_hue.HueFixtures.LOUNGE_GROUP
import uk.co.siland.culvery.provider.lights_hue.HueFixtures.PM_STARTED
import uk.co.siland.culvery.provider.lights_hue.HueFixtures.PM_STOPPED
import uk.co.siland.culvery.provider.lights_hue.HueFixtures.RELAX
import uk.co.siland.culvery.provider.lights_hue.HueFixtures.SCHEDULE_OFF
import uk.co.siland.culvery.provider.lights_hue.HueFixtures.SCHEDULE_ON
import uk.co.siland.culvery.provider.lights_hue.HueFixtures.SCHEDULE_SCRIPT
import uk.co.siland.culvery.provider.lights_hue.HueFixtures.SHED_LIGHT
import uk.co.siland.culvery.provider.lights_hue.HueFixtures.SWITCH

private const val RESOURCE = "/clip/v2/resource"
private const val OFF = """{"on":{"on":false}}"""
private const val ANOTHER_ID = "001788fffe00ffff"
private const val REAL_WAIT_NANOS = 5_000_000_000L
private const val POLL_MS = 5L

/** Both Mimic presence instances started, and both stopped. */
private val PM_ALL_STARTED = BEHAVIORS.replace(""""pm_state":"stopped"""", """"pm_state":"started"""")
private val PM_ALL_STOPPED = BEHAVIORS.replace(""""pm_state":"started"""", """"pm_state":"stopped"""")

/** The Evening Schedule disabled; its status still says running. */
private val EVENING_DISABLED = BEHAVIORS.replace(
    """"id":"$SCHEDULE_ON","type":"behavior_instance","script_id":"$SCHEDULE_SCRIPT","enabled":true""",
    """"id":"$SCHEDULE_ON","type":"behavior_instance","script_id":"$SCHEDULE_SCRIPT","enabled":false""",
)

/** 7 §4.1–§4.5, §6: the rooms from a full read and the stream, their commands, All lights off, automations and recovery. */
@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@ConscryptMode(ConscryptMode.Mode.OFF)
class HueSystemTest {
    private val bridges = mutableListOf<FakeBridge>()
    private val bridge = fake()

    private fun fake(keystore: String = "bridge") = FakeBridge(keystore).also { bridges += it }

    private fun TestScope.system(
        address: String = bridge.address,
        search: suspend (String?) -> List<BridgeFound> = { emptyList() },
        online: () -> Boolean = { true },
        onMoved: suspend (LightsLink) -> Unit = {},
        timeoutMs: Long = HUE_TIMEOUT_MS,
        wall: WallClock = WallClock { testScheduler.currentTime },
    ) = HueSystem(
        HueClient(
            HueTrust(TestCerts.testCa()),
            HuePacer(now = { testScheduler.currentTime }),
            accept = onThisMachine,
            timeoutMs = timeoutMs,
            capture = { _, _ -> },
        ),
        search = search,
        online = online,
        link = LightsLink(BRIDGE_ID, address, KEY),
        onMoved = onMoved,
        clock = wall,
        elapsedMillis = { testScheduler.currentTime },
    )

    @After
    fun tearDown() = bridges.forEach { it.close() }

    private fun HueSystem.room(id: String): LightRoom = rooms.value.single { it.id == id }

    private fun FakeBridge.puts() = received.filter { it.method == "PUT" }.map { it.path to it.body }

    private fun FakeBridge.behaviorReads() = received.filter { it.method == "GET" && it.path.startsWith("$RESOURCE/behavior_instance") }

    /** The problem [block] failed with as unreachable; null when it didn't. */
    private suspend fun failure(block: suspend () -> Unit): LightsProblem? =
        (runCatching { block() }.exceptionOrNull() as? LightsUnreachableException)?.problem

    private fun FakeBridge.reads() = received.filter { it.method == "GET" && it.path.startsWith(RESOURCE) }.map { it.path.removePrefix("$RESOURCE/") }

    /**
     * Fails if the rooms leave Hue's order, a brightness or scene is misread, an off room loses its lights' level to the
     * group's 0 (9 §2.5), or a room with no dimming light can dim (7 §4.2).
     */
    @Test
    fun aFullReadBuildsTheRoomsInHuesOrder() = runTest {
        val s = system()
        s.readAll()
        assertThat(s.rooms.value).containsExactly(
            LightRoom(LOUNGE, "Lounge", on = true, brightness = 70, listOf(LightScene(RELAX, "Relax"), LightScene(BRIGHT, "Bright")), RELAX, dimmable = true),
            LightRoom(KITCHEN, "Kitchen", on = false, brightness = 45, emptyList(), null, dimmable = true),
            LightRoom(GARDEN, "Garden", on = true, brightness = 0, emptyList(), null, dimmable = false),
        ).inOrder()
        assertThat(bridge.reads()).containsExactly("room", "grouped_light", "light", "scene")
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

    /** 9 §2.5. Fails if a room the stream reports off at brightness 0 loses the level it was at. */
    @Test
    fun aRoomTurnedOffKeepsItsLevel() = runTest {
        val s = system()
        s.readAll()
        bridge.events = """
            data: [{"data":[{"id":"$LOUNGE_GROUP","on":{"on":false},"dimming":{"brightness":0.0},"type":"grouped_light"}],"type":"update"}]
        """.trimIndent()
        assertThat(s.stream().toList()).containsExactly(Unit)
        assertThat(s.room(LOUNGE).on).isFalse()
        assertThat(s.room(LOUNGE).brightness).isEqualTo(70)
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

    private var marks = 0

    /** Opens the stream on [reports] and returns once they are applied, leaving it open: Kitchen's brightness comes with them as a mark. */
    private suspend fun TestScope.reporting(s: HueSystem, vararg reports: Pair<String, Double>): Job {
        val mark = ++marks
        val items = (reports.toList() + (KITCHEN_GROUP to mark.toDouble()))
            .joinToString(",") { (group, level) -> """{"id":"$group","dimming":{"brightness":$level},"type":"grouped_light"}""" }
        bridge.events = """data: [{"data":[$items],"type":"update"}]"""
        val open = launch { s.stream().collect {} }
        s.rooms.first { rooms -> rooms.single { it.id == KITCHEN }.brightness == mark }
        return open
    }

    private suspend fun TestScope.reported(s: HueSystem, vararg reports: Pair<String, Double>) = reporting(s, *reports).cancelAndJoin()

    private fun TestScope.at(millis: Long) = advanceTimeBy(millis - testScheduler.currentTime)

    /**
     * Fails if the stream's report of a level sent earlier in a drag, which the bridge sends a second or more late, moves the
     * room off the brightness just sent; once that brightness is reported, a change from elsewhere shows at once (7 §4.3).
     */
    @Test
    fun aLateReportOfAnEarlierLevelLeavesTheBrightnessJustSent() = runTest {
        val s = system()
        s.readAll()
        s.setBrightness(s.room(LOUNGE), 90)
        reported(s, LOUNGE_GROUP to 60.2)
        assertThat(s.room(LOUNGE).brightness).isEqualTo(90)
        reported(s, LOUNGE_GROUP to 89.8)
        reported(s, LOUNGE_GROUP to 40.0)
        assertThat(s.room(LOUNGE).brightness).isEqualTo(40)
    }

    /** Fails if a brightness the bridge never reports outranks its reports for good, so a level set elsewhere never shows. */
    @Test
    fun aReportOfAnotherLevelShowsOnceTheBridgeHadTimeToReportTheBrightnessSent() = runTest {
        val s = system()
        s.readAll()
        val sentAt = testScheduler.currentTime
        s.setBrightness(s.room(LOUNGE), 90)
        at(sentAt + REPORT_WAIT_MS - 1)
        reported(s, LOUNGE_GROUP to 60.2)
        assertThat(s.room(LOUNGE).brightness).isEqualTo(90)
        at(sentAt + REPORT_WAIT_MS)
        reported(s, LOUNGE_GROUP to 60.2)
        assertThat(s.room(LOUNGE).brightness).isEqualTo(60)
    }

    /** Fails if a level reported during the wait, with none after it, isn't shown when the wait ends: the bridge settled there (7 §4.2). */
    @Test
    fun theLatestLevelHeldBackShowsWhenTheWaitEnds() = runTest {
        val s = system()
        s.readAll()
        val sentAt = testScheduler.currentTime
        s.setBrightness(s.room(LOUNGE), 90)
        at(sentAt + 1_000)
        bridge.streamHeld = true
        val open = reporting(s, LOUNGE_GROUP to 45.0, LOUNGE_GROUP to 60.2)
        at(sentAt + REPORT_WAIT_MS - 1)
        assertThat(s.room(LOUNGE).brightness).isEqualTo(90)
        at(sentAt + REPORT_WAIT_MS)
        runCurrent()
        assertThat(s.room(LOUNGE).brightness).isEqualTo(60)
        open.cancelAndJoin()
    }

    /** Fails if the wait is timed by the wall clock, so setting it back keeps a report of another level held back past the wait. */
    @Test
    fun settingTheWallClockBackDoesNotExtendTheWaitForAReport() = runTest {
        var wallShift = 0L
        val s = system(wall = WallClock { testScheduler.currentTime + wallShift })
        s.readAll()
        val sentAt = testScheduler.currentTime
        s.setBrightness(s.room(LOUNGE), 90)
        wallShift = -3_600_000L
        at(sentAt + REPORT_WAIT_MS)
        reported(s, LOUNGE_GROUP to 60.2)
        assertThat(s.room(LOUNGE).brightness).isEqualTo(60)
    }

    /** Fails if the wait is timed by the wall clock, so setting it back keeps the level held back from showing when the wait ends. */
    @Test
    fun settingTheWallClockBackDoesNotExtendTheWaitForTheLevelHeldBack() = runTest {
        var wallShift = 0L
        val s = system(wall = WallClock { testScheduler.currentTime + wallShift })
        s.readAll()
        val sentAt = testScheduler.currentTime
        s.setBrightness(s.room(LOUNGE), 90)
        wallShift = -3_600_000L
        at(sentAt + 1_000)
        bridge.streamHeld = true
        val open = reporting(s, LOUNGE_GROUP to 45.0)
        at(sentAt + REPORT_WAIT_MS)
        runCurrent()
        assertThat(s.room(LOUNGE).brightness).isEqualTo(45)
        open.cancelAndJoin()
    }

    /** Fails if the stream, once the bridge ends it, waits out a pending wait before it finishes, which holds back the reconnect. */
    @Test
    fun theStreamEndsAtOnceWithAWaitPending() = runTest {
        val s = system()
        s.readAll()
        s.setBrightness(s.room(LOUNGE), 90)
        bridge.events = """data: [{"data":[{"id":"$LOUNGE_GROUP","dimming":{"brightness":45.0},"type":"grouped_light"}],"type":"update"}]"""
        val before = testScheduler.currentTime
        val stream = async { s.stream().toList() }
        // The bridge is read on a real thread; while it is, an idle runTest would jump virtual time to the wait's deadline.
        val giveUp = System.nanoTime() + REAL_WAIT_NANOS
        while (!stream.isCompleted && System.nanoTime() < giveUp) {
            runCurrent()
            Thread.sleep(POLL_MS)
        }
        assertThat(stream.isCompleted).isTrue()
        assertThat(testScheduler.currentTime).isEqualTo(before)
        assertThat(s.room(LOUNGE).brightness).isEqualTo(90)
    }

    /** Fails if a scene recalled after a brightness still waits for that brightness, so the scene's own level is dropped. */
    @Test
    fun aSceneRecalledEndsTheWaitForTheBrightnessSentBefore() = runTest {
        val s = system()
        s.readAll()
        s.setBrightness(s.room(LOUNGE), 90)
        s.recallScene(s.room(LOUNGE), LightScene(BRIGHT, "Bright"))
        reported(s, LOUNGE_GROUP to 56.0)
        assertThat(s.room(LOUNGE).brightness).isEqualTo(56)
    }

    /** Pin: passes without the wait. Fails if a full read stops clearing the waits, so a refused brightness outranks the stream. */
    @Test
    fun aRefusedBrightnessLeavesTheStreamsReportsShowing() = runTest {
        val s = system()
        s.readAll()
        bridge.refusingCommands = true
        runCatching { s.setBrightness(s.room(LOUNGE), 90) }
        reported(s, LOUNGE_GROUP to 55.0)
        assertThat(s.room(LOUNGE).brightness).isEqualTo(55)
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

    /** Fails if a slider at 0 turns the room on at Hue's minimum instead of off. */
    @Test
    fun brightnessZeroTurnsTheRoomOff() = runTest {
        val s = system()
        s.readAll()
        s.setBrightness(s.room(LOUNGE), 0)
        assertThat(bridge.puts()).containsExactly("$RESOURCE/grouped_light/$LOUNGE_GROUP" to OFF)
        assertThat(s.room(LOUNGE).on).isFalse()
    }

    /** Fails if a command the bridge never got stays shown, so the wall shows a change that never happened (7 §6). */
    @Test
    fun aCommandThatCantReachTheBridgeIsUndone() = runTest {
        val s = system()
        s.readAll()
        bridge.close()
        assertThat(failure { s.setOn(s.room(KITCHEN), true) }).isEqualTo(LightsProblem.UNREACHABLE)
        assertThat(s.room(KITCHEN).on).isFalse()
        assertThat(failure { s.recallScene(s.room(LOUNGE), LightScene(BRIGHT, "Bright")) }).isEqualTo(LightsProblem.UNREACHABLE)
        assertThat(s.room(LOUNGE).activeSceneId).isEqualTo(RELAX)
    }

    /** Fails if nothing left out is sent room by room, or the screen waits for the bridge to show every room off (7 §4.4). */
    @Test
    fun allOffWithNothingLeftOutIsOneCommandToTheWholeHome() = runTest {
        val s = system()
        s.readAll()
        val hold = CountDownLatch(1).also { bridge.holdCommands = it }
        val off = async { s.allOff(emptySet()) }
        assertThat(runInterruptible(Dispatchers.IO) { bridge.held.tryAcquire(5, TimeUnit.SECONDS) }).isTrue()
        assertThat(s.rooms.value.map { it.on }).containsExactly(false, false, false)
        hold.countDown()
        assertThat(off.await()).isEqualTo(AllOffResult.Done)
        assertThat(bridge.puts()).containsExactly("$RESOURCE/grouped_light/$HOME_GROUP" to OFF)
    }

    /** Fails if left-out ids no longer on the bridge aren't dropped first, so the rooms are turned off one by one. */
    @Test
    fun allOffWithEveryLeftOutRoomGoneIsTheWholeHome() = runTest {
        val s = system()
        s.readAll()
        assertThat(s.allOff(setOf("gone-id"))).isEqualTo(AllOffResult.Done)
        assertThat(bridge.puts()).containsExactly("$RESOURCE/grouped_light/$HOME_GROUP" to OFF)
    }

    /** Fails if a left-out room is turned off, the whole home is, or a light in no room is left on (7 §4.4). */
    @Test
    fun allOffLeavingTheGardenOut() = runTest {
        val s = system()
        s.readAll()
        assertThat(s.allOff(setOf(GARDEN))).isEqualTo(AllOffResult.Done)
        assertThat(bridge.puts()).containsExactly(
            "$RESOURCE/grouped_light/$LOUNGE_GROUP" to OFF,
            "$RESOURCE/grouped_light/$KITCHEN_GROUP" to OFF,
            "$RESOURCE/light/$SHED_LIGHT" to OFF,
        )
        assertThat(bridge.puts().map { it.first })
            .containsNoneOf("$RESOURCE/grouped_light/$GARDEN_GROUP", "$RESOURCE/grouped_light/$HOME_GROUP")
        assertThat(s.room(LOUNGE).on).isFalse()
        assertThat(s.room(GARDEN).on).isTrue()
    }

    /** Fails if a room command still waiting its turn is sent after All lights off, turning a room back on. */
    @Test
    fun allOffDropsWaitingRoomCommands() = runTest {
        val s = system()
        s.readAll()
        s.setOn(s.room(KITCHEN), true)
        val waiting = async { s.setOn(s.room(LOUNGE), true) }
        runCurrent()
        assertThat(s.allOff(emptySet())).isEqualTo(AllOffResult.Done)
        waiting.await()
        assertThat(bridge.puts()).containsExactly(
            "$RESOURCE/grouped_light/$KITCHEN_GROUP" to """{"on":{"on":true}}""",
            "$RESOURCE/grouped_light/$HOME_GROUP" to OFF,
        ).inOrder()
        assertThat(s.room(LOUNGE).on).isFalse()
    }

    /** Fails if one failing room stops the rest, or the count leaves out a room or the lights in no room (7 §4.4). */
    @Test
    fun aRoomThatFailsStillLetsTheOthersGo() = runTest {
        val s = system()
        s.readAll()
        bridge.received.clear()
        bridge.refusedIds = setOf(KITCHEN_GROUP)
        assertThat(s.allOff(setOf(GARDEN))).isEqualTo(AllOffResult.RoomsLeftOn(1))
        assertThat(bridge.puts().map { it.first }).containsExactly(
            "$RESOURCE/grouped_light/$LOUNGE_GROUP", "$RESOURCE/grouped_light/$KITCHEN_GROUP", "$RESOURCE/light/$SHED_LIGHT",
        )
        assertThat(bridge.reads()).contains("grouped_light")

        bridge.refusedIds = setOf(KITCHEN_GROUP, SHED_LIGHT)
        assertThat(s.allOff(setOf(GARDEN))).isEqualTo(AllOffResult.RoomsLeftOn(2))
    }

    /** Fails if a refused whole-home command passes as done, or the rooms keep showing off. */
    @Test
    fun theWholeHomeRefusedIsRefused() = runTest {
        val s = system()
        s.readAll()
        bridge.refusingCommands = true
        assertThat(s.allOff(emptySet())).isEqualTo(AllOffResult.Refused)
        assertThat(s.room(LOUNGE).on).isTrue()
    }

    /** Fails if an unreachable bridge reads as done or refused, or the rooms keep showing off (7 §4.5 "nothing changes"). */
    @Test
    fun anUnreachableBridgeIsCantReach() = runTest {
        val s = system()
        s.readAll()
        bridge.close()
        assertThat(s.allOff(emptySet())).isEqualTo(AllOffResult.CantReach(LightsProblem.UNREACHABLE))
        assertThat(bridge.puts()).isEmpty()
        assertThat(s.room(LOUNGE).on).isTrue()
        assertThat(s.room(GARDEN).on).isTrue()
    }

    /** Fails if Mimic presence's state is read from `enabled`, a Schedule's from `status`, or an unnamed one is listed (7 §4.5). */
    @Test
    fun automationsByNameAndState() = runTest {
        val s = system()
        assertThat(s.automations()).containsExactly(
            Automation(PM_STOPPED, "Mimic presence", on = false),
            Automation(PM_STARTED, "Mimic presence away", on = true),
            Automation(SCHEDULE_ON, "Evening lights", on = true),
            Automation(SCHEDULE_OFF, "Morning lights", on = false),
            Automation(SWITCH, "Hall dimmer", on = true),
        ).inOrder()
    }

    /** Fails if Mimic presence is switched by `enabled`, isn't read back until started, or is read on once it is (7 §4.5). */
    @Test
    fun mimicPresenceStartsByItsTriggerAndIsReadBack() = runTest {
        val s = system()
        val mimic = s.automations().single { it.id == PM_STOPPED }
        bridge.received.clear()
        bridge.onRequest = {
            if (it.path == "$RESOURCE/behavior_instance/$PM_STOPPED" && bridge.behaviorReads().size == 2) {
                bridge.resources["behavior_instance"] = PM_ALL_STARTED
            }
        }
        assertThat(s.setAutomation(mimic, true)).isEqualTo(AutomationResult.CONFIRMED)
        assertThat(bridge.puts()).containsExactly("$RESOURCE/behavior_instance/$PM_STOPPED" to """{"trigger":{"start":true}}""")
        assertThat(bridge.behaviorReads().map { it.path }).containsExactly(
            "$RESOURCE/behavior_instance/$PM_STOPPED", "$RESOURCE/behavior_instance/$PM_STOPPED",
        )

        bridge.received.clear()
        bridge.onRequest = { if (it.method == "PUT") bridge.resources["behavior_instance"] = PM_ALL_STOPPED }
        assertThat(s.setAutomation(mimic.copy(on = true), false)).isEqualTo(AutomationResult.CONFIRMED)
        assertThat(bridge.puts()).containsExactly("$RESOURCE/behavior_instance/$PM_STOPPED" to """{"trigger":{"stop":true}}""")
    }

    /** Fails if a Schedule is switched by a trigger, or read back by its `status`, which stays running when it is off. */
    @Test
    fun aScheduleIsSwitchedByEnabledAndReadBackByIt() = runTest {
        val s = system()
        val evening = s.automations().single { it.id == SCHEDULE_ON }
        bridge.received.clear()
        bridge.onRequest = { if (it.method == "PUT") bridge.resources["behavior_instance"] = EVENING_DISABLED }
        assertThat(s.setAutomation(evening, false)).isEqualTo(AutomationResult.CONFIRMED)
        assertThat(bridge.puts()).containsExactly("$RESOURCE/behavior_instance/$SCHEDULE_ON" to """{"enabled":false}""")
        assertThat(EVENING_DISABLED).contains(""""enabled":false,"status":"running"""")
    }

    /** Fails if the read-back stops early, runs on, or doesn't wait a second before each read (7 §4.5). */
    @Test
    fun anAutomationNotConfirmedInSixReads() = runTest {
        val s = system()
        bridge.now = { testScheduler.currentTime }
        val mimic = s.automations().single { it.id == PM_STOPPED }
        bridge.received.clear()
        val start = testScheduler.currentTime
        assertThat(s.setAutomation(mimic, true)).isEqualTo(AutomationResult.NOT_CONFIRMED)
        assertThat(bridge.behaviorReads().map { it.at - start })
            .containsExactly(1_000L, 2_000L, 3_000L, 4_000L, 5_000L, 6_000L).inOrder()
        assertThat(READ_BACK_TRIES).isEqualTo(6)
    }

    /** Fails if a read-back read the bridge answers with an error ends the read-back, or searches for the bridge (7 §4.5). */
    @Test
    fun aRefusedReadBackReadIsReadAgain() = runTest {
        var searches = 0
        val s = system(search = { searches++; emptyList() })
        val mimic = s.automations().single { it.id == PM_STOPPED }
        bridge.received.clear()
        bridge.onRequest = {
            if (it.method == "GET") {
                bridge.failing = bridge.behaviorReads().size == 1
                if (bridge.behaviorReads().size == 2) bridge.resources["behavior_instance"] = PM_ALL_STARTED
            }
        }
        assertThat(s.setAutomation(mimic, true)).isEqualTo(AutomationResult.CONFIRMED)
        assertThat(bridge.behaviorReads()).hasSize(2)
        assertThat(searches).isEqualTo(0)
    }

    /** Fails if a read-back read with no answer throws, or searches for the bridge, when the switch was already sent (7 §4.5). */
    @Test
    fun aReadBackReadWithNoAnswerIsReadAgain() = runTest {
        var searches = 0
        val s = system(search = { searches++; emptyList() }, timeoutMs = 200L)
        val mimic = s.automations().single { it.id == PM_STOPPED }
        bridge.received.clear()
        bridge.onRequest = {
            if (it.method == "GET") {
                bridge.delayMs = if (bridge.behaviorReads().size == 1) 400L else 0L
                if (bridge.behaviorReads().size == 2) bridge.resources["behavior_instance"] = PM_ALL_STARTED
            }
        }
        assertThat(s.setAutomation(mimic, true)).isEqualTo(AutomationResult.CONFIRMED)
        assertThat(bridge.behaviorReads()).hasSize(2)
        assertThat(searches).isEqualTo(0)
        assertThat(s.problem.value).isNull()
    }

    /** Pin. Fails if the bridge's 200 to a trigger counts as the automation started: the read-back is the only proof (7 §4.5). */
    @Test
    fun anUnknownTriggerAnswered200IsNotConfirmed() = runTest {
        val s = system()
        val mimic = s.automations().single { it.id == PM_STOPPED }
        bridge.received.clear()
        assertThat(s.setAutomation(mimic, true)).isEqualTo(AutomationResult.NOT_CONFIRMED)
        assertThat(bridge.puts()).hasSize(1)
        assertThat(s.problem.value).isNull()
    }

    /** Fails if a 403 is taken for a missing bridge and searched for, rather than a lost pairing (7 §6). */
    @Test
    fun aForbiddenAnswerIsUnpaired() = runTest {
        var searches = 0
        val s = system(search = { searches++; emptyList() })
        bridge.forbidden = true
        assertThat(failure { s.readAll() }).isEqualTo(LightsProblem.UNPAIRED)
        assertThat(s.problem.value).isEqualTo(LightsProblem.UNPAIRED)
        assertThat(searches).isEqualTo(0)
    }

    /**
     * Fails if a moved bridge's address is saved before a verified read there, the key is lost, a problem shows, a
     * bridge the search heard with another id is asked, the search isn't for the saved id, or everything is read twice (7 §4.1).
     */
    @Test
    fun aMovedBridgeIsSavedOnlyAfterAVerifiedRead() = runTest {
        val another = fake()
        val moved = fake()
        bridge.delayMs = 400
        var saved: LightsLink? = null
        var answeredBeforeSaved = 0
        var wanted: String? = null
        val s = system(
            timeoutMs = 200,
            search = {
                wanted = it
                listOf(BridgeFound(ANOTHER_ID, another.address), BridgeFound(BRIDGE_ID.uppercase(), moved.address))
            },
            onMoved = {
                saved = it
                answeredBeforeSaved = moved.reads().size
            },
        )
        s.readAll()
        assertThat(saved).isEqualTo(LightsLink(BRIDGE_ID, moved.address, KEY))
        assertThat(saved?.key).isEqualTo(KEY)
        assertThat(answeredBeforeSaved).isEqualTo(4)
        assertThat(moved.reads()).containsExactly("room", "grouped_light", "light", "scene")
        assertThat(wanted).isEqualTo(BRIDGE_ID)
        assertThat(s.problem.value).isNull()
        assertThat(s.rooms.value).hasSize(3)
        assertThat(another.received).isEmpty()
    }

    /** Fails if the key is sent to a bridge whose certificate names another id, or that bridge is saved as moved. */
    @Test
    fun aDifferentBridgeAtTheAddressNeverGetsTheKey() = runTest {
        val stranger = fake("other")
        val candidate = fake("other")
        var moved = false
        var searches = 0
        val s = system(
            address = stranger.address,
            search = { searches++; listOf(BridgeFound(BRIDGE_ID, candidate.address)) },
            onMoved = { moved = true },
        )
        assertThat(failure { s.readAll() }).isEqualTo(LightsProblem.OTHER_BRIDGE)
        assertThat(searches).isEqualTo(1)
        assertThat(stranger.received).isEmpty()
        assertThat(candidate.received).isEmpty()
        assertThat(moved).isFalse()
        assertThat(s.problem.value).isEqualTo(LightsProblem.OTHER_BRIDGE)

        bridge.close()
        val gone = system(search = { listOf(BridgeFound(BRIDGE_ID, candidate.address)) }, onMoved = { moved = true })
        assertThat(failure { gone.readAll() }).isEqualTo(LightsProblem.OTHER_BRIDGE)
        assertThat(candidate.received).isEmpty()
        assertThat(moved).isFalse()
    }

    /** Fails if no search is made before saying unreachable, or one is made more often than every 30 seconds (7 §4.1, §6). */
    @Test
    fun noBridgeAfterTheSearchIsUnreachable() = runTest {
        var searches = 0
        val s = system(search = { searches++; emptyList() })
        bridge.close()
        assertThat(failure { s.readAll() }).isEqualTo(LightsProblem.UNREACHABLE)
        assertThat(searches).isEqualTo(1)
        assertThat(s.problem.value).isEqualTo(LightsProblem.UNREACHABLE)
        advanceTimeBy(SEARCH_GAP_MS - 1)
        assertThat(failure { s.readAll() }).isEqualTo(LightsProblem.UNREACHABLE)
        assertThat(searches).isEqualTo(1)
        advanceTimeBy(1)
        assertThat(failure { s.readAll() }).isEqualTo(LightsProblem.UNREACHABLE)
        assertThat(searches).isEqualTo(2)
        assertThat(SEARCH_GAP_MS).isEqualTo(30_000L)
    }

    /** Fails if an offline tablet searches for the bridge, or says it can't reach it rather than offline (7 §6). */
    @Test
    fun offlineDoesntSearch() = runTest {
        var searches = 0
        val s = system(search = { searches++; emptyList() }, online = { false })
        bridge.close()
        assertThat(failure { s.readAll() }).isEqualTo(LightsProblem.OFFLINE)
        assertThat(s.problem.value).isEqualTo(LightsProblem.OFFLINE)
        assertThat(searches).isEqualTo(0)
    }
}
