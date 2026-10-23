package uk.co.siland.culvery.core.setup

import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsFocused
import androidx.compose.ui.test.assertIsNotFocused
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performTextInput
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import uk.co.siland.culvery.core.setup.steps.YouForm
import uk.co.siland.culvery.core.setup.steps.YouFormContent
import uk.co.siland.culvery.core.ui.CulveryTheme
import uk.co.siland.culvery.core.ui.test.assertAccessible
import uk.co.siland.culvery.core.ui.test.assertClickablesHaveRoleAndLabel

@RunWith(AndroidJUnit4::class)
class YouFormTest {
    @get:Rule val compose = createComposeRule()

    @Test
    fun aTakenNameIsAnErrorOnTheNamedFieldAndSaidOnce() {
        val taken = someoneCalled("Sam")
        val form = YouForm().apply {
            name = "Sam"
            nameError = taken
        }
        compose.setContent { CulveryTheme(dark = true) { YouFormContent(form, onSetPin = {}) } }
        compose.onNodeWithTag("you_name")
            .assert(SemanticsMatcher.expectValue(SemanticsProperties.ContentDescription, listOf("Your name")))
            .assert(SemanticsMatcher.expectValue(SemanticsProperties.Error, taken))
        compose.onNodeWithTag("you_name_error").assertIsDisplayed().assert(hasText(taken))
        // The error is said by the field, under it; the form's own message line stays empty.
        compose.onNodeWithTag("you_message").assertTextEquals("")
        compose.onNodeWithTag("you_name").performTextInput(" B")
        compose.onNodeWithTag("you_name_error").assertDoesNotExist()
        compose.onNodeWithTag("you_message").assertTextEquals("")
        compose.assertClickablesHaveRoleAndLabel()
    }

    @Test
    fun aTakenNameMovesFocusToTheNameField() {
        val form = YouForm().apply { name = "Sam" }
        compose.setContent { CulveryTheme(dark = true) { YouFormContent(form, onSetPin = {}) } }
        compose.onNodeWithTag("you_name").assertIsNotFocused()
        compose.runOnIdle { form.refuseName(someoneCalled("Sam")) }
        compose.onNodeWithTag("you_name").assertIsFocused()
        compose.assertClickablesHaveRoleAndLabel()
    }

    @Test
    fun theYouStepPassesTheSharedCheck() {
        compose.setContent { CulveryTheme(dark = true) { YouFormContent(YouForm(), onSetPin = {}) } }
        compose.assertAccessible()
    }

    @Test
    fun thePinButtonSaysChangeOnceAPinIsChosen() {
        val form = YouForm()
        compose.setContent { CulveryTheme(dark = true) { YouFormContent(form, onSetPin = {}) } }
        compose.onNodeWithTag("you_set_pin").assertTextEquals("Set your PIN")
        compose.runOnIdle { form.pin = "1234" }
        compose.onNodeWithTag("you_set_pin").assertTextEquals("Change PIN")
    }
}
