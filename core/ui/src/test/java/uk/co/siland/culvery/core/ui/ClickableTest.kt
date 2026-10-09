package uk.co.siland.culvery.core.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.size
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.google.common.truth.Truth.assertThat
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class ClickableTest {
    @get:Rule val compose = createComposeRule()

    private fun clickLabel(tag: String): String? = compose.onNodeWithTag(tag).fetchSemanticsNode().config[SemanticsActions.OnClick].label

    @Test
    fun aClickableIsAButtonWithItsLabelUnlessTold() {
        var taps = 0
        compose.setContent {
            CulveryTheme(dark = true) {
                // In a Column, so the boxes don't sit on top of each other and take each other's taps.
                Column {
                    Box(Modifier.size(48.dp).testTag("plain").hhClickable({ taps++ }))
                    Box(Modifier.size(48.dp).testTag("labelled").hhClickable({ taps++ }, label = "open event details", role = Role.Tab))
                    Box(Modifier.size(48.dp).testTag("off").hhClickable({ taps++ }, enabled = false))
                }
            }
        }
        compose.onNodeWithTag("plain").assert(SemanticsMatcher.expectValue(SemanticsProperties.Role, Role.Button)).performClick()
        assertThat(clickLabel("plain")).isNull()
        compose.onNodeWithTag("labelled").assert(SemanticsMatcher.expectValue(SemanticsProperties.Role, Role.Tab))
        assertThat(clickLabel("labelled")).isEqualTo("open event details")
        compose.onNodeWithTag("off").assertIsNotEnabled().performClick()
        assertThat(taps).isEqualTo(1)
    }

    @Test
    fun aButtonsSpokenNameIsItsDescription() {
        compose.setContent {
            CulveryTheme(dark = true) {
                HhButton("Disconnect", {}, Modifier.testTag("disconnect"), on = ButtonSurface.Surf, spokenName = "Disconnect Google Calendar, alex@example.com")
            }
        }
        compose.onNodeWithTag("disconnect")
            .assert(SemanticsMatcher.expectValue(SemanticsProperties.ContentDescription, listOf("Disconnect Google Calendar, alex@example.com")))
    }

    @Test
    fun aListRowsClickLabelSaysWhatHappens() {
        compose.setContent {
            CulveryTheme(dark = true) {
                HhListRow("Sam", Modifier.testTag("row"), subtitle = "Adult · PIN set", onClick = {}, clickLabel = "change Sam’s details")
            }
        }
        compose.onNodeWithTag("row").assert(SemanticsMatcher.expectValue(SemanticsProperties.Role, Role.Button))
        assertThat(clickLabel("row")).isEqualTo("change Sam’s details")
    }
}
