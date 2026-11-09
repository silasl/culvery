package uk.co.siland.culvery.core.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.toArgb
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
import uk.co.siland.culvery.core.ui.test.pixelAt

/** Real graphics: a tray tile's fill is read from pixels. */
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class HouseModeTileTest {
    @get:Rule val compose = createComposeRule()

    private fun fill() = pixelAt(compose.onNodeWithTag("tile"), 40.dp, 40.dp)

    private fun show(tile: @Composable () -> Unit) {
        compose.mainClock.autoAdvance = false
        compose.setContent { CulveryTheme(dark = true) { Box(Modifier.size(100.dp)) { tile() } } }
        compose.mainClock.advanceTimeByFrame()
    }

    /** A frame recomposes a change; the window then needs a draw pass before the next frame shows it. */
    private fun settle() {
        compose.mainClock.advanceTimeByFrame()
        fill()
        compose.mainClock.advanceTimeByFrame()
    }

    @Test
    fun aTapFlashesForAHundredAndFiftyMs() {
        show { HouseModeThirdTile("tile", "Third", "Open", enabled = true, onTap = {}) {} }
        assertThat(fill()).isEqualTo(DarkColors.surf2.toArgb())

        compose.onNodeWithTag("tile").performClick()
        settle()
        assertThat(fill()).isEqualTo(DarkColors.accentSoft.toArgb())

        compose.mainClock.advanceTimeBy(PRESS_FLASH_MS - 50)
        assertThat(fill()).isEqualTo(DarkColors.accentSoft.toArgb())

        compose.mainClock.advanceTimeBy(50)
        settle()
        assertThat(fill()).isEqualTo(DarkColors.surf2.toArgb())
    }

    /** Regression guard: passes today, as mode tiles never flash. */
    @Test
    fun aModeTileDoesNotFlash() {
        show { HouseModeTile("tile", "home", "Party", on = false, second = null, secondTag = "second", spoken = "Party", enabled = true, onTap = {}) }
        compose.onNodeWithTag("tile").performClick()
        settle()

        assertThat(fill()).isEqualTo(DarkColors.surf2.toArgb())
    }
}
