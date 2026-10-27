package uk.co.siland.culvery.core.homeassistant.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import uk.co.siland.culvery.core.homeassistant.EntityChoice
import uk.co.siland.culvery.core.ui.test.snap

/** Settings › Other integrations as Settings shows it, beside the 320 dp page list. */
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = "w1280dp-h1000dp-land-hdpi")
class OtherIntegrationsScreenshotTest {
    @get:Rule val compose = createComposeRule()

    private val hoover = DeviceUi(
        "Hoover", "vacuum", "Home Assistant has no hoover.", listOf(EntityChoice("vacuum.hoover", "Robot")), "vacuum.hoover", "Robot · Docked · 86%",
    )
    private val thermostat = DeviceUi(
        "Thermostat", "climate", "Home Assistant has no thermostat.",
        listOf(EntityChoice("climate.hall", "Hall"), EntityChoice("climate.landing", "Landing")), "climate.hall", "Hall · 20.8 °C · heating to 21.2 °C",
    )
    private val unlinked = OtherIntegrationsUi(linked = false, status = null, checking = false, checkLine = null, devices = emptyList())
    private val linked = unlinked.copy(linked = true, devices = listOf(hoover, thermostat))

    private fun snap(name: String, dark: Boolean, ui: OtherIntegrationsUi, address: String = "", token: String = "") =
        compose.snap(name, dark, Modifier.size(960.dp, 900.dp)) {
            InPane { OtherIntegrations(ui, address, {}, token, {}, {}, { _, _ -> }) }
        }

    /** Hosts a page as SettingsScreen does: inside a vertical scroll, in a column 720 dp wide. */
    @Composable
    private fun InPane(page: @Composable () -> Unit) {
        Column(Modifier.fillMaxWidth().fillMaxHeight().verticalScroll(rememberScrollState()).padding(horizontal = 48.dp, vertical = 40.dp)) {
            Column(verticalArrangement = Arrangement.spacedBy(20.dp), modifier = Modifier.widthIn(max = 720.dp)) { page() }
        }
    }

    @Test fun notLinkedDark() = snap("settings_other_not_linked_dark", true, unlinked)

    @Test fun notLinkedLight() = snap("settings_other_not_linked_light", false, unlinked)

    private val failed = unlinked.copy(
        checkLine = "Home Assistant didn’t accept that token. Create a new one and paste it here.",
    )

    @Test fun checkFailedDark() = snap("settings_other_check_failed_dark", true, failed, "http://192.168.1.20:8123/", "new-token")

    @Test fun checkFailedLight() = snap("settings_other_check_failed_light", false, failed, "http://192.168.1.20:8123/", "new-token")

    @Test fun linkedDark() = snap("settings_other_linked_dark", true, linked, "http://192.168.1.20:8123/")

    @Test fun linkedLight() = snap("settings_other_linked_light", false, linked, "http://192.168.1.20:8123/")

    private val noThermostat = linked.copy(devices = listOf(hoover, thermostat.copy(choices = emptyList(), chosen = null, line = null)))

    @Test fun noThermostatDark() = snap("settings_other_no_thermostat_dark", true, noThermostat, "http://192.168.1.20:8123/")

    @Test fun noThermostatLight() = snap("settings_other_no_thermostat_light", false, noThermostat, "http://192.168.1.20:8123/")

    private val problem = linked.copy(
        status = "Home Assistant no longer accepts Culvery’s token. Paste a new one below, then Check.",
    )

    @Test fun problemDark() = snap("settings_other_problem_dark", true, problem, "http://192.168.1.20:8123/")

    @Test fun problemLight() = snap("settings_other_problem_light", false, problem, "http://192.168.1.20:8123/")
}
