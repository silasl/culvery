package uk.co.siland.culvery.core.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.unit.height
import androidx.compose.ui.unit.sp
import androidx.compose.ui.unit.width
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.google.common.truth.Truth.assertThat
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.GraphicsMode
import uk.co.siland.culvery.core.ui.test.assertClickablesHaveRoleAndLabel
import uk.co.siland.culvery.core.ui.test.pixelAt

@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class StatusLineTest {
    @get:Rule val compose = createComposeRule()

    private val liveRegion = SemanticsMatcher.expectValue(SemanticsProperties.LiveRegion, LiveRegionMode.Polite)

    private fun show(content: @Composable () -> Unit) =
        compose.setContent { CulveryTheme(dark = true) { Box(Modifier.fillMaxSize().background(Culvery.colors.bg)) { content() } } }

    @Test
    fun aFieldErrorIsDangerWithTheErrorIcon() {
        show { HhStatusLine("Someone is already called Sam.", "x", icon = Icons.ERROR, style = ControlType.fieldError) }
        compose.onNodeWithTag("x").assertIsDisplayed().assert(liveRegion)
        val text = compose.onNode(hasText("Someone is already called Sam."), useUnmergedTree = true)
        assertThat(text.textLayout().layoutInput.style.color).isEqualTo(DarkColors.danger)
        assertThat(text.textLayout().layoutInput.style.fontSize).isEqualTo(14.sp)
        val iconNode = compose.onNodeWithTag("x_icon", useUnmergedTree = true)
        val icon = iconNode.getUnclippedBoundsInRoot()
        assertThat(icon.width.value).isWithin(0.5f).of(17f)
        assertThat((text.getUnclippedBoundsInRoot().left - icon.right).value).isWithin(0.5f).of(5f)
        // The filled glyph's disc is solid `danger` a quarter in from its left; the outline's is empty there.
        assertThat(pixelAt(iconNode, icon.width / 4, icon.height / 2)).isEqualTo(DarkColors.danger.toArgb())
        compose.assertClickablesHaveRoleAndLabel()
    }

    @Test
    fun aFieldsOwnErrorIsHiddenFromTalkBack() {
        show { HhStatusLine("Someone is already called Sam.", "x", icon = Icons.ERROR, style = ControlType.fieldError, spoken = false) }
        compose.onNodeWithTag("x").assertIsDisplayed()
            .assert(SemanticsMatcher.keyIsDefined(SemanticsProperties.HideFromAccessibility))
            .assert(SemanticsMatcher.keyNotDefined(SemanticsProperties.LiveRegion))
        compose.assertClickablesHaveRoleAndLabel()
    }

    /** Pin: the plain status line keeps the outline `info` icon and the 16 sp body, and is live. */
    @Test
    fun theStatusLineIsUnchangedByDefault() {
        show { HhStatusLine("Can’t reach the bridge.", "x") }
        compose.onNodeWithTag("x").assert(liveRegion)
        val text = compose.onNode(hasText("Can’t reach the bridge."), useUnmergedTree = true)
        assertThat(text.textLayout().layoutInput.style.fontSize).isEqualTo(16.sp)
        val iconNode = compose.onNodeWithTag("x_icon", useUnmergedTree = true)
        val icon = iconNode.getUnclippedBoundsInRoot()
        assertThat(icon.width.value).isEqualTo(20f)
        // An outline glyph is hollow a quarter in from its left.
        assertThat(pixelAt(iconNode, icon.width / 4, icon.height / 2)).isNotEqualTo(DarkColors.danger.toArgb())
    }
}
