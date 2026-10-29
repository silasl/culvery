package uk.co.siland.culvery.provider.plugs_homeassistant

import android.util.Log
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.withTimeout
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.shadows.ShadowLog
import uk.co.siland.culvery.capability.plugs.PlugCandidates
import uk.co.siland.culvery.capability.plugs.PlugPower
import uk.co.siland.culvery.capability.plugs.PlugPrefs
import uk.co.siland.culvery.capability.plugs.PlugResult
import uk.co.siland.culvery.capability.plugs.PlugsWords
import uk.co.siland.culvery.capability.plugs.isOn
import uk.co.siland.culvery.core.homeassistant.EntityState
import uk.co.siland.culvery.core.homeassistant.HaProblem
import uk.co.siland.culvery.core.homeassistant.HomeAssistant
import uk.co.siland.culvery.core.homeassistant.HomeAssistantApi
import uk.co.siland.culvery.core.homeassistant.HomeAssistantLink
import uk.co.siland.culvery.core.homeassistant.NOT_LINKED
import uk.co.siland.culvery.core.homeassistant.RegistryEntry
import uk.co.siland.culvery.core.homeassistant.problemLine
import uk.co.siland.culvery.core.homeassistant.test.FakeHomeAssistantApi
import uk.co.siland.culvery.core.homeassistant.test.TEST_LINK
import uk.co.siland.culvery.core.homeassistant.test.entityState
import uk.co.siland.culvery.core.homeassistant.test.testHomeAssistant
import uk.co.siland.culvery.core.plugin.WallClock
import uk.co.siland.culvery.core.plugin.retryDelayMillis

/** 12 §4.1, §6: the plugs' states, their watts by name, and a switch with its 30-second check. */
@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(AndroidJUnit4::class)
class HaPlugSystemTest {
    private val api = FakeHomeAssistantApi()
    private val link = MutableStateFlow<HomeAssistantLink?>(TEST_LINK)
    private val prefs = MutableStateFlow(PlugPrefs(plugs = listOf("switch.lamp", "switch.fan", "switch.fairy_lights")))
    private val inFront = MutableStateFlow(true)
    private val unreachable = "Can’t reach Home Assistant. Check the NAS is on."
    private var now = 0L
    private val clock = WallClock { now }

    private fun TestScope.ha(over: HomeAssistantApi = api): HomeAssistant =
        testHomeAssistant(over, link, inFront, backgroundScope, clock).also { runCurrent() }

    private fun TestScope.system(ha: HomeAssistant = ha()) = HaPlugSystem(ha, prefs, clock, backgroundScope)

    private suspend fun TestScope.after(ms: Long) {
        advanceTimeBy(ms)
        runCurrent()
    }

    private fun lamp(state: String = "on", name: String = "Lamp") = entityState("switch.lamp", state, mapOf("friendly_name" to name))
    private fun fan(state: String = "off") = entityState("switch.fan", state, mapOf("friendly_name" to "Fan"))
    private fun fairy(state: String = "unavailable") = entityState("switch.fairy_lights", state, mapOf("friendly_name" to "Fairy lights"))
    private fun lampLed() = entityState("switch.lamp_led", "on", mapOf("friendly_name" to "Lamp LED"))
    private fun lampWatts(state: String = "182") =
        entityState("sensor.lamp_current_consumption", state, mapOf("unit_of_measurement" to "W", "device_class" to "power"))

    private fun entry(id: String, platform: String = "tplink", device: String? = null, category: String? = null, key: String? = null) =
        RegistryEntry(id, platform, device, category, key)

    /** The shape of a real list: a lamp with its LED, auto-off and watts, a fan, a fairy-lights plug, and a Sonos switch. */
    private fun fixtureRegistry() = listOf(
        entry("switch.lamp", device = "dev_lamp"),
        entry("switch.lamp_led", device = "dev_lamp", category = "config"),
        entry("switch.lamp_auto_off", device = "dev_lamp", category = "config"),
        entry("sensor.lamp_current_consumption", device = "dev_lamp", key = "current_consumption"),
        entry("switch.fan", device = "dev_fan"),
        entry("switch.fairy_lights", device = "dev_fairy"),
        entry("switch.kitchen_crossfade", platform = "sonos"),
    )

    private fun seed(vararg states: EntityState) = states.forEach { api.states[it.entityId] = it }

