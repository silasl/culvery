package uk.co.siland.culvery.core.ui

import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.google.common.truth.Truth.assertThat
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.GraphicsMode
import uk.co.siland.culvery.core.ui.test.windowBitmap

@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class HalloweenArtTest {
    @get:Rule val compose = createComposeRule()

    @Test
    fun thePumpkinIsAPictureOnly() {
        compose.setContent { CulveryTheme(dark = true, seasonal = SeasonalTheme.HALLOWEEN) { PumpkinMark(216.dp) } }
        val pumpkin = compose.onNodeWithTag("pumpkin_mark", useUnmergedTree = true)
        val config = pumpkin.fetchSemanticsNode().config
        assertThat(config.contains(SemanticsProperties.Text)).isFalse()
        assertThat(config.contains(SemanticsProperties.ContentDescription)).isFalse()
        assertThat(config.contains(SemanticsProperties.HideFromAccessibility)).isTrue()
        val bounds = pumpkin.fetchSemanticsNode().boundsInWindow
        val image = windowBitmap(pumpkin)
        fun drawn(colour: Color) = (bounds.top.toInt() until bounds.bottom.toInt()).any { y ->
            (bounds.left.toInt() until bounds.right.toInt()).any { x -> image.getPixel(x, y) == colour.toArgb() }
        }
        assertThat(drawn(HalloweenNight.accent)).isTrue()
        assertThat(drawn(HalloweenInks.PUMPKIN_STEM)).isTrue()
        assertThat(drawn(HalloweenInks.PUMPKIN_FACE)).isTrue()
    }
}
