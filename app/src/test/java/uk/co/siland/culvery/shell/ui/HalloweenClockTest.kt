package uk.co.siland.culvery.shell.ui

import android.graphics.Bitmap
import android.graphics.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.platform.ViewRootForTest
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.height
import androidx.compose.ui.unit.width
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.google.common.truth.Truth.assertThat
import java.time.LocalDateTime
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.GraphicsMode
import uk.co.siland.culvery.core.plugin.HeaderItem
import uk.co.siland.culvery.core.ui.Creepster
import uk.co.siland.culvery.core.ui.CulveryTheme
import uk.co.siland.culvery.core.ui.DmSans
import uk.co.siland.culvery.core.ui.HalloweenNight
import uk.co.siland.culvery.core.ui.SeasonalTheme

/** Needs real glyph metrics and pixels: legacy Robolectric graphics fakes both. */
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class HalloweenClockTest {
    @get:Rule val compose = createComposeRule()
    private var now by mutableStateOf(LocalDateTime.of(2026, 10, 29, 23, 58))
    private var seasonal by mutableStateOf<SeasonalTheme?>(SeasonalTheme.HALLOWEEN)

    private fun show(items: List<HeaderItem> = emptyList()) = compose.setContent {
        CulveryTheme(dark = true, seasonal = seasonal) {
            Box(Modifier.background(HalloweenNight.bg)) { HomeHeader({ now }, items, Modifier.fillMaxWidth().testTag("header")) }
        }
    }

    private fun at(hour: Int, minute: Int) {
        now = LocalDateTime.of(2026, 10, 29, hour, minute)
        compose.waitForIdle()
    }

    private fun bounds(tag: String) = compose.onNodeWithTag(tag).getUnclippedBoundsInRoot()

    @Test
    fun theClockIsInCreepster() {
        show()
        val slots = compose.onAllNodesWithTag("clock_slot", useUnmergedTree = true)
        assertThat(slots[0].textLayout().layoutInput.style.fontFamily).isEqualTo(Creepster)
    }

    @Test
    fun theClockKeepsItsWidthEveryMinute() {
        show()
        val widths = listOf(11 to 11, 0 to 0, 8 to 47, 23 to 58).map { (h, m) ->
            at(h, m)
            bounds("home_clock").width.value
        }
        assertThat(widths.toSet()).hasSize(1)
    }

    @Test
    fun theHeaderKeepsItsHeightInEitherFace() {
        show()
        seasonal = null
        compose.waitForIdle()
        val normal = bounds("header").height.value to bounds("home_clock").height.value
        seasonal = SeasonalTheme.HALLOWEEN
        compose.waitForIdle()
        assertThat(bounds("header").height.value to bounds("home_clock").height.value).isEqualTo(normal)
    }

    @Test
    fun theClockDrawsNothingIntoTheDate() {
        show()
        // 22:22 and 23:58 hold Creepster's lowest drips.
        listOf(23 to 58, 22 to 22).forEach { (h, m) ->
            at(h, m)
            val dateTop = compose.onNodeWithTag("home_date").fetchSemanticsNode().boundsInRoot.top.toInt()
            val clock = compose.onNodeWithTag("home_clock").fetchSemanticsNode().boundsInRoot
            val image = snapshot()
            val low = lowestPixel(image, HalloweenNight.accent, clock)
            assertThat(low).isLessThan(dateTop)
        }
    }

    @Test
    fun theHeaderItemsDontMoveWhenTheFaceChanges() {
        val item = HeaderItem("sample", 10) { Box(Modifier.size(180.dp, 60.dp).background(Color.Red)) }
        show(listOf(item))
        seasonal = null
        compose.waitForIdle()
        val normal = compose.onNodeWithTag("home_header_items").fetchSemanticsNode().boundsInRoot
        seasonal = SeasonalTheme.HALLOWEEN
        compose.waitForIdle()
        assertThat(compose.onNodeWithTag("home_header_items").fetchSemanticsNode().boundsInRoot).isEqualTo(normal)
    }

    /** Pin: the date is not part of the clock face. */
    @Test
    fun theDateStaysInDmSans() {
        show()
        assertThat(compose.onNodeWithTag("home_date").textLayout().layoutInput.style.fontFamily).isEqualTo(DmSans)
    }

    @Test
    fun theClockIsTheThemesOrange() {
        show()
        val clock = compose.onNodeWithTag("home_clock").fetchSemanticsNode().boundsInRoot
        val image = snapshot()
        assertThat(lowestPixel(image, HalloweenNight.accent, clock)).isAtLeast(0)
        assertThat(lowestPixel(image, HalloweenNight.ink, clock)).isLessThan(0)
    }

    /** The composed window drawn to a bitmap; `captureToImage` waits on a redraw that Robolectric never sends. */
    private fun snapshot(): Bitmap {
        val view = (compose.onRoot().fetchSemanticsNode().root as ViewRootForTest).view
        return Bitmap.createBitmap(view.width, view.height, Bitmap.Config.ARGB_8888).also { view.draw(Canvas(it)) }
    }

    /** The y of the lowest pixel exactly [colour] in the columns of [across], or -1. */
    private fun lowestPixel(image: Bitmap, colour: Color, across: Rect): Int {
        val want = colour.toArgb()
        for (y in image.height - 1 downTo 0) {
            for (x in across.left.toInt() until minOf(across.right.toInt(), image.width)) {
                if (image.getPixel(x, y) == want) return y
            }
        }
        return -1
    }
}
