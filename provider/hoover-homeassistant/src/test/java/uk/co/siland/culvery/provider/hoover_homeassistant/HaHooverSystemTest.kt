package uk.co.siland.culvery.provider.hoover_homeassistant

import androidx.test.ext.junit.runners.AndroidJUnit4
import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Test
import org.junit.runner.RunWith
import uk.co.siland.culvery.capability.hoover.HooverActivity
import uk.co.siland.culvery.capability.hoover.HooverPrefs
import uk.co.siland.culvery.capability.hoover.HooverResult
import uk.co.siland.culvery.capability.hoover.HooverState
import uk.co.siland.culvery.capability.hoover.NO_HOOVER_CHOSEN
import uk.co.siland.culvery.core.homeassistant.HaProblem
import uk.co.siland.culvery.core.homeassistant.HomeAssistant
import uk.co.siland.culvery.core.homeassistant.HomeAssistantException
import uk.co.siland.culvery.core.homeassistant.HomeAssistantLink
import uk.co.siland.culvery.core.homeassistant.NOT_LINKED
import uk.co.siland.culvery.core.homeassistant.problemLine
import uk.co.siland.culvery.core.homeassistant.test.FakeHomeAssistantApi
import uk.co.siland.culvery.core.homeassistant.test.TEST_LINK
import uk.co.siland.culvery.core.homeassistant.test.entityState
import uk.co.siland.culvery.core.homeassistant.test.testHomeAssistant

/** 11 §4.2–§4.4, §6: the hoover's state, its battery by name, and Start with its 30-second check. */
@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(AndroidJUnit4::class)
class HaHooverSystemTest {
    private val api = FakeHomeAssistantApi()
    private val link = MutableStateFlow<HomeAssistantLink?>(TEST_LINK)
    private val prefs = MutableStateFlow(HooverPrefs("vacuum.hoover"))
    private val inFront = MutableStateFlow(true)
    private val start = "service vacuum/start vacuum.hoover"

    private fun TestScope.ha(): HomeAssistant = testHomeAssistant(api, link, inFront, backgroundScope).also { runCurrent() }

    private fun TestScope.system(ha: HomeAssistant = ha()) = HaHooverSystem(ha, prefs)

    private suspend fun TestScope.after(ms: Long) {
        advanceTimeBy(ms)
        runCurrent()
    }

    private fun vacuum(state: String) = entityState("vacuum.hoover", state)

    private suspend fun HomeAssistant.cached(vararg states: String) {
        api.states["vacuum.hoover"] = vacuum(states.first())
        read("vacuum.hoover")
    }

    /** The system watches and the socket brings [state], which later reads agree with; nothing is read now. */
    private suspend fun TestScope.live(system: HaHooverSystem, state: String) {
        api.states["vacuum.hoover"] = vacuum(state)
        system.watch()
        runCurrent()
        api.socket.emit(mapOf("vacuum.hoover" to vacuum(state)))
        runCurrent()
    }

    /** Fails if a home with no link, or no hoover chosen, gets a row. */
    @Test
    fun noLinkOrNoDeviceNoRow() = runTest {
        val system = system()
        link.value = null
        runCurrent()
        assertThat(system.state.first()).isNull()
        link.value = TEST_LINK
        prefs.value = HooverPrefs()
        runCurrent()
        assertThat(system.state.first()).isNull()
    }

    /** Fails if a vacuum state maps to the wrong activity, or the battery isn't read from its sensor. */
    @Test
    fun eachStateReads() = runTest {
        val ha = ha()
        val system = system(ha)
        api.states["sensor.hoover_battery"] = entityState("sensor.hoover_battery", "86")
        ha.read("sensor.hoover_battery")
        mapOf(
            "docked" to HooverActivity.DOCKED,
            "cleaning" to HooverActivity.CLEANING,
            "returning" to HooverActivity.RETURNING,
            "paused" to HooverActivity.PAUSED,
            "idle" to HooverActivity.IDLE,
            "error" to HooverActivity.ERROR,
        ).forEach { (word, activity) ->
            ha.cached(word)
            assertThat(system.state.first()).isEqualTo(HooverState(activity, 86, null))
        }
    }

