package uk.co.siland.culvery.shell.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onRoot
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.github.takahirom.roborazzi.captureRoboImage
import java.time.LocalDate
import java.time.LocalDateTime
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.GraphicsMode
import uk.co.siland.culvery.capability.calendar.DayUi
import uk.co.siland.culvery.capability.calendar.EventUi
import uk.co.siland.culvery.capability.calendar.ui.ComingUpCard
import uk.co.siland.culvery.capability.calendar.ui.TodayCard
import uk.co.siland.culvery.core.access.ui.PinPadSheet
import uk.co.siland.culvery.core.household.Person
import uk.co.siland.culvery.core.household.PersonId
import uk.co.siland.culvery.core.plugin.HomeCard
import uk.co.siland.culvery.core.plugin.HomeCardPlacer
import uk.co.siland.culvery.core.plugin.HomeCardSize
import uk.co.siland.culvery.core.plugin.LocalShellNavigator
import uk.co.siland.culvery.core.plugin.ShellNavigator
import uk.co.siland.culvery.core.ui.CulveryTheme
import uk.co.siland.culvery.shell.SessionChip
import uk.co.siland.culvery.shell.ShellUiState
import uk.co.siland.culvery.shell.TabItem

private object NoNavigation : ShellNavigator {
    override fun openTab(id: String) = Unit
    override fun openSettings() = Unit
}

@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class ShellScreenshotTest {
    @get:Rule val compose = createComposeRule()

    private val at = LocalDateTime.of(2026, 9, 23, 11, 54)

    private val alex = Person(PersonId("alex"), "Alex", 0xFF4CB387)
    private val sam = Person(PersonId("sam"), "Sam", 0xFF5B9BE0)
    private val mia = Person(PersonId("mia"), "Mia", 0xFFE07BA8)

    private fun event(title: String, time: String, person: Person, recurring: Boolean = false) =
        EventUi(title, title, time, time.substringBefore('–'), person, allDay = false, recurring = recurring, startSort = 0)

    private fun allDay(title: String, person: Person, recurring: Boolean = false) =
        EventUi(title, title, "All day", "All day", person, allDay = true, recurring = recurring, startSort = 0)

    private val today = listOf(
        event("School run", "07:45–08:30", sam),
        event("Boiler service", "10:00–11:00", Person.Family),
        event("Plumber quote call", "13:00–13:30", Person.Family),
        event("Swimming", "16:00–17:00", mia, recurring = true),
        event("Dinner with Jo & Priya", "19:30–21:00", alex),
    )

    private val comingUp = listOf(
        DayUi(LocalDate.of(2026, 9, 24), listOf(event("Office day", "09:00–17:00", alex), event("Football", "18:00–19:00", mia))),
        DayUi(
            LocalDate.of(2026, 9, 25),
            listOf(allDay("Bin day", Person.Family, recurring = true), event("Dentist", "12:30–13:30", sam)),
        ),
        DayUi(
            LocalDate.of(2026, 9, 26),
            listOf(
                allDay("INSET day — no school", Person.Family),
                event("Piano", "15:30–16:30", mia, recurring = true),
                event("Book club", "20:00–22:00", sam),
            ),
        ),
    )

    private fun calendarHome(dark: Boolean) = ShellUiState(
        now = at,
        dark = dark,
        tabs = listOf(TabItem("calendar", "Calendar", "calendar_month")),
        homeCards = HomeCardPlacer.place(
            listOf(
                HomeCard("calendar.today", HomeCardSize.TALL, 100) { TodayCard(today) },
                HomeCard("calendar.comingUp", HomeCardSize.WIDE, 50) { ComingUpCard(comingUp) },
            ),
        ),
    )

    private fun snap(
        name: String,
        dark: Boolean,
        state: ShellUiState = ShellUiState(now = at, dark = dark),
        overlay: @Composable () -> Unit = {},
    ) {
        compose.setContent {
            CompositionLocalProvider(LocalShellNavigator provides NoNavigation) {
                CulveryTheme(dark = dark) {
                    Box(Modifier.fillMaxSize()) {
                        CulveryShell(
                            state = state,
                            onSelectTab = {},
                            onOpenSettings = {},
                            onLockSession = {},
                            onToggleThemePreview = {},
                            tabContent = {},
                        )
                        overlay()
                    }
                }
            }
        }
        compose.onRoot().captureRoboImage("src/test/screenshots/$name.png")
    }

    @Test
    fun homeEmptyDark() = snap("home_empty_dark", dark = true)

    @Test
    fun homeEmptyLight() = snap("home_empty_light", dark = false)

    @Test
    fun homeWithSessionDark() = snap(
        "home_session_dark",
        dark = true,
        state = ShellUiState(now = at, dark = true, session = SessionChip("Admin", 0xFF4CB387)),
    )

    @Test
    fun homeWithCalendarDark() = snap("home_calendar_dark", dark = true, state = calendarHome(dark = true))

    @Test
    fun homeWithCalendarLight() = snap("home_calendar_light", dark = false, state = calendarHome(dark = false))

    @Test
    fun settingsDark() = snap("settings_dark", dark = true) {
        SettingsPlaceholder(onExitKiosk = {}, onClose = {})
    }

    @Test
    fun pinPadDark() = snap("pin_pad_dark", dark = true) {
        PinPadSheet("Change settings", error = null, lockedUntilMillis = null, onSubmit = {}, onCancel = {})
    }

    @Test
    fun pinPadLight() = snap("pin_pad_light", dark = false) {
        PinPadSheet("Change settings", error = null, lockedUntilMillis = null, onSubmit = {}, onCancel = {})
    }
}
