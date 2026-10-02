package uk.co.siland.culvery.core.setup

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.github.takahirom.roborazzi.captureRoboImage
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.GraphicsMode
import uk.co.siland.culvery.core.household.Member
import uk.co.siland.culvery.core.household.Person
import uk.co.siland.culvery.core.household.PersonId
import uk.co.siland.culvery.core.household.Role
import uk.co.siland.culvery.core.plugin.LocalShellNavigator
import uk.co.siland.culvery.core.setup.pages.KioskPage
import uk.co.siland.culvery.core.ui.CulveryTheme
import uk.co.siland.culvery.core.ui.PersonPalette

@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class SettingsScreenshotTest {
    @get:Rule val compose = createComposeRule()

    private val canterbury = PlaceMatch("Canterbury", "England", "United Kingdom", 51.27904, 1.07992, "Europe/London")
    private val people = listOf(
        Member(Person(PersonId("alex"), "Alex", PersonPalette.colors[0]), Role.ADMIN, hasPin = true),
        Member(Person(PersonId("sam"), "Sam", PersonPalette.colors[1]), Role.ADULT, hasPin = false),
    )

    private val pages = listOf(
        StillPage("location", "Home location", 0) {
            StepTitle("Home location")
            LocationContent("", {}, TownResults.Idle, canterbury.toHome(), showCurrent = true, busy = false, onChoose = {})
        },
        StillPage("people", "People", 100) {
            StepTitle("People")
            PeopleList(people, onEdit = {}, onAdd = {}, onMove = { _, _ -> })
        },
        StillPage("calendars", "Calendars", 400) { StepTitle("Calendars") },
        KioskPage(FakeHomeApp(default = false)),
    )

    private fun snap(name: String, dark: Boolean, pageId: String) {
        compose.setContent {
            CulveryTheme(dark = dark) {
                CompositionLocalProvider(LocalShellNavigator provides RecordingNavigator()) {
                    Box(Modifier.testTag("shot").size(CANVAS_W, CANVAS_H)) { SettingsScreen(pages, onClose = {}) }
                }
            }
        }
        compose.onNodeWithTag("settings_page_$pageId").performClick()
        compose.onNodeWithTag("shot").captureRoboImage("src/test/screenshots/$name.png")
    }

    @Test fun locationDark() = snap("settings_location_dark", true, "location")
    @Test fun locationLight() = snap("settings_location_light", false, "location")
    @Test fun peopleDark() = snap("settings_people_dark", true, "people")
    @Test fun peopleLight() = snap("settings_people_light", false, "people")
    @Test fun kioskDark() = snap("settings_kiosk_dark", true, "kiosk")
    @Test fun kioskLight() = snap("settings_kiosk_light", false, "kiosk")
}
