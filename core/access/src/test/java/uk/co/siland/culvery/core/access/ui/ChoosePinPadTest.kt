package uk.co.siland.culvery.core.access.ui

import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.google.common.truth.Truth.assertThat
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import uk.co.siland.culvery.core.ui.CulveryTheme

@RunWith(AndroidJUnit4::class)
class ChoosePinPadTest {
    @get:Rule val compose = createComposeRule()
    private var chosen: String? = null
    private var cancelled = 0

    private fun show() = compose.setContent {
        CulveryTheme(dark = true) { ChoosePinPad(onChosen = { chosen = it }, onCancel = { cancelled++ }) }
    }

    private fun tap(pin: String) = pin.forEach { compose.onNodeWithTag("pin_key_$it").performClick() }

    @Test
    fun twoMatchingEntriesChooseThePin() {
        show()
        compose.onNodeWithText("Choose a 4-digit PIN").assertExists()
        tap("2468")
        compose.onNodeWithText("Enter it again").assertExists()
        tap("2468")
        compose.waitForIdle()
        assertThat(chosen).isEqualTo("2468")
    }

    @Test
    fun aMismatchStartsAgainAndSaysSo() {
        show()
        tap("2468")
        tap("1357")
        compose.onNodeWithText("Choose a 4-digit PIN").assertExists()
        compose.onNodeWithText("Those PINs didn't match — try again.").assertExists()
        compose.onNodeWithTag("pin_dots").assert(SemanticsMatcher.expectValue(SemanticsProperties.Error, "Those PINs didn't match — try again."))
        assertThat(chosen).isNull()
        tap("1357")
        tap("1357")
        compose.waitForIdle()
        assertThat(chosen).isEqualTo("1357")
    }

    @Test
    fun cancelLeavesWithNothingChosen() {
        show()
        tap("24")
        compose.onNodeWithTag("pin_cancel").performClick()
        assertThat(cancelled).isEqualTo(1)
        assertThat(chosen).isNull()
    }

    @Test
    fun eachStageIsAPaneNamedByItsTitle() {
        show()
        compose.onNode(SemanticsMatcher.expectValue(SemanticsProperties.PaneTitle, "Choose a 4-digit PIN")).assertExists()
        tap("2468")
        compose.onNode(SemanticsMatcher.expectValue(SemanticsProperties.PaneTitle, "Enter it again")).assertExists()
    }
}
