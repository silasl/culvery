package uk.co.siland.culvery.capability.hoover

import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Test
import uk.co.siland.culvery.core.house.HouseStep

class HouseHooverSeamTest {
    private class Fake(var result: HooverResult = HooverResult.Started) : HooverSystem {
        override val state = MutableStateFlow<HooverState?>(null)
        override suspend fun start() = result
        override fun watch(): () -> Unit = {}
    }

    @Test
    fun startMapsEachResult() = runTest {
        val system = Fake()
        val seam = HouseHooverSeam(setOf(system))
        val missed = HouseStep.Failed("The hoover didn’t run.")
        assertThat(seam.start()).isEqualTo(HouseStep.Done)
        system.result = HooverResult.AlreadyRunning
        assertThat(seam.start()).isEqualTo(HouseStep.Done)
        system.result = HooverResult.NotStarted
        assertThat(seam.start()).isEqualTo(missed)
        system.result = HooverResult.Busy
        assertThat(seam.start()).isEqualTo(missed)
        system.result = HooverResult.Failed("Home Assistant isn’t linked.")
        assertThat(seam.start()).isEqualTo(HouseStep.Failed("Home Assistant isn’t linked."))
    }

    @Test
    fun readyFollowsTheChosenHoover() = runTest {
        val system = Fake()
        val seam = HouseHooverSeam(setOf(system))
        assertThat(seam.ready.first()).isFalse()
        system.state.value = HooverState(HooverActivity.DOCKED, 80, null)
        assertThat(seam.ready.first()).isTrue()
        system.state.value = null
        assertThat(seam.ready.first()).isFalse()
        assertThat(HouseHooverSeam(emptySet()).ready.first()).isFalse()
    }
}
