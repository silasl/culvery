package uk.co.siland.culvery.capability.weather

import androidx.compose.runtime.Composable
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import uk.co.siland.culvery.capability.weather.ui.WeatherHeaderItem
import uk.co.siland.culvery.core.plugin.Capability
import uk.co.siland.culvery.core.plugin.HeaderItem
import uk.co.siland.culvery.core.plugin.HomeCard
import uk.co.siland.culvery.core.ui.Icons

private const val WEATHER_ID = "weather"

private const val HEADER_ITEM_ID = "weather"
private const val HEADER_ITEM_ORDER = 10

/** Weather lives in Home's header (9 §4.2): no card, no tab, no Settings page, no connection (D7); runs whenever a location is set. */
@Singleton
class WeatherCapability @Inject constructor(
    private val repo: WeatherRepository,
) : Capability {
    override val id = WEATHER_ID
    override val label = "Weather"
    override val icon = Icons.PARTLY_CLOUDY_DAY

    /** After Security's 50; weather has no tab, so nothing appears on the rail. */
    override val order = 60
    override val hasTab: Flow<Boolean> = flowOf(false)

    override fun cards(): Flow<List<HomeCard>> = flowOf(emptyList<HomeCard>())

    // Present only while shown, so the shell never draws a divider beside an empty item.
    override fun headerItems(): Flow<List<HeaderItem>> = repo.header.map { weather ->
        listOfNotNull(weather?.let { HeaderItem(HEADER_ITEM_ID, HEADER_ITEM_ORDER) { WeatherHeaderItem(it) } })
    }

    @Composable
    override fun TabContent() = Unit
}
