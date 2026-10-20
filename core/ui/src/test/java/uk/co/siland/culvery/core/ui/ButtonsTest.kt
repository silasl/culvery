package uk.co.siland.culvery.core.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertContentDescriptionEquals
import androidx.compose.ui.test.assertHeightIsEqualTo
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.assertWidthIsEqualTo
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.google.common.truth.Truth.assertThat
import com.google.common.truth.Truth.assertWithMessage
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class ButtonsTest {
    @get:Rule val compose = createComposeRule()

    private val tones = ButtonTone.entries
    private val enabledStates = listOf(true, false)

    private fun tag(tone: ButtonTone, size: ButtonSize, enabled: Boolean, on: ButtonSurface) = "${tone}_${size}_${enabled}_$on"

    @Test
    fun everyToneSizeAndSurfaceIsAButtonOfItsHeightAndADisabledOneIsMuteAndDoesNothing() {
        val taps = mutableListOf<String>()
        compose.setContent {
            CulveryTheme(dark = false) {
                FlowRow(horizontalArrangement = Arrangement.spacedBy(4.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    tones.forEach { tone ->
                        ButtonSize.entries.forEach { size ->
                            enabledStates.forEach { enabled ->
                                ButtonSurface.entries.forEach { on ->
                                    val t = tag(tone, size, enabled, on)
                                    HhButton("Save", { taps += t }, Modifier.testTag(t), tone = tone, size = size, enabled = enabled, on = on)
                                }
                            }
                        }
                    }
                }
            }
        }
        tones.forEach { tone ->
            ButtonSize.entries.forEach { size ->
                enabledStates.forEach { enabled ->
                    ButtonSurface.entries.forEach { on ->
                        val t = tag(tone, size, enabled, on)
                        val node = compose.onNodeWithTag(t)
                            .assert(SemanticsMatcher.expectValue(SemanticsProperties.Role, Role.Button))
                            .assertHeightIsEqualTo(if (size == ButtonSize.Large) 60.dp else 48.dp)
                        if (enabled) node.assertIsEnabled() else node.assertIsNotEnabled()
                        node.performClick()
                        val label = compose.onNode(hasText("Save") and hasAnyAncestor(hasTestTag(t)), useUnmergedTree = true)
                            .textLayout().layoutInput.style.color
                        assertWithMessage("$t label").that(label).isEqualTo(buttonColors(tone, enabled, on, LightColors).ink)
                        if (!enabled) assertWithMessage("$t disabled label").that(label).isEqualTo(LightColors.mute)
                    }
                }
            }
        }
        assertWithMessage("disabled taps").that(taps.filter { it.contains("_false_") }).isEmpty()
        assertWithMessage("enabled buttons tapped").that(taps).hasSize(tones.size * ButtonSize.entries.size * ButtonSurface.entries.size)
    }

    @Test
    fun aDisabledButtonIsAnOutlineWithAMutedLabel() {
        listOf(DarkColors, LightColors).forEach { c ->
            tones.forEach { tone ->
                ButtonSurface.entries.forEach { on ->
                    assertWithMessage("$tone on $on").that(buttonColors(tone, enabled = false, on, c))
                        .isEqualTo(ButtonColors(Color.Transparent, c.mute, c.surf3))
                }
            }
        }
        val taps = mutableListOf<String>()
        compose.setContent {
            CulveryTheme(dark = false) {
                HhButton("Save", { taps += "button" }, Modifier.testTag("button"), enabled = false, on = ButtonSurface.Surf)
                HhRoundButton(Icons.PAUSE, "Pause Kitchen", { taps += "round" }, Modifier.testTag("round"), enabled = false, on = ButtonSurface.Surf)
            }
        }
        listOf("button", "round").forEach { compose.onNodeWithTag(it).assertIsNotEnabled().performClick() }
        assertThat(taps).isEmpty()
    }

    @Test
    fun aTrailingIconSitsAfterTheLabel() {
        compose.setContent {
            CulveryTheme(dark = false) {
                HhButton("Door history", {}, Modifier.testTag("door"), trailingIcon = Icons.CHEVRON_RIGHT, on = ButtonSurface.Surf)
            }
        }
        val node = compose.onNodeWithTag("door").assertTextEquals("Door history")
        val label = compose.onNode(hasText("Door history") and hasAnyAncestor(hasTestTag("door")), useUnmergedTree = true).getUnclippedBoundsInRoot()
        val whole = node.getUnclippedBoundsInRoot()
        // A plain button ends 20 dp after its label; the icon, its gap and the 14 dp end padding make more.
        assertThat((whole.right - label.right).value).isGreaterThan(ButtonTokens.regularPaddingH.value)
    }

    @Test
    fun aRoundButtonIsNamedAndPrimaryIsAccent() {
        compose.setContent {
            CulveryTheme(dark = false) {
                HhRoundButton(Icons.PAUSE, "Pause Kitchen", {}, Modifier.testTag("pause"), tone = ButtonTone.Primary, on = ButtonSurface.Surf)
            }
        }
        compose.onNodeWithTag("pause")
            .assert(SemanticsMatcher.expectValue(SemanticsProperties.Role, Role.Button))
            .assertContentDescriptionEquals("Pause Kitchen")
            .assertWidthIsEqualTo(48.dp)
            .assertHeightIsEqualTo(48.dp)
        assertThat(buttonColors(ButtonTone.Primary, true, ButtonSurface.Surf, LightColors).fill).isEqualTo(LightColors.accent)
    }
}
