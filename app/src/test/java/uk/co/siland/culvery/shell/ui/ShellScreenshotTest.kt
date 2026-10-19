package uk.co.siland.culvery.shell.ui

import android.graphics.Bitmap
import android.graphics.Color
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onRoot
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.github.takahirom.roborazzi.captureRoboImage
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.GraphicsMode
import uk.co.siland.culvery.capability.calendar.DayUi
import uk.co.siland.culvery.capability.calendar.EventRef
import uk.co.siland.culvery.capability.calendar.EventUi
import uk.co.siland.culvery.capability.calendar.ui.ComingUpCard
import uk.co.siland.culvery.capability.calendar.ui.TodayCard
import uk.co.siland.culvery.capability.door.ui.DoorCard
import uk.co.siland.culvery.capability.door.ui.DoorCardUi
import uk.co.siland.culvery.capability.lights.LightRoom
import uk.co.siland.culvery.capability.lights.LightScene
import uk.co.siland.culvery.capability.lights.ui.HouseActions
import uk.co.siland.culvery.capability.lights.ui.LightsCard
import uk.co.siland.culvery.capability.lights.ui.LightsTab
import uk.co.siland.culvery.capability.lights.ui.MainRoomActions
import uk.co.siland.culvery.capability.lights.ui.RoomActions
import uk.co.siland.culvery.capability.music.HomePick
import uk.co.siland.culvery.capability.music.NowPlaying
import uk.co.siland.culvery.capability.music.PlayState
import uk.co.siland.culvery.capability.music.SpeakerGroup
import uk.co.siland.culvery.capability.music.SpeakerRoom
import uk.co.siland.culvery.capability.music.ui.ArtSource
import uk.co.siland.culvery.capability.music.ui.GroupActions
import uk.co.siland.culvery.capability.music.ui.LocalArtSource
import uk.co.siland.culvery.capability.music.ui.MusicCard
import uk.co.siland.culvery.capability.music.ui.MusicTab
import uk.co.siland.culvery.capability.weather.Condition
import uk.co.siland.culvery.capability.weather.DailyWeather
import uk.co.siland.culvery.capability.weather.HeaderWeather
import uk.co.siland.culvery.capability.weather.ui.WeatherHeaderItem
import uk.co.siland.culvery.core.access.PinError
import uk.co.siland.culvery.core.access.PinReason
import uk.co.siland.culvery.core.access.ui.PinPadSheet
import uk.co.siland.culvery.core.household.Person
import uk.co.siland.culvery.core.household.PersonId
import uk.co.siland.culvery.core.plugin.CLOSING_WARNING_MS
import uk.co.siland.culvery.core.plugin.HeaderItem
import uk.co.siland.culvery.core.plugin.HomeCard
import uk.co.siland.culvery.core.plugin.HomeCardPlacer
import uk.co.siland.culvery.core.plugin.HomeCardSize
import uk.co.siland.culvery.core.plugin.LocalShellNavigator
import uk.co.siland.culvery.core.plugin.RailBadge
import uk.co.siland.culvery.core.plugin.test.FakeUserActivity
import uk.co.siland.culvery.core.plugin.test.RecordingNavigator
import uk.co.siland.culvery.core.ui.CulveryTheme
import uk.co.siland.culvery.core.ui.Icons
import uk.co.siland.culvery.core.ui.WithFontScale
import uk.co.siland.culvery.shell.SessionUi
import uk.co.siland.culvery.shell.ShellUiState
import uk.co.siland.culvery.shell.TabItem
import uk.co.siland.culvery.shell.ToastMessage

