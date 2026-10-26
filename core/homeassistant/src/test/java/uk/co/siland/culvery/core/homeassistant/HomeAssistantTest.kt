package uk.co.siland.culvery.core.homeassistant

import androidx.test.ext.junit.runners.AndroidJUnit4
import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Test
import org.junit.runner.RunWith
import uk.co.siland.culvery.core.homeassistant.test.FakeHomeAssistantApi
import uk.co.siland.culvery.core.homeassistant.test.TEST_LINK
import uk.co.siland.culvery.core.homeassistant.test.entityState
import uk.co.siland.culvery.core.homeassistant.test.testHomeAssistant
import uk.co.siland.culvery.core.plugin.retryDelayMillis

/** 11 §4.3, §6: one cache, one problem, one socket while a screen in front watches; a refused token stops all but Check. */
@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(AndroidJUnit4::class)
class HomeAssistantTest {
    private val api = FakeHomeAssistantApi()
    private val inFront = MutableStateFlow(true)
    private val hoover = entityState("vacuum.hoover", "docked")
    private val battery = entityState("sensor.hoover_battery", "86")
    private val hall = entityState("climate.hall", "heat", mapOf("friendly_name" to "Hall"))

    private fun TestScope.ha(link: Flow<HomeAssistantLink?> = flowOf(TEST_LINK)) =
        testHomeAssistant(api, link, inFront, backgroundScope).also { runCurrent() }

    private suspend fun TestScope.after(ms: Long) {
        advanceTimeBy(ms)
        runCurrent()
    }

    private suspend fun refusal(block: suspend () -> Unit): HaProblem = try {
        block()
        error("Nothing was refused")
    } catch (e: HomeAssistantException) {
        e.problem
    }

    /** Fails if a read doesn't land in the cache, or an entity Home Assistant doesn't have isn't cached as null. */
    @Test
    fun aReadFillsTheCacheAndAMissingOneIsNull() = runTest {
        api.states["vacuum.hoover"] = hoover
        val ha = ha()
        assertThat(ha.read("vacuum.hoover")).isEqualTo(hoover)
        assertThat(ha.read("sensor.hoover_battery")).isNull()
        assertThat(ha.entities.value).containsExactly("vacuum.hoover", hoover, "sensor.hoover_battery", null)
    }

    /** Fails if a failed read leaves no problem, or the next good read doesn't clear it. */
    @Test
    fun aFailureSetsTheProblemAndASuccessClearsIt() = runTest {
        api.states["vacuum.hoover"] = hoover
        val ha = ha()
        api.failNext = HaProblem.UNREACHABLE
        assertThat(refusal { ha.read("vacuum.hoover") }).isEqualTo(HaProblem.UNREACHABLE)
        assertThat(ha.problem.value).isEqualTo(HaProblem.UNREACHABLE)
        ha.read("vacuum.hoover")
        assertThat(ha.problem.value).isNull()
    }

    /** Fails if a service Home Assistant refuses is taken as the link's problem. */
    @Test
    fun aRefusedServiceLeavesTheProblemAlone() = runTest {
        val ha = ha()
        api.failNext = HaProblem.REFUSED
        assertThat(refusal { ha.call("vacuum", "start", "vacuum.hoover") }).isEqualTo(HaProblem.REFUSED)
        assertThat(ha.problem.value).isNull()
    }

    /** Fails if anything is called with no link saved. */
    @Test
    fun noLinkIsTokenRefusedAndCallsNothing() = runTest {
        val ha = ha(flowOf(null))
        assertThat(refusal { ha.read("vacuum.hoover") }).isEqualTo(HaProblem.TOKEN_REFUSED)
        assertThat(refusal { ha.states() }).isEqualTo(HaProblem.TOKEN_REFUSED)
        assertThat(refusal { ha.call("vacuum", "start", "vacuum.hoover") }).isEqualTo(HaProblem.TOKEN_REFUSED)
        assertThat(api.calls).isEmpty()
        ha.watch(flowOf(setOf("vacuum.hoover")))
        after(60_000)
        assertThat(api.follows).isEmpty()
    }

    /** Fails if a new link keeps the old one's entities or problem. */
    @Test
    fun aNewLinkStartsAfresh() = runTest {
        api.states["vacuum.hoover"] = hoover
        val link = MutableStateFlow<HomeAssistantLink?>(TEST_LINK)
        val ha = ha(link)
        ha.read("vacuum.hoover")
        api.failNext = HaProblem.UNREACHABLE
        refusal { ha.read("vacuum.hoover") }
        link.value = HomeAssistantLink(TEST_LINK.baseUrl, "new-token")
        runCurrent()
        assertThat(ha.link.value).isEqualTo(link.value)
        assertThat(ha.entities.value).isEmpty()
        assertThat(ha.problem.value).isNull()
    }

    /** Fails if the socket opens with no screen watching. */
    @Test
    fun nothingOpensUntilAScreenWatches() = runTest {
        ha()
        after(60_000)
        assertThat(api.follows).isEmpty()
    }

    /** Fails if each watcher opens its own socket, or the socket's states don't reach the cache. */
    @Test
    fun watchersShareOneSocket() = runTest {
        val ha = ha()
        ha.watch(flowOf(setOf("vacuum.hoover", "sensor.hoover_battery")))
        ha.watch(flowOf(setOf("climate.hall")))
        runCurrent()
        assertThat(ha.watching).isEqualTo(2)
        assertThat(api.follows.last()).containsExactly("vacuum.hoover", "sensor.hoover_battery", "climate.hall")
        assertThat(api.follows.size - api.cancels).isEqualTo(1)
        api.socket.emit(mapOf("vacuum.hoover" to hoover, "sensor.hoover_battery" to battery, "climate.hall" to null))
        runCurrent()
        assertThat(ha.entities.value).containsExactly("vacuum.hoover", hoover, "sensor.hoover_battery", battery, "climate.hall", null)
    }

