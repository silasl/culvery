package uk.co.siland.culvery.core.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshots.Snapshot
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.AccessibilityAction
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.unit.Density
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
class HalloweenSkyTest {
    @get:Rule val compose = createComposeRule()

    private val band = Rect(100f, 0f, 1100f, 130f)
    private val density = Density(1f)
    private val span = 26f
    private val cycle = band.width + span

    /** The write reaches the composition at the next frame, not by itself while the clock is held. */
    private fun set(change: () -> Unit) = compose.runOnIdle {
        change()
        Snapshot.sendApplyNotifications()
    }

    private fun bats(at: Long) = batPositions(at, band, density)

    @Test
    fun batsDriftABandWidthEveryLap() {
        val start = bats(0)
        val half = bats(BAT_LAP_MS / 2)
        assertThat(start).hasSize(4)
        start.zip(half).forEach { (a, b) ->
            val moved = ((b.x - a.x) % cycle + cycle) % cycle
            assertThat(moved).isWithin(0.01f).of(cycle / 2)
            assertThat(b.y).isEqualTo(a.y)
        }
        assertThat(bats(BAT_LAP_MS)).isEqualTo(start)
    }

    /** The first bat reaches the band's right edge at 39 375 ms; there it comes back in at the left, a bat's width outside. */
    @Test
    fun aBatWrapsExactlyAtTheEdge() {
        assertThat(bats(0)[0].x).isWithin(0.01f).of(band.left - span + 0.125f * cycle)
        assertThat(bats(39_374)[0].x).isWithin(0.05f).of(band.right)
        assertThat(bats(39_375)[0].x).isWithin(0.01f).of(band.left - span)
        // One pixel of travel takes BAT_LAP_MS / cycle = 43.9 ms.
        assertThat(bats(39_375 + 44)[0].x).isWithin(0.1f).of(band.left - span + 1f)
    }

    @Test
    fun theBatsShareTwoLanesInsideTheBand() {
        assertThat(bats(0).map { it.y }).containsExactly(14f, 84f, 14f, 84f).inOrder()
    }

    @Test
    fun aShortBandKeepsTheBatsInside() {
        val short = Rect(0f, 0f, 500f, 60f)
        assertThat(batPositions(0, short, density).map { it.y + span / 2 }).isNotEmpty()
        batPositions(0, short, density).forEach { assertThat(it.y + span / 2).isAtMost(60f) }
    }

    @Test
    fun theSkyDriftsWhileAsked() {
        compose.mainClock.autoAdvance = false
        val elapsed = mutableLongStateOf(0L)
        compose.setContent { HalloweenSky(drifting = true, elapsed = elapsed) }
        compose.mainClock.advanceTimeBy(1_000)
        assertThat(elapsed.longValue).isAtLeast(900L)
        assertThat(elapsed.longValue).isAtMost(1_000L)
    }

    @Test
    fun aStepIsAboutAFrameAndAWait() {
        compose.mainClock.autoAdvance = false
        val elapsed = mutableLongStateOf(0L)
        compose.setContent { HalloweenSky(drifting = true, elapsed = elapsed) }
        // The first frame only starts the clock.
        compose.mainClock.advanceTimeByFrame()
        assertThat(elapsed.longValue).isEqualTo(0L)
        compose.mainClock.advanceTimeBy(2 * (SKY_FRAME_MS + FRAME_MS))
        assertThat(elapsed.longValue).isAtLeast(2 * SKY_FRAME_MS)
        assertThat(elapsed.longValue).isAtMost(2 * (SKY_FRAME_MS + FRAME_MS))
    }

    @Test
    fun stillMeansNoFrames() {
        compose.mainClock.autoAdvance = false
        val elapsed = mutableLongStateOf(5_000L)
        var drifting by mutableStateOf(false)
        compose.setContent { HalloweenSky(drifting, elapsed = elapsed) }
        compose.mainClock.advanceTimeBy(10_000)
        assertThat(elapsed.longValue).isEqualTo(5_000L)
        set { drifting = true }
        compose.mainClock.advanceTimeBy(500)
        assertThat(elapsed.longValue).isGreaterThan(5_300L)
        assertThat(elapsed.longValue).isAtMost(5_500L)
    }

    @Test
    fun turningStillStopsTheFrames() {
        compose.mainClock.autoAdvance = false
        val elapsed = mutableLongStateOf(0L)
        var drifting by mutableStateOf(true)
        compose.setContent { HalloweenSky(drifting, elapsed = elapsed) }
        compose.mainClock.advanceTimeBy(500)
        set { drifting = false }
        compose.mainClock.advanceTimeByFrame()
        val stood = elapsed.longValue
        compose.mainClock.advanceTimeBy(10_000)
        assertThat(elapsed.longValue).isEqualTo(stood)
    }

