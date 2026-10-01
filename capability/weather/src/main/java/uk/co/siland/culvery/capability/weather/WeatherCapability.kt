package uk.co.siland.culvery.capability.weather

import androidx.compose.runtime.Composable
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import uk.co.siland.culvery.capability.weather.ui.ForecastCardHost
import uk.co.siland.culvery.capability.weather.ui.WeatherHeaderItem
import uk.co.siland.culvery.core.plugin.Capability
import uk.co.siland.culvery.core.plugin.HeaderItem
import uk.co.siland.culvery.core.plugin.HomeCard
import uk.co.siland.culvery.core.plugin.HomeCardSize
import uk.co.siland.culvery.core.plugin.WallClock

private const val WEATHER_ID = "weather"
private const val FORECAST_CARD_ID = "weather.forecast"

/** After Today (100) and Coming up (50): row 2, col 2 (§4.2). */
private const val FORECAST_PRIORITY = 40
private const val HEADER_ITEM_ID = "weather"
private const val HEADER_ITEM_ORDER = 10

/** Weather on Home (4b design §3.1): no tab, no Settings page, no connection (D7); runs whenever a location is set. */
@Singleton
class WeatherCapability @Inject constructor(
    private val repo: WeatherRepository,
    private val clock: WallClock,
) : Capability {
    override val id = WEATHER_ID
    override val label = "Weather"
    override val icon = "partly_cloudy_day"

    /** No rail position is used. */
    override val order = 60
    override val hasTab: Flow<Boolean> = flowOf(false)

    private val forecastCard = HomeCard(FORECAST_CARD_ID, HomeCardSize.REGULAR, FORECAST_PRIORITY) { ForecastCardHost(repo, clock) }

    override fun cards(): Flow<List<HomeCard>> = flowOf(listOf(forecastCard))

    // Present only while shown, so the shell never draws a divider beside an empty item (ruling 4).
    override fun headerItems(): Flow<List<HeaderItem>> = repo.header.map { weather ->
        listOfNotNull(weather?.let { HeaderItem(HEADER_ITEM_ID, HEADER_ITEM_ORDER) { WeatherHeaderItem(it) } })
    }

    @Composable
    override fun TabContent() = Unit
}
