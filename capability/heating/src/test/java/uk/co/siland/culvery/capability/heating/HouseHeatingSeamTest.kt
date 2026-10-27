package uk.co.siland.culvery.capability.heating

import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Test
import uk.co.siland.culvery.core.house.HouseStep

class HouseHeatingSeamTest {
    private class Fake(var result: HeatingResult = HeatingResult.Done) : HeatingSystem {
        override val state = MutableStateFlow<HeatingState?>(null)
        override suspend fun setEco(on: Boolean) = result
    }

    private val hall = HeatingState("Hall", eco = false, currentC = 19.0, targetC = 20.0, heating = false, problem = null)

    @Test
    fun ecoMapsEachResult() = runTest {
        val system = Fake()
        val seam = HouseHeatingSeam(setOf(system))
        assertThat(seam.setEco(true)).isEqualTo(HouseStep.Done)
        system.result = HeatingResult.NotConfirmed
        assertThat(seam.setEco(true)).isEqualTo(HouseStep.Failed("The heating didn’t go to Eco. Check the Nest app."))
        assertThat(seam.setEco(false)).isEqualTo(HouseStep.Failed("The heating didn’t come off Eco. Turn it off in the Nest app."))
        system.result = HeatingResult.Failed("Home Assistant isn’t linked.")
        assertThat(seam.setEco(true)).isEqualTo(HouseStep.Failed("Home Assistant isn’t linked."))
    }

    @Test
    fun readyFollowsTheChosenThermostat() = runTest {
        val system = Fake()
        val seam = HouseHeatingSeam(setOf(system))
        assertThat(seam.ready.first()).isFalse()
        system.state.value = hall
        assertThat(seam.ready.first()).isTrue()
        system.state.value = null
        assertThat(seam.ready.first()).isFalse()
    }

    @Test
    fun noSystemIsNeverReady() = runTest {
        val seam = HouseHeatingSeam(emptySet())
        assertThat(seam.ready.first()).isFalse()
        assertThat(seam.setEco(true)).isInstanceOf(HouseStep.Failed::class.java)
    }
}
