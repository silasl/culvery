package uk.co.siland.culvery.core.ui

import android.graphics.Bitmap
import android.graphics.Canvas
import android.view.View
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.google.common.truth.Truth.assertThat
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.GraphicsMode

/** Real graphics: the close button's fill is read from pixels drawn on the sheet's `bg`. */
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class ShellPartsTest {
    @get:Rule val compose = createComposeRule()

    private fun closeButtonFill(dark: Boolean): Int {
        lateinit var window: View
        compose.setContent {
            window = LocalView.current.rootView
            CulveryTheme(dark = dark) { Box(Modifier.fillMaxSize().background(Culvery.colors.bg)) { HhCloseButton({}) } }
        }
        compose.waitForIdle()
        // captureToImage times out under Robolectric's paused looper, so draw the window.
        val bitmap = Bitmap.createBitmap(window.width, window.height, Bitmap.Config.ARGB_8888)
        window.draw(Canvas(bitmap))
        val bounds = compose.onNodeWithTag("sheet_close").fetchSemanticsNode().boundsInWindow
        // 6 dp in from the circle's left edge, level with its centre: inside the circle, outside the 26 dp icon.
        val sixDp = with(compose.density) { 6.dp.toPx() }
        return bitmap.getPixel((bounds.left + sixDp).toInt(), bounds.center.y.toInt())
    }

    @Test
    fun theCloseButtonIsAQuietButtonOnTheSheetInTheDay() {
        assertThat(closeButtonFill(dark = false)).isEqualTo(quietFill(ButtonSurface.Bg, LightColors).toArgb())
    }

    @Test
    fun theCloseButtonIsAQuietButtonOnTheSheetAtNight() {
        assertThat(closeButtonFill(dark = true)).isEqualTo(quietFill(ButtonSurface.Bg, DarkColors).toArgb())
    }

    @Test
    fun theCloseButtonIsAButtonNamedOnceByItsIcon() {
        compose.setContent { CulveryTheme(dark = true) { HhCloseButton({}) } }
        val node = compose.onNodeWithTag("sheet_close")
            .assert(SemanticsMatcher.expectValue(SemanticsProperties.Role, Role.Button))
            .assert(SemanticsMatcher.expectValue(SemanticsProperties.ContentDescription, listOf("Close")))
        assertThat(node.fetchSemanticsNode().config[SemanticsActions.OnClick].label).isNull()
    }
}
