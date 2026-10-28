package uk.co.siland.culvery.provider.plugs_homeassistant

import androidx.compose.runtime.Composable
import javax.inject.Inject
import uk.co.siland.culvery.capability.plugs.PlugActions
import uk.co.siland.culvery.capability.plugs.PlugSettings
import uk.co.siland.culvery.capability.plugs.PlugSystem
import uk.co.siland.culvery.capability.plugs.ui.PlugsSettingsHost
import uk.co.siland.culvery.core.homeassistant.HomeAssistantSection

/** Plugs under the device pickers in Settings › Other integrations (12 §4.2); nothing without a plug system. */
class PlugsSettingsSection @Inject constructor(
    systems: Set<@JvmSuppressWildcards PlugSystem>,
    private val actions: PlugActions,
    private val settings: PlugSettings,
) : HomeAssistantSection {
    private val system = systems.firstOrNull()

    override val order = 10

    @Composable
    override fun Content() {
        system?.let { PlugsSettingsHost(it, actions, settings) }
    }
}