    private fun seedAll() {
        seed(lamp(), fan(), fairy(), lampLed(), lampWatts())
        api.registry = fixtureRegistry()
    }

    /** The system watches and the socket brings every seeded state; later reads of them come from the cache. The registry then widens the follow, so the socket reopens and reports again. */
    private suspend fun TestScope.live(system: HaPlugSystem): () -> Unit {
        val stop = system.watch()
        runCurrent()
        repeat(2) {
            api.socket.emit(api.states.toMap())
            runCurrent()
        }
        return stop
    }

    private val lampRegistry = PlugRegistry(setOf("switch.lamp"), mapOf("switch.lamp" to "sensor.lamp_current_consumption"))

    private fun plugOf(entities: Map<String, EntityState?>, id: String = "switch.lamp", steady: Boolean = true) =
        stateOf(id, entities, null, lampRegistry, steady)

    /** Fails if a home with no link has a tab, or one with a link and no plugs has anything but an empty list. */
    @Test
    fun noLinkIsNull() = runTest {
        val system = system()
        prefs.value = PlugPrefs()
        runCurrent()
        assertThat(system.plugs.first()).isEmpty()
        link.value = null
        runCurrent()
        assertThat(system.plugs.first()).isNull()
    }

    /** Fails if a state, a name or the watts is misread, the order isn't the store's, or the watts sensor isn't followed. */
    @Test
    fun eachStateReads() = runTest {
        seedAll()
        val system = system()
        live(system)
        val plugs = system.plugs.first()!!
        assertThat(plugs.map { it.power }).containsExactly(PlugPower.ON, PlugPower.OFF, PlugPower.UNREACHABLE).inOrder()
        assertThat(plugs.map { it.name }).containsExactly("Lamp", "Fan", "Fairy lights").inOrder()
        assertThat(plugs.map { it.watts }).containsExactly(182, null, null).inOrder()
        assertThat(api.follows.last()).contains("sensor.lamp_current_consumption")
    }

    /** Fails if a reading that isn't a sensible number is shown, 0.4 isn't rounded, or an off plug shows watts. */
    @Test
    fun oddWattsReadSafely() {
        fun watts(reading: String, switch: String = "on") = plugOf(mapOf("switch.lamp" to lamp(switch), "sensor.lamp_current_consumption" to lampWatts(reading))).watts
        listOf("unavailable", "-3", "abc", "NaN", "100001").forEach { assertThat(watts(it)).isNull() }
        assertThat(watts("0.4")).isEqualTo(0)
        assertThat(watts("181.6")).isEqualTo(182)
        assertThat(watts("182", switch = "off")).isNull()
    }

    /** Fails if a rename in Home Assistant doesn't reach the next list. */
    @Test
    fun aRenameShowsAtOnce() = runTest {
        seedAll()
        val system = system()
        live(system)
        assertThat(system.plugs.first()!!.first().name).isEqualTo("Lamp")
        api.socket.emit(mapOf("switch.lamp" to lamp(name = "Reading lamp")))
        runCurrent()
        assertThat(system.plugs.first()!!.first().name).isEqualTo("Reading lamp")
    }

    /** Fails if a word Culvery doesn't know, or an entity not read yet, is anything but unknown. */
    @Test
    fun oddStatesAreUnknown() {
        assertThat(plugOf(mapOf("switch.lamp" to lamp("unknown"))).power).isEqualTo(PlugPower.UNKNOWN)
        assertThat(plugOf(mapOf("switch.lamp" to lamp("jammed"))).power).isEqualTo(PlugPower.UNKNOWN)
        assertThat(plugOf(emptyMap(), steady = false).power).isEqualTo(PlugPower.UNKNOWN)
    }

    /** Fails if a link problem leaves any plug with a state, or the problem isn't the token's line. */
    @Test
    fun aLinkProblemIsNoLink() = runTest {
        seedAll()
        val ha = ha()
        ha.read("switch.lamp")
        val system = system(ha)
        api.failNext = HaProblem.TOKEN_REFUSED
        assertThat(system.read("switch.fan").power).isEqualTo(PlugPower.UNREACHABLE)
        assertThat(system.plugs.first()!!.map { it.power }).containsExactly(PlugPower.NO_LINK, PlugPower.NO_LINK, PlugPower.NO_LINK)
        assertThat(system.plugs.first()!!.map { it.isOn }).containsExactly(null, null, null)
        assertThat(system.problem.first()).isEqualTo(problemLine(HaProblem.TOKEN_REFUSED))
    }