    /** Fails if the socket stays open behind another app, or doesn't come back in front. */
    @Test
    fun theSocketClosesBehindAnotherApp() = runTest {
        val ha = ha()
        ha.watch(flowOf(setOf("vacuum.hoover")))
        runCurrent()
        assertThat(api.follows).hasSize(1)
        inFront.value = false
        runCurrent()
        assertThat(api.cancels).isEqualTo(1)
        after(60_000)
        assertThat(api.follows).hasSize(1)
        inFront.value = true
        runCurrent()
        assertThat(api.follows).hasSize(2)
    }

    /** Fails if a drop leaves no problem, reopens sooner than the backoff's first wait, or the first event doesn't clear it. */
    @Test
    fun aDroppedSocketReopensAndReadsAgain() = runTest {
        val ha = ha()
        ha.watch(flowOf(setOf("vacuum.hoover")))
        runCurrent()
        api.dropSocket(HaProblem.UNREACHABLE)
        runCurrent()
        assertThat(ha.problem.value).isEqualTo(HaProblem.UNREACHABLE)
        after(retryDelayMillis(0) - 1)
        assertThat(api.follows).hasSize(1)
        after(1)
        assertThat(api.follows).hasSize(2)
        api.socket.emit(mapOf("vacuum.hoover" to hoover))
        runCurrent()
        assertThat(ha.problem.value).isNull()
        assertThat(ha.entities.value).containsExactly("vacuum.hoover", hoover)
    }

    /** Fails if a refused token reopens the socket, or lets a read or a service call reach Home Assistant. */
    @Test
    fun aRefusedTokenStopsEverythingButCheck() = runTest {
        val ha = ha()
        ha.watch(flowOf(setOf("vacuum.hoover")))
        runCurrent()
        api.dropSocket(HaProblem.TOKEN_REFUSED)
        runCurrent()
        assertThat(ha.problem.value).isEqualTo(HaProblem.TOKEN_REFUSED)
        after(600_000)
        assertThat(api.follows).hasSize(1)
        assertThat(refusal { ha.read("vacuum.hoover") }).isEqualTo(HaProblem.TOKEN_REFUSED)
        assertThat(refusal { ha.states() }).isEqualTo(HaProblem.TOKEN_REFUSED)
        assertThat(refusal { ha.call("vacuum", "start", "vacuum.hoover") }).isEqualTo(HaProblem.TOKEN_REFUSED)
        assertThat(api.calls).isEmpty()
        api.failNext = HaProblem.TOKEN_REFUSED
        assertThat(refusal { ha.check(TEST_LINK) }).isEqualTo(HaProblem.TOKEN_REFUSED)
        assertThat(api.calls).containsExactly("check")
    }

    /** Fails if a good Check of the saved token leaves the line or the socket closed. */
    @Test
    fun aGoodCheckOfTheSameTokenReopensTheSocket() = runTest {
        val ha = ha()
        ha.watch(flowOf(setOf("vacuum.hoover")))
        runCurrent()
        api.dropSocket(HaProblem.TOKEN_REFUSED)
        runCurrent()
        ha.check(TEST_LINK)
        assertThat(ha.problem.value).isNull()
        runCurrent()
        assertThat(api.follows).hasSize(2)
    }

    /** Fails if a Check of a link not yet saved clears a refused token, so the old token would be sent again. */
    @Test
    fun aGoodCheckOfAnotherLinkLeavesTheRefusal() = runTest {
        val ha = ha()
        ha.watch(flowOf(setOf("vacuum.hoover")))
        runCurrent()
        api.dropSocket(HaProblem.TOKEN_REFUSED)
        runCurrent()
        ha.check(HomeAssistantLink(TEST_LINK.baseUrl, "new-token"))
        runCurrent()
        assertThat(ha.problem.value).isEqualTo(HaProblem.TOKEN_REFUSED)
        assertThat(api.follows).hasSize(1)
    }

    /** Fails if a failed Check is recorded as the link's problem. */
    @Test
    fun aFailedCheckLeavesTheProblemAlone() = runTest {
        val ha = ha()
        api.failNext = HaProblem.UNREACHABLE
        refusal { ha.check(TEST_LINK) }
        assertThat(ha.problem.value).isNull()
    }

    /** Fails if a second stop removes another watcher. */
    @Test
    fun stoppingTwiceCountsOnce() = runTest {
        val ha = ha()
        val home = ha.watch(flowOf(setOf("vacuum.hoover")))
        ha.watch(flowOf(setOf("climate.hall")))
        runCurrent()
        home()
        home()
        runCurrent()
        assertThat(ha.watching).isEqualTo(1)
        assertThat(api.follows.last()).containsExactly("climate.hall")
        assertThat(api.follows.size - api.cancels).isEqualTo(1)
    }

    /** Fails if a socket opens for a watcher that follows nothing. */
    @Test
    fun nothingWatchedNoSocket() = runTest {
        val ha = ha()
        ha.watch(flowOf(emptySet()))
        after(60_000)
        assertThat(api.follows).isEmpty()
    }
}
