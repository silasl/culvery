package uk.co.siland.culvery.core.setup

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.test.assertContentDescriptionEquals
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.unit.height
import androidx.compose.ui.unit.width
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.google.common.truth.Truth.assertThat
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.GraphicsMode
import uk.co.siland.culvery.core.ui.Culvery
import uk.co.siland.culvery.core.ui.CulveryTheme
import uk.co.siland.culvery.core.ui.LightColors
import uk.co.siland.culvery.core.ui.test.assertClickablesHaveRoleAndLabel
import uk.co.siland.culvery.core.ui.test.pixelAt

/** Done, here and to go (9 §4.11): the pill's width tells the current step without colour. Real graphics: this reads pixels. */
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class WizardDotsTest {
    @get:Rule val compose = createComposeRule()

    @Test
    fun doneHereAndToGo() {
        compose.setContent {
            CulveryTheme(dark = false) {
                Box(Modifier.fillMaxSize().background(Culvery.colors.bg)) {
                    WizardFrame(7, 3, back = {}, forward = Forward.Next("Next", enabled = true), busy = false, onForward = {}, onSkip = {}) {}
                }
            }
        }
        compose.waitForIdle()
        val dots = compose.onAllNodesWithTag("wizard_dot")
        fun bounds(i: Int) = dots[i].getUnclippedBoundsInRoot()
        fun centre(i: Int) = pixelAt(dots[i], bounds(i).width / 2, bounds(i).height / 2)
        (0..2).forEach {
            assertThat(centre(it)).isEqualTo(LightColors.accent.toArgb())
            assertThat(bounds(it).width.value).isWithin(0.5f).of(11f)
            assertThat(bounds(it).height.value).isWithin(0.5f).of(11f)
        }
        assertThat(bounds(3).width.value).isWithin(0.5f).of(29f)
        assertThat(bounds(3).height.value).isWithin(0.5f).of(11f)
        assertThat(centre(3)).isEqualTo(LightColors.accent.toArgb())
        (4..6).forEach {
            assertThat(centre(it)).isEqualTo(LightColors.surf3.toArgb())
            assertThat(bounds(it).width.value).isWithin(0.5f).of(11f)
        }
        (0..5).forEach { assertThat(bounds(it + 1).left.value - bounds(it).right.value).isWithin(0.5f).of(9f) }
        compose.assertClickablesHaveRoleAndLabel()
    }

    @Test
    fun theDotsAreOneSpokenStep() {
        compose.setContent {
            CulveryTheme(dark = false) {
                WizardFrame(7, 0, back = null, forward = Forward.Next("Start", enabled = true), busy = false, onForward = {}, onSkip = {}) {}
            }
        }
        compose.onNodeWithTag("wizard_dots").assertContentDescriptionEquals("Step 1 of 7")
        compose.assertClickablesHaveRoleAndLabel()
    }
}