    /** Fails if the Home Assistant down line is anything but NO_LINK for every plug. */
    @Test
    fun unreachableIsNoLinkForEveryPlug() = runTest {
        seedAll()
        val system = system()
        live(system)
        api.dropSocket(HaProblem.UNREACHABLE)
        runCurrent()
        assertThat(system.plugs.first()!!.map { it.power }).containsExactly(PlugPower.NO_LINK, PlugPower.NO_LINK, PlugPower.NO_LINK)
    }

    /** Fails if a LED, auto-off or Sonos switch is offered, or the plugs aren't sorted by name. */
    @Test
    fun onlyTpLinkMainSwitchesArePlugs() = runTest {
        seedAll()
        seed(entityState("switch.kitchen_crossfade", "on", mapOf("friendly_name" to "Crossfade")))
        val system = system()
        live(system)
        assertThat(system.plugs.first()!!.map { it.entityId }).doesNotContain("switch.lamp_led")
        val found = system.available() as PlugCandidates.Found
        assertThat(found.all.map { it.name }).containsExactly("Fairy lights", "Fan", "Lamp").inOrder()
        assertThat(found.all.map { it.entityId }).containsExactly("switch.fairy_lights", "switch.fan", "switch.lamp").inOrder()
    }

    /** Fails if the watts come from the device's own sensor wrongly: a plug without one shows none, the lamp's shows. */
    @Test
    fun wattsComeFromTheSameDevice() = runTest {
        seedAll()
        seed(fan("on"))
        val system = system()
        live(system)
        val plugs = system.plugs.first()!!
        assertThat(plugs.first { it.entityId == "switch.lamp" }.watts).isEqualTo(182)
        assertThat(plugs.first { it.entityId == "switch.fan" }.watts).isNull()
    }

    /** Fails if the plugs and the watts sensor aren't told apart by platform, domain, category and translation key. */
    @Test
    fun theRegistryRuleIsExact() {
        val read = plugRegistry(
            fixtureRegistry() + listOf(
                entry("sensor.fan_today", device = "dev_fan", key = "today_energy"),
                entry("sensor.other_current_consumption", platform = "sonos", device = "dev_fan", key = "current_consumption"),
                entry("light.lamp_strip", device = "dev_lamp"),
                entry("switch.fan_mystery", device = "dev_fan", category = "unknown"),
            ),
        )
        assertThat(read.plugs).containsExactly("switch.lamp", "switch.fan", "switch.fairy_lights")
        assertThat(read.watts).containsExactly("switch.lamp", "sensor.lamp_current_consumption")
    }

    /** Fails if a socket that was live 3 minutes doesn't call a missing plug gone, or a reopened one does before 2 steady minutes. */
    @Test
    fun aRestartIsNotGone() = runTest {
        seed(lamp(), lampWatts())
        api.registry = fixtureRegistry()
        prefs.value = PlugPrefs(plugs = listOf("switch.lamp"))
        val system = system()
        live(system)
        now = 180_000
        api.socket.emit(mapOf("switch.lamp" to null))
        runCurrent()
        assertThat(system.plugs.first()!!.single().power).isEqualTo(PlugPower.GONE)

        api.dropSocket(HaProblem.UNREACHABLE)
        runCurrent()
        now = 200_000
        after(retryDelayMillis(0))
        api.socket.emit(mapOf("switch.lamp" to null))
        runCurrent()
        assertThat(system.plugs.first()!!.single().power).isEqualTo(PlugPower.UNKNOWN)
        now = 200_000 + GONE_AFTER_MS - 1
        assertThat(system.plugs.first()!!.single().power).isEqualTo(PlugPower.UNKNOWN)
        now = 200_000 + GONE_AFTER_MS
        assertThat(system.plugs.first()!!.single().power).isEqualTo(PlugPower.GONE)

        api.socket.emit(mapOf("switch.lamp" to lamp()))
        runCurrent()
        assertThat(system.plugs.first()!!.single().power).isEqualTo(PlugPower.ON)
    }

