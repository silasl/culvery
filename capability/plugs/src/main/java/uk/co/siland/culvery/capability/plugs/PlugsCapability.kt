package uk.co.siland.culvery.capability.plugs

import androidx.compose.runtime.Composable
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import uk.co.siland.culvery.capability.plugs.ui.PlugsTabHost
import uk.co.siland.culvery.core.house.HouseModeInfo
import uk.co.siland.culvery.core.plugin.Capability
import uk.co.siland.culvery.core.plugin.HomeCard
import uk.co.siland.culvery.core.ui.Icons

/** The Plugs tab, after the door (12 §4.3): shown once Home Assistant is linked and at least one plug is shown. */
@Singleton
class PlugsCapability @Inject constructor(
    systems: Set<@JvmSuppressWildcards PlugSystem>,
    private val actions: PlugActions,
    private val settings: PlugSettings,
    private val modes: Set<@JvmSuppressWildcards HouseModeInfo>,
) : Capability {
    private val system = systems.firstOrNull()

    override val id = "plugs"
    override val label = PlugsWords.PLUGS
    override val icon = Icons.POWER
    override val order = 55

    override val hasTab: Flow<Boolean> =
        system?.plugs?.map { !it.isNullOrEmpty() }?.distinctUntilChanged() ?: flowOf(false)

    override fun cards(): Flow<List<HomeCard>> = flowOf(emptyList())

    @Composable
    override fun TabContent() {
        system?.let { PlugsTabHost(it, actions, settings, modes) }
    }
}
