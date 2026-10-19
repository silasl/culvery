package uk.co.siland.culvery.core.ui

import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertContentDescriptionEquals
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.google.common.truth.Truth.assertThat
import java.time.LocalTime
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import uk.co.siland.culvery.core.ui.test.assertClickablesHaveRoleAndLabel

@RunWith(AndroidJUnit4::class)
class RowsTest {
    @get:Rule val compose = createComposeRule()

    /** Fails if: the clock text loses its zero padding or its 24-hour form. */
    @Test
    fun theClockIsTwoDigitsEachSide() {
        assertThat(listOf("00:00", "07:05", "13:30", "23:59").map { clockText(LocalTime.parse(it)) })
            .containsExactly("00:00", "07:05", "13:30", "23:59").inOrder()
    }

    /** Fails if: the row isn't one switch, or a tap on it doesn't report the new state. */
    @Test
    fun aSwitchRowIsOneSwitch() {
        val changes = mutableListOf<Boolean>()
        compose.setContent { CulveryTheme(dark = true) { HhSwitchRow("Night clock", "A dim clock.", checked = false, tag = "row", onChange = changes::add) } }
        compose.onNodeWithTag("row").assert(SemanticsMatcher.expectValue(SemanticsProperties.Role, Role.Switch)).performClick()
        assertThat(changes).containsExactly(true)
        compose.assertClickablesHaveRoleAndLabel()
    }

    /** Fails if: a step isn't half an hour, the buttons swap direction, the time isn't shown, or the spoken name drops the subject. */
    @Test
    fun theStepperStepsByHalfAnHour() {
        val times = mutableListOf<LocalTime>()
        compose.setContent { CulveryTheme(dark = true) { HhTimeStepperRow("From", "night clock start", LocalTime.of(22, 0), "from", times::add) } }
        compose.onNodeWithText("22:00").assertTextEquals("22:00")
        compose.onNodeWithTag("from_earlier").assertContentDescriptionEquals("Earlier, night clock start").performClick()
        compose.onNodeWithTag("from_later").assertContentDescriptionEquals("Later, night clock start").performClick()
        assertThat(times).containsExactly(LocalTime.of(21, 30), LocalTime.of(22, 30)).inOrder()
        compose.assertClickablesHaveRoleAndLabel()
    }
}
