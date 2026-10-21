package uk.co.siland.culvery.capability.calendar.ui

import android.graphics.Bitmap
import android.graphics.Canvas
import android.view.View
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.height
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.google.common.truth.Truth.assertThat
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.GraphicsMode
import uk.co.siland.culvery.capability.calendar.SyncStatusUi
import uk.co.siland.culvery.core.ui.Culvery
import uk.co.siland.culvery.core.ui.DarkColors
import uk.co.siland.culvery.core.ui.CulveryTheme
import uk.co.siland.culvery.core.ui.test.pixelAt
import uk.co.siland.culvery.capability.calendar.WeekUi

/** Real graphics: the week's tinted chips and today's ring are read from drawn pixels. */
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class WeekPixelsTest {
    @get:Rule val compose = createComposeRule()

    private var accent = 0

    private fun drawn(dark: Boolean, week: WeekUi = SampleUi.week): Bitmap {
        lateinit var window: View
        compose.setContent {
            window = LocalView.current.rootView
            CulveryTheme(dark = dark) {
                accent = Culvery.colors.accent.toArgb()
                Box(Modifier.fillMaxSize().background(Culvery.colors.bg)) {
                    WeekView(WeekViewState(week, SampleUi.TODAY, SyncStatusUi(SampleUi.NOW - 120_000, emptyList(), listOf("Google Calendar"), failingBeforeFirstSync = false), SampleUi.NOW))
                }
            }
        }
        compose.waitForIdle()
        // captureToImage waits on a frame callback that Robolectric's paused looper never delivers, so draw the window.
        return Bitmap.createBitmap(window.width, window.height, Bitmap.Config.ARGB_8888).also { window.draw(Canvas(it)) }
    }

    private fun assertNear(actual: Int, expected: Long) {
        val want = expected.toInt()
        listOf(16, 8, 0).forEach { shift ->
            assertThat(((actual shr shift) and 0xFF).toDouble()).isWithin(1.0).of(((want shr shift) and 0xFF).toDouble())
        }
    }

    /** A point 4 dp in from the chip's left edge, level with its middle: inside the padding, clear of the text. */
    private fun chipPixel(dark: Boolean): Int {
        val bitmap = drawn(dark)
        val bounds = compose.onNodeWithContentDescription("Dinner with Jo & Priya, Alex, Wednesday 19:30").fetchSemanticsNode().boundsInWindow
        val inside = with(compose.density) { 4.dp.toPx() }
        return bitmap.getPixel((bounds.left + inside).toInt(), bounds.center.y.toInt())
    }

    @Test
    fun aChipIsTintedWithItsPersonsColourInDark() = assertNear(chipPixel(dark = true), 0xFF233831)

    @Test
    fun aChipIsTintedWithItsPersonsColourInLight() = assertNear(chipPixel(dark = false), 0xFFE4F4ED)

    /** Cinema's chip, on the Friday of [SampleUi.weekWithShared]. */
    private fun cinemaChip() = compose.onNodeWithContentDescription("Cinema, Sam and Mia, Friday 17:00")

    @Test
    fun aSharedChipIsNeutralWithAStripedEdge() {
        drawn(dark = true, SampleUi.weekWithShared)
        val chip = cinemaChip()
        val height = chip.getUnclippedBoundsInRoot().height
        assertThat(pixelAt(chip, 30.dp, 4.dp)).isEqualTo(DarkColors.surf2.toArgb())
        // 10 dp in: the chip's 12 dp corner arc clips the edge away above 7 dp.
        assertThat(pixelAt(chip, 1.dp, 10.dp)).isEqualTo(SampleUi.sam.color.toInt())
        assertThat(pixelAt(chip, 1.dp, height - 10.dp)).isEqualTo(SampleUi.mia.color.toInt())
    }

    /** Pin: one person's chip is still their tint, with nothing along its edge. */
    @Test
    fun aSinglePersonChipKeepsItsTintAndNoEdge() {
        drawn(dark = true)
        val chip = compose.onNodeWithContentDescription("Dinner with Jo & Priya, Alex, Wednesday 19:30")
        assertNear(pixelAt(chip, 1.dp, 20.dp), 0xFF233831)
        assertNear(pixelAt(chip, 30.dp, 4.dp), 0xFF233831)
    }

    @Test
    fun todaysColumnHasAnInsetRing() {
        val bitmap = drawn(dark = true)
        val bounds = compose.onNodeWithTag("week_day_${SampleUi.TODAY}").fetchSemanticsNode().boundsInWindow
        val px = with(compose.density) { 1.dp.toPx() }
        val y = bounds.center.y.toInt()
        // 3 dp in is the middle of a 2 dp ring that starts 2 dp inside the column; the column's own edge is not ring.
        assertThat(bitmap.getPixel((bounds.left + 3 * px).toInt(), y)).isEqualTo(accent)
        assertThat(bitmap.getPixel((bounds.left + 0.5f * px).toInt(), y)).isNotEqualTo(accent)
        assertThat(bitmap.getPixel((bounds.left + 6 * px).toInt(), y)).isNotEqualTo(accent)
    }
}
