package uk.co.siland.culvery.capability.calendar

import androidx.compose.runtime.Composable
import kotlinx.coroutines.flow.Flow
import uk.co.siland.culvery.capability.calendar.ui.ConnectStepHost
import uk.co.siland.culvery.capability.calendar.ui.ReviewCalendarsHost
import uk.co.siland.culvery.core.plugin.SettingsPage
import uk.co.siland.culvery.core.plugin.SetupStep
import uk.co.siland.culvery.core.plugin.WallClock

/** 4a design §4.5. */
const val YOUR_CALENDARS = "Your calendars"
const val CALENDARS = "Calendars"

/** The wizard's Connect step (4a design §3.9): the Connect card, or Skip for now. Done once any connection exists. */
class CalendarConnectStep(repo: CalendarRepository, private val connections: CalendarConnections) : SetupStep {
    override val id = "calendar.connect"
    override val order = 400
    override val skippable = true
    override val done: Flow<Boolean> = repo.hasConnections

    @Composable
    override fun Content(onNext: () -> Unit) = ConnectStepHost(connections)
}

/** The wizard's Review calendars (4a design D3), shown once a connection exists. */
class ReviewCalendarsStep(
    repo: CalendarRepository,
    private val review: CalendarReview,
    private val connections: CalendarConnections,
    private val clock: WallClock,
) : SetupStep {
    override val id = "calendar.review"
    override val order = 410
    override val shown: Flow<Boolean> = repo.hasConnections
    override val done: Flow<Boolean> = repo.hasConnections

    @Composable
    override fun Content(onNext: () -> Unit) = ReviewCalendarsHost(review, connections, clock, YOUR_CALENDARS, offerConnect = false)
}

/** Settings › Calendars (4a design D4): Review calendars, and Connect for a service not yet connected (ruling 14). */
class CalendarsPage(private val review: CalendarReview, private val connections: CalendarConnections, private val clock: WallClock) : SettingsPage {
    override val id = "calendars"
    override val title = CALENDARS
    override val order = 400

    @Composable
    override fun Content() = ReviewCalendarsHost(review, connections, clock, CALENDARS, offerConnect = true)
}
