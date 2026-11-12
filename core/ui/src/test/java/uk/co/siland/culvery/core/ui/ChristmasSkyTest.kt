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
import com.google.common.truth.Truth.assertWithMessage
import kotlin.math.abs
import kotlin.math.sqrt
import kotlin.time.Duration.Companion.days
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.GraphicsMode
import uk.co.siland.culvery.core.ui.test.windowBitmap

@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class ChristmasSkyTest {
    @get:Rule val compose = createComposeRule()

    private val area = Rect(0f, 0f, 1000f, 300f)
    private val one = Density(1f)
    private val pitch = ChristmasDimens.bulbPitch.value

    private fun snow(at: Long, within: Rect = area) = snowFlakes(at, within, one)

    private fun mod(v: Float, m: Float) = ((v % m) + m) % m

    @Test
    fun snowFallsTheHeaderEveryLap() {
        val start = snow(0)
        assertThat(start).hasSize(SNOW_FLAKES)
        snow(SNOW_FALL_MS).zip(start).forEach { (a, b) ->
            assertThat(a.centre.x).isWithin(0.01f).of(b.centre.x)
            assertThat(a.centre.y).isWithin(0.01f).of(b.centre.y)
        }
        snow(SNOW_FALL_MS / 4).zip(start).forEach { (a, b) ->
            val fell = mod(a.centre.y - b.centre.y, area.height)
            assertThat(minOf(abs(fell - area.height / 4), abs(fell - area.height / 4 - area.height))).isAtMost(0.5f)
        }
    }

    @Test
    fun aFlakeWrapsFromTheBottomToTheTop() {
        val y0 = snow(0)[3].centre.y
        val wrap = ((area.bottom - y0) / area.height * SNOW_FALL_MS).toLong()
        assertThat(wrap).isGreaterThan(10L)
        assertThat(snow(wrap - 10)[3].centre.y).isWithin(1.5f).of(area.bottom)
        assertThat(snow(wrap + 10)[3].centre.y).isWithin(1.5f).of(area.top)
    }

    /** **pin**: the stub's fixed list was inside the area too. */
    @Test
    fun theSnowStaysInTheHeader() {
        val narrow = Rect(40f, 20f, 160f, 90f)
        for (t in 0L..SNOW_FALL_MS step 500L) {
            snow(t).forEach { assertThat(area.contains(it.centre)).isTrue() }
            snow(t, narrow).forEach { assertThat(narrow.contains(it.centre)).isTrue() }
        }
    }

    @Test
    fun theFlakesAreSmallAndSpreadAcrossTheWidth() {
        val flakes = snow(0)
        flakes.forEach { assertThat(it.radius).isAtLeast(ChristmasDimens.flakeRadiusMin.value) }
        flakes.forEach { assertThat(it.radius).isAtMost(ChristmasDimens.flakeRadiusMax.value) }
        assertThat(flakes.map { it.radius }.toSet().size).isGreaterThan(1)
        assertThat(flakes.map { it.centre.x }.toSet().size).isEqualTo(SNOW_FLAKES)
    }

    /** The flakes don't line up: across the width, how far down each is doesn't follow where it is (the first scatter drew a diagonal). */
    @Test
    fun theSnowIsScatteredNotInALine() {
        for (t in listOf(0L, SNOW_FALL_MS / 3, SNOW_FALL_MS * 2 / 3)) {
            val flakes = snow(t)
            val xs = flakes.map { it.centre.x.toDouble() }
            val ys = flakes.map { it.centre.y.toDouble() }
            val mx = xs.average()
            val my = ys.average()
            val cov = xs.zip(ys).sumOf { (x, y) -> (x - mx) * (y - my) }
            val r = cov / sqrt(xs.sumOf { (it - mx) * (it - mx) } * ys.sumOf { (it - my) * (it - my) })
            assertWithMessage("correlation of height with column at $t ms").that(abs(r)).isLessThan(0.5)
            val turns = flakes.sortedBy { it.centre.x }.map { it.centre.y }.zipWithNext { a, b -> b > a }.zipWithNext { a, b -> a != b }.count { it }
            assertWithMessage("up/down turns along the width at $t ms").that(turns).isAtLeast(6)
        }
    }

    /** **pin** against the stub's fixed list; a time fed into a phase is reduced on the Long first, so a long run draws as the first minutes did (21 §4.4). */
    @Test
    fun theSnowRepeatsOverLongRuns() {
        val forty = 40.days.inWholeMilliseconds
        for (t in listOf(0L, 7_000L, 23_999L)) {
            snow(forty + t).zip(snow(t)).forEach { (a, b) ->
                assertThat(a.centre).isEqualTo(b.centre)
                assertThat(a.radius).isEqualTo(b.radius)
            }
            for (i in 0..3) assertThat(bulbAlpha(forty + t, i)).isEqualTo(bulbAlpha(t, i))
        }
    }

    private val band = Rect(100f, 0f, 1100f, 130f)

    @Test
    fun theLightsHangInTheBand() {
        val bulbs = bulbCentres(band, one)
        assertThat(bulbs).isNotEmpty()
        bulbs.forEach { assertThat(band.contains(it)).isTrue() }
        assertThat(bulbs.first().x - band.left).isAtLeast(pitch / 2)
        assertThat(band.right - bulbs.last().x).isAtLeast(pitch / 2)
        bulbs.zipWithNext().forEach { (a, b) -> assertThat(b.x - a.x).isWithin(0.01f).of(pitch) }
        listOf(band.left, band.right).forEach { assertThat(hangingY(band, it, one)).isEqualTo(band.top + SkyString.top.value) }
        bulbs.forEach { assertThat(it.y).isWithin(0.01f).of(hangingY(band, it.x, one) + ChristmasDimens.bulbRadius.value) }
    }

    @Test
    fun aNarrowBandHasNoLights() {
        assertThat(bulbCentres(Rect(0f, 0f, 2.9f * pitch, 100f), one)).isEmpty()
        assertThat(bulbCentres(Rect(0f, 0f, 3f * pitch, 100f), one)).hasSize(3)
    }

    @Test
    fun theBulbsTwinkleOutOfStep() {
        for (i in 0..3) {
            val seen = (0L until TWINKLE_MS step 50L).map { bulbAlpha(it, i) }
            seen.forEach { assertThat(it).isAtLeast(ChristmasInks.BULB_DIM) }
            seen.forEach { assertThat(it).isAtMost(1f) }
            assertThat(seen.max()).isAtLeast(0.95f)
            assertThat(seen.min()).isAtMost(ChristmasInks.BULB_DIM + 0.05f)
            assertThat(bulbAlpha(TWINKLE_MS, i)).isEqualTo(bulbAlpha(0, i))
        }
        assertThat(bulbAlpha(0, 0)).isEqualTo(1f)
        val quarter = TWINKLE_MS / 4
        assertThat((0..3).map { bulbAlpha(quarter, it) }.toSet().size).isGreaterThan(1)
        assertThat(bulbAlpha(quarter, 1)).isWithin(0.0001f).of(bulbAlpha(0, 0))
        assertThat(bulbAlpha(0, 4)).isEqualTo(bulbAlpha(0, 0))
    }

    /** **pin**: the frames come from `SkyFrames`, which the stubs already ran. */
    @Test
    fun theSkyDriftsWhileAsked() {
        compose.mainClock.autoAdvance = false
        val elapsed = mutableLongStateOf(0L)
        compose.setContent { ChristmasSky(drifting = true, elapsed = elapsed) }
        compose.mainClock.advanceTimeBy(1_000)
        assertThat(elapsed.longValue).isAtLeast(900L)
        assertThat(elapsed.longValue).isAtMost(1_000L)
    }

    /** **pin**: as above. */
    @Test
    fun stillMeansNoFrames() {
        compose.mainClock.autoAdvance = false
        val elapsed = mutableLongStateOf(5_000L)
        var drifting by mutableStateOf(false)
        compose.setContent { ChristmasSky(drifting, elapsed = elapsed) }
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

    /** Still, the snow is where `elapsed` puts it and every bulb is at full, not dimmed. */
    @Test
    fun aStillSkyDrawsTheSnowAndTheLightsAtFull() {
        val elapsed = mutableLongStateOf(TWINKLE_MS / 2)
        compose.setContent {
            CulveryTheme(dark = true, seasonal = SeasonalTheme.CHRISTMAS) {
                Box(Modifier.size(300.dp, 300.dp).testTag("still_frame").background(ChristmasNight.bg)) {
                    ChristmasSky(drifting = false, modifier = Modifier.fillMaxSize(), elapsed = elapsed)
                }
            }
        }
        val node = compose.onNodeWithTag("christmas_sky")
        val bounds = node.fetchSemanticsNode().boundsInWindow
        val image = windowBitmap(node)
        val d = Density(compose.density.density)
        val local = Rect(Offset.Zero, Size(bounds.width, bounds.height))
        val bulbs = bulbCentres(local, d)
        assertThat(bulbs).isNotEmpty()
        val inks = ChristmasInks.bulbs(ChristmasNight)
        bulbs.forEachIndexed { i, at ->
            assertThat(image.getPixel((bounds.left + at.x).toInt(), (bounds.top + at.y).toInt())).isEqualTo(inks[i % inks.size].toArgb())
        }
        val stringBottom = hangingY(local, local.center.x, d) + (2 * ChristmasDimens.bulbRadius.value + ChristmasDimens.wireStroke.value) * d.density
        val flake = snowFlakes(TWINKLE_MS / 2, local, d).filter { it.centre.y - it.radius > stringBottom }.maxByOrNull { it.radius }!!
        val r = flake.radius.toInt()
        val snow = ChristmasInks.snow(ChristmasNight).toArgb()
        val found = (-r..r).any { dy ->
            (-r..r).any { dx ->
                image.getPixel((bounds.left + flake.centre.x).toInt() + dx, (bounds.top + flake.centre.y).toInt() + dy) == snow
            }
        }
        assertThat(found).isTrue()
    }

    /** Fails if a sky that stops mid-twinkle leaves a bulb dimmed. */
    @Test
    fun aSkyThatStopsLightsItsBulbs() {
        compose.mainClock.autoAdvance = false
        val elapsed = mutableLongStateOf(TWINKLE_MS * 3 / 4)
        var drifting by mutableStateOf(true)
        compose.setContent {
            CulveryTheme(dark = true, seasonal = SeasonalTheme.CHRISTMAS) {
                Box(Modifier.size(300.dp, 300.dp).background(ChristmasNight.bg)) {
                    ChristmasSky(drifting, modifier = Modifier.fillMaxSize(), elapsed = elapsed)
                }
            }
        }
        compose.mainClock.advanceTimeByFrame()
        val node = compose.onNodeWithTag("christmas_sky")
        val bounds = node.fetchSemanticsNode().boundsInWindow
        val d = Density(compose.density.density)
        val bulbs = bulbCentres(Rect(Offset.Zero, Size(bounds.width, bounds.height)), d)
        val inks = ChristmasInks.bulbs(ChristmasNight).map { it.toArgb() }
        fun pixels() = windowBitmap(node).let { image -> bulbs.map { image.getPixel((bounds.left + it.x).toInt(), (bounds.top + it.y).toInt()) } }
        assertWithMessage("group 1 is dimmed while twinkling").that(pixels()[1]).isNotEqualTo(inks[1])
        compose.runOnIdle {
            drifting = false
            Snapshot.sendApplyNotifications()
        }
        compose.mainClock.advanceTimeByFrame()
        pixels().forEachIndexed { i, pixel -> assertWithMessage("bulb $i").that(pixel).isEqualTo(inks[i % inks.size]) }
    }

    /** **pin**: the sky is a picture; the stubs were silent too. */
    @Test
    fun theSkyIsSilent() {
        compose.setContent {
            CulveryTheme(dark = true, seasonal = SeasonalTheme.CHRISTMAS) { ChristmasSky(drifting = false, modifier = Modifier.fillMaxSize()) }
        }
        val nodes = listOf(compose.onNodeWithTag("christmas_sky", useUnmergedTree = true).fetchSemanticsNode()) +
            compose.onAllNodes(hasAnyAncestor(hasTestTag("christmas_sky")), useUnmergedTree = true).fetchSemanticsNodes()
        nodes.forEach { node ->
            assertThat(node.config.contains(SemanticsProperties.Text)).isFalse()
            assertThat(node.config.contains(SemanticsProperties.ContentDescription)).isFalse()
            assertThat(node.config.filter { it.value is AccessibilityAction<*> }).isEmpty()
        }
    }
}
