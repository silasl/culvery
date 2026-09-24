package uk.co.siland.culvery.core.access.ui

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.test.assertHeightIsEqualTo
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertWidthIsEqualTo
import androidx.compose.ui.test.click
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.ExperimentalCoroutinesApi
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import uk.co.siland.culvery.core.access.PinError
import uk.co.siland.culvery.core.access.PinPromptController
import uk.co.siland.culvery.core.access.PinReason
import uk.co.siland.culvery.core.ui.CulveryTheme

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(AndroidJUnit4::class)
class PinPadTest {
    @get:Rule val compose = createComposeRule()
    private val controller = PinPromptController()

    private fun show(overSheet: Boolean = false) =
        compose.setContent { CulveryTheme(dark = true) { PinPadHost(controller, overSheet) } }

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
    fun cancelKeyAnswersNull() {
        val request = controller.open("Change settings", null, null)
        show()
        compose.onNodeWithTag("pin_cancel").performClick()
        compose.waitForIdle()
        assertThat(request.answer.getCompleted()).isNull()
    }

    @Test
    fun tappingOutsideTheCardCancels() {
        val request = controller.open("Change settings", null, null)
        show()
        compose.onNodeWithTag("pin_scrim").performTouchInput { click(Offset(10f, 10f)) }
        compose.waitForIdle()
        assertThat(request.answer.getCompleted()).isNull()
    }

    @Test
    fun tappingTheCardItselfDoesNotCancel() {
        val request = controller.open("Change settings", null, null)
        show()
        compose.onNodeWithText("Who's this?").performClick()
        compose.waitForIdle()
        assertThat(request.answer.isCompleted).isFalse()
    }

    @Test
    fun asksWhoIsThisWithTheEventReason() {
        controller.open("Change any event", null, null, PinReason.Delete)
        show()
        compose.onNodeWithText("Who's this?").assertExists()
        compose.onNodeWithText("Enter your PIN to delete this event. It also records who made the change.").assertExists()
    }

    @Test
    fun genericReasonUsesThePermissionLabel() {
        controller.open("Change settings", null, null)
        show()
        compose.onNodeWithText("Enter your PIN to change settings.").assertExists()
    }

    @Test
    fun wrongPinShowsTheErrorLineWithEmptyDigits() {
        controller.open("Change settings", PinError.WrongPin, null)
        show()
        compose.onNodeWithText("Wrong PIN — try again").assertExists()
    }

    @Test
    fun notAllowedShowsItsMessage() {
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

    @Test
    fun backspaceIsLabelled() {
        controller.open("Change settings", null, null)
        show()
        compose.onNodeWithContentDescription("Delete last digit").assertExists()
    }

    @Test
    fun keysAre76dpCirclesInA400dpCard() {
        controller.open("Change settings", null, null)
        show()
        compose.onNodeWithTag("pin_key_5").assertWidthIsEqualTo(76.dp).assertHeightIsEqualTo(76.dp)
        compose.onNodeWithTag("pin_card", useUnmergedTree = true).assertWidthIsEqualTo(400.dp)
    }

    @Test
    fun withoutASheetTheCardIsCentredOnTheScreen() {
        controller.open("Change settings", null, null)
        show()
        val card = compose.onNodeWithTag("pin_card", useUnmergedTree = true).getUnclippedBoundsInRoot()
        assertThat(((card.left + card.right) / 2).value).isWithin(1f).of(640f)
    }

    @Test
    fun overASheetTheScrimCoversOnlyTheSheetAndTheCardIsCentredInIt() {
        controller.open("Change settings", null, null)
        show(overSheet = true)
        val area = compose.onNodeWithTag("pin_area", useUnmergedTree = true).getUnclippedBoundsInRoot()
        assertThat(area.left.value).isWithin(1f).of(680f)
        assertThat((area.right - area.left).value).isWithin(1f).of(600f)
        val card = compose.onNodeWithTag("pin_card", useUnmergedTree = true).getUnclippedBoundsInRoot()
        assertThat(((card.left + card.right) / 2).value).isWithin(1f).of(980f)
    }
}
