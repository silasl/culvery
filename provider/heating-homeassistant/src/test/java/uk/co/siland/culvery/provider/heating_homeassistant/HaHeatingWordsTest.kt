package uk.co.siland.culvery.provider.heating_homeassistant

import com.google.common.truth.Truth.assertThat
import org.junit.Test
import uk.co.siland.culvery.capability.heating.HeatingState

/** 11 §4.1: the thermostat's line in Settings. */
class HaHeatingWordsTest {
    private fun state(
        eco: Boolean = false,
        current: Double? = 20.8,
        target: Double? = 21.2,
        heating: Boolean = true,
        problem: String? = null,
    ) = HeatingState("Hall", eco, current, target, heating, problem)

    /** Fails if heating doesn't say what it is heating to. */
    @Test
    fun heatingSaysTheTarget() {
        assertThat(heatingLine(state())).isEqualTo("Hall · 20.8 °C · heating to 21.2 °C")
    }

    /** Fails if Eco isn't the tail, whatever else is true. */
    @Test
    fun ecoSaysEco() {
        assertThat(heatingLine(state(eco = true))).isEqualTo("Hall · 20.8 °C · Eco")
    }

    /** Fails if a thermostat that isn't heating doesn't say what it is set to. */
    @Test
    fun notHeatingSaysSetTo() {
        assertThat(heatingLine(state(heating = false))).isEqualTo("Hall · 20.8 °C · set to 21.2 °C")
    }

    /** Fails if a missing target leaves a dangling tail. */
    @Test
    fun noTargetIsTheTemperatureAlone() {
        assertThat(heatingLine(state(target = null))).isEqualTo("Hall · 20.8 °C")
    }

    /** Fails if a whole number loses its decimal. */
    @Test
    fun aWholeNumberKeepsOneDecimal() {
        assertThat(heatingLine(state(target = 21.0))).isEqualTo("Hall · 20.8 °C · heating to 21.0 °C")
    }

    /** Fails if a problem doesn't replace the line. */
    @Test
    fun aProblemIsTheLine() {
        assertThat(heatingLine(state(problem = THERMOSTAT_UNREACHABLE))).isEqualTo(THERMOSTAT_UNREACHABLE)
    }
}