@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class ShellScreenshotTest {
    @get:Rule val compose = createComposeRule()

    private val at = LocalDateTime.of(2026, 9, 23, 11, 54)

    private val alex = Person(PersonId("alex"), "Alex", 0xFF4CB387)
    private val sam = Person(PersonId("sam"), "Sam", 0xFF5B9BE0)
    private val mia = Person(PersonId("mia"), "Mia", 0xFFE07BA8)

    private fun event(title: String, time: String, person: Person, recurring: Boolean = false) = EventUi(
        EventRef("sample", "family", title), title, time, time.substringBefore('–'), person,
        allDay = false, recurring = recurring, startSort = 0,
    )

    private fun allDay(title: String, person: Person, recurring: Boolean = false) =
        EventUi(EventRef("sample", "family", title), title, "All day", "All day", person, allDay = true, recurring = recurring, startSort = 0)

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
        dark = dark,
        tabs = listOf(TabItem("calendar", "Calendar", "calendar_month")),
        homeCards = HomeCardPlacer.place(
            listOf(
                HomeCard("calendar.today", HomeCardSize.TALL, 100) { TodayCard(today, onAdd = {}) },
                HomeCard("calendar.comingUp", HomeCardSize.WIDE, 50) { ComingUpCard(comingUp) },
            ),
        ),
    )

    private val forecastDays = listOf(
        DailyWeather(at.toLocalDate(), Condition.PARTLY_CLOUDY, 19.0, 11.0, LocalTime.of(6, 50), LocalTime.of(19, 2)),
        DailyWeather(at.toLocalDate().plusDays(1), Condition.RAIN, 17.0, 10.0, LocalTime.of(6, 52), LocalTime.of(19, 0)),
        DailyWeather(at.toLocalDate().plusDays(2), Condition.CLEAR, 21.0, 12.0, LocalTime.of(6, 53), LocalTime.of(18, 58)),
    )

    /** The whole Home with Today, Coming up and the weather in the header (9 §4.2). */
    private fun weatherHome(dark: Boolean) = calendarHome(dark).copy(
        homeCards = HomeCardPlacer.place(
            listOf(
                HomeCard("calendar.today", HomeCardSize.TALL, 100) { TodayCard(today) },
                HomeCard("calendar.comingUp", HomeCardSize.WIDE, 50) { ComingUpCard(comingUp) },
            ),
        ),
        headerItems = listOf(
            HeaderItem("weather", 10) { WeatherHeaderItem(HeaderWeather.Now(Condition.PARTLY_CLOUDY, night = false, temperature = 17.0, high = 19.0, low = 11.0, days = forecastDays)) },
        ),
    )

    private val relax = LightScene("relax", "Relax")
    private val bright = LightScene("bright", "Bright")
    private val rooms = listOf(
        LightRoom("lounge", "Lounge", on = true, brightness = 70, scenes = listOf(relax, bright), activeSceneId = relax.id, dimmable = true),
        LightRoom("kitchen", "Kitchen", on = false, brightness = 70, scenes = listOf(relax, bright), activeSceneId = null, dimmable = true),
        LightRoom("hall", "Hall", on = true, brightness = 100, scenes = emptyList(), activeSceneId = null, dimmable = false),
        LightRoom("garden", "Garden", on = true, brightness = 30, scenes = emptyList(), activeSceneId = null, dimmable = true),
    )
    private val noHouse = HouseActions({}, {})

    private fun speakers(id: String, vararg names: String, state: PlayState, nowPlaying: NowPlaying?, volume: Int) = SpeakerGroup(
        id, names.map { SpeakerRoom("$it-id", it, volume) }, "${names.first()}-id", state, nowPlaying,
        artUrl = nowPlaying?.let { "art" }, canSkip = nowPlaying !is NowPlaying.Radio, volume = volume, startedAtMillis = null,
    )

    private val groups = listOf(
        speakers("l", "Lounge", "Dining room", state = PlayState.PLAYING, nowPlaying = NowPlaying.Radio("Culvery FM", "The Breakfast Show"), volume = 40),
        speakers("k", "Kitchen", state = PlayState.PAUSED, nowPlaying = NowPlaying.Track("Song", "Artist", "Album"), volume = 30),
        speakers("b", "Bedroom", state = PlayState.STOPPED, nowPlaying = null, volume = 30),
    )

    // In capability order, as the rail lists them once each is set up.
    private val allTabs = listOf(
        TabItem("calendar", "Calendar", Icons.CALENDAR_MONTH),
        TabItem("music", "Music", Icons.SPEAKER),
        TabItem("lights", "Lights", Icons.LIGHTBULB),
        TabItem("door", "Door", Icons.DOORBELL),
    )

    /** Every card wanting to show, at the capabilities' sizes and priorities: Lights (35) above Music (30) on the right. */
    private fun fullHome(dark: Boolean) = weatherHome(dark).copy(
        tabs = allTabs,
        homeCards = HomeCardPlacer.place(
            listOf(
                HomeCard("calendar.today", HomeCardSize.TALL, 100) { TodayCard(today) },
                HomeCard("calendar.comingUp", HomeCardSize.WIDE, 50) { ComingUpCard(comingUp) },
                HomeCard("lights.card", HomeCardSize.REGULAR, 35) {
                    LightsCard(rooms[0], holidayOn = false, allOffLit = true, run = null, status = null, line = null, MainRoomActions({}, {}), noHouse)
                },
                HomeCard("music.card", HomeCardSize.REGULAR, 30) { MusicCard(HomePick(groups[0], nothingPlaying = false), null, null, GroupActions.None) },
            ),
        ),
    )

    private fun lightsHome(dark: Boolean, music: Boolean) = calendarHome(dark).copy(
        tabs = allTabs,
        homeCards = HomeCardPlacer.place(
            listOfNotNull(
                HomeCard("calendar.today", HomeCardSize.TALL, 100) { TodayCard(today) },
                HomeCard("calendar.comingUp", HomeCardSize.WIDE, 50) { ComingUpCard(comingUp) },
                HomeCard("lights.card", HomeCardSize.REGULAR, 35) {
                    LightsCard(rooms[0], holidayOn = false, allOffLit = true, run = null, status = null, line = null, MainRoomActions({}, {}), noHouse)
                },
                HomeCard("music.card", HomeCardSize.REGULAR, 30) {
                    MusicCard(HomePick(groups[0], nothingPlaying = false), null, null, GroupActions.None)
                }.takeIf { music },
            ),
        ),
    )

    private fun snap(
        name: String,
        dark: Boolean,
        state: ShellUiState = ShellUiState(dark = dark),
        fontScale: Float = 1f,
        watching: Boolean = false,
        tabContent: @Composable (String) -> Unit = {},
        overlay: @Composable () -> Unit = {},
    ) {
        val grey = Bitmap.createBitmap(96, 96, Bitmap.Config.ARGB_8888).apply { eraseColor(Color.GRAY) }.asImageBitmap()
        compose.setContent {
            CompositionLocalProvider(LocalShellNavigator provides RecordingNavigator(), LocalArtSource provides ArtSource { grey }) {
                CulveryTheme(dark = dark) {
                    WithFontScale(fontScale) {
                        Box(Modifier.fillMaxSize()) {
                            CulveryShell(
                                state = state,
                                now = { at },
                                onSelectTab = {},
                                onOpenSettings = {},
                                onSignOut = {},
                                onToggleThemePreview = {},
                                watching = watching,
                                tabContent = tabContent,
                            )
                            overlay()
                        }
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
        state = ShellUiState(dark = true, session = SessionUi("Alex", "Admin")),
    )

    /** The camera running: the pill sits left of the signed-in block (9 §6.7). */
    @Test
    fun homeWatchingDark() = snap(
        "home_watching_dark", dark = true, watching = true,
        state = ShellUiState(dark = true, session = SessionUi("Alex", "Admin")),
    )

    @Test
    fun homeWatchingLight() = snap(
        "home_watching_light", dark = false, watching = true,
        state = ShellUiState(dark = false, session = SessionUi("Alex", "Admin")),
    )

    /** Previewing the day theme by day's end: the control reads "Day · Preview" (9 §4.1). */
    @Test
    fun homePreviewLight() = snap(
        "home_preview_light", dark = false,
        state = ShellUiState(dark = false, previewing = true, session = SessionUi("Alex", "Admin")),
    )

    @Test
    fun toastDark() {
        // The toast hides itself after 3.5 s; hold the clock so it is still there for the capture.
        compose.mainClock.autoAdvance = false
        snap("home_toast_dark", dark = true) {
            ToastLayer(ToastMessage(1, "Mia can only change events they created.", "info"), FakeUserActivity()) {}
        }
    }

    /** A warning with [left] ms of its 20 s to go, on a clock that holds still so the capture shows exactly that. */
    private fun warningSnap(name: String, text: String, tag: String, left: Long) {
        compose.mainClock.autoAdvance = false
        snap(name, dark = true) { ClosingWarningLayer(text, tag, endsAt = 100_000L + left, nowMillis = { 100_000L }) }
    }

    @Test
    fun settingsWarningDark() = warningSnap("settings_warning_dark", SETTINGS_CLOSING, "settings_warning", CLOSING_WARNING_MS)

    @Test
    fun settingsWarningHalfDark() = warningSnap("settings_warning_half_dark", SETTINGS_CLOSING, "settings_warning", CLOSING_WARNING_MS / 2)

    @Test
    fun sheetWarningDark() = warningSnap("sheet_warning_dark", SHEET_CLOSING, "sheet_warning", CLOSING_WARNING_MS)

    /** The wide card alone on the right, with neither Lights nor Music (9 §6.1). */
    @Test
    fun homeComingUpDark() = snap("home_coming_up_dark", dark = true, state = calendarHome(dark = true))

    /** One REGULAR card takes the whole right column (9 §6.1). */
    @Test
    fun homeLightsOnlyDark() = snap("home_lights_only_dark", dark = true, state = lightsHome(dark = true, music = false))

    /** Lights above Music, Coming up gone (9 §6.1). */
    @Test
    fun homeLightsMusicDark() = snap("home_lights_music_dark", dark = true, state = lightsHome(dark = true, music = true))

    @Test
    fun homeWithCalendarLight() = snap("home_calendar_light", dark = false, state = calendarHome(dark = false))

    @Test
    fun homeRailBadgeDark() = snap(
        "home_rail_badge_dark", dark = true,
        state = calendarHome(dark = true).copy(tabs = listOf(TabItem("calendar", "Calendar", "calendar_month", RailBadge.Count(2, "2 unseen rings")))),
    )

    @Test
    fun homeWithWeatherDark() = snap("home_weather_dark", dark = true, state = weatherHome(dark = true))

    @Test
    fun homeWithWeatherLight() = snap("home_weather_light", dark = false, state = weatherHome(dark = false))

    @Test
    fun pinPadDark() = snap("pin_pad_dark", dark = true) {
        PinPadSheet("Change settings", PinReason.Generic, error = null, lockedUntilMillis = null, onSubmit = {}, onCancel = {})
    }

    @Test
    fun pinPadLight() = snap("pin_pad_light", dark = false) {
        PinPadSheet("Change settings", PinReason.Generic, error = null, lockedUntilMillis = null, onSubmit = {}, onCancel = {})
    }

    @Test
    fun pinPadWrongDark() = snap("pin_pad_wrong_dark", dark = true) {
        PinPadSheet("Change settings", PinReason.Generic, error = PinError.WrongPin, lockedUntilMillis = null, onSubmit = {}, onCancel = {})
    }

    @Test
    fun homeFontScale15() = snap("home_fs15", dark = false, state = weatherHome(dark = false), fontScale = 1.5f)

    @Test
    fun pinPadFontScale15() = snap("pin_pad_fs15", dark = false, fontScale = 1.5f) {
        PinPadSheet("Change settings", PinReason.Generic, error = PinError.WrongPin, lockedUntilMillis = null, onSubmit = {}, onCancel = {})
    }

    @Test
    fun homeFullDark() = snap("home_full_dark", dark = true, state = fullHome(dark = true))

    @Test
    fun homeFullLight() = snap("home_full_light", dark = false, state = fullHome(dark = false))

    @Test
    fun doorCardOverHomeDark() = snap("home_door_card_dark", dark = true, state = fullHome(dark = true)) {
        AlertLayer({ DoorCard(DoorCardUi(unrecognised = false, device = "Front Door", time = "11:54", snapshot = null), onGotIt = {}) }, hidden = false)
    }

    @Test
    fun pinPadLockedDark() {
        // The countdown ticks each second; hold the clock so it still reads its first value.
        compose.mainClock.autoAdvance = false
        snap("pin_pad_locked_dark", dark = true) {
            PinPadSheet(
                "Change settings", PinReason.Generic, error = PinError.WrongPin, lockedUntilMillis = 30_000L,
                onSubmit = {}, onCancel = {}, nowMillis = { 0L },
            )
        }
    }

    @Test
    fun musicTabLight() = snap(
        "tab_music_light", dark = false, state = fullHome(dark = false).copy(selectedTabId = "music"),
        tabContent = { MusicTab(groups, null, emptyMap(), emptySet(), {}, GroupActions.None) },
    )

    @Test
    fun lightsTabLight() = snap(
        "tab_lights_light", dark = false, state = fullHome(dark = false).copy(selectedTabId = "lights"),
        tabContent = { LightsTab(rooms, false, allOffLit = true, null, null, null, emptyMap(), null, {}, noHouse, RoomActions.None) },
    )
}
