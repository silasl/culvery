package uk.co.siland.culvery.core.setup

import androidx.compose.foundation.layout.Column
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertHeightIsEqualTo
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import uk.co.siland.culvery.core.household.Member
import uk.co.siland.culvery.core.household.Person
import uk.co.siland.culvery.core.household.PersonId
import uk.co.siland.culvery.core.household.Role as HouseholdRole
import uk.co.siland.culvery.core.plugin.LocalShellNavigator
import uk.co.siland.culvery.core.setup.pages.KioskPage
import uk.co.siland.culvery.core.setup.steps.WelcomeContent
import uk.co.siland.culvery.core.ui.CulveryTheme
import uk.co.siland.culvery.core.ui.PersonPalette

/** Every button in the wizard, Settings and the person sheet is the shared one: a button, 48 dp, or 60 in a footer. */
@RunWith(AndroidJUnit4::class)
class SetupButtonsTest {
    @get:Rule val compose = createComposeRule()

    private val sam = Member(Person(PersonId("sam"), "Sam", PersonPalette.colors[1]), HouseholdRole.ADULT, hasPin = false)

    private fun show(content: @Composable () -> Unit) = compose.setContent {
        CulveryTheme(dark = false) {
            CompositionLocalProvider(LocalShellNavigator provides RecordingNavigator()) { content() }
        }
    }

    private fun assertButton(tag: String, height: Dp) {
        compose.onNodeWithTag(tag)
            .assert(SemanticsMatcher.expectValue(SemanticsProperties.Role, Role.Button))
            .assertHeightIsEqualTo(height)
    }

    @Test
    fun theWizardsButtonsAreButtons() {
        show {
            Column {
                WizardFrame(6, 2, back = {}, forward = Forward.Next("Next", enabled = false), busy = false, onForward = {}, onSkip = {}) {
                    WelcomeContent(onSample = {}, busy = false)
                    PeopleList(listOf(sam), onEdit = {}, onAdd = {}, onMove = null)
                }
            }
        }
        listOf("wizard_back", "wizard_next", "welcome_sample", "people_add").forEach { assertButton(it, 48.dp) }
    }

    @Test
    fun skipIsAButton() {
        show { WizardFrame(6, 2, back = null, forward = Forward.Skip, busy = false, onForward = {}, onSkip = {}) {} }
        assertButton("wizard_skip", 48.dp)
    }

    @Test
    fun enterPinIsAButton() {
        show { PinGateContent(busy = false, onEnterPin = {}) }
        assertButton("wizard_enter_pin", 48.dp)
    }

    @Test
    fun settingsCloseAndTheKioskPageAreButtons() {
        show { SettingsScreen(listOf(KioskPage(FakeHomeApp(default = false))), onClose = {}) }
        listOf("settings_close", "choose_home_app", "settings_exit_kiosk").forEach { assertButton(it, 48.dp) }
    }

    @Test
    fun thePersonSheetsButtonsAreButtons() {
        show {
            PersonEditorSheet(
                form = PersonForm(sam, taken = setOf(PersonPalette.colors[0])),
                busy = false, canRemove = true, confirmingRemove = false, choosingPin = false,
                onChoosePin = {}, onPinChosen = {}, onPinCancelled = {}, onSave = {}, onRemove = {}, onKeep = {},
                onConfirmRemove = {}, onClose = {},
            )
        }
        assertButton("person_set_pin", 48.dp)
        assertButton("person_remove", 60.dp)
        assertButton("person_save", 60.dp)
    }
}