    /** Fails if a collecting screen isn't told when the 2 minutes pass. */
    @Test
    fun theRowTurnsGoneWhenTheTwoMinutesPassQuietly() = runTest {
        seed(fan())
        api.registry = fixtureRegistry()
        prefs.value = PlugPrefs(plugs = listOf("switch.lamp"))
        val system = system()
        val seen = mutableListOf<PlugPower>()
        backgroundScope.launch { system.plugs.collect { seen += it!!.single().power } }
        live(system)
        api.socket.emit(mapOf("switch.lamp" to null))
        runCurrent()
        assertThat(seen.last()).isEqualTo(PlugPower.UNKNOWN)
        now = GONE_AFTER_MS
        after(GONE_AFTER_MS)
        assertThat(seen.last()).isEqualTo(PlugPower.GONE)
    }

    /** Fails if a plug in the states but no longer a plug in the registry isn't gone once steady. */
    @Test
    fun aShownPlugLeftTheIntegrationIsGone() = runTest {
        seed(lamp(), lampWatts())
        api.registry = fixtureRegistry().filterNot { it.entityId.startsWith("switch.lamp") }
        prefs.value = PlugPrefs(plugs = listOf("switch.lamp"))
        val system = system()
        live(system)
        assertThat(system.plugs.first()!!.single().power).isEqualTo(PlugPower.UNKNOWN)
        now = GONE_AFTER_MS
        assertThat(system.plugs.first()!!.single().power).isEqualTo(PlugPower.GONE)
    }

    /** Fails if a registry that fails on a reconnect loses the plugs or their watts. */
    @Test
    fun aFailedRegistryKeepsTheLast() = runTest {
        seed(lamp(), lampWatts())
        api.registry = fixtureRegistry()
        prefs.value = PlugPrefs(plugs = listOf("switch.lamp"))
        val system = system()
        live(system)
        api.registryProblem = HaProblem.UNREACHABLE
        api.dropSocket(HaProblem.UNREACHABLE)
        runCurrent()
        now = REGISTRY_FRESH_MS
        after(retryDelayMillis(0))
        api.socket.emit(mapOf("switch.lamp" to lamp(), "sensor.lamp_current_consumption" to lampWatts("90")))
        runCurrent()
        assertThat(api.calls.count { it == "registry" }).isAtLeast(2)
        now = REGISTRY_FRESH_MS + GONE_AFTER_MS
        val plug = system.plugs.first()!!.single()
        assertThat(plug.power).isEqualTo(PlugPower.ON)
        assertThat(plug.watts).isEqualTo(90)
        assertThat(api.follows.last()).contains("sensor.lamp_current_consumption")
    }

    /** Fails if a reconnect within 5 minutes of a good read reads the registry again, a later one doesn't, or Settings doesn't always read. */
    @Test
    fun aReconnectSoonAfterAReadSkipsTheRegistry() = runTest {
        seedAll()
        val system = system()
        live(system)
        fun reads() = api.calls.count { it == "registry" }
        assertThat(reads()).isEqualTo(1)

        suspend fun reconnect(at: Long, attempt: Long) {
            api.dropSocket(HaProblem.UNREACHABLE)
            runCurrent()
            now = at
            after(retryDelayMillis(attempt))
            api.socket.emit(api.states.toMap())
            runCurrent()
        }
        reconnect(REGISTRY_FRESH_MS - 1, attempt = 0)
        assertThat(reads()).isEqualTo(1)
        reconnect(REGISTRY_FRESH_MS, attempt = 1)
        assertThat(reads()).isEqualTo(2)

        system.available()
        assertThat(reads()).isEqualTo(3)
    }

    /** Fails if the debug line carries more than counts, or shows when HaCapture isn't switched on. */
    @Test
    fun theRegistryCountsAreLoggedAtDebugOnly() = runTest {
        seedAll()
        ShadowLog.clear()
        ShadowLog.setLoggable("HaCapture", Log.DEBUG)
        val system = system()
        live(system)
        assertThat(ShadowLog.getLogsForTag("HaCapture").map { it.msg }).contains("registry: 3 plugs, 1 with watts, 7 entries")
        ShadowLog.clear()
        ShadowLog.setLoggable("HaCapture", Log.INFO)
        system.available()
        assertThat(ShadowLog.getLogsForTag("HaCapture")).isEmpty()
    }

