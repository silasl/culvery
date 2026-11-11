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
import androidx.compose.ui.platform.ViewRootForTest
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.height
import androidx.compose.ui.unit.width
import com.google.common.truth.Truth.assertThat
import java.time.LocalDateTime
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.ParameterizedRobolectricTestRunner
import org.robolectric.annotation.GraphicsMode
import uk.co.siland.culvery.core.plugin.HeaderItem
import uk.co.siland.culvery.core.ui.Birthday
import uk.co.siland.culvery.core.ui.CherryBombOne
import uk.co.siland.culvery.core.ui.ChristmasNight
import uk.co.siland.culvery.core.ui.Creepster
import uk.co.siland.culvery.core.ui.CulveryTheme
import uk.co.siland.culvery.core.ui.DmSans
import uk.co.siland.culvery.core.ui.HalloweenNight
import uk.co.siland.culvery.core.ui.HhColors
import uk.co.siland.culvery.core.ui.MountainsOfChristmas
import uk.co.siland.culvery.core.ui.PersonPalette
import uk.co.siland.culvery.core.ui.SeasonalTheme
import uk.co.siland.culvery.core.ui.birthdayLook

/** Needs real glyph metrics and pixels: legacy Robolectric graphics fakes both. */
@RunWith(ParameterizedRobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class ThemedClockTest(private val look: Look) {
    class Look(
        val name: String,
        val seasonal: SeasonalTheme?,
        val birthday: Birthday?,
        val face: FontFamily,
        val night: HhColors,
    ) {
        override fun toString() = name
    }

    companion object {
        private val pink = PersonPalette.colors[2]

        @JvmStatic
        @ParameterizedRobolectricTestRunner.Parameters(name = "{0}")
        fun looks() = listOf(
            Look("halloween", SeasonalTheme.HALLOWEEN, null, Creepster, HalloweenNight),
            Look("birthday", null, Birthday(pink, listOf("Sam")), CherryBombOne, birthdayLook(pink)!!.night),
            Look("christmas", SeasonalTheme.CHRISTMAS, null, MountainsOfChristmas, ChristmasNight),
        ).map { arrayOf<Any>(it) }
    }

    @get:Rule val compose = createComposeRule()
    private var now by mutableStateOf(LocalDateTime.of(2026, 10, 29, 23, 58))
    private var lookOn by mutableStateOf(true)

    private fun show(items: List<HeaderItem> = emptyList()) = compose.setContent {
        CulveryTheme(
            dark = true,
            seasonal = look.seasonal.takeIf { lookOn },
            birthday = look.birthday.takeIf { lookOn },
        ) {
            Box(Modifier.background(Color.Black)) { HomeHeader({ now }, items, Modifier.fillMaxWidth().testTag("header")) }
        }
    }

    private fun at(hour: Int, minute: Int) {
        now = LocalDateTime.of(2026, 10, 29, hour, minute)
        compose.waitForIdle()
    }

    private fun bounds(tag: String) = compose.onNodeWithTag(tag).getUnclippedBoundsInRoot()

    @Test
    fun theClockIsInTheLooksFace() {
        show()
        val slots = compose.onAllNodesWithTag("clock_slot", useUnmergedTree = true)
        assertThat(slots[0].textLayout().layoutInput.style.fontFamily).isEqualTo(look.face)
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
    fun theClockKeepsItsHeightInEitherFace() {
        show()
        lookOn = false
        compose.waitForIdle()
        val normal = bounds("home_clock").height.value
        lookOn = true
        compose.waitForIdle()
        assertThat(bounds("home_clock").height.value).isEqualTo(normal)
    }

    @Test
    fun theClockDrawsNothingIntoTheDate() {
        show()
        listOf(23 to 58, 22 to 22).forEach { (h, m) ->
            at(h, m)
            val date = compose.onNodeWithTag("home_date").fetchSemanticsNode().boundsInRoot
            val clock = compose.onNodeWithTag("home_clock").fetchSemanticsNode().boundsInRoot
            val rows = Rect(clock.left, date.top, clock.right, date.bottom)
            assertThat(hasPixel(snapshot(), look.night.accent, rows)).isFalse()
        }
    }

    @Test
    fun theHeaderItemsDontMoveWhenTheFaceChanges() {
        val item = HeaderItem("sample", 10) { Box(Modifier.size(180.dp, 60.dp).background(Color.Red)) }
        show(listOf(item))
        lookOn = false
        compose.waitForIdle()
        val normal = compose.onNodeWithTag("home_header_items").fetchSemanticsNode().boundsInRoot
        lookOn = true
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
    fun theClockIsTheLooksAccent() {
        show()
        val clock = compose.onNodeWithTag("home_clock").fetchSemanticsNode().boundsInRoot
        val image = snapshot()
        assertThat(hasPixel(image, look.night.accent, clock)).isTrue()
        assertThat(hasPixel(image, look.night.ink, clock)).isFalse()
    }

    /** The composed window drawn to a bitmap; `captureToImage` waits on a redraw that Robolectric never sends. */
    private fun snapshot(): Bitmap {
        val view = (compose.onRoot().fetchSemanticsNode().root as ViewRootForTest).view
        return Bitmap.createBitmap(view.width, view.height, Bitmap.Config.ARGB_8888).also { view.draw(Canvas(it)) }
    }

    /** Whether any pixel in [area] is exactly [colour]. */
    private fun hasPixel(image: Bitmap, colour: Color, area: Rect): Boolean {
        val want = colour.toArgb()
        for (y in area.top.toInt() until minOf(area.bottom.toInt(), image.height)) {
            for (x in area.left.toInt() until minOf(area.right.toInt(), image.width)) {
                if (image.getPixel(x, y) == want) return true
            }
        }
        return false
    }
}
