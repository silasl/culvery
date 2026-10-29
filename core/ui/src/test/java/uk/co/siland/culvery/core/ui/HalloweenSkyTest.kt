package uk.co.siland.culvery.core.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshots.Snapshot
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.toArgb
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

    private val size = Size(1000f, 600f)
    private val density = Density(1f)
    private val span = 26f
    private val cycle = size.width + span

    /** The write reaches the composition at the next frame, not by itself while the clock is held. */
    private fun set(change: () -> Unit) = compose.runOnIdle {
        change()
        Snapshot.sendApplyNotifications()
    }

    private fun bats(at: Long) = batPositions(at, size, headerBottomPx = 100f, density = density)

    @Test
    fun batsDriftAScreenWidthEveryLap() {
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

    /** The first bat reaches the right edge at 78 750 ms; there it comes back in at the left, a bat's width off screen. */
    @Test
    fun aBatWrapsExactlyAtTheEdge() {
        assertThat(bats(0)[0].x).isWithin(0.01f).of(0.125f * cycle - span)
        assertThat(bats(78_749)[0].x).isWithin(0.05f).of(size.width)
        assertThat(bats(78_750)[0].x).isWithin(0.01f).of(-span)
        // One pixel of travel takes BAT_LAP_MS / cycle = 87.7 ms.
        assertThat(bats(78_750 + 88)[0].x).isWithin(0.1f).of(-span + 1f)
    }

    @Test
    fun theLanesAreTheTopOfHomeAndBelowTheHeader() {
        assertThat(bats(0).map { it.y }).containsExactly(20f, 140f, 260f, 380f).inOrder()
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

    @Test
    fun aStillSkyStillDrawsTheBats() {
        compose.setContent {
            CulveryTheme(dark = true, seasonal = SeasonalTheme.HALLOWEEN) {
                Box(Modifier.size(300.dp, 300.dp).background(HalloweenNight.bg)) {
                    HalloweenSky(drifting = false, modifier = Modifier.fillMaxSize())
                }
            }
        }
        val node = compose.onNodeWithTag("halloween_sky")
        val bounds = node.fetchSemanticsNode().boundsInWindow
        val image = windowBitmap(node)
        val density = Density(compose.density.density)
        val bat = batPositions(0, Size(bounds.width, bounds.height), 0f, density).first()
        val half = SeasonalDimens.batSpan.value * density.density / 2
        val body = image.getPixel((bounds.left + bat.x + half).toInt(), (bounds.top + bat.y + half / 2).toInt())
        assertThat(body).isEqualTo(HalloweenInks.bat(HalloweenNight).toArgb())
    }

    /** The weather's top edge is 52 dp down, so a 60 dp moon 4 dp from the top would touch it; nothing is drawn there. */
    @Test
    fun theMoonStaysOutOfTheWeatherBand() {
        compose.setContent {
            CulveryTheme(dark = true, seasonal = SeasonalTheme.HALLOWEEN) {
                Box(Modifier.size(300.dp, 300.dp).background(HalloweenNight.bg)) {
                    HalloweenSky(drifting = false, modifier = Modifier.fillMaxSize())
                }
            }
        }
        val node = compose.onNodeWithTag("halloween_sky")
        val bounds = node.fetchSemanticsNode().boundsInWindow
        val image = windowBitmap(node)
        val d = compose.density.density
        val band = (bounds.top + 4 * d).toInt() until (bounds.top + 64 * d).toInt()
        val corner = (bounds.right - 114 * d).toInt() until (bounds.right - 54 * d).toInt()
        val painted = band.filter { y -> corner.any { x -> image.getPixel(x, y) != HalloweenNight.bg.toArgb() } }
        assertThat(painted).isEmpty()
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
