package uk.co.siland.culvery.core.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.width
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.assertHeightIsEqualTo
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.google.common.truth.Truth.assertThat
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.GraphicsMode

/** Real text metrics: these measure lines and ellipses. */
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class FontScaleTest {
    @get:Rule val compose = createComposeRule()

    /** Two Large buttons sharing a narrow footer at 1.5: a long label mustn't wrap or shrink its button. */
    @Test
    fun atFontScale15ALabelLongerThanItsButtonStaysOneLine() {
        compose.setContent {
            CulveryTheme(dark = false) {
                WithFontScale(1.5f) {
                    Row(Modifier.width(280.dp)) {
                        HhButton(
                            "Delete", {}, Modifier.weight(1f),
                            tone = ButtonTone.Danger, size = ButtonSize.Large, icon = Icons.DELETE, on = ButtonSurface.Bg,
                        )
                        HhButton(
                            "Save changes", {}, Modifier.weight(1f).testTag("save"),
                            tone = ButtonTone.Primary, size = ButtonSize.Large, icon = Icons.CHECK, on = ButtonSurface.Bg,
                        )
                    }
                }
            }
        }
        val label = compose.onNode(hasText("Save changes") and hasAnyAncestor(hasTestTag("save")), useUnmergedTree = true).textLayout()
        assertThat(label.layoutInput.density.fontScale).isEqualTo(1.5f)
        assertThat(label.lineCount).isEqualTo(1)
        assertThat(label.isLineEllipsized(0)).isTrue()
        compose.onNodeWithTag("save").assertHeightIsEqualTo(60.dp)
    }

    @Test
    fun atFontScale15AChipsLongLabelTakesTwoLinesAndTheChipGrowsToHoldThem() {
        val label = "Saturday morning at the leisure centre pool"
        compose.setContent {
            CulveryTheme(dark = false) {
                WithFontScale(1.5f) { Box(Modifier.width(220.dp)) { HhChoiceChip(label, selected = false, tag = "chip", onClick = {}) } }
            }
        }
        val layout = compose.onNodeWithText(label, useUnmergedTree = true).textLayout()
        assertThat(layout.lineCount).isEqualTo(2)
        assertThat(layout.isLineEllipsized(1)).isTrue()
        val chip = compose.onNodeWithTag("chip").getUnclippedBoundsInRoot()
        val text = compose.onNodeWithText(label, useUnmergedTree = true).getUnclippedBoundsInRoot()
        assertThat(text.bottom.value).isAtMost(chip.bottom.value)
    }
}
