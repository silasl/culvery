package uk.co.siland.culvery.core.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Text
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.assertIsFocused
import androidx.compose.ui.test.assertIsNotFocused
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performImeAction
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.google.common.truth.Truth.assertThat
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import uk.co.siland.culvery.core.ui.test.TouchModeRule

/** 4e session 1: on the SM-T510 the keyboard's Done didn't close it, and it covered the buttons under it. */
@RunWith(AndroidJUnit4::class)
class KeyboardTest {
    /** Focus behaves as on the tablet only in touch mode, set before the activity launches. */
    @get:Rule(order = 0) val touchMode = TouchModeRule()
    @get:Rule(order = 1) val compose = createComposeRule()

    @Test
    fun theKeyboardsDoneLeavesTheField() {
        var done = 0
        compose.setContent {
            CulveryTheme(dark = true) { HhTextField("Sam", {}, placeholder = "Name", label = "Name", tag = "field", onDone = { done++ }) }
        }
        compose.onNodeWithTag("field").performClick().assertIsFocused()
        compose.onNodeWithTag("field").performImeAction()
        assertThat(done).isEqualTo(1)
        compose.onNodeWithTag("field").assertIsNotFocused()
    }

    /** `WindowInsets.ime` is zero under Robolectric, so a 320 dp keyboard on an 800 dp screen is passed in. */
    @Test
    fun aSheetsContentStaysAboveTheKeyboard() {
        compose.setContent {
            CulveryTheme(dark = true) {
                Box(Modifier.size(ShellTokens.sheetWidth, 800.dp)) {
                    HhSheet(PaddingValues(), keyboard = WindowInsets(bottom = 320.dp)) {
                        Spacer(Modifier.weight(1f))
                        Text("Save", Modifier.testTag("footer"))
                    }
                }
            }
        }
        assertThat(compose.onNodeWithTag("footer").getUnclippedBoundsInRoot().bottom).isAtMost(480.dp)
    }
}
