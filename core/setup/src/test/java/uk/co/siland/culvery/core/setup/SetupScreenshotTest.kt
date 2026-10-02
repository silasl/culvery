package uk.co.siland.culvery.core.setup

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
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
import uk.co.siland.culvery.core.setup.steps.WelcomeContent
import uk.co.siland.culvery.core.setup.steps.YouForm
import uk.co.siland.culvery.core.setup.steps.YouFormContent
import uk.co.siland.culvery.core.ui.Culvery
import uk.co.siland.culvery.core.ui.CulveryTheme
import uk.co.siland.culvery.core.ui.PersonPalette
import uk.co.siland.culvery.core.ui.ShellTokens

@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class SetupScreenshotTest {
    @get:Rule val compose = createComposeRule()

    private fun snap(name: String, dark: Boolean, content: @Composable BoxScope.() -> Unit) {
        compose.setContent {
            CulveryTheme(dark = dark) {
                Box(Modifier.testTag("shot").size(CANVAS_W, CANVAS_H).background(Culvery.colors.bg)) { content() }
            }
        }
        compose.onNodeWithTag("shot").captureRoboImage("src/test/screenshots/$name.png")
    }

    private fun frame(name: String, dark: Boolean) = snap(name, dark) {
        WizardFrame(
            dotCount = 6,
            dotIndex = 2,
            back = {},
            forward = Forward.Next("Next", enabled = false),
            busy = false,
            onForward = {},
            onSkip = {},
        ) {
            StepTitle("Who's setting this up?", "The step's content goes here.")
        }
    }

    @Test fun wizardFrameDark() = frame("wizard_frame_dark", true)
    @Test fun wizardFrameLight() = frame("wizard_frame_light", false)

    @Test
    fun wizardPinGateDark() = snap("wizard_pin_gate_dark", true) { PinGateContent(busy = false, onEnterPin = {}) }

    private val alex = Member(Person(PersonId("alex"), "Alex", PersonPalette.colors[0]), Role.ADMIN, hasPin = true)
    private val sam = Member(Person(PersonId("sam"), "Sam", PersonPalette.colors[1]), Role.ADULT, hasPin = false)
    private val mia = Member(Person(PersonId("mia"), "Mia", PersonPalette.colors[2]), Role.CHILD, hasPin = true)

    private fun people(name: String, dark: Boolean) = snap(name, dark) {
        WizardFrame(6, 3, back = {}, forward = Forward.Next("Next", enabled = true), busy = false, onForward = {}, onSkip = {}) {
            StepTitle("Who else lives here?")
            PeopleList(listOf(alex, sam, mia), onEdit = {}, onAdd = {}, onMove = null)
        }
    }

    private fun sheet(
        name: String,
        dark: Boolean,
        form: PersonForm,
        confirmingRemove: Boolean = false,
        choosingPin: Boolean = false,
    ) = snap(name, dark) {
        Box(Modifier.fillMaxSize().background(ShellTokens.sheetScrim))
        Box(Modifier.align(Alignment.CenterEnd)) {
            PersonEditorSheet(
                form = form,
                busy = false,
                canRemove = form.existing != null,
                confirmingRemove = confirmingRemove,
                choosingPin = choosingPin,
                onChoosePin = {},
                onPinChosen = {},
                onPinCancelled = {},
                onSave = {},
                onRemove = {},
                onKeep = {},
                onConfirmRemove = {},
                onClose = {},
            )
        }
    }

    private fun newPerson() = PersonForm(existing = null, taken = setOf(PersonPalette.colors[0]))

    @Test fun peopleListDark() = people("people_list_dark", true)
    @Test fun peopleListLight() = people("people_list_light", false)
    @Test fun personNewDark() = sheet("person_new_dark", true, newPerson())
    @Test fun personNewLight() = sheet("person_new_light", false, newPerson())

    @Test
    fun personErrorDark() = sheet(
        "person_error_dark",
        true,
        PersonForm(existing = null, taken = setOf(PersonPalette.colors[0], PersonPalette.colors[1])).apply {
            name = "Sam"
            message = "Someone is already called Sam."
        },
    )

    @Test
    fun personRemoveDark() = sheet("person_remove_dark", true, PersonForm(sam, taken = setOf(PersonPalette.colors[0])), confirmingRemove = true)

    @Test
    fun personPinDark() = sheet("person_pin_dark", true, PersonForm(sam, taken = setOf(PersonPalette.colors[0])), choosingPin = true)

    private fun step(name: String, dark: Boolean, dot: Int, forward: Forward, back: Boolean = true, content: @Composable () -> Unit) =
        snap(name, dark) {
            WizardFrame(7, dot, back = if (back) ({}) else null, forward = forward, busy = false, onForward = {}, onSkip = {}, content = content)
        }

    private val canterbury = PlaceMatch("Canterbury", "England", "United Kingdom", 51.27904, 1.07992, "Europe/London")
    private val canterburyNz = PlaceMatch("Canterbury", "Canterbury", "New Zealand", -43.5, 172.0, "Pacific/Auckland")

    private fun welcome(name: String, dark: Boolean, sample: Boolean) =
        step(name, dark, 0, Forward.Next("Start", enabled = true), back = false) {
            WelcomeContent(onSample = if (sample) ({}) else null, busy = false)
        }

    private fun location(name: String, dark: Boolean, results: TownResults) = step(name, dark, 1, Forward.Skip) {
        StepTitle("Where's home?")
        LocationContent("Canter", {}, results, current = canterbury.toHome(), showCurrent = false, busy = false, onChoose = {})
    }

    private fun you(name: String, dark: Boolean) = step(name, dark, 2, Forward.Next("Next", enabled = true)) {
        StepTitle("Who's setting this up?")
        YouFormContent(YouForm().apply { this.name = "Alex"; pin = "1234" }, onSetPin = {})
    }

    private fun done(name: String, dark: Boolean) = step(name, dark, 6, Forward.Next("Open Culvery", enabled = true)) {
        StepTitle("Culvery is ready")
        HomeAppPrompt(onChoose = {})
    }

    @Test fun welcomeDark() = welcome("welcome_dark", true, sample = false)
    @Test fun welcomeLight() = welcome("welcome_light", false, sample = false)
    @Test fun welcomeSampleDark() = welcome("welcome_sample_dark", true, sample = true)
    @Test fun locationResultsDark() = location("location_results_dark", true, TownResults.Found(listOf(canterbury, canterburyNz)))
    @Test fun locationResultsLight() = location("location_results_light", false, TownResults.Found(listOf(canterbury, canterburyNz)))
    @Test fun locationFailedDark() = location("location_failed_dark", true, TownResults.Failed)
    @Test fun youDark() = you("you_dark", true)
    @Test fun youLight() = you("you_light", false)
    @Test fun doneDark() = done("done_dark", true)
    @Test fun doneLight() = done("done_light", false)
}
