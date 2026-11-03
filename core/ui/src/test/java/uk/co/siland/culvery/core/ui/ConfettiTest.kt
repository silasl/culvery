package uk.co.siland.culvery.core.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.unit.Density
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.google.common.truth.Truth.assertThat
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class ConfettiTest {
    @get:Rule val compose = createComposeRule()

    private val size = Size(1000f, 600f)
    private val density = Density(2f)
    private val accent = Color(0xFFB42A68)
    private val colours = PersonPalette.colors.map(::Color) + accent

    private fun pieces(at: Long, seed: Long = 7L) = confettiPieces(seed, at, size, colours, density)

    /** Pin: the stub already returns sixty. */
    @Test
    fun sixtyPieces() {
        listOf(0L, 1_000L, CONFETTI_MS).forEach { assertThat(pieces(it)).hasSize(60) }
    }

    /** Every piece starts between 0.3 of a height above the top and the top, spread across the width. */
    @Test
    fun theyStartAboveTheTop() {
        val start = pieces(0)
        assertThat(start.all { it.centre.y <= 0f && it.centre.y >= -0.3f * size.height }).isTrue()
        assertThat(start.minOf { it.centre.y }).isLessThan(-0.15f * size.height)
        assertThat(start.minOf { it.centre.x }).isLessThan(0.25f * size.width)
        assertThat(start.maxOf { it.centre.x }).isGreaterThan(0.75f * size.width)
    }

    /** A piece's own half-diagonal is under 8 dp, so 10 dp clear of the bottom puts every piece wholly out of sight. */
    @Test
    fun theyAreBelowTheBottomBy2500() {
        pieces(CONFETTI_MS).forEach { assertThat(it.centre.y).isAtLeast(size.height + 10f * density.density) }
        assertThat(pieces(1_000).any { it.centre.y in 0f..size.height }).isTrue()
    }

    @Test
    fun theyFallAndNothingIsLeftAfterTheyAreGone() {
        val a = pieces(500)
        val b = pieces(1_500)
        assertThat(a.indices.count { b[it].centre.y > a[it].centre.y }).isEqualTo(60)
        assertThat(pieces(CONFETTI_GONE_MS)).isEmpty()
    }

    @Test
    fun theColoursAreThePeoplesAndTheAccent() {
        assertThat(pieces(0).map { it.colour }.toSet()).containsExactlyElementsIn(colours)
    }

    @Test
    fun theSameSeedFallsTheSameWay() {
        assertThat(pieces(900, seed = 5)).isEqualTo(pieces(900, seed = 5))
        assertThat(pieces(900, seed = 5)).isNotEqualTo(pieces(900, seed = 6))
    }

    @Test
    fun thePiecesSpin() {
        val a = pieces(500)
        val b = pieces(900)
        assertThat(a.indices.count { a[it].angleDegrees != b[it].angleDegrees }).isAtLeast(50)
    }

    @Test
    fun aBurstEndsOnceAtTwoAndAHalfSeconds() {
        var done = 0
        compose.mainClock.autoAdvance = false
        compose.setContent { CulveryTheme(dark = true) { Box(Modifier.fillMaxSize()) { ConfettiBurst(seed = 3L, onDone = { done++ }, Modifier.fillMaxSize()) } } }
        compose.mainClock.advanceTimeByFrame()
        compose.mainClock.advanceTimeBy(CONFETTI_MS - 200)
        assertThat(done).isEqualTo(0)
        compose.mainClock.advanceTimeBy(500)
        assertThat(done).isEqualTo(1)
        compose.mainClock.advanceTimeBy(2_000)
        assertThat(done).isEqualTo(1)
    }
}