    /** Fails if the registry failing or Home Assistant being old says anything but why. */
    @Test
    fun availableFailuresSayWhy() = runTest {
        val system = system()
        api.registryProblem = HaProblem.REFUSED
        assertThat(system.available()).isEqualTo(PlugCandidates.Failed(PlugsWords.CANT_LIST_PLUGS))
        api.registryProblem = null
        api.failNext = HaProblem.UNREACHABLE
        assertThat(system.available()).isEqualTo(PlugCandidates.Failed(unreachable))
    }

    /** Fails if the candidates with Home Assistant down say anything but why. */
    @Test
    fun candidatesWithHomeAssistantDownSayWhy() = runTest {
        val system = system()
        api.failAll = HaProblem.UNREACHABLE
        assertThat(system.available()).isEqualTo(PlugCandidates.Failed(unreachable))
    }

    /** Fails if the right service isn't sent, or Done comes before the plug shows the change. */
    @Test
    fun switchSendsTheServiceAndWaitsForTheChange() = runTest {
        seedAll()
        val system = system()
        val on = async { system.switch("switch.fan", true) }
        after(7_000)
        assertThat(api.calls).contains("service switch/turn_on switch.fan")
        api.states["switch.fan"] = fan("on")
        after(2_999)
        assertThat(on.isCompleted).isFalse()
        after(1)
        assertThat(on.await()).isEqualTo(PlugResult.Done)

        val off = async { system.switch("switch.lamp", false) }
        after(1)
        api.states["switch.lamp"] = lamp("off")
        after(5_000)
        assertThat(off.await()).isEqualTo(PlugResult.Done)
        assertThat(api.calls).contains("service switch/turn_off switch.lamp")
    }

    /** Fails if a plug that never changes is Done, or NotSwitched comes before 30 seconds. */
    @Test
    fun aPlugThatDoesntChangeIsNotSwitched() = runTest {
        seedAll()
        val system = system()
        val result = async { system.switch("switch.fan", true) }
        after(29_999)
        assertThat(result.isCompleted).isFalse()
        after(1)
        assertThat(result.await()).isEqualTo(PlugResult.NotSwitched)
    }

    /** Fails if a change the socket brings waits for the next read. */
    @Test
    fun aLiveChangeCountsBeforeTheNextRead() = runTest {
        seedAll()
        val system = system()
        live(system)
        val result = async { system.switch("switch.fan", true) }
        after(2_000)
        assertThat(result.isCompleted).isFalse()
        api.socket.emit(mapOf("switch.fan" to fan("on")))
        runCurrent()
        assertThat(result.await()).isEqualTo(PlugResult.Done)
    }

    /** Fails if a plug already as wanted gets a command. */
    @Test
    fun alreadyAsWantedSendsNothing() = runTest {
        seedAll()
        val system = system()
        assertThat(system.switch("switch.fan", false)).isEqualTo(PlugResult.Done)
        assertThat(api.calls.filter { it.startsWith("service") }).isEmpty()
    }

    /** Fails if an unavailable plug gets a command, or isn't unreachable. */
    @Test
    fun unavailableIsUnreachableAndSendsNothing() = runTest {
        seedAll()
        val system = system()
        assertThat(system.switch("switch.fairy_lights", true)).isEqualTo(PlugResult.Unreachable)
        assertThat(api.calls.filter { it.startsWith("service") }).isEmpty()
    }

    /** Fails if a plug Home Assistant no longer has gets a command, or doesn't say so. */
    @Test
    fun aGonePlugSaysSo() = runTest {
        seedAll()
        val system = system()
        assertThat(system.switch("switch.desk", true)).isEqualTo(PlugResult.Failed(PlugsWords.GONE_PLUG))
        assertThat(api.calls.filter { it.startsWith("service") }).isEmpty()
    }

    /** Fails if a switch with no link reaches Home Assistant. */
    @Test
    fun noLinkSendsNothing() = runTest {
        val system = system()
        link.value = null
        runCurrent()
        assertThat(system.switch("switch.fan", true)).isEqualTo(PlugResult.Failed(NOT_LINKED))
        assertThat(api.calls).isEmpty()
    }

    /** Fails if a refused service is anything but not switched, or blames the link. */
    @Test
    fun aRefusedServiceIsNotSwitched() = runTest {
        seedAll()
        val ha = ha()
        val system = system(ha)
        live(system)
        api.failNext = HaProblem.REFUSED
        assertThat(system.switch("switch.fan", true)).isEqualTo(PlugResult.NotSwitched)
        assertThat(ha.problem.value).isNull()
    }

