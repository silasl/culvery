package uk.co.siland.culvery.core.ui

import android.graphics.Bitmap
import android.graphics.Canvas
import android.view.View
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.getUnclippedBoundsInRoot
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
import uk.co.siland.culvery.core.ui.test.assertClickablesHaveRoleAndLabel
import uk.co.siland.culvery.core.ui.test.pixelAt

/** Real graphics: a chip's fill is read from pixels drawn on `bg`. */
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class ChipFillTest {
    @get:Rule val compose = createComposeRule()

    private fun chipFill(chip: @Composable () -> Unit): Int {
        lateinit var window: View
        compose.setContent {
            window = LocalView.current.rootView
            CulveryTheme(dark = true) { Box(Modifier.fillMaxSize().background(Culvery.colors.bg)) { chip() } }
        }
        compose.waitForIdle()
        val bitmap = Bitmap.createBitmap(window.width, window.height, Bitmap.Config.ARGB_8888)
        window.draw(Canvas(bitmap))
        val bounds = compose.onNodeWithTag("chip").fetchSemanticsNode().boundsInWindow
        // 4 dp in from the left edge, level with the centre: inside the padding, clear of the label.
        val inset = with(compose.density) { 4.dp.toPx() }
        return bitmap.getPixel((bounds.left + inset).toInt(), bounds.center.y.toInt())
    }

    @Test
    fun anUnselectedChipCanBeFilled() {
        val fill = chipFill { HhChoiceChip("Relax", selected = false, tag = "chip", onClick = {}, unselectedFill = Culvery.colors.surf2) }
        assertThat(fill).isEqualTo(DarkColors.surf2.toArgb())
        compose.assertClickablesHaveRoleAndLabel()
    }

    /** Pin: with no fill given, an unselected chip is `surf`. */
    @Test
    fun theDefaultChipIsStillSurf() {
        val fill = chipFill { HhChoiceChip("Relax", selected = false, tag = "chip", onClick = {}) }
        assertThat(fill).isEqualTo(DarkColors.surf.toArgb())
    }

    @Test
    fun aChipCanCarryATrailingIcon() {
        compose.setContent {
            CulveryTheme(dark = true) {
                HhChoiceChip(
                    "2 rooms", selected = false, tag = "chip", onClick = {}, role = Role.Button,
                    trailing = { HhIcon(Icons.EXPAND_MORE, size = 20.dp, modifier = Modifier.background(Culvery.colors.accent)) },
                )
            }
        }
        val label = compose.onNodeWithText("2 rooms", useUnmergedTree = true).getUnclippedBoundsInRoot()
        val chip = compose.onNodeWithTag("chip").getUnclippedBoundsInRoot()
        assertThat(chip.right.value - label.right.value).isAtLeast(20f)
        compose.assertClickablesHaveRoleAndLabel()
    }

    private fun lockedChip(selected: Boolean, taps: () -> Unit = {}) {
        compose.setContent {
            CulveryTheme(dark = true) {
                Box(Modifier.fillMaxSize().background(Culvery.colors.bg)) {
                    HhChoiceChip(
                        "Mia", selected = selected, tag = "chip", onClick = taps, locked = true,
                        leading = { Box(Modifier.size(ControlTokens.chipDot).background(Culvery.colors.accent, CircleShape).testTag("dot")) },
                    )
                }
            }
        }
        compose.waitForIdle()
    }

    @Test
    fun aLockedChipHasNoFillALockAndAMutedLabel() {
        var taps = 0
        lockedChip(selected = false) { taps++ }
        val chip = compose.onNodeWithTag("chip")
        // Level with the centre: 4 dp in is inside the padding, 1 dp is on the edge.
        assertThat(pixelAt(chip, 4.dp, 24.dp)).isEqualTo(DarkColors.bg.toArgb())
        assertThat(pixelAt(chip, 1.dp, 24.dp)).isEqualTo(DarkColors.surf3.toArgb())
        val lock = compose.onNodeWithTag("chip_lock", useUnmergedTree = true).getUnclippedBoundsInRoot()
        val dot = compose.onNodeWithTag("dot", useUnmergedTree = true).getUnclippedBoundsInRoot()
        assertThat(lock.right).isAtMost(dot.left)
        assertThat(compose.onNodeWithText("Mia", useUnmergedTree = true).textLayout().layoutInput.style.color).isEqualTo(DarkColors.mute)
        chip.assert(SemanticsMatcher.expectValue(SemanticsProperties.StateDescription, "not available"))
        assertThat(chip.fetchSemanticsNode().config[SemanticsActions.OnClick].label).isEqualTo("find out why")
        chip.performClick()
        assertThat(taps).isEqualTo(1)
        compose.assertClickablesHaveRoleAndLabel()
    }

    @Test
    fun aLockedChipThatIsChosenIsFilled() {
        lockedChip(selected = true)
        assertThat(pixelAt(compose.onNodeWithTag("chip"), 4.dp, 24.dp)).isEqualTo(DarkColors.accent.toArgb())
        compose.onNodeWithTag("chip_lock", useUnmergedTree = true).assertExists()
        compose.assertClickablesHaveRoleAndLabel()
    }
}