    private fun showStill(measured: HomeSkyBand?) = compose.setContent {
        CompositionLocalProvider(LocalHomeSkyBand provides measured) {
            CulveryTheme(dark = true, seasonal = SeasonalTheme.HALLOWEEN) {
                Box(Modifier.size(300.dp, 300.dp).testTag("still_frame").background(HalloweenNight.bg)) {
                    HalloweenSky(drifting = false, modifier = Modifier.fillMaxSize())
                }
            }
        }
    }

    @Test
    fun aStillSkyStillDrawsTheBatsAndTheMoon() {
        showStill(null)
        val node = compose.onNodeWithTag("halloween_sky")
        val bounds = node.fetchSemanticsNode().boundsInWindow
        val image = windowBitmap(node)
        val density = Density(compose.density.density)
        val all = Rect(Offset.Zero, Size(bounds.width, bounds.height))
        val bat = batPositions(0, all, density).first()
        val half = HalloweenDimens.batSpan.value * density.density / 2
        val body = image.getPixel((bounds.left + bat.x + half).toInt(), (bounds.top + bat.y + half / 2).toInt())
        assertThat(body).isEqualTo(HalloweenInks.bat(HalloweenNight).toArgb())
        val moon = moonIn(all, density).centre
        assertThat(image.getPixel((bounds.left + moon.x).toInt(), (bounds.top + moon.y).toInt()))
            .isEqualTo(HalloweenInks.moon(HalloweenNight).toArgb())
    }

    /** With a measured header the moon is drawn, and only inside the open band between the clock and the header items. */
    @Test
    fun theMoonIsDrawnWhollyInsideTheBand() {
        val d = compose.density.density
        val measured = HomeSkyBand().apply {
            headerWidth = (300 * d).toInt()
            headerHeight = (130 * d).toInt()
            clockWidth = (40 * d).toInt()
            dateWidth = (60 * d).toInt()
            itemsWidth = (80 * d).toInt()
        }
        showStill(measured)
        val node = compose.onNodeWithTag("halloween_sky")
        val origin = node.fetchSemanticsNode().boundsInWindow
        val image = windowBitmap(node)
        val frame = compose.onNodeWithTag("still_frame").fetchSemanticsNode().boundsInWindow
        val drawn = skyBand(measured, Size(300 * d, 300 * d), Density(d)).translate(frame.topLeft)
        // The sky is only as big as the band.
        assertThat(origin.left).isWithin(1f).of(drawn.left)
        assertThat(origin.top).isWithin(1f).of(drawn.top)
        assertThat(origin.width).isWithin(1f).of(drawn.width)
        assertThat(origin.height).isWithin(1f).of(drawn.height)
        val ink = HalloweenInks.moon(HalloweenNight).toArgb()
        val moon = (0 until image.height).flatMap { y -> (0 until image.width).filter { x -> image.getPixel(x, y) == ink }.map { x -> x to y } }
        assertThat(moon).isNotEmpty()
        assertThat(moon.minOf { it.first }.toFloat()).isAtLeast(drawn.left - 1f)
        assertThat(moon.maxOf { it.first }.toFloat()).isAtMost(drawn.right + 1f)
        assertThat(moon.minOf { it.second }.toFloat()).isAtLeast(drawn.top - 1f)
        assertThat(moon.maxOf { it.second }.toFloat()).isAtMost(drawn.bottom + 1f)
        // Left edge: the date's 60 plus the 24 gap; right: 300 - 80 - 24.
        assertThat(drawn.left - frame.left).isEqualTo((60 + 24) * d)
        assertThat(drawn.right - frame.left).isEqualTo((300 - 80 - 24) * d)
        val bandLocal = Rect(Offset.Zero, Size(drawn.width, drawn.height))
        val radius = moonIn(bandLocal, Density(d)).radius
        assertThat(radius).isEqualTo(30 * d)
    }

    @Test
    fun theSkyIsSilent() {
        compose.setContent {
            CulveryTheme(dark = true, seasonal = SeasonalTheme.HALLOWEEN) { HalloweenSky(drifting = false, modifier = Modifier.fillMaxSize()) }
        }
        val nodes = listOf(compose.onNodeWithTag("halloween_sky", useUnmergedTree = true).fetchSemanticsNode()) +
            compose.onAllNodes(hasAnyAncestor(hasTestTag("halloween_sky")), useUnmergedTree = true).fetchSemanticsNodes()
        nodes.forEach { node ->
            assertThat(node.config.contains(SemanticsProperties.Text)).isFalse()
            assertThat(node.config.contains(SemanticsProperties.ContentDescription)).isFalse()
            assertThat(node.config.filter { it.value is AccessibilityAction<*> }).isEmpty()
        }
    }

    private companion object {
        const val FRAME_MS = 16L
    }
}
