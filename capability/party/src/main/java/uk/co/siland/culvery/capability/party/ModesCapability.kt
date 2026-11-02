package uk.co.siland.culvery.capability.party

import androidx.compose.runtime.Composable
import java.util.Optional
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flowOf
import uk.co.siland.culvery.capability.party.ui.ModesPage
import uk.co.siland.culvery.capability.party.ui.ModeControls
import uk.co.siland.culvery.capability.party.ui.StoredModeTileHost
import uk.co.siland.culvery.core.house.HouseLights
import uk.co.siland.culvery.core.house.HouseModeSwitch
import uk.co.siland.culvery.core.house.HouseModes
import uk.co.siland.culvery.core.house.HouseMusic
import uk.co.siland.culvery.core.house.HousePlugs
import uk.co.siland.culvery.core.plugin.BuiltInModePage
import uk.co.siland.culvery.core.plugin.Capability
import uk.co.siland.culvery.core.plugin.HomeCard
import uk.co.siland.culvery.core.plugin.SettingsPage
import uk.co.siland.culvery.core.plugin.Toaster
import uk.co.siland.culvery.core.plugin.TrayButton
import uk.co.siland.culvery.core.plugin.TrayWidth
import uk.co.siland.culvery.core.ui.Icons

/** The stored modes' tiles in Home's One-tap tray, placed by [ModesShelf] (15b decision 3), and Settings › Modes: no tab and no card. */
@Singleton
class ModesCapability @Inject constructor(
    private val actions: StoredModeActions,
    private val house: StoredModesHouse,
    private val settings: ModesStore,
    private val shelf: ModesShelf,
    private val music: Optional<HouseMusic>,
    private val lights: Optional<HouseLights>,
    private val plugs: Optional<HousePlugs>,
    private val switches: Set<@JvmSuppressWildcards HouseModeSwitch>,
    private val builtIn: Set<@JvmSuppressWildcards BuiltInModePage>,
    private val toaster: Toaster,
) : Capability {
    override val id = "party"
    override val label = ModesWords.LABEL
    override val icon = Icons.CELEBRATION
    override val order = 76
    override val hasTab: Flow<Boolean> = flowOf(false)

    override fun cards(): Flow<List<HomeCard>> = flowOf(emptyList())

    @Composable
    override fun TabContent() = Unit

    // Mode buttons take orders 10–13 by place (15b Ruling 11).
    override fun trayButtons(): Flow<List<TrayButton>> = combine(settings.prefs, shelf.shown) { prefs, shown ->
        prefs.modes.mapNotNull { mode ->
            shown.indexOf(mode.id).takeIf { it >= 0 }?.let { place ->
                TrayButton("mode.${mode.id}", 10 + place, TrayWidth.HALF) { StoredModeTileHost(mode, actions, house) }
            }
        }
    }

    override fun settingsPages(): List<SettingsPage> = listOf(
        ModesPage(
            settings, ModeControls(actions, house, shelf::place, music.orElse(null), lights.orElse(null)),
            switches.firstOrNull { it.modeId == HouseModes.HOLIDAY }?.isOn ?: MutableStateFlow(false),
            shelf.room, plugs.orElse(null), toaster, builtIn,
        ),
    )
}
