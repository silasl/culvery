package uk.co.siland.culvery.capability.lights

import androidx.compose.runtime.Composable
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import uk.co.siland.culvery.capability.lights.ui.LightsCardHost
import uk.co.siland.culvery.capability.lights.ui.LightsPage
import uk.co.siland.culvery.capability.lights.ui.LightsSettingsHost
import uk.co.siland.culvery.capability.lights.ui.LightsTabHost
import uk.co.siland.culvery.core.plugin.Capability
import uk.co.siland.culvery.core.plugin.HomeCard
import uk.co.siland.culvery.core.plugin.HomeCardSize
import uk.co.siland.culvery.core.plugin.LightsTabSection
import uk.co.siland.culvery.core.plugin.SettingsPage
import uk.co.siland.culvery.core.plugin.Toaster
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
    private val settings: LightsSettings,
    private val connect: LightsConnect,
    private val toaster: Toaster,
    private val sections: Set<@JvmSuppressWildcards LightsTabSection>,
) : Capability {
    override val id = LIGHTS_ID
    override val label = LIGHTS
    override val icon = Icons.LIGHTBULB
    override val order = 40

    private val sectionList = sections.toList()

    // A combine of no flows never emits, so a build with no section says false itself.
    private val sectionShown: Flow<Boolean> =
        if (sectionList.isEmpty()) flowOf(false) else combine(sectionList.map { it.shown }) { shown -> shown.any { it } }.distinctUntilChanged()

    private val bridgeAndPlugs: Flow<Pair<Boolean, Boolean>> = combine(view.paired, sectionShown, ::Pair)

    /** Once a bridge is paired and its provider is in this build (7 §3.1), or a plugs section has something to show (15a §4.5). */
    override val hasTab: Flow<Boolean> = bridgeAndPlugs.map { (bridge, plugs) -> bridge || plugs }.distinctUntilChanged()

    private val plugsOnly: Flow<Boolean> = bridgeAndPlugs.map { (bridge, plugs) -> !bridge && plugs }.distinctUntilChanged()

    /** A house with plugs and no Hue Bridge has a Plugs tab (15a §4.5). */
    override val railLabel: Flow<String> = plugsOnly.map { if (it) PLUGS else LIGHTS }

    override val railIcon: Flow<String> = plugsOnly.map { if (it) Icons.POWER else Icons.LIGHTBULB }

    private val card = HomeCard(LIGHTS_CARD_ID, HomeCardSize.REGULAR, LIGHTS_CARD_PRIORITY) { LightsCardHost(view, actions, follow) }

    /** Home's card once a bridge is paired (7 §5.1). */
    override fun cards(): Flow<List<HomeCard>> = view.paired.map { if (it) listOf(card) else emptyList() }

    @Composable
    override fun TabContent() = LightsTabHost(view, actions, house, follow, sectionList)

    private val pages: List<SettingsPage> =
        if (bridges.providers.isEmpty()) emptyList() else listOf(LightsPage { LightsSettingsHost(settings, bridges, view, connect, toaster) })

    override fun settingsPages(): List<SettingsPage> = pages
}
