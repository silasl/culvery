package uk.co.siland.culvery.core.access.ui

import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.ExperimentalCoroutinesApi
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import uk.co.siland.culvery.core.access.PinError
import uk.co.siland.culvery.core.access.PinPromptController
import uk.co.siland.culvery.core.ui.CulveryTheme

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(AndroidJUnit4::class)
class PinPadTest {
    @get:Rule val compose = createComposeRule()
    private val controller = PinPromptController()

    private fun show() = compose.setContent { CulveryTheme(dark = true) { PinPadHost(controller) } }

    private fun tap(vararg keys: String) = keys.forEach { compose.onNodeWithTag("pin_key_$it").performClick() }

    @Test
    fun fourthDigitSubmitsAutomatically() {
        val request = controller.open("Change settings", null, null)
        show()
        tap("1", "2", "3", "4")
        compose.waitForIdle()
        assertThat(request.answer.getCompleted()).isEqualTo("1234")
    }

    @Test
    fun threeDigitsDoNotSubmit() {
        val request = controller.open("Change settings", null, null)
        show()
        tap("1", "2", "3")
        compose.waitForIdle()
        assertThat(request.answer.isCompleted).isFalse()
    }

    @Test
    fun backspaceRemovesLastDigit() {
        val request = controller.open("Change settings", null, null)
        show()
        tap("1", "2", "3")
        compose.onNodeWithTag("pin_backspace").performClick()
        tap("4", "5")
        compose.waitForIdle()
        assertThat(request.answer.getCompleted()).isEqualTo("1245")
    }

    @Test
    fun cancelAnswersNull() {
        val request = controller.open("Change settings", null, null)
        show()
        compose.onNodeWithTag("pin_cancel").performClick()
        compose.waitForIdle()
        assertThat(request.answer.getCompleted()).isNull()
    }

    @Test
    fun showsWhoIsNotAllowed() {
        controller.open("Change settings", PinError.NotAllowed("Mia"), null)
        show()
        compose.onNodeWithText("Mia can't do that").assertExists()
    }

    @Test
    fun keysAreDisabledWhileLocked() {
        // The countdown loops on delay(); stop the test clock racing through it.
        compose.mainClock.autoAdvance = false
        controller.open("Change settings", PinError.WrongPin, System.currentTimeMillis() + 60_000)
        show()
        compose.onNodeWithTag("pin_key_1").assertIsNotEnabled()
        compose.onNodeWithText("Too many tries", substring = true).assertExists()
    }

    @Test
    fun hiddenWhenNoRequest() {
        show()
        compose.onNodeWithTag("pin_scrim").assertDoesNotExist()
    }
}