    /** Fails if the battery sensor's id isn't the vacuum's object id plus _battery. */
    @Test
    fun theBatteryIsFoundByName() {
        assertThat(batteryIdOf("vacuum.eufy_clean_x10_pro_omni")).isEqualTo("sensor.eufy_clean_x10_pro_omni_battery")
    }

    /** Fails if an odd state shows a status or a problem it shouldn't. */
    @Test
    fun oddStatesReadSafely() = runTest {
        val ha = ha()
        val system = system(ha)
        listOf("unknown", "mopping").forEach {
            ha.cached(it)
            assertThat(system.state.first()).isEqualTo(HooverState(null, null, null))
        }
        ha.cached("unavailable")
        assertThat(system.state.first()).isEqualTo(HooverState(null, null, HOOVER_UNREACHABLE))
    }

    /** Fails if the vacuum shows before its first read, as a problem. */
    @Test
    fun nothingReadYetIsChecking() = runTest {
        assertThat(system().state.first()).isEqualTo(HooverState(null, null, null))
    }

    /** Fails if a battery that is missing, null, unavailable or out of range shows a percentage. */
    @Test
    fun aMissingBatteryLeavesThePercentOut() = runTest {
        val ha = ha()
        val system = system(ha)
        ha.cached("docked")
        assertThat(system.state.first()).isEqualTo(HooverState(HooverActivity.DOCKED, null, null))
        ha.read("sensor.hoover_battery")
        assertThat(ha.entities.value).containsEntry("sensor.hoover_battery", null)
        assertThat(system.state.first()?.batteryPercent).isNull()
        listOf("unavailable", "120", "-1", "unknown").forEach {
            api.states["sensor.hoover_battery"] = entityState("sensor.hoover_battery", it)
            ha.read("sensor.hoover_battery")
            assertThat(system.state.first()).isEqualTo(HooverState(HooverActivity.DOCKED, null, null))
        }
    }

    /** Fails if a hoover deleted in Home Assistant doesn't say to choose it again. */
    @Test
    fun aDeletedHooverSaysChooseItAgain() = runTest {
        val ha = ha()
        assertThat(ha.read("vacuum.hoover")).isNull()
        assertThat(system(ha).state.first()).isEqualTo(HooverState(null, null, GONE_HOOVER))
    }

    /** Fails if a link problem doesn't replace the status with its line. */
    @Test
    fun aProblemShowsItsLine() = runTest {
        val ha = ha()
        val system = system(ha)
        ha.cached("docked")
        api.failNext = HaProblem.TOKEN_REFUSED
        try {
            ha.read("vacuum.hoover")
        } catch (_: HomeAssistantException) {
        }
        assertThat(system.state.first()).isEqualTo(HooverState(null, null, problemLine(HaProblem.TOKEN_REFUSED)))
    }

    /** Fails if the system watches more or less than the vacuum and its battery, or doesn't stop. */
    @Test
    fun itWatchesTheVacuumAndItsBattery() = runTest {
        val system = system()
        val stop = system.watch()
        runCurrent()
        assertThat(api.follows.last()).containsExactly("vacuum.hoover", "sensor.hoover_battery")
        stop()
        runCurrent()
        assertThat(api.cancels).isEqualTo(1)
    }

    /** Fails if Start doesn't send one service call, read every 5 s, and report Started once the hoover leaves the dock. */
    @Test
    fun startSendsTheServiceAndWaitsForTheChange() = runTest {
        val system = system()
        live(system, "docked")
        val result = async { system.start() }
        runCurrent()
        assertThat(api.calls).containsExactly(start)
        after(4_999)
        assertThat(api.calls).hasSize(1)
        after(1)
        assertThat(api.calls).containsExactly(start, "state vacuum.hoover")
        api.states["vacuum.hoover"] = vacuum("cleaning")
        after(4_999)
        assertThat(result.isCompleted).isFalse()
        after(1)
        assertThat(api.calls).containsExactly(start, "state vacuum.hoover", "state vacuum.hoover")
        assertThat(result.await()).isEqualTo(HooverResult.Started)
    }

