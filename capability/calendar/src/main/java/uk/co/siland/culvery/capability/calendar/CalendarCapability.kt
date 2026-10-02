package uk.co.siland.culvery.capability.calendar

import androidx.compose.runtime.Composable
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import uk.co.siland.culvery.capability.calendar.ui.ComingUpCardHost
import uk.co.siland.culvery.capability.calendar.ui.ConnectCardHost
import uk.co.siland.culvery.capability.calendar.ui.TodayCardHost
import uk.co.siland.culvery.capability.calendar.ui.WeekViewHost
import uk.co.siland.culvery.capability.calendar.ui.rememberConnector
import uk.co.siland.culvery.capability.calendar.ui.rememberToday
import uk.co.siland.culvery.core.plugin.Capability
import uk.co.siland.culvery.core.plugin.HomeCard
import uk.co.siland.culvery.core.plugin.HomeCardSize
import uk.co.siland.culvery.core.plugin.HouseholdClock
import uk.co.siland.culvery.core.plugin.SettingsPage
import uk.co.siland.culvery.core.plugin.SetupStep
import uk.co.siland.culvery.core.plugin.rememberNowMillis
import uk.co.siland.culvery.core.ui.Icons

const val CALENDAR_TAB_ID = "calendar"
const val CONNECT_CARD_ID = "calendar.connect"
const val TODAY_CARD_ID = "calendar.today"
const val COMING_UP_CARD_ID = "calendar.comingUp"

@Singleton
class CalendarCapability @Inject constructor(
    private val repo: CalendarRepository,
    private val clock: HouseholdClock,
    private val editor: CalendarEditor,
    private val connections: CalendarConnections,
    private val review: CalendarReview,
) : Capability {
    override val id = CALENDAR_TAB_ID
    override val label = "Calendar"
    override val icon = Icons.CALENDAR_MONTH
    override val order = 10
    override val hasTab: Flow<Boolean> = repo.hasConnections

    private val steps: List<SetupStep> = listOf(
        CalendarConnectStep(repo, connections),
        ReviewCalendarsStep(repo, review, connections, clock),
    )
    private val pages: List<SettingsPage> = listOf(CalendarsPage(review, connections, clock))

    override fun setupSteps(): List<SetupStep> = steps

    override fun settingsPages(): List<SettingsPage> = pages

    override fun cards(): Flow<List<HomeCard>> = repo.hasConnections.map { connected ->
        if (!connected) {
            listOf(HomeCard(CONNECT_CARD_ID, HomeCardSize.TALL, 100) { ConnectCardHost(connections) })
        } else {
            listOf(
                HomeCard(TODAY_CARD_ID, HomeCardSize.TALL, 100) { rememberToday(clock)?.let { TodayCardHost(repo, editor, it) } },
                HomeCard(COMING_UP_CARD_ID, HomeCardSize.WIDE, 50) { rememberToday(clock)?.let { ComingUpCardHost(repo, it) } },
            )
        }
    }

    @Composable
    override fun TabContent() {
        val connector = rememberConnector(connections)
        val today = rememberToday(clock) ?: return
        WeekViewHost(repo, editor, today = today, nowMillis = rememberNowMillis(clock), onReconnect = connector::reconnect)
    }
}
