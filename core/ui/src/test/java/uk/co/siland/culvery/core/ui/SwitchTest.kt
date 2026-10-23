package uk.co.siland.culvery.core.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.compositeOver
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.state.ToggleableState
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertHeightIsEqualTo
import androidx.compose.ui.test.assertWidthIsEqualTo
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.isNotEnabled
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.google.common.truth.Truth.assertThat
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.GraphicsMode
import uk.co.siland.culvery.core.ui.test.assertClickablesHaveRoleAndLabel
import uk.co.siland.culvery.core.ui.test.pixelAt

/** The redesign's switch (9 §4.12): 59 × 33 dp, drawn here. */
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class SwitchTest {
    @get:Rule val compose = createComposeRule()

    // A switch with an action has no name of its own (its row names it), so those tests skip assertClickablesHaveRoleAndLabel.
    private val sw get() = compose.onNodeWithTag("sw", useUnmergedTree = true)

    private fun show(checked: Boolean, enabled: Boolean = true, onChange: ((Boolean) -> Unit)? = null) {
        compose.mainClock.autoAdvance = false
        compose.setContent {
            CulveryTheme(dark = true) {
                Box(Modifier.fillMaxSize().background(Culvery.colors.bg)) { HhSwitch(checked, onChange, "sw", enabled) }
    }
        }
        compose.mainClock.advanceTimeBy(300)
    }

    private fun assertPixel(actual: Int, expected: Color) {
        val e = expected.toArgb()
        listOf(16, 8, 0).forEach { shift ->
            assertThat(((actual shr shift) and 0xFF).toFloat()).isWithin(2f).of(((e shr shift) and 0xFF).toFloat())
        }
    }

    @Test
    fun theSwitchIsFiftyNineByThirtyThree() {
        show(checked = true)
        sw.assertWidthIsEqualTo(59.dp).assertHeightIsEqualTo(33.dp)
        assertPixel(pixelAt(sw, 50.dp, 16.dp), DarkColors.accentInk)
        assertPixel(pixelAt(sw, 6.dp, 16.dp), DarkColors.accent)
        compose.assertClickablesHaveRoleAndLabel()
    }

    @Test
    fun anOffSwitchHasAMuteKnobOnASurf3Track() {
        show(checked = false)
        assertPixel(pixelAt(sw, 6.dp, 16.dp), DarkColors.mute)
        assertPixel(pixelAt(sw, 50.dp, 16.dp), DarkColors.surf3)
        compose.assertClickablesHaveRoleAndLabel()
    }

    @Test
    fun aDisabledOnSwitchIsTheAccentAtThirtyEightPercent() {
        show(checked = true, enabled = false)
        assertPixel(pixelAt(sw, 6.dp, 16.dp), DarkColors.accent.copy(alpha = 0.38f).compositeOver(DarkColors.bg))
        assertPixel(pixelAt(sw, 50.dp, 16.dp), DarkColors.accentInk)
        compose.assertClickablesHaveRoleAndLabel()
    }

    @Test
    fun aSwitchThatOnlyDrawsHasNoAction() {
        show(checked = true)
        sw.assert(!hasClickAction())
        compose.assertClickablesHaveRoleAndLabel()
    }

    @Test
    fun aDisabledSwitchDoesNotToggle() {
        val changes = mutableListOf<Boolean>()
        show(checked = false, enabled = false, onChange = changes::add)
        sw.performClick()
        sw.fetchSemanticsNode().config.getOrNull(SemanticsActions.OnClick)?.action?.invoke()
        assertThat(changes).isEmpty()
    }

    @Test
    fun anEnabledSwitchReportsTheNewState() {
        val changes = mutableListOf<Boolean>()
        show(checked = false, onChange = changes::add)
        sw.performClick()
        assertThat(changes).containsExactly(true)
    }

    @Test
    fun aDisabledOnSwitchStaysOnAndIsNamedAsDisabled() {
        show(checked = true, enabled = false, onChange = {})
        assertThat(sw.fetchSemanticsNode().config[SemanticsProperties.ToggleableState]).isEqualTo(ToggleableState.On)
        sw.assert(isNotEnabled())
    }

    @Test
    fun theKnobDoesNotAnimateOnFirstShow() {
        compose.mainClock.autoAdvance = false
        compose.setContent {
            CulveryTheme(dark = true) { Box(Modifier.fillMaxSize().background(Culvery.colors.bg)) { HhSwitch(true, null, "sw") } }
        }
        assertPixel(pixelAt(sw, 50.dp, 16.dp), DarkColors.accentInk)
        compose.assertClickablesHaveRoleAndLabel()
    }
}
