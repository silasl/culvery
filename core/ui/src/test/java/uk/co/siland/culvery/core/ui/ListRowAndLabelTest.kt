package uk.co.siland.culvery.core.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertContentDescriptionEquals
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.isHeading
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.google.common.truth.Truth.assertThat
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class ListRowAndLabelTest {
    @get:Rule val compose = createComposeRule()

    @Test
    fun aSectionLabelShowsCapitalsIsAHeadingAndSaysItsWordInSentenceCase() {
        compose.setContent { CulveryTheme(dark = true) { HhSectionLabel("Who") } }
        compose.onNodeWithText("WHO").assert(isHeading()).assertContentDescriptionEquals("Who")
    }

    @Test
    fun aRowWithAnActionIsAButtonAndOneWithoutTakesNoTap() {
        var taps = 0
        compose.setContent {
            CulveryTheme(dark = true) {
                Column {
                    HhListRow("Alex", Modifier.testTag("tappable"), subtitle = "Admin · PIN set", onClick = { taps++ })
                    HhListRow("Brighton", Modifier.testTag("still"))
                }
            }
        }
        compose.onNodeWithTag("tappable").assert(SemanticsMatcher.expectValue(SemanticsProperties.Role, Role.Button)).performClick()
        assertThat(taps).isEqualTo(1)
        compose.onNodeWithTag("still").assert(!hasClickAction())
    }

    @Test
    fun aRowsTitleAndSubtitleAreOneLineInTheSharedStyles() {
        compose.setContent { CulveryTheme(dark = true) { HhListRow("Alex", subtitle = "Admin · PIN set") } }
        val title = compose.onNodeWithText("Alex", useUnmergedTree = true).textLayout()
        val subtitle = compose.onNodeWithText("Admin · PIN set", useUnmergedTree = true).textLayout()
        assertThat(title.layoutInput.style.fontSize).isEqualTo(17.sp)
        assertThat(title.layoutInput.style.fontWeight).isEqualTo(FontWeight.W600)
        assertThat(title.layoutInput.maxLines).isEqualTo(1)
        assertThat(subtitle.layoutInput.style.fontSize).isEqualTo(14.sp)
        assertThat(subtitle.layoutInput.maxLines).isEqualTo(1)
    }
}
