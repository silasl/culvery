package uk.co.siland.culvery.capability.plugs

import androidx.compose.runtime.Composable
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import uk.co.siland.culvery.core.plugin.Capability
import uk.co.siland.culvery.core.plugin.HomeCard
import uk.co.siland.culvery.core.ui.Icons

/** Plugs have no tab of their own (15a §4.5): [PlugsSection] draws them on the Lights tab. This keeps their place in the rail order. */
@Singleton
class PlugsCapability @Inject constructor() : Capability {
    override val id = "plugs"
    override val label = PlugsWords.PLUGS
    override val icon = Icons.POWER
    override val order = 55

    override val hasTab: Flow<Boolean> = flowOf(false)

    override fun cards(): Flow<List<HomeCard>> = flowOf(emptyList())

    @Composable
    override fun TabContent() = Unit
}
