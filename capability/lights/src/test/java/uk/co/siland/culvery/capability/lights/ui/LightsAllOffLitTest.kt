package uk.co.siland.culvery.capability.lights.ui

import android.graphics.Bitmap
import android.graphics.Canvas
import android.view.View
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.google.common.truth.Truth.assertThat
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.GraphicsMode
import uk.co.siland.culvery.capability.lights.room
import uk.co.siland.culvery.core.ui.Culvery
import uk.co.siland.culvery.core.ui.CulveryTheme
import uk.co.siland.culvery.core.ui.DarkColors

/** All lights off is lit in the accent, as Holiday lights are when on, while a light it would turn off is on. Real graphics: this reads pixels. */
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class LightsAllOffLitTest {
    @get:Rule val compose = createComposeRule()

    private fun allOffTag(onTab: Boolean) = if (onTab) "lights_tray_all_off" else "lights_card_all_off"

    private fun fillOf(lit: Boolean, onTab: Boolean): Int {
        lateinit var window: View
        compose.setContent {
            window = LocalView.current.rootView
            CulveryTheme(dark = true) {
                Box(Modifier.fillMaxSize().background(Culvery.colors.bg)) {
                    if (onTab) {
                        Box(Modifier.size(900.dp, 800.dp)) {
                            LightsTab(listOf(room("Lounge", on = lit)), false, lit, null, null, null, emptyMap(), null, {}, NO_HOUSE_ACTIONS, RoomActions.None)
                        }
                    } else {
                        Box(Modifier.size(345.dp, 279.dp)) {
                            LightsCard(room("Lounge", on = lit), false, lit, null, null, null, NO_MAIN_ROOM_ACTIONS, NO_HOUSE_ACTIONS)
                        }
                    }
                }
            }
        }
        compose.waitForIdle()
        // captureToImage waits on a frame callback that Robolectric's paused main looper never delivers, so draw the window.
        val bitmap = Bitmap.createBitmap(window.width, window.height, Bitmap.Config.ARGB_8888)
        window.draw(Canvas(bitmap))
        val bounds = compose.onNodeWithTag(allOffTag(onTab)).fetchSemanticsNode().boundsInWindow
        // 6 dp in from the pill's left edge, level with its centre: inside the pill, left of the icon.
        val sixDp = with(compose.density) { 6.dp.toPx() }
        return bitmap.getPixel((bounds.left + sixDp).toInt(), bounds.center.y.toInt())
    }

    /** Fails if the card's button isn't the accent while lit. */
    @Test
    fun theCardButtonIsAccentWhenLit() {
        assertThat(fillOf(lit = true, onTab = false)).isEqualTo(DarkColors.accent.toArgb())
    }

    /** Fails if the card's button keeps a fill, rather than the shared outline, with nothing to turn off. */
    @Test
    fun theCardButtonIsAnOutlineWhenNotLit() {
        assertThat(fillOf(lit = false, onTab = false)).isEqualTo(DarkColors.surf.toArgb())
    }

    /** Fails if the tab's button isn't the accent while lit. */
    @Test
    fun theTabButtonIsAccentWhenLit() {
        assertThat(fillOf(lit = true, onTab = true)).isEqualTo(DarkColors.accent.toArgb())
    }

    /** Fails if the tab's button keeps a fill, rather than the shared outline, with nothing to turn off. */
    @Test
    fun theTabButtonIsAnOutlineWhenNotLit() {
        assertThat(fillOf(lit = false, onTab = true)).isEqualTo(DarkColors.bg.toArgb())
    }

    private fun tap(lit: Boolean, onTab: Boolean): List<String> {
        val calls = mutableListOf<String>()
        val house = HouseActions(onAllOff = { calls += "all off" }, onHoliday = {})
        compose.setContent {
            CulveryTheme(dark = true) {
                if (onTab) {
                    LightsTab(listOf(room("Lounge", on = lit)), false, lit, null, null, null, emptyMap(), null, {}, house, RoomActions.None)
                } else {
                    LightsCard(room("Lounge", on = lit), false, lit, null, null, null, NO_MAIN_ROOM_ACTIONS, house)
                }
            }
        }
        compose.onNodeWithTag(allOffTag(onTab)).performClick()
        return calls
    }

    /** Fails if the card's button is enabled with nothing to turn off, or a tap on it sends All lights off. */
    @Test
    fun theCardButtonIsDisabledAndSendsNothingWhenNotLit() {
        assertThat(tap(lit = false, onTab = false)).isEmpty()
        compose.onNodeWithTag("lights_card_all_off").assertIsNotEnabled()
    }

    /** Fails if the tab's button is enabled with nothing to turn off, or a tap on it sends All lights off. */
    @Test
    fun theTabButtonIsDisabledAndSendsNothingWhenNotLit() {
        assertThat(tap(lit = false, onTab = true)).isEmpty()
        compose.onNodeWithTag("lights_tray_all_off").assertIsNotEnabled()
    }

    /** Fails if a lit button is disabled or its tap doesn't send All lights off. */
    @Test
    fun aLitButtonIsEnabledAndSends() {
        assertThat(tap(lit = true, onTab = false)).containsExactly("all off")
        compose.onNodeWithTag("lights_card_all_off").assertIsEnabled()
    }
}
