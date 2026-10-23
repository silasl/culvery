package uk.co.siland.culvery.core.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.click
import androidx.compose.ui.test.center
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.height
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.google.common.truth.Truth.assertThat
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.GraphicsMode
import uk.co.siland.culvery.core.ui.test.assertClickablesHaveRoleAndLabel
import uk.co.siland.culvery.core.ui.test.pixelAt

/** The one list row (9 §4.12): at least 69 dp high, content centred, the light theme's card edge. */
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class RowLookTest {
    @get:Rule val compose = createComposeRule()

    private fun height(tag: String): Dp = compose.onNodeWithTag(tag).getUnclippedBoundsInRoot().height

    private fun assertAbout(actual: Dp, expected: Dp) {
        assertThat(actual.value).isWithin(1f).of(expected.value)
    }

    @Test
    fun aListRowIsAtLeastSixtyNineHigh() {
        compose.setContent {
            CulveryTheme(dark = true) {
                Column {
                    HhListRow("Alex", Modifier.testTag("plain"))
                    HhListRow("Sam", Modifier.testTag("tall"), minHeight = 75.dp)
                }
            }
        }
        assertAbout(height("plain"), 69.dp)
        assertAbout(height("tall"), 75.dp)
        compose.assertClickablesHaveRoleAndLabel()
    }

    @Test
    fun aRowsContentIsCentredInItsHeight() {
        compose.setContent { CulveryTheme(dark = true) { HhListRow("Alex", Modifier.testTag("row")) } }
        val row = compose.onNodeWithTag("row").getUnclippedBoundsInRoot()
        val title = compose.onNodeWithText("Alex", useUnmergedTree = true).getUnclippedBoundsInRoot()
        assertAbout(title.top - row.top, row.bottom - title.bottom)
        compose.assertClickablesHaveRoleAndLabel()
    }

    @Test
    fun aListRowHasTheCardEdgeInLight() {
        compose.setContent { CulveryTheme(dark = false) { HhListRow("Alex", Modifier.testTag("row")) } }
        assertThat(pixelAt(compose.onNodeWithTag("row"), 0.dp, 30.dp)).isEqualTo(LightColors.border.toArgb())
        compose.assertClickablesHaveRoleAndLabel()
    }

    @Test
    fun aListRowHasNoEdgeInDark() {
        compose.setContent { CulveryTheme(dark = true) { HhListRow("Alex", Modifier.testTag("row")) } }
        assertThat(pixelAt(compose.onNodeWithTag("row"), 0.dp, 30.dp)).isEqualTo(DarkColors.surf.toArgb())
        compose.assertClickablesHaveRoleAndLabel()
    }

    @Test
    fun aSwitchRowIsTheSameRow() {
        compose.setContent {
            CulveryTheme(dark = true) { HhSwitchRow("Night clock", null, checked = false, tag = "row", onChange = {}) }
        }
        assertAbout(height("row"), 69.dp)
        compose.onNodeWithTag("row").assert(SemanticsMatcher.expectValue(SemanticsProperties.Role, Role.Switch))
        compose.assertClickablesHaveRoleAndLabel()
    }

    @Test
    fun aSwitchRowTakesAMinHeight() {
        compose.setContent {
            CulveryTheme(dark = true) { HhSwitchRow("All day", null, checked = false, tag = "row", onChange = {}, minHeight = 77.dp) }
        }
        assertAbout(height("row"), 77.dp)
        compose.assertClickablesHaveRoleAndLabel()
    }

    @Test
    fun aSliderRowSaysItsValueAndLetsGo() {
        val letGo = mutableListOf<Int>()
        compose.setContent {
            CulveryTheme(dark = true) { HhSliderRow("Chime volume", 30, "x", steps = 9, onLetGo = { letGo += it }) }
        }
        val config = compose.onNodeWithTag("x").fetchSemanticsNode().config
        assertThat(config[SemanticsProperties.ContentDescription].single() + ", " + config[SemanticsProperties.StateDescription])
            .isEqualTo("Chime volume, 30 percent")
        compose.onNodeWithText("30 %").assertExists()
        assertAbout(height("x_row"), 69.dp)
        compose.onNodeWithTag("x").performSemanticsAction(SemanticsActions.SetProgress) { it(50f) }
        compose.waitForIdle()
        assertThat(letGo).containsExactly(50)
        compose.assertClickablesHaveRoleAndLabel()
    }

    @Test
    fun aSliderRowCanBeDisabled() {
        val letGo = mutableListOf<Int>()
        compose.setContent {
            CulveryTheme(dark = true) { HhSliderRow("Chime volume", 30, "x", enabled = false, onLetGo = { letGo += it }) }
        }
        assertThat(compose.onNodeWithTag("x").fetchSemanticsNode().config.contains(SemanticsActions.SetProgress)).isFalse()
        compose.onNodeWithTag("x").performTouchInput { click(center) }
        compose.waitForIdle()
        assertThat(letGo).isEmpty()
        compose.assertClickablesHaveRoleAndLabel()
    }
}