    /** Fails if a hoover that never leaves the dock is called started, or the check ends before 30 s. */
    @Test
    fun aHooverThatStaysDockedDidNotStart() = runTest {
        val system = system()
        live(system, "docked")
        api.states["vacuum.hoover"] = vacuum("docked")
        val result = async { system.start() }
        after(29_999)
        assertThat(result.isCompleted).isFalse()
        after(1)
        assertThat(result.await()).isEqualTo(HooverResult.NotStarted)
    }

    /** Fails if a hoover that went to error waits out the 30 s, or counts as started. */
    @Test
    fun anErrorIsNotStartedAtOnce() = runTest {
        val system = system()
        live(system, "docked")
        api.states["vacuum.hoover"] = vacuum("error")
        val result = async { system.start() }
        after(4_999)
        assertThat(result.isCompleted).isFalse()
        after(1)
        assertThat(result.await()).isEqualTo(HooverResult.NotStarted)
    }

    /** Fails if a change on the socket has to wait for the next read. */
    @Test
    fun aLiveChangeCountsBeforeTheNextRead() = runTest {
        val system = system()
        live(system, "docked")
        val result = async { system.start() }
        after(2_000)
        assertThat(result.isCompleted).isFalse()
        api.socket.emit(mapOf("vacuum.hoover" to vacuum("cleaning")))
        runCurrent()
        assertThat(result.await()).isEqualTo(HooverResult.Started)
        assertThat(api.calls).containsExactly(start)
    }

    /** Fails if a second Start while one runs sends another service call, or the lock isn't released afterwards. */
    @Test
    fun aSecondStartWhileOneRunsSendsNothing() = runTest {
        val system = system()
        live(system, "docked")
        val first = async { system.start() }
        val second = async { system.start() }
        runCurrent()
        assertThat(second.await()).isEqualTo(HooverResult.Busy)
        assertThat(api.calls).containsExactly(start)
        after(30_000)
        assertThat(first.await()).isEqualTo(HooverResult.NotStarted)
        val third = async { system.start() }
        runCurrent()
        assertThat(third.isCompleted).isFalse()
        assertThat(api.calls.count { it == start }).isEqualTo(2)
        after(30_000)
        assertThat(third.await()).isEqualTo(HooverResult.NotStarted)
    }

    /** Fails if Start while the hoover is cleaning or going home is anything but already running, or sends a call. */
    @Test
    fun startWhileCleaningIsAlreadyRunning() = runTest {
        val system = system()
        live(system, "cleaning")
        assertThat(system.start()).isEqualTo(HooverResult.AlreadyRunning)
        live(system, "returning")
        assertThat(system.start()).isEqualTo(HooverResult.AlreadyRunning)
        assertThat(api.calls).isEmpty()
    }

    /** Fails if a hoover Home Assistant no longer has, or can't reach, is called not started instead of saying why. */
    @Test
    fun aGoneOrUnavailableHooverSaysWhy() = runTest {
        val system = system()
        assertThat(system.start()).isEqualTo(HooverResult.Failed(GONE_HOOVER))
        assertThat(system.start().let { it as HooverResult.Failed }.line)
            .isEqualTo("Home Assistant has no hoover called that any more. Choose it again in Settings › Other integrations.")
        api.states["vacuum.hoover"] = vacuum("unavailable")
        assertThat(system.start()).isEqualTo(HooverResult.Failed("Home Assistant can’t reach the hoover. Check the Eufy app."))
        assertThat(api.calls.none { it.startsWith("service") }).isTrue()
    }

    /** Fails if Start finds no link just because Home Assistant hasn't collected the saved one yet. */
    @Test
    fun aColdStartFindsTheLink() = runTest {
        api.states["vacuum.hoover"] = vacuum("docked")
        val cold = HomeAssistant(api, link, inFront, backgroundScope)
        val result = async { HaHooverSystem(cold, prefs).start() }
        runCurrent()
        assertThat(api.calls).containsExactly("state vacuum.hoover", start).inOrder()
        result.cancel()
    }

    /** Fails if Start trusts a cleaning cached before the socket closed, rather than reading the hoover now docked. */
    @Test
    fun aStaleCleaningInTheCacheIsReadAgain() = runTest {
        val system = system()
        live(system, "cleaning")
        inFront.value = false
        runCurrent()
        api.states["vacuum.hoover"] = vacuum("docked")
        val result = async { system.start() }
        runCurrent()
        assertThat(api.calls).containsExactly("state vacuum.hoover", start).inOrder()
        result.cancel()
    }

