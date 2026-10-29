package uk.co.siland.culvery.capability.party

import androidx.compose.runtime.Composable
import java.util.Optional
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import uk.co.siland.culvery.capability.party.ui.PartyPage
import uk.co.siland.culvery.capability.party.ui.PartySettingsHost
import uk.co.siland.culvery.capability.party.ui.PartyTileHost
import uk.co.siland.culvery.core.house.HouseLights
import uk.co.siland.culvery.core.house.HouseMusic
import uk.co.siland.culvery.core.house.HousePlugs
import uk.co.siland.culvery.core.plugin.Capability
import uk.co.siland.culvery.core.plugin.HomeCard
import uk.co.siland.culvery.core.plugin.SettingsPage
import uk.co.siland.culvery.core.plugin.TrayButton
import uk.co.siland.culvery.core.ui.Icons

/** Party mode's tile, beside Holiday's in Home's One-tap tray (13 §2.6), and its Settings page (13 §3): no tab and no card. */
@Singleton
class PartyCapability @Inject constructor(
    private val actions: PartyActions,
    private val house: PartyHouse,
    private val settings: PartySettings,
    private val music: Optional<HouseMusic>,
    private val lights: Optional<HouseLights>,
    plugs: Optional<HousePlugs>,
) : Capability {
    override val id = "party"
    override val label = PartyWords.LABEL
    override val icon = Icons.CELEBRATION
    override val order = 76
    override val hasTab: Flow<Boolean> = flowOf(false)

    override fun cards(): Flow<List<HomeCard>> = flowOf(emptyList())

    @Composable
    override fun TabContent() = Unit

    // Always there: what it can do depends on which seams are set up, which the sheet reads when it opens.
    override fun trayButtons(): Flow<List<TrayButton>> = flowOf(listOf(TrayButton("party.mode", 20) { PartyTileHost(actions, house) }))

    override fun settingsPages(): List<SettingsPage> = listOf(PartyPage { PartySettingsHost(settings, music.orElse(null), lights.orElse(null)) })
}
