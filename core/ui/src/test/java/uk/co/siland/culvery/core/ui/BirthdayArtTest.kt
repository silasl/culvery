package uk.co.siland.culvery.core.ui

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
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
class BirthdayArtTest {
    @get:Rule val compose = createComposeRule()

    @Test
    fun theCakeIsAPictureOnly() {
        val pink = PersonPalette.colors[2]
        compose.setContent { CulveryTheme(dark = true, birthday = Birthday(pink, listOf("Sam"))) { CakeMark(216.dp) } }
        val cake = compose.onNodeWithTag("cake_mark", useUnmergedTree = true)
        val config = cake.fetchSemanticsNode().config
        assertThat(config.contains(SemanticsProperties.Text)).isFalse()
        assertThat(config.contains(SemanticsProperties.ContentDescription)).isFalse()
        assertThat(config.contains(SemanticsProperties.HideFromAccessibility)).isTrue()
        val bounds = cake.fetchSemanticsNode().boundsInWindow
        val image = windowBitmap(cake)
        fun drawn(colour: Color) = (bounds.top.toInt() until bounds.bottom.toInt()).any { y ->
            (bounds.left.toInt() until bounds.right.toInt()).any { x -> image.getPixel(x, y) == colour.toArgb() }
        }
        val colours = birthdayLook(pink)!!.night
        assertThat(drawn(colours.accent)).isTrue()
        assertThat(drawn(colours.weather)).isTrue()
        assertThat(drawn(BirthdayInks.ICING)).isTrue()
    }
}
