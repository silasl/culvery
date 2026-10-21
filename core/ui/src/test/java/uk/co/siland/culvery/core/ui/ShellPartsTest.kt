package uk.co.siland.culvery.core.ui

import android.graphics.Bitmap
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertWidthIsEqualTo
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.google.common.truth.Truth.assertThat
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.GraphicsMode
import uk.co.siland.culvery.core.ui.test.windowBitmap

/** Real graphics: the close button's fill is read from pixels drawn on the sheet's `bg`. */
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class ShellPartsTest {
    @get:Rule val compose = createComposeRule()

    private fun drawWindow(content: @Composable () -> Unit): Bitmap {
        compose.setContent(content)
        compose.waitForIdle()
        return windowBitmap(compose.onRoot())
    }

    private fun closeButtonFill(dark: Boolean): Int {
        val bitmap = drawWindow {
            CulveryTheme(dark = dark) { Box(Modifier.fillMaxSize().background(Culvery.colors.bg)) { HhCloseButton({}) } }
        }
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

    @Test
    fun theSheetIsTheHandoffsWidth() {
        compose.setContent {
            CulveryTheme(dark = true) { Box(Modifier.size(1280.dp, 800.dp)) { HhSheet(PaddingValues(), Modifier.testTag("sheet")) {} } }
        }
        compose.onNodeWithTag("sheet").assertWidthIsEqualTo(613.dp)
    }

    private fun sheetEdge(dark: Boolean): Int {
        val bitmap = drawWindow {
            CulveryTheme(dark = dark) { Box(Modifier.fillMaxSize()) { HhSheet(PaddingValues(), Modifier.testTag("sheet")) {} } }
        }
        val bounds = compose.onNodeWithTag("sheet").fetchSemanticsNode().boundsInWindow
        return bitmap.getPixel(bounds.left.toInt(), bounds.center.y.toInt())
    }

    @Test
    fun theSheetsEdgeIsSurf3AtNight() {
        assertThat(sheetEdge(dark = true)).isEqualTo(0xFF303537.toInt())
    }

    @Test
    fun theSheetsEdgeIsSurf3InTheDay() {
        assertThat(sheetEdge(dark = false)).isEqualTo(0xFFDDE3E0.toInt())
    }

    private fun scrimOverBg(dark: Boolean): Int {
        val bitmap = drawWindow {
            CulveryTheme(dark = dark) {
                Box(Modifier.fillMaxSize().background(Culvery.colors.bg)) { Box(Modifier.fillMaxSize().background(ShellTokens.sheetScrim)) }
            }
        }
        return bitmap.getPixel(bitmap.width / 2, bitmap.height / 2)
    }

    private fun assertNear(actual: Int, expected: Int) {
        for (shift in intArrayOf(16, 8, 0)) {
            assertThat(((actual shr shift) and 0xFF).toFloat()).isWithin(2f).of(((expected shr shift) and 0xFF).toFloat())
        }
    }

    @Test
    fun theScrimDarkensBgToTheHandoffsAtNight() {
        assertNear(scrimOverBg(dark = true), 0xFF060606.toInt())
    }

    @Test
    fun theScrimDarkensBgToTheHandoffsInTheDay() {
        assertNear(scrimOverBg(dark = false), 0xFF5F6060.toInt())
    }

    @Test
    fun theRingShowsTheTimeLeft() {
        assertThat(ringLeft(20_000, 20_000)).isEqualTo(1f)
        assertThat(ringLeft(10_000, 20_000)).isEqualTo(0.5f)
        assertThat(ringLeft(0, 20_000)).isEqualTo(0f)
        assertThat(ringLeft(-5_000, 20_000)).isEqualTo(0f)
        assertThat(ringLeft(30_000, 20_000)).isEqualTo(1f)
    }
}
