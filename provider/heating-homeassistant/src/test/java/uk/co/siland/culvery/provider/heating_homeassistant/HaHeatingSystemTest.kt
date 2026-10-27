package uk.co.siland.culvery.provider.heating_homeassistant

import androidx.test.ext.junit.runners.AndroidJUnit4
import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Test
import org.junit.runner.RunWith
import uk.co.siland.culvery.capability.heating.HeatingPrefs
import uk.co.siland.culvery.capability.heating.HeatingResult
import uk.co.siland.culvery.capability.heating.HeatingState
import uk.co.siland.culvery.core.homeassistant.EntityState
import uk.co.siland.culvery.core.homeassistant.HaProblem
import uk.co.siland.culvery.core.homeassistant.HomeAssistant
import uk.co.siland.culvery.core.homeassistant.HomeAssistantLink
import uk.co.siland.culvery.core.homeassistant.NOT_LINKED
import uk.co.siland.culvery.core.homeassistant.problemLine
import uk.co.siland.culvery.core.homeassistant.test.FakeHomeAssistantApi
import uk.co.siland.culvery.core.homeassistant.test.TEST_LINK
import uk.co.siland.culvery.core.homeassistant.test.entityState
import uk.co.siland.culvery.core.homeassistant.test.testHomeAssistant

/** 11 §4.2, §4.5, §6: the thermostat's state, and setEco with its check. */
@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(AndroidJUnit4::class)
class HaHeatingSystemTest {
    private val api = FakeHomeAssistantApi()
    private val link = MutableStateFlow<HomeAssistantLink?>(TEST_LINK)
    private val prefs = MutableStateFlow(HeatingPrefs("climate.hall"))
    private fun set(preset: String) = "service climate/set_preset_mode climate.hall preset_mode=$preset"

    private fun TestScope.ha(): HomeAssistant = testHomeAssistant(api, link, scope = backgroundScope).also { runCurrent() }

    private fun TestScope.system(ha: HomeAssistant = ha()) = HaHeatingSystem(ha, prefs)

    private suspend fun TestScope.after(ms: Long) {
        advanceTimeBy(ms)
        runCurrent()
    }

    private fun hall(preset: String = "none", state: String = "heat", action: String = "heating") = entityState(
        "climate.hall",
        state,
        mapOf(
            "friendly_name" to "Hall",
            "preset_mode" to preset,
            "hvac_action" to action,
            "current_temperature" to 20.8,
            "temperature" to 21.2,
        ),
    )

    private suspend fun HomeAssistant.cached(entity: EntityState) {
        api.states["climate.hall"] = entity
        read("climate.hall")
    }

    /** Home Assistant follows the thermostat and the socket brings [entity], which later reads agree with; nothing is read now. */
    private suspend fun TestScope.live(ha: HomeAssistant, entity: EntityState) {
        api.states["climate.hall"] = entity
        ha.watch(flowOf(setOf("climate.hall")))
        runCurrent()
        api.socket.emit(mapOf("climate.hall" to entity))
        runCurrent()
    }

    /** Fails if the thermostat's name, temperatures, preset or heating action are read wrongly. */
    @Test
    fun theStateReads() = runTest {
        val ha = ha()
        val system = system(ha)
        ha.cached(hall())
        assertThat(system.state.first()).isEqualTo(HeatingState("Hall", false, 20.8, 21.2, true, null))
        ha.cached(hall(preset = "eco", action = "idle"))
        assertThat(system.state.first()).isEqualTo(HeatingState("Hall", true, 20.8, 21.2, false, null))
    }

    /** Fails if an unavailable thermostat doesn't point at the Nest integration. */
    @Test
    fun unavailableSaysCheckTheNestIntegration() = runTest {
        val ha = ha()
        ha.cached(hall(state = "unavailable"))
        assertThat(system(ha).state.first())
            .isEqualTo(HeatingState(THERMOSTAT, false, null, null, false, THERMOSTAT_UNREACHABLE))
    }

    /** Fails if a thermostat deleted in Home Assistant doesn't say to choose it again. */
    @Test
    fun aDeletedThermostatSaysChooseItAgain() = runTest {
        val ha = ha()
        assertThat(ha.read("climate.hall")).isNull()
        assertThat(system(ha).state.first()).isEqualTo(HeatingState(THERMOSTAT, false, null, null, false, GONE_THERMOSTAT))
    }

    /** Fails if a missing link or thermostat gives a state. */
    @Test
    fun noLinkOrNoDeviceIsNull() = runTest {
        val system = system()
        link.value = null
        runCurrent()
        assertThat(system.state.first()).isNull()
        link.value = TEST_LINK
        prefs.value = HeatingPrefs()
        runCurrent()
        assertThat(system.state.first()).isNull()
    }

