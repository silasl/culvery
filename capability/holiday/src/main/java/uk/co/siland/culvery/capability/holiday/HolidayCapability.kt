package uk.co.siland.culvery.capability.holiday

import androidx.compose.runtime.Composable
import java.util.Optional
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import uk.co.siland.culvery.capability.holiday.ui.HOLIDAY_TILE_LINE_TAG
import uk.co.siland.culvery.capability.holiday.ui.HolidayTileHost
import uk.co.siland.culvery.capability.holiday.ui.holidayTileLine
import uk.co.siland.culvery.core.house.HouseModes
import uk.co.siland.culvery.core.house.ModeShelf
import uk.co.siland.culvery.core.plugin.Capability
import uk.co.siland.culvery.core.plugin.HomeCard
import uk.co.siland.culvery.core.plugin.TrayButton
import uk.co.siland.culvery.core.plugin.TrayWidth
import uk.co.siland.culvery.core.ui.Icons

/** Holiday mode's tile in Home's One-tap tray (11 §5.2), where [shelf] places it among the modes (15b Ruling 12): no tab and no card. */
@Singleton
class HolidayCapability internal constructor(
    private val actions: HolidayActions,
    private val house: HolidayHouse,
    private val shelf: ModeShelf?,
) : Capability {
    @Inject
    constructor(actions: HolidayActions, house: HolidayHouse, shelf: Optional<ModeShelf>) : this(actions, house, shelf.orElse(null))

    override val id = "holiday"
    override val label = HolidayWords.LABEL
    override val icon = Icons.BEACH_ACCESS
    override val order = 75
    override val hasTab: Flow<Boolean> = flowOf(false)

    override fun cards(): Flow<List<HomeCard>> = flowOf(emptyList())

    @Composable
    override fun TabContent() = Unit

    // What it can do depends on which seams are set up, which the sheet reads when it opens. With no shelf it is first.
    override fun trayButtons(): Flow<List<TrayButton>> = (shelf?.place(HouseModes.HOLIDAY) ?: flowOf(0)).map { place ->
        listOfNotNull(
            place?.let {
                TrayButton(HouseModes.HOLIDAY_TRAY_ID, 10 + it, TrayWidth.HALF, holidayTileLine(actions, house), HOLIDAY_TILE_LINE_TAG) {
                    HolidayTileHost(actions, house)
                }
            },
        )
    }
}
