package uk.co.siland.culvery.provider.hoover_homeassistant

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
import uk.co.siland.culvery.capability.hoover.HooverSettings
import uk.co.siland.culvery.core.homeassistant.HomeAssistant
import uk.co.siland.culvery.core.homeassistant.test.FakeHomeAssistantApi
import uk.co.siland.culvery.core.homeassistant.test.entityState
import uk.co.siland.culvery.core.homeassistant.test.testHomeAssistant
import uk.co.siland.culvery.core.plugin.settingsDataStore
import uk.co.siland.culvery.core.plugin.test.soon

/** 11 §4.1: the Hoover picker's device. */
@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(AndroidJUnit4::class)
class HaHooverDeviceTest {
    @get:Rule val folder = TemporaryFolder()

    private val api = FakeHomeAssistantApi()
    private val dataScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    private fun TestScope.ha() = testHomeAssistant(api, scope = backgroundScope).also { runCurrent() }

    private fun device(ha: HomeAssistant): HaHooverDevice {
        val settings = HooverSettings(settingsDataStore(dataScope) { File(folder.root, "hoover_settings.preferences_pb") })
        return HaHooverDevice(ha, settings)
    }

    /** Fails if the picker lists the wrong domain, or its words or place are wrong. */
    @Test
    fun theDeviceIsTheHoover() = runTest {
        val device = device(ha())
        assertThat(device.order).isEqualTo(10)
        assertThat(device.title).isEqualTo("Hoover")
        assertThat(device.domain).isEqualTo("vacuum")
        assertThat(device.noneFound).isEqualTo("Home Assistant has no hoover.")
        dataScope.cancel()
    }

    /** Fails if Settings watches anything but the vacuum and the sensor named for it. */
    @Test
    fun watchedIsTheVacuumAndItsBattery() = runTest {
        val device = device(ha())
        soon { device.choose("vacuum.eufy_clean_x10_pro_omni") }
        assertThat(soon { device.watched.first { it.isNotEmpty() } })
            .containsExactly("vacuum.eufy_clean_x10_pro_omni", "sensor.eufy_clean_x10_pro_omni_battery")
        dataScope.cancel()
    }

    /** Fails if a choice isn't saved. */
    @Test
    fun choosingSavesTheId() = runTest {
        val device = device(ha())
        soon { device.choose("vacuum.hoover") }
        assertThat(soon { device.chosen.first { it != null } }).isEqualTo("vacuum.hoover")
        dataScope.cancel()
    }

    /** Fails if None doesn't clear the choice. */
    @Test
    fun noneClears() = runTest {
        val device = device(ha())
        soon { device.choose("vacuum.hoover") }
        assertThat(soon { device.chosen.first { it != null } }).isEqualTo("vacuum.hoover")
        soon { device.choose(null) }
        assertThat(soon { device.chosen.first { it == null } }).isNull()
        dataScope.cancel()
    }

    /** Fails if the line under the picker isn't the row's status. */
    @Test
    fun theLineIsTheStatus() = runTest {
        val ha = ha()
        val device = device(ha)
        api.states["vacuum.hoover"] = entityState("vacuum.hoover", "docked")
        api.states["sensor.hoover_battery"] = entityState("sensor.hoover_battery", "86")
        ha.read("vacuum.hoover")
        ha.read("sensor.hoover_battery")
        assertThat(soon { device.line.first() }).isNull()
        soon { device.choose("vacuum.hoover") }
        assertThat(soon { device.line.first { it != null } }).isEqualTo("Docked · 86%")
        dataScope.cancel()
    }
}
