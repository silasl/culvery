package uk.co.siland.culvery.capability.calendar.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.github.takahirom.roborazzi.captureRoboImage
import kotlinx.coroutines.flow.MutableStateFlow
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.GraphicsMode
import uk.co.siland.culvery.capability.calendar.CONFIG_ACCOUNT
import uk.co.siland.culvery.capability.calendar.CalendarRow
import uk.co.siland.culvery.capability.calendar.CalendarSource
import uk.co.siland.culvery.capability.calendar.READ_REFUSED
import uk.co.siland.culvery.capability.calendar.ReviewConnection
import uk.co.siland.culvery.capability.calendar.SourceMapping
import uk.co.siland.culvery.capability.calendar.StoredSource
import uk.co.siland.culvery.core.household.Person
import uk.co.siland.culvery.core.household.PersonId
import uk.co.siland.culvery.core.plugin.Connection
import uk.co.siland.culvery.core.plugin.ConnectionHealth
import uk.co.siland.culvery.core.plugin.HomeApp
import uk.co.siland.culvery.core.plugin.LocalShellNavigator
import uk.co.siland.culvery.core.plugin.SettingsPage
import uk.co.siland.culvery.core.setup.SettingsScreen
import uk.co.siland.culvery.core.setup.pages.KioskPage
import uk.co.siland.culvery.core.ui.Culvery
import uk.co.siland.culvery.core.ui.CulveryTheme

/** The tablet's canvas, and the wizard's 720 dp column (4a design §4.1). */
private val CANVAS_W = 1280.dp
private val CANVAS_H = 800.dp
private val COLUMN_W = 720.dp
private val MARGIN = 48.dp

@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class ReviewScreenshotTest {
    @get:Rule val compose = createComposeRule()

    private val now = 100_000_000L
    private val mia = Person(PersonId("mia"), "Mia", 0xFFE07BA8)
    private val sam = Person(PersonId("sam"), "Sam", 0xFF5B9BE0)
    private val google = Connection("g1", "calendar.google", "Google", mapOf(CONFIG_ACCOUNT to "family@example.com"))
    private val sources = listOf(
        StoredSource("g1", CalendarSource("family", "Family", writable = true), SourceMapping(PersonId.FAMILY, visible = true), isMaster = true),
        StoredSource("g1", CalendarSource("swim", "Mia's swimming", writable = false), SourceMapping(mia.id, visible = true)),
        StoredSource("g1", CalendarSource("chores", "Chores", writable = true), SourceMapping(PersonId.FAMILY, visible = false)),
    )

    private fun connections(health: ConnectionHealth) =
        listOf(ReviewConnection(CalendarRow(google, "Google Calendar", "calendar_month", health, now - 2 * 60_000), sources))

    @Composable
    private fun Review(title: String, health: ConnectionHealth, picking: String? = null, confirming: Confirming? = null) = ReviewCalendars(
        title, connections(health), listOf(Person.Family, sam, mia), now, false, picking, confirming, emptyList(), ReviewActions(),
    )

    /** Draws [content] on the canvas, runs [before] (a tap, say), then captures it as [name]. */
    private fun snap(name: String, dark: Boolean, before: () -> Unit = {}, content: @Composable () -> Unit) {
        compose.setContent {
            CulveryTheme(dark = dark) {
                CompositionLocalProvider(LocalShellNavigator provides RecordingNavigator()) {
                    Box(Modifier.testTag("shot").size(CANVAS_W, CANVAS_H).background(Culvery.colors.bg)) { content() }
                }
            }
        }
        before()
        compose.onNodeWithTag("shot").captureRoboImage("src/test/screenshots/$name.png")
    }

    private fun wizard(name: String, dark: Boolean, health: ConnectionHealth, picking: String? = null, confirming: Confirming? = null) =
        snap(name, dark) {
            Box(Modifier.padding(MARGIN).width(COLUMN_W)) { Review("Your calendars", health, picking, confirming) }
        }

    @Test fun reviewOkDark() = wizard("review_ok_dark", true, ConnectionHealth.Ok)
    @Test fun reviewOkLight() = wizard("review_ok_light", false, ConnectionHealth.Ok)
    @Test fun reviewReconnectDark() = wizard("review_reconnect_dark", true, ConnectionHealth.NeedsSignIn)
    @Test fun reviewReconnectLight() = wizard("review_reconnect_light", false, ConnectionHealth.NeedsSignIn)
    @Test fun reviewPersonPickerDark() = wizard("review_person_picker_dark", true, ConnectionHealth.Ok, picking = sourceKey(sources[1]))

    @Test
    fun reviewDisconnectDark() = wizard(
        "review_disconnect_dark", true, ConnectionHealth.Ok,
        confirming = Confirming(connections(ConnectionHealth.Ok).single().row, 3),
    )

    /** 4c design §6.4: an unreadable calendar and an unreadable master, in a healthy connection. */
    private fun readProblem(name: String, dark: Boolean) = snap(name, dark) {
        Box(Modifier.padding(MARGIN).width(COLUMN_W)) {
            ReviewCalendars(
                "Calendars",
                listOf(
                    ReviewConnection(
                        CalendarRow(google, "Google Calendar", "calendar_month", ConnectionHealth.Ok, now - 2 * 60_000),
                        listOf(sources[0].copy(readProblem = READ_REFUSED), sources[1].copy(readProblem = READ_REFUSED), sources[2]),
                    ),
                ),
                listOf(Person.Family, sam, mia), now, false, null, null, emptyList(), ReviewActions(),
            )
        }
    }

    @Test fun reviewReadProblemDark() = readProblem("review_read_problem_dark", true)
    @Test fun reviewReadProblemLight() = readProblem("review_read_problem_light", false)

    private fun connectStep(name: String, dark: Boolean) = snap(name, dark) {
        Box(Modifier.padding(MARGIN)) { ConnectStepCard(connectService = "Google Calendar", onConnect = {}) }
    }

    @Test fun connectStepDark() = connectStep("connect_step_dark", true)
    @Test fun connectStepLight() = connectStep("connect_step_light", false)

    private fun connectedStep(name: String, dark: Boolean) = snap(name, dark) {
        Box(Modifier.padding(MARGIN)) { ConnectedStepCard(connections(ConnectionHealth.Ok).map { it.row }) }
    }

    @Test fun connectedStepDark() = connectedStep("connected_step_dark", true)
    @Test fun connectedStepLight() = connectedStep("connected_step_light", false)

    private class StillPage(override val id: String, override val title: String, override val order: Int, val content: @Composable () -> Unit) : SettingsPage {
        @Composable
        override fun Content() = content()
    }

    private fun settings(name: String, dark: Boolean) {
        val pages = listOf(
            StillPage("location", "Home location", 0) {},
            StillPage("people", "People", 100) {},
            StillPage("calendars", "Calendars", 400) { Review("Calendars", ConnectionHealth.Ok) },
            KioskPage(object : HomeApp { override val isDefault = MutableStateFlow(true) }),
        )
        snap(name, dark, before = { compose.onNodeWithTag("settings_page_calendars").performClick() }) { SettingsScreen(pages, onClose = {}) }
    }

    @Test fun settingsCalendarsPageDark() = settings("settings_calendars_page_dark", true)
    @Test fun settingsCalendarsPageLight() = settings("settings_calendars_page_light", false)
}
