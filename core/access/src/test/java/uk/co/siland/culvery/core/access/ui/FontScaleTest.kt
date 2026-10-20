package uk.co.siland.culvery.core.access.ui

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.test.click
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.google.common.truth.Truth.assertThat
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.GraphicsMode
import uk.co.siland.culvery.core.access.PinError
import uk.co.siland.culvery.core.access.PinReason
import uk.co.siland.culvery.core.access.pinReasonText
import uk.co.siland.culvery.core.ui.CulveryTheme
import uk.co.siland.culvery.core.ui.WithFontScale

/** The PIN pad at font scale 1.5 (4d-1 §6.5). Real text metrics: these measure lines and overflow. */
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class FontScaleTest {
    @get:Rule val compose = createComposeRule()

    private fun show(error: PinError?) = compose.setContent {
        CulveryTheme(dark = false) {
            WithFontScale(1.5f) {
                PinPadSheet("Change settings", PinReason.Generic, error = error, lockedUntilMillis = null, onSubmit = {}, onCancel = {})
            }
        }
    }

    @Test
    fun atFontScale15CancelStaysOneWord() {
        show(null)
        val cancel = compose.onNodeWithText("Cancel", useUnmergedTree = true).textLayout()
        // Pins how it can't break, whatever Robolectric's metrics: one line that never wraps.
        assertThat(cancel.layoutInput.softWrap).isFalse()
        assertThat(cancel.layoutInput.maxLines).isEqualTo(1)
        assertThat(cancel.lineCount).isEqualTo(1)
    }

    @Test
    fun atFontScale15ARefusalIsShownWhole() {
        show(PinError.NotAllowed("That PIN can’t do this. Ask an Admin."))
        val layout = compose.onNodeWithTag("pin_error", useUnmergedTree = true).textLayout()
        assertThat(layout.lineCount).isAtMost(2)
        assertThat(layout.isLineEllipsized(layout.lineCount - 1)).isFalse()
    }

    @Test
    fun atFontScale15AMessageAppearingDoesNotMoveTheKeypad() {
        var message by mutableStateOf("")
        compose.setContent {
            CulveryTheme(dark = false) {
                WithFontScale(1.5f) { PinPadFrame("Who’s this?", null, message, wrongPin = false, enabled = true, onSubmit = {}, onCancel = {}) }
            }
        }
        val before = compose.onNodeWithTag("pin_key_1").getUnclippedBoundsInRoot().top
        message = "Maximilian-Alexander Fotheringham-Smythe can't do that"
        compose.waitForIdle()
        assertThat(compose.onNodeWithTag("pin_key_1").getUnclippedBoundsInRoot().top).isEqualTo(before)
    }

    /** Cancel's label may run past its key at 1.5; a tap on the next key must still be that key. */
    @Test
    fun atFontScale15ATapAtTheZeroKeysLeftEdgeTypesZero() {
        var cancels = 0
        var submitted: String? = null
        compose.setContent {
            CulveryTheme(dark = false) {
                WithFontScale(1.5f) {
                    PinPadFrame("Who’s this?", null, "", wrongPin = false, enabled = true, onSubmit = { submitted = it }, onCancel = { cancels++ })
                }
            }
        }
        compose.onNodeWithTag("pin_key_0").performTouchInput { click(Offset(2.dp.toPx(), centerY)) }
        listOf("1", "2", "3").forEach { compose.onNodeWithTag("pin_key_$it").performClick() }
        assertThat(cancels).isEqualTo(0)
        assertThat(submitted).isEqualTo("0123")
    }

    /** (9 §4.9) A reason that wraps to two lines is laid out beside the lock's one line, so the keys keep their place both ways. */
    @Test
    fun aWrappingReasonDoesNotMoveTheKeysWhenTheLockStartsOrEnds() {
        var locked by mutableStateOf(false)
        val reason = pinReasonText(PinReason.Delete, "Change any event")
        compose.setContent {
            CulveryTheme(dark = false) {
                PinPadFrame(
                    "Who’s this?", line = reason, message = "", wrongPin = false, enabled = !locked,
                    lock = if (locked) PinLock(27, 27) else null, onSubmit = {}, onCancel = {},
                )
            }
        }
        // Real text metrics: the delete reason wraps here, as on the tablet.
        assertThat(compose.onNodeWithText(reason).textLayout().lineCount).isEqualTo(2)
        val unlocked = compose.onNodeWithTag("pin_key_1").getUnclippedBoundsInRoot().top
        locked = true
        compose.waitForIdle()
        assertThat(compose.onNodeWithTag("pin_key_1").getUnclippedBoundsInRoot().top).isEqualTo(unlocked)
        locked = false
        compose.waitForIdle()
        assertThat(compose.onNodeWithTag("pin_key_1").getUnclippedBoundsInRoot().top).isEqualTo(unlocked)
    }
}
