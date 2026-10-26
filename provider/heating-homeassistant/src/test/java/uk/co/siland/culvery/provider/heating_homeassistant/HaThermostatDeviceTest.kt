package uk.co.siland.culvery.provider.heating_homeassistant

import androidx.test.ext.junit.runners.AndroidJUnit4
import com.google.common.truth.Truth.assertThat
import java.io.File
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import uk.co.siland.culvery.capability.heating.HeatingSettings
import uk.co.siland.culvery.core.homeassistant.HomeAssistant
import uk.co.siland.culvery.core.homeassistant.test.FakeHomeAssistantApi
import uk.co.siland.culvery.core.homeassistant.test.entityState
import uk.co.siland.culvery.core.homeassistant.test.testHomeAssistant
import uk.co.siland.culvery.core.plugin.settingsDataStore
import uk.co.siland.culvery.core.plugin.test.soon

/** 11 §4.1: the Thermostat picker's device. */
@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(AndroidJUnit4::class)
class HaThermostatDeviceTest {
    @get:Rule val folder = TemporaryFolder()

    private val api = FakeHomeAssistantApi()
    private val dataScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    private fun TestScope.ha() = testHomeAssistant(api, scope = backgroundScope).also { runCurrent() }

    private fun device(ha: HomeAssistant): HaThermostatDevice {
        val settings = HeatingSettings(settingsDataStore(dataScope) { File(folder.root, "heating_settings.preferences_pb") })
        return HaThermostatDevice(ha, settings)
    }

    /** Fails if the picker lists the wrong domain, or its words or place are wrong. */
    @Test
    fun theDeviceIsTheThermostat() = runTest {
        val device = device(ha())
        assertThat(device.order).isEqualTo(20)
        assertThat(device.title).isEqualTo("Thermostat")
        assertThat(device.domain).isEqualTo("climate")
        assertThat(device.noneFound).isEqualTo("Home Assistant has no thermostat.")
        dataScope.cancel()
    }

    /** Fails if Settings watches anything but the chosen thermostat. */
    @Test
    fun watchedIsTheThermostat() = runTest {
        val device = device(ha())
        assertThat(soon { device.watched.first() }).isEmpty()
        soon { device.choose("climate.hall") }
        assertThat(soon { device.watched.first { it.isNotEmpty() } }).containsExactly("climate.hall")
        dataScope.cancel()
    }

    /** Fails if the line under the picker isn't the thermostat's state. */
    @Test
    fun theLineIsTheThermostatsState() = runTest {
        val ha = ha()
        val device = device(ha)
        api.states["climate.hall"] = entityState(
            "climate.hall",
            "heat",
            mapOf(
                "friendly_name" to "Hall",
                "preset_mode" to "none",
                "hvac_action" to "heating",
                "current_temperature" to 20.8,
                "temperature" to 21.2,
            ),
        )
        ha.read("climate.hall")
        assertThat(soon { device.line.first() }).isNull()
        soon { device.choose("climate.hall") }
        assertThat(soon { device.line.first { it != null } }).isEqualTo("Hall · 20.8 °C · heating to 21.2 °C")
        dataScope.cancel()
    }

    /** Fails if a choice isn't saved, or None doesn't clear it. */
    @Test
    fun choosingSavesAndNoneClears() = runTest {
        val device = device(ha())
        soon { device.choose("climate.hall") }
        assertThat(soon { device.chosen.first { it != null } }).isEqualTo("climate.hall")
        soon { device.choose(null) }
        assertThat(soon { device.chosen.first { it == null } }).isNull()
        dataScope.cancel()
    }
}