    /** Fails if a service that can't reach Home Assistant doesn't say so. */
    @Test
    fun aTimeoutIsCantReachHomeAssistant() = runTest {
        seedAll()
        val system = system()
        live(system)
        api.failNext = HaProblem.UNREACHABLE
        assertThat(system.switch("switch.fan", true)).isEqualTo(PlugResult.Failed(unreachable))
    }

    /** Fails if a timeout of the switch's own escapes as a cancellation, which would cancel every sibling plug. */
    @Test
    fun aTimeoutInsideSwitchIsAResultNotAThrow() = runTest {
        seedAll()
        val timing = object : HomeAssistantApi by api {
            override suspend fun callService(link: HomeAssistantLink, domain: String, service: String, entityId: String, data: Map<String, String>) =
                runBlocking { withTimeout(1) { awaitCancellation() } }
        }
        val system = system(ha(timing))
        live(system)
        assertThat(system.switch("switch.fan", true)).isEqualTo(PlugResult.Unreachable)
    }

    /** Fails if one plug's switch can run twice at once, blocks another plug's, or keeps its lock after a cancel. */
    @Test
    fun aSecondSwitchOfOnePlugIsBusy() = runTest {
        seedAll()
        val system = system()
        live(system)
        val first = async { system.switch("switch.fan", true) }
        runCurrent()
        val second = async { system.switch("switch.fan", true) }
        val other = async { system.switch("switch.lamp", false) }
        runCurrent()
        assertThat(second.await()).isEqualTo(PlugResult.Busy)
        assertThat(other.isCompleted).isFalse()
        assertThat(api.calls.filter { it == "service switch/turn_on switch.fan" }).hasSize(1)
        first.cancel()
        runCurrent()
        assertThat(system.switch("switch.fan", false)).isEqualTo(PlugResult.Done)
        after(30_000)
        assertThat(other.await()).isEqualTo(PlugResult.NotSwitched)
    }

    /** Fails if a read that can't reach Home Assistant throws, or loses the plug's name. */
    @Test
    fun readNeverThrows() = runTest {
        seedAll()
        val ha = ha()
        ha.read("switch.lamp")
        val system = system(ha)
        api.failAll = HaProblem.UNREACHABLE
        val state = system.read("switch.lamp")
        assertThat(state.power).isEqualTo(PlugPower.UNREACHABLE)
        assertThat(state.name).isEqualTo("Lamp")
    }

    /** Fails if a timeout of the read's own escapes as a cancellation. */
    @Test
    fun aTimeoutInsideReadIsUnreachable() = runTest {
        seedAll()
        val timing = object : HomeAssistantApi by api {
            override suspend fun state(link: HomeAssistantLink, entityId: String): EntityState? = runBlocking { withTimeout(1) { awaitCancellation() } }
        }
        assertThat(system(ha(timing)).read("switch.lamp").power).isEqualTo(PlugPower.UNREACHABLE)
    }

    /** Fails if the socket doesn't follow every added switch and the watts sensor the registry names, and no more. */
    @Test
    fun watchFollowsThePlugsAndTheirWatts() = runTest {
        seedAll()
        prefs.value = PlugPrefs(plugs = listOf("switch.lamp", "switch.fan"))
        val system = system()
        live(system)
        assertThat(api.follows.first()).containsExactly("switch.lamp", "switch.fan")
        assertThat(api.follows.last()).containsExactly("switch.lamp", "sensor.lamp_current_consumption", "switch.fan")
    }

    /** Fails if a log line carries an entity id, a name or a watt figure. */
    @Test
    fun nothingPersonalReachesTheLog() = runTest {
        seedAll()
        val system = system()
        live(system)
        ShadowLog.clear()
        api.failNext = HaProblem.REFUSED
        system.switch("switch.fan", true)
        api.failNext = HaProblem.UNREACHABLE
        system.switch("switch.fan", true)
        val unchanged = async { system.switch("switch.fan", true) }
        after(30_000)
        unchanged.await()
        api.failAll = HaProblem.UNREACHABLE
        system.available()
        val ours = ShadowLog.getLogs().filter { it.tag == "HaPlugSystem" }
        assertThat(ours).isNotEmpty()
        ShadowLog.getLogs().forEach { line ->
            listOf("switch.", "Lamp", "Fan", "182").forEach { assertThat(line.msg).doesNotContain(it) }
        }
    }
}
