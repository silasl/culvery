package uk.co.siland.culvery.core.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.google.common.truth.Truth.assertThat
import org.junit.Assert.assertThrows
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import uk.co.siland.culvery.core.ui.test.assertClickablesHaveRoleAndLabel
import uk.co.siland.culvery.core.ui.test.assertNothingReadTwice

/** The shared helper fails on each fault it exists for, and passes the parts that are right, so it can't pass by default. */
@RunWith(AndroidJUnit4::class)
class AccessibilityHelperTest {
    @get:Rule val compose = createComposeRule()

    private fun show(content: @Composable () -> Unit) = compose.setContent { CulveryTheme(dark = true) { Column { content() } } }

    private fun failure(): String = assertThrows(AssertionError::class.java) { compose.assertClickablesHaveRoleAndLabel() }.message.orEmpty()

    @Test
    fun aClickableWithNoRoleFails() {
        show { Text("Open", Modifier.clickable {}) }
        assertThat(failure()).contains("Open: no role")
    }

    @Test
    fun aClickableWithNoNameFails() {
        show { Box(Modifier.size(48.dp).hhClickable({})) }
        assertThat(failure()).contains("no name")
    }

    @Test
    fun aClickLabelThatRepeatsTheNameFails() {
        show { Text("Sign out", Modifier.hhClickable({}, label = "sign out")) }
        assertThat(failure()).contains("click label repeats its name")
    }

    @Test
    fun aClickLabelThatRepeatsTheVisibleTextUnderALongerNameFails() {
        show {
            Text(
                "Disconnect",
                Modifier.semantics { contentDescription = "Disconnect Google Calendar, alex@example.com" }
                    .hhClickable({}, label = "disconnect"),
            )
        }
        assertThat(failure()).contains("click label repeats its name")
    }

    @Test
    fun aSpokenNameThatDoesNotStartWithItsTextFails() {
        show { HhButton("Hide this calendar", {}, on = ButtonSurface.Surf, spokenName = "Hide School terms") }
        assertThat(failure()).contains("doesn't start with its text")
    }

    @Test
    fun namedButtonsRowsAndFieldsPass() {
        show {
            HhButton("Disconnect", {}, on = ButtonSurface.Surf, spokenName = "Disconnect Google Calendar, alex@example.com")
            HhListRow("Sam", subtitle = "Adult · PIN set", onClick = {}, clickLabel = "change Sam’s details")
            Box(Modifier.size(48.dp).hhClickable({}, role = Role.Tab)) { Text("Home") }
            HhTextField("", {}, placeholder = "Town or city", label = "Town or city", tag = "field")
        }
        compose.assertClickablesHaveRoleAndLabel()
        compose.assertNothingReadTwice()
    }

    @Test
    fun aDescriptionOverMergedTextFails() {
        show { Row(Modifier.semantics(mergeDescendants = true) { contentDescription = "Now 10°, cloudy" }) { Text("10°") } }
        val failure = assertThrows(AssertionError::class.java) { compose.assertNothingReadTwice() }.message.orEmpty()
        assertThat(failure).contains("\"Now 10°, cloudy\" then \"10°\"")
    }

    @Test
    fun textShownNotSpokenUnderADescriptionIsReadOnceAndStaysInTheTree() {
        show {
            Row(Modifier.semantics(mergeDescendants = true) { contentDescription = "Now 10°, cloudy" }) {
                Text("10°", Modifier.shownNotSpoken())
            }
        }
        compose.assertNothingReadTwice()
        compose.onNodeWithText("10°", useUnmergedTree = true).assertExists()
    }
}
