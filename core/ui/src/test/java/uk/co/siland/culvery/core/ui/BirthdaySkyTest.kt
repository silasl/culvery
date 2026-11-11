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
class BirthdaySkyTest {
    @get:Rule val compose = createComposeRule()

    private fun show(measured: HomeSkyBand?, width: Int = 400, height: Int = 100) = compose.setContent {
        CompositionLocalProvider(LocalHomeSkyBand provides measured) {
            CulveryTheme(dark = true) {
                Box(Modifier.size(width.dp, height.dp).testTag("still_frame").background(DarkColors.bg)) {
                    BirthdaySky(drifting = false, modifier = Modifier.size(width.dp, height.dp))
                }
            }
        }
    }

    /** The header's clock, greeting and items leave a band; the sky is that band and the bunting hangs wholly inside it. */
    @Test
    fun theBuntingStaysInTheBand() {
        val measured = HomeSkyBand().apply {
            headerWidth = 1100
            headerHeight = 220
            clockWidth = 400
            greetingWidth = 420
            dateWidth = 100
            itemsWidth = 300
        }
        val d = compose.density.density
        compose.setContent {
            CompositionLocalProvider(LocalHomeSkyBand provides measured) {
                CulveryTheme(dark = true) { Box(Modifier.size((1100 / d).dp, (220 / d).dp).testTag("still_frame")) { BirthdaySky(drifting = false, modifier = Modifier.size((1100 / d).dp, (220 / d).dp)) } }
            }
        }
        val band = skyBand(measured, Size(1100f, 220f), Density(d))
        val frame = compose.onNodeWithTag("still_frame").fetchSemanticsNode().boundsInRoot
        val sky = compose.onNodeWithTag("birthday_sky").fetchSemanticsNode().boundsInRoot
        val want = band.translate(frame.topLeft)
        assertThat(sky.left).isWithin(1f).of(want.left)
        assertThat(sky.right).isWithin(1f).of(want.right)
        assertThat(sky.top).isWithin(1f).of(want.top)
        assertThat(sky.bottom).isWithin(1f).of(want.bottom)
        // Left edge: the greeting is the widest of the three, plus the gap.
        assertThat(band.left).isWithin(0.01f).of(420f + HalloweenDimens.skyGap.value * d)
        assertThat(lowestFlagTip(Rect(Offset.Zero, Size(sky.width, sky.height)), Density(d))).isAtMost(sky.height)
    }

    @Test
    fun flagsFillTheBand() {
        val pitch = BirthdayDimens.flagPitch.value
        assertThat(fitCount(280f, pitch, 3)).isEqualTo(10)
        assertThat(fitCount(83f, pitch, 3)).isEqualTo(0)
        assertThat(fitCount(84f, pitch, 3)).isEqualTo(3)
        assertThat(fitCount(560f, pitch * 2, 3)).isEqualTo(10)
    }

    @Test
    fun theFlagsAreThePersonColours() {
        show(null)
        val node = compose.onNodeWithTag("birthday_sky")
        val bounds = node.fetchSemanticsNode().boundsInWindow
        val image = windowBitmap(node)
        val d = compose.density
        val anchors = flagAnchors(Rect(Offset.Zero, Size(bounds.width, bounds.height)), d)
        assertThat(anchors.size).isAtLeast(PersonPalette.colors.size + 1)
        val centre = BirthdayDimens.flagHeight.value * d.density / 3
        PersonPalette.colors.forEachIndexed { i, colour ->
            val at = anchors[i]
            assertThat(image.getPixel((bounds.left + at.x).toInt(), (bounds.top + at.y + centre).toInt())).isEqualTo(BirthdayInks.flag(colour).toArgb())
        }
        // The seventh flag starts the colours again.
        val seventh = anchors[6]
        assertThat(image.getPixel((bounds.left + seventh.x).toInt(), (bounds.top + seventh.y + centre).toInt())).isEqualTo(BirthdayInks.flag(PersonPalette.colors[0]).toArgb())
    }

    private val band = Rect(0f, 0f, 400f, 200f)
    private val one = Density(1f)
    private val cycle = band.height + 38f + 26f
    private fun balloons(at: Long) = balloonPositions(at, band, one)

    @Test
    fun balloonsRiseTheBandEveryLap() {
        val start = balloons(0)
        val half = balloons(BALLOON_LAP_MS / 2)
        assertThat(start).hasSize(4)
        start.zip(half).forEach { (a, b) ->
            val moved = (((a.y - b.y) % cycle) + cycle) % cycle
            assertThat(moved).isWithin(0.01f).of(cycle / 2)
        }
        assertThat(balloons(BALLOON_LAP_MS)).isEqualTo(start)
        // A quarter lap apart, each in its own lane, swaying no more than the sway.
        for (at in listOf(0L, 7_000L, 13_000L, 29_000L)) {
            balloons(at).forEachIndexed { i, p ->
                assertThat(p.x).isWithin(6f + 0.01f).of(band.left + band.width * (i + 0.5f) / 4)
            }
        }
        val ys = start.map { it.y }
        for (i in 0 until 4) assertThat((((ys[i] - ys[(i + 1) % 4]) % cycle) + cycle) % cycle).isWithin(0.01f).of(cycle / 4)
    }

