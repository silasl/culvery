package uk.co.siland.culvery.shell.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.height
import androidx.compose.ui.unit.width
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.google.common.truth.Truth.assertThat
import java.time.LocalDateTime
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import uk.co.siland.culvery.core.plugin.HomeCard
import uk.co.siland.culvery.core.plugin.HomeCardPlacer
import uk.co.siland.culvery.core.plugin.HomeCardSize
import uk.co.siland.culvery.core.ui.CulveryTheme
import uk.co.siland.culvery.shell.ShellUiState

/** The shell at 1280 × 800 dp with a tagged box for each card (9 §6.1). */
@RunWith(AndroidJUnit4::class)
class HomeGridTest {
    @get:Rule val compose = createComposeRule()

    // Tasks that test a card at its Home size copy these two literals into their own modules' tests.
    private val todayCardSize = DpSize(400.dp, 586.dp)
    private val rightHalfSize = DpSize(701.dp, 285.5.dp)

    private fun box(tag: String, size: HomeCardSize, priority: Int) =
        HomeCard(tag, size, priority) { Box(Modifier.fillMaxSize().testTag(tag)) }

    private val today = box("today", HomeCardSize.TALL, 100)
    private val comingUp = box("comingUp", HomeCardSize.WIDE, 50)
    private val lights = box("lights", HomeCardSize.REGULAR, 35)
    private val music = box("music", HomeCardSize.REGULAR, 30)
    private val shopping = box("shopping", HomeCardSize.UTILITY, 40)

    private fun show(vararg cards: HomeCard) {
        val state = ShellUiState(homeCards = HomeCardPlacer.place(cards.toList()))
        compose.setContent {
            CulveryTheme(dark = true) {
                CulveryShell(state, { LocalDateTime.of(2026, 10, 19, 11, 54) }, {}, {}, {}, {}) {}
            }
        }
    }

    private fun bounds(tag: String) = compose.onNodeWithTag(tag).getUnclippedBoundsInRoot()

    @Test
    fun theLeftColumnIs400Dp() {
        show(today, lights, music)
        assertThat(bounds("today").width.value).isWithin(0.5f).of(400f)
        assertThat(bounds("lights").left.value - bounds("today").right.value).isWithin(0.5f).of(15f)
    }

    @Test
    fun twoRightCardsShareTheColumn() {
        show(today, lights, music)
        val top = bounds("lights")
        val bottom = bounds("music")
        val column = bounds("today")
        val half = (column.height.value - 15f) / 2
        assertThat(top.height.value).isWithin(1f).of(half)
        assertThat(bottom.height.value).isWithin(1f).of(half)
        assertThat(bottom.top.value - top.bottom.value).isWithin(0.5f).of(15f)
        assertThat(top.top.value).isEqualTo(column.top.value)
        assertThat(bottom.bottom.value).isWithin(0.5f).of(column.bottom.value)
    }

    @Test
    fun oneRightCardTakesTheColumn() {
        show(today, music)
        assertThat(bounds("music").height.value).isWithin(0.5f).of(bounds("today").height.value)
    }

    @Test
    fun comingUpTakesTheColumnAlone() {
        show(today, comingUp)
        assertThat(bounds("comingUp").height.value).isWithin(0.5f).of(bounds("today").height.value)
        assertThat(bounds("comingUp").width.value).isWithin(1f).of(rightHalfSize.width.value)
    }

    @Test
    fun theCardsKeepTheirMeasuredSizes() {
        show(today, lights, music)
        assertThat(bounds("today").width.value).isWithin(1f).of(todayCardSize.width.value)
        assertThat(bounds("today").height.value).isWithin(1f).of(todayCardSize.height.value)
        assertThat(bounds("lights").width.value).isWithin(1f).of(rightHalfSize.width.value)
        assertThat(bounds("lights").height.value).isWithin(1f).of(rightHalfSize.height.value)
    }

    @Test
    fun shoppingSitsAboveARowOfLightsAndMusic() {
        show(today, shopping, lights, music)
        val top = bounds("shopping")
        val l = bounds("lights")
        val m = bounds("music")
        val column = bounds("today")
        assertThat(top.top.value).isEqualTo(column.top.value)
        assertThat(top.width.value).isWithin(1f).of(701f)
        assertThat(top.height.value).isWithin(1f).of(359f)
        val half = (701f - 15f) / 2
        for (b in listOf(l, m)) {
            assertThat(b.height.value).isWithin(0.5f).of(212f)
            assertThat(b.width.value).isWithin(1f).of(half)
        }
        assertThat(m.left.value - l.right.value).isWithin(0.5f).of(15f)
        assertThat(l.left.value).isEqualTo(top.left.value)
        assertThat(m.right.value).isWithin(0.5f).of(top.right.value)
        assertThat(m.bottom.value).isWithin(0.5f).of(column.bottom.value)
        assertThat(l.top.value - top.bottom.value).isWithin(0.5f).of(15f)
    }

    @Test
    fun oneOfLightsOrMusicTakesTheRowsWidth() {
        show(today, shopping, music)
        assertThat(bounds("music").width.value).isWithin(1f).of(701f)
        assertThat(bounds("music").height.value).isWithin(0.5f).of(212f)
    }

    @Test
    fun lightsAloneTakesTheRowsWidth() {
        show(today, shopping, lights)
        assertThat(bounds("lights").width.value).isWithin(1f).of(701f)
        assertThat(bounds("lights").height.value).isWithin(0.5f).of(212f)
    }

    @Test
    fun shoppingAloneFillsTheColumn() {
        show(today, shopping)
        assertThat(bounds("shopping").width.value).isWithin(1f).of(701f)
        assertThat(bounds("shopping").height.value).isWithin(1f).of(586f)
    }
}
