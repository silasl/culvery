package uk.co.siland.culvery.capability.lights

import androidx.compose.runtime.Composable
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import uk.co.siland.culvery.core.plugin.Capability
import uk.co.siland.culvery.core.plugin.HomeCard
import uk.co.siland.culvery.core.ui.Icons

const val LIGHTS_ID = "lights"

/** Hue on the wall (7 §3.1): rail slot 40, open to everyone, no badge. */
@Singleton
class LightsCapability @Inject constructor(
    private val bridges: Bridges,
) : Capability {
    override val id = LIGHTS_ID
    override val label = LIGHTS
    override val icon = Icons.LIGHTBULB
    override val order = 40

    /** Once a bridge is paired and its provider is in this build (7 §3.1). */
    override val hasTab: Flow<Boolean> = bridges.system.map { it != null }.distinctUntilChanged()

    override fun cards(): Flow<List<HomeCard>> = flowOf(emptyList())

    @Composable
    override fun TabContent() = Unit
}