    @Test
    fun aBalloonRisesFromBelowTheBandToAboveIt() {
        // The body's top starts at the band's bottom and the string clears the band's top at the end of a lap.
        val tops = (0L until BALLOON_LAP_MS step 250L).map { balloons(it)[0].y }
        assertThat(tops.max()).isAtMost(band.bottom + 0.01f)
        assertThat(tops.max()).isAtLeast(band.bottom - 10f)
        assertThat(tops.min()).isAtMost(band.top - 38f - 26f + 10f)
        assertThat(tops.min()).isAtLeast(band.top - 38f - 26f - 0.01f)
    }

    @Test
    fun theSkyDriftsWhileAsked() {
        compose.mainClock.autoAdvance = false
        val elapsed = mutableLongStateOf(0L)
        compose.setContent { BirthdaySky(drifting = true, elapsed = elapsed) }
        compose.mainClock.advanceTimeBy(1_000)
        assertThat(elapsed.longValue).isAtLeast(900L)
        assertThat(elapsed.longValue).isAtMost(1_000L)
    }

    @Test
    fun aStepIsAboutAFrameAndAWait() {
        compose.mainClock.autoAdvance = false
        val elapsed = mutableLongStateOf(0L)
        compose.setContent { BirthdaySky(drifting = true, elapsed = elapsed) }
        compose.mainClock.advanceTimeByFrame()
        assertThat(elapsed.longValue).isEqualTo(0L)
        compose.mainClock.advanceTimeBy(2 * (SKY_FRAME_MS + 16L))
        assertThat(elapsed.longValue).isAtLeast(2 * SKY_FRAME_MS)
        assertThat(elapsed.longValue).isAtMost(2 * (SKY_FRAME_MS + 16L))
    }

    @Test
    fun stillMeansNoFrames() {
        compose.mainClock.autoAdvance = false
        val elapsed = mutableLongStateOf(5_000L)
        var drifting by mutableStateOf(false)
        compose.setContent { BirthdaySky(drifting, elapsed = elapsed) }
        compose.mainClock.advanceTimeBy(10_000)
        assertThat(elapsed.longValue).isEqualTo(5_000L)
        compose.runOnIdle {
            drifting = true
            Snapshot.sendApplyNotifications()
        }
        compose.mainClock.advanceTimeBy(500)
        assertThat(elapsed.longValue).isGreaterThan(5_300L)
        assertThat(elapsed.longValue).isAtMost(5_500L)
    }

    /** Lane 2 is half a lap up at time zero, clear of the bunting, and drawn in its person colour, faint, over the page. */
    @Test
    fun aStillSkyStillDrawsTheBalloons() {
        compose.setContent {
            CulveryTheme(dark = true) {
                Box(Modifier.size(300.dp, 300.dp).testTag("still_frame").background(DarkColors.bg)) {
                    BirthdaySky(drifting = false, modifier = Modifier.fillMaxSize())
                }
            }
        }
        val node = compose.onNodeWithTag("birthday_sky")
        val bounds = node.fetchSemanticsNode().boundsInWindow
        val image = windowBitmap(node)
        val d = compose.density
        val at = balloonPositions(0, Rect(Offset.Zero, Size(bounds.width, bounds.height)), d)[2]
        val x = (bounds.left + at.x).toInt()
        val y = (bounds.top + at.y + BirthdayDimens.balloonHeight.value * d.density / 2).toInt()
        assertThat(image.getPixel(x, y)).isEqualTo(BirthdayInks.balloon(PersonPalette.colors[2], DarkColors).toArgb())
    }

    @Test
    fun theSkyIsSilent() {
        show(null)
        val nodes = listOf(compose.onNodeWithTag("birthday_sky", useUnmergedTree = true).fetchSemanticsNode()) +
            compose.onAllNodes(hasAnyAncestor(hasTestTag("birthday_sky")), useUnmergedTree = true).fetchSemanticsNodes()
        nodes.forEach { node ->
            assertThat(node.config.contains(SemanticsProperties.Text)).isFalse()
            assertThat(node.config.contains(SemanticsProperties.ContentDescription)).isFalse()
            assertThat(node.config.filter { it.value is AccessibilityAction<*> }).isEmpty()
        }
        assertThat(nodes.first().config.isClearingSemantics).isTrue()
    }
}