    /** Fails if Start with nothing cached sends before it has read the vacuum. */
    @Test
    fun aStartWithNothingCachedReadsFirst() = runTest {
        api.states["vacuum.hoover"] = vacuum("docked")
        val system = system()
        val result = async { system.start() }
        runCurrent()
        assertThat(api.calls).containsExactly("state vacuum.hoover", start).inOrder()
        after(30_000)
        assertThat(result.await()).isEqualTo(HooverResult.NotStarted)
    }

    /** Fails if Start sends anything with no link or no hoover chosen. */
    @Test
    fun startWithNoLinkOrNoDeviceSendsNothing() = runTest {
        val system = system()
        link.value = null
        runCurrent()
        assertThat(system.start()).isEqualTo(HooverResult.Failed(NOT_LINKED))
        link.value = TEST_LINK
        prefs.value = HooverPrefs()
        runCurrent()
        assertThat(system.start()).isEqualTo(HooverResult.Failed(NO_HOOVER_CHOSEN))
        assertThat(api.calls).isEmpty()
    }

    /** Fails if a service Home Assistant refuses is anything but "didn't start", or sets the link's problem. */
    @Test
    fun aRefusedServiceIsNotStarted() = runTest {
        val ha = ha()
        val system = system(ha)
        live(system, "docked")
        api.failNext = HaProblem.REFUSED
        assertThat(system.start()).isEqualTo(HooverResult.NotStarted)
        after(30_000)
        assertThat(api.calls).containsExactly(start)
        assertThat(ha.problem.value).isNull()
    }

    /** Fails if a service that can't be reached isn't a failure with its line, or becomes the link's problem while the socket is live. */
    @Test
    fun anUnreachableServiceFailsWithItsLine() = runTest {
        val ha = ha()
        val system = system(ha)
        live(system, "docked")
        api.failNext = HaProblem.UNREACHABLE
        assertThat(system.start()).isEqualTo(HooverResult.Failed(problemLine(HaProblem.UNREACHABLE)))
        assertThat(ha.problem.value).isNull()
    }

    /** Fails if a check whose every read can't reach Home Assistant is called not started, or ends before 30 s. */
    @Test
    fun aCheckThatReachesNothingFails() = runTest {
        val system = system()
        live(system, "docked")
        val result = async { system.start() }
        runCurrent()
        api.failAll = HaProblem.UNREACHABLE
        after(29_999)
        assertThat(result.isCompleted).isFalse()
        after(1)
        assertThat(result.await()).isEqualTo(HooverResult.Failed(problemLine(HaProblem.UNREACHABLE)))
    }

    /** Fails if one failed read in the check ends it, when the next read shows the hoover cleaning. */
    @Test
    fun oneFailedReadDuringTheCheckIsRiddenOut() = runTest {
        val system = system()
        live(system, "docked")
        val result = async { system.start() }
        runCurrent()
        api.failNext = HaProblem.UNREACHABLE
        after(5_000)
        assertThat(result.isCompleted).isFalse()
        api.states["vacuum.hoover"] = vacuum("cleaning")
        after(5_000)
        assertThat(result.await()).isEqualTo(HooverResult.Started)
    }

    /** Fails if a confirm read that times out while the socket is live takes the row's status and Start away. */
    @Test
    fun aTimedOutConfirmReadKeepsStartWhileTheSocketIsLive() = runTest {
        val system = system()
        live(system, "docked")
        val result = async { system.start() }
        runCurrent()
        api.failNext = HaProblem.UNREACHABLE
        after(5_000)
        assertThat(system.state.first()).isEqualTo(HooverState(HooverActivity.DOCKED, null, null))
        result.cancel()
    }

    /** Fails if cancelling a Start mid-check leaves the lock held. */
    @Test
    fun cancellingTheCheckReleasesTheLock() = runTest {
        val system = system()
        live(system, "docked")
        val first = async { system.start() }
        after(10_000)
        first.cancel()
        runCurrent()
        val next = async { system.start() }
        runCurrent()
        assertThat(next.isCompleted).isFalse()
        assertThat(api.calls.count { it == start }).isEqualTo(2)
        next.cancel()
    }
}
