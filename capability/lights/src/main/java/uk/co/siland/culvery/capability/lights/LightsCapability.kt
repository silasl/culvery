package uk.co.siland.culvery.capability.lights

import androidx.compose.runtime.Composable
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import uk.co.siland.culvery.core.plugin.Capability
import uk.co.siland.culvery.capability.lights.ui.LightsCardHost
import uk.co.siland.culvery.capability.lights.ui.LightsTabHost
import uk.co.siland.culvery.core.plugin.HomeCard
import uk.co.siland.culvery.core.plugin.HomeCardSize
import uk.co.siland.culvery.core.ui.Icons

const val LIGHTS_ID = "lights"

private const val LIGHTS_CARD_ID = "lights.home"

/** After Today (100) and Coming up (50), ahead of Music (30) and the forecast (25): the forecast gives way when Home is full. */
private const val LIGHTS_CARD_PRIORITY = 35

/** Hue on the wall (7 §3.1): rail slot 40, open to everyone, no badge. */
@Singleton
class LightsCapability @Inject constructor(
    private val bridges: Bridges,
    private val view: LightsView,
    private val actions: LightsActions,
    private val house: LightsHouse,
    private val follow: LightsFollow,
) : Capability {
    override val id = LIGHTS_ID
    override val label = LIGHTS
    override val icon = Icons.LIGHTBULB
    override val order = 40

    /** Once a bridge is paired and its provider is in this build (7 §3.1). */
    override val hasTab: Flow<Boolean> = bridges.system.map { it != null }.distinctUntilChanged()

    private val card = HomeCard(LIGHTS_CARD_ID, HomeCardSize.REGULAR, LIGHTS_CARD_PRIORITY) { LightsCardHost(view, actions, house, follow) }

    /** Home's card once a bridge is paired (7 §5.1). */
    override fun cards(): Flow<List<HomeCard>> = hasTab.map { if (it) listOf(card) else emptyList() }

    @Composable
    override fun TabContent() = LightsTabHost(view, actions, house, follow)
}
