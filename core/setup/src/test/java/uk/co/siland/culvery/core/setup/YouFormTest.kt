package uk.co.siland.culvery.core.setup

import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import uk.co.siland.culvery.core.setup.steps.YouForm
import uk.co.siland.culvery.core.setup.steps.YouFormContent
import uk.co.siland.culvery.core.ui.CulveryTheme
import uk.co.siland.culvery.core.ui.test.assertAccessible

@RunWith(AndroidJUnit4::class)
class YouFormTest {
    @get:Rule val compose = createComposeRule()

    @Test
    fun aTakenNameIsAnErrorOnTheNamedFieldAndSaidOnce() {
        val form = YouForm().apply {
            name = "Family"
            message = "Someone is already called Family."
            nameError = message
        }
        compose.setContent { CulveryTheme(dark = true) { YouFormContent(form, onSetPin = {}) } }
        compose.onNodeWithTag("you_name")
            .assert(SemanticsMatcher.expectValue(SemanticsProperties.ContentDescription, listOf("Your name")))
            .assert(SemanticsMatcher.expectValue(SemanticsProperties.Error, "Someone is already called Family."))
        // TalkBack says the field's error as it appears; a live line would say the same words again.
        compose.onNodeWithTag("you_message").assert(SemanticsMatcher.keyNotDefined(SemanticsProperties.LiveRegion))
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
