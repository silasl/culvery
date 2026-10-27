package uk.co.siland.culvery.capability.holiday

import androidx.compose.runtime.Composable
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import uk.co.siland.culvery.capability.holiday.ui.HolidayTileHost
import uk.co.siland.culvery.core.plugin.Capability
import uk.co.siland.culvery.core.plugin.HomeCard
import uk.co.siland.culvery.core.plugin.TrayButton
import uk.co.siland.culvery.core.ui.Icons

/** Holiday mode's tile, first in Home's One-tap tray (11 §5.2): no tab and no card. */
@Singleton
class HolidayCapability @Inject constructor(
    private val actions: HolidayActions,
    private val house: HolidayHouse,
) : Capability {
    override val id = "holiday"
    override val label = HolidayWords.LABEL
    override val icon = Icons.BEACH_ACCESS
    override val order = 75
    override val hasTab: Flow<Boolean> = flowOf(false)

    override fun cards(): Flow<List<HomeCard>> = flowOf(emptyList())

    @Composable
    override fun TabContent() = Unit

    // Always there: what it can do depends on which seams are set up, which the sheet reads when it opens.
    override fun trayButtons(): Flow<List<TrayButton>> = flowOf(listOf(TrayButton("holiday.mode", 10) { HolidayTileHost(actions, house) }))
}
