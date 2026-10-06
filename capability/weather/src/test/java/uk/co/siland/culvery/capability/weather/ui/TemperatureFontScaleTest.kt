package uk.co.siland.culvery.capability.weather.ui

import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.google.common.truth.Truth.assertThat
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.GraphicsMode
import uk.co.siland.culvery.core.ui.CulveryTheme
import uk.co.siland.culvery.core.ui.WithFontScale

/** Real text metrics: this measures lines and positions. */
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class TemperatureFontScaleTest {
    @get:Rule val compose = createComposeRule()

    @Test
    fun atFontScale15ColdTemperaturesStayOneLineAndApart() {
        compose.setContent { CulveryTheme(dark = false) { WithFontScale(1.5f) { TemperaturePair("-12°", "-23°") } } }
        val high = compose.onNodeWithText("-12°")
        val low = compose.onNodeWithText("-23°")
        assertThat(high.textLayout().lineCount).isEqualTo(1)
        assertThat(low.textLayout().lineCount).isEqualTo(1)
        assertThat(high.getUnclippedBoundsInRoot().right.value + 8f).isAtMost(low.getUnclippedBoundsInRoot().left.value + 0.5f)
    }
}