    /** Fails if setEco sends anything but the preset, or the wrong one. */
    @Test
    fun setEcoSendsThePreset() = runTest {
        val ha = ha()
        val system = system(ha)
        live(ha, hall())
        val on = async { system.setEco(true) }
        runCurrent()
        assertThat(api.calls).containsExactly(set("eco"))
        after(30_000)
        on.await()
        live(ha, hall(preset = "eco"))
        val off = async { system.setEco(false) }
        runCurrent()
        assertThat(api.calls.last()).isEqualTo(set("none"))
        after(30_000)
        off.await()
    }

    /** Fails if Eco is called done before the preset shows, or not once it does. */
    @Test
    fun ecoIsDoneWhenThePresetShows() = runTest {
        val ha = ha()
        val system = system(ha)
        live(ha, hall())
        val result = async { system.setEco(true) }
        runCurrent()
        after(4_999)
        assertThat(api.calls).containsExactly(set("eco"))
        after(1)
        assertThat(api.calls).containsExactly(set("eco"), "state climate.hall")
        api.states["climate.hall"] = hall(preset = "eco")
        after(4_999)
        assertThat(result.isCompleted).isFalse()
        after(1)
        assertThat(api.calls).hasSize(3)
        assertThat(result.await()).isEqualTo(HeatingResult.Done)
    }

    /** Fails if a preset that never changes is confirmed, or the check ends before 30 s. */
    @Test
    fun aPresetThatNeverChangesIsNotConfirmed() = runTest {
        val ha = ha()
        val system = system(ha)
        live(ha, hall())
        api.states["climate.hall"] = hall()
        val result = async { system.setEco(true) }
        after(29_999)
        assertThat(result.isCompleted).isFalse()
        after(1)
        assertThat(result.await()).isEqualTo(HeatingResult.NotConfirmed)
    }

    /** Fails if Eco cached before the call confirms Eco on, when Home Assistant now reads otherwise. */
    @Test
    fun aStaleCachedPresetIsNotConfirmation() = runTest {
        val ha = ha()
        val system = system(ha)
        ha.cached(hall(preset = "eco"))
        api.states["climate.hall"] = hall()
        val result = async { system.setEco(true) }
        runCurrent()
        assertThat(result.isCompleted).isFalse()
        after(29_999)
        assertThat(result.isCompleted).isFalse()
        after(1)
        assertThat(result.await()).isEqualTo(HeatingResult.NotConfirmed)
    }

    /** Fails if a preset that was already set is confirmed without a read, or never confirmed. */
    @Test
    fun aPresetAlreadySetIsConfirmedByARead() = runTest {
        val ha = ha()
        val system = system(ha)
        ha.cached(hall(preset = "eco"))
        val result = async { system.setEco(true) }
        runCurrent()
        assertThat(result.isCompleted).isFalse()
        after(4_999)
        assertThat(result.isCompleted).isFalse()
        after(1)
        assertThat(result.await()).isEqualTo(HeatingResult.Done)
    }

    /** Fails if a service Home Assistant refuses is anything but "didn't change", or sets the link's problem. */
    @Test
    fun aRefusedCallSaysTheThermostatDidntChange() = runTest {
        val ha = ha()
        val system = system(ha)
        live(ha, hall())
        api.failNext = HaProblem.REFUSED
        assertThat(system.setEco(true)).isEqualTo(HeatingResult.Failed(THERMOSTAT_REFUSED))
        assertThat(ha.problem.value).isNull()
        after(30_000)
        assertThat(api.calls).containsExactly(set("eco"))
    }

    /** Fails if a service that can't be reached isn't a failure with its line. */
    @Test
    fun anUnreachableCallFailsWithItsLine() = runTest {
        val ha = ha()
        val system = system(ha)
        live(ha, hall())
        api.failNext = HaProblem.UNREACHABLE
        assertThat(system.setEco(true))
            .isEqualTo(HeatingResult.Failed(problemLine(HaProblem.UNREACHABLE)))
    }

    /** Fails if setEco sends anything with no link or no thermostat chosen. */
    @Test
    fun setEcoWithNoLinkOrNoDeviceSendsNothing() = runTest {
        val system = system()
        link.value = null
        runCurrent()
        assertThat(system.setEco(true)).isEqualTo(HeatingResult.Failed(NOT_LINKED))
        link.value = TEST_LINK
        prefs.value = HeatingPrefs()
        runCurrent()
        assertThat(system.setEco(true)).isEqualTo(HeatingResult.Failed(NO_THERMOSTAT_CHOSEN))
        assertThat(api.calls).isEmpty()
    }

    /** Fails if cancelling a change mid-check leaves the lock held. */
    @Test
    fun cancellingTheCheckReleasesTheLock() = runTest {
        val ha = ha()
        val system = system(ha)
        live(ha, hall())
        val first = async { system.setEco(true) }
        after(10_000)
        first.cancel()
        runCurrent()
        val next = async { system.setEco(true) }
        runCurrent()
        assertThat(api.calls.count { it == set("eco") }).isEqualTo(2)
        next.cancel()
    }
}
