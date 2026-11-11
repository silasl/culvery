package uk.co.siland.culvery.core.ui

import android.graphics.Bitmap
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.google.common.truth.Truth.assertThat
import kotlin.math.abs
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.GraphicsMode
import uk.co.siland.culvery.core.ui.test.windowBitmap

@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class ChristmasArtTest {
    @get:Rule val compose = createComposeRule()

    private class Drawing(val bitmap: Bitmap, val left: Int, val top: Int, val width: Int, val height: Int, val palette: Map<String, Color>) {
        /** The name of the palette colour [x],[y] is: every channel within 8, and no other colour nearer. */
        fun nameAt(x: Int, y: Int): String? {
            val p = bitmap.getPixel(left + x, top + y)
            fun gap(c: Color): Int {
                val a = c.toArgb()
                return maxOf(abs((p shr 16 and 255) - (a shr 16 and 255)), abs((p shr 8 and 255) - (a shr 8 and 255)), abs((p and 255) - (a and 255)))
            }
            val nearest = palette.minBy { gap(it.value) }
            return nearest.key.takeIf { gap(nearest.value) <= 8 }
        }

        fun drawn(name: String) = (0 until height).any { y -> (0 until width).any { x -> nameAt(x, y) == name } }
    }

    private fun draw(colors: HhColors): Drawing {
        compose.setContent {
            CulveryTheme(dark = !colors.isLight, seasonal = SeasonalTheme.CHRISTMAS) {
                Box(Modifier.background(Culvery.colors.bg)) { ChristmasMark(216.dp) }
            }
        }
        val mark = compose.onNodeWithTag("christmas_mark", useUnmergedTree = true)
        val bounds = mark.fetchSemanticsNode().boundsInWindow
        val palette = mapOf(
            "bg" to colors.bg,
            "ink" to colors.ink,
            "snow" to ChristmasInks.markSnow(colors),
            "robin" to ChristmasInks.robin(colors),
            "wreath" to ChristmasInks.wreath(colors),
        )
        return Drawing(windowBitmap(mark), bounds.left.toInt(), bounds.top.toInt(), bounds.width.toInt(), bounds.height.toInt(), palette)
    }

    @Test
    fun theChristmasMarkIsAPictureOnly() {
        val picture = draw(ChristmasNight)
        val config = compose.onNodeWithTag("christmas_mark", useUnmergedTree = true).fetchSemanticsNode().config
        assertThat(config.contains(SemanticsProperties.Text)).isFalse()
        assertThat(config.contains(SemanticsProperties.ContentDescription)).isFalse()
        assertThat(config.contains(SemanticsProperties.HideFromAccessibility)).isTrue()
        listOf("ink", "snow", "robin", "wreath").forEach { assertThat(picture.drawn(it)).isTrue() }
    }

    /** P1: the apex's pixel column runs cap, a line of `bg`, then the roof, with the cap inside the picture. */
    private fun theCapSitsOnTheRoof(colors: HhColors) {
        val picture = draw(colors)
        val x = ((51.5f - MarkViewport.LEFT) / MarkViewport.WIDTH * picture.width).toInt()
        val runs = mutableListOf<Pair<String, Int>>()
        for (y in 0 until picture.height) {
            val name = picture.nameAt(x, y) ?: continue
            if (runs.isEmpty() || runs.last().first != name) runs += name to 1 else runs[runs.size - 1] = name to runs.last().second + 1
        }
        val seen = runs.map { it.first }
        assertThat(seen.first { it != "bg" }).isEqualTo("snow")
        val cap = seen.indexOf("snow")
        assertThat(seen.getOrNull(cap + 1)).isEqualTo("bg")
        assertThat(seen.getOrNull(cap + 2)).isEqualTo("ink")
        var top = 0
        while (picture.nameAt(x, top) == "bg") top++
        assertThat(top).isLessThan(picture.height / 4)
    }

    @Test
    fun theSnowCapSitsOnTheRoofByNight() = theCapSitsOnTheRoof(ChristmasNight)

    @Test
    fun theSnowCapSitsOnTheRoofByDay() = theCapSitsOnTheRoof(ChristmasDay)
}
