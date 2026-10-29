package uk.co.siland.culvery.shell.ui

import android.graphics.Bitmap
import android.graphics.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.ViewRootForTest
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.unit.Density
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.google.common.truth.Truth.assertThat
import java.time.LocalDateTime
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.GraphicsMode
import uk.co.siland.culvery.capability.weather.Condition
import uk.co.siland.culvery.capability.weather.DailyWeather
import uk.co.siland.culvery.capability.weather.HeaderWeather
import uk.co.siland.culvery.capability.weather.ui.WeatherHeaderItem
import uk.co.siland.culvery.core.plugin.HeaderItem
import uk.co.siland.culvery.core.plugin.HomeCard
import uk.co.siland.culvery.core.plugin.HomeCardPlacer
import uk.co.siland.culvery.core.plugin.HomeCardSize
import uk.co.siland.culvery.core.ui.CulveryTheme
import uk.co.siland.culvery.core.ui.HalloweenDay
import uk.co.siland.culvery.core.ui.HalloweenInks
import uk.co.siland.culvery.core.ui.HalloweenNight
import uk.co.siland.culvery.core.ui.SeasonalDimens
import uk.co.siland.culvery.core.ui.SeasonalTheme
import uk.co.siland.culvery.core.ui.moonIn
import uk.co.siland.culvery.shell.ShellUiState
import uk.co.siland.culvery.shell.TabItem

@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class CulveryShellSkyTest {
    @get:Rule val compose = createComposeRule()
    private val at = LocalDateTime.of(2026, 10, 29, 11, 54)

    private val days = listOf(
        DailyWeather(at.toLocalDate(), Condition.PARTLY_CLOUDY, 19.0, 11.0, null, null),
        DailyWeather(at.toLocalDate().plusDays(1), Condition.RAIN, 17.0, 10.0, null, null),
        DailyWeather(at.toLocalDate().plusDays(2), Condition.CLEAR, 21.0, 12.0, null, null),
    )

    private fun fullHome(dark: Boolean, tab: String = "home") = ShellUiState(
        dark = dark,
        tabs = listOf(TabItem("door", "Door", "doorbell")),
        selectedTabId = tab,
        homeCards = HomeCardPlacer.place(
            listOf(
                HomeCard("test.a", HomeCardSize.TALL, 100) { Box(Modifier.fillMaxSize().testTag("card_a")) },
                HomeCard("test.b", HomeCardSize.WIDE, 50) { Box(Modifier.fillMaxSize().testTag("card_b")) },
            ),
        ),
        headerItems = listOf(
            HeaderItem("weather", 10) {
                WeatherHeaderItem(HeaderWeather.Now(Condition.PARTLY_CLOUDY, night = false, temperature = 17.0, high = 19.0, low = 11.0, days = days))
            },
        ),
    )

    private fun show(state: ShellUiState, seasonal: SeasonalTheme?) = compose.setContent {
        CulveryTheme(dark = state.dark, seasonal = seasonal) {
            CulveryShell(state, { at }, {}, {}, {}, {}, tabContent = {})
        }
    }

    private fun bounds(tag: String): Rect = compose.onNodeWithTag(tag).fetchSemanticsNode().boundsInRoot

    @Test
    fun theSkyIsBehindHomeWhileHalloweenShows() {
        show(fullHome(dark = true), SeasonalTheme.HALLOWEEN)
        val sky = bounds("halloween_sky")
        val clock = bounds("home_clock")
        assertThat(sky.left).isAtMost(clock.left)
        assertThat(sky.top).isAtMost(clock.top)
        assertThat(sky.right).isAtLeast(clock.right)
        assertThat(sky.bottom).isAtLeast(clock.bottom)
        val skyNode = compose.onNodeWithTag("halloween_sky").fetchSemanticsNode()
        val siblings = skyNode.parent!!.children.map { it.id }
        var above = compose.onNodeWithTag("home_clock", useUnmergedTree = true).fetchSemanticsNode()
        while (above.parent!!.id != skyNode.parent!!.id) above = above.parent!!
        assertThat(siblings.indexOf(skyNode.id)).isLessThan(siblings.indexOf(above.id))
        val reading = listOf("home_clock", "home_date", "home_header_items").map {
            compose.onNodeWithTag(it).fetchSemanticsNode().config.getOrNull(SemanticsProperties.TraversalIndex)
        }
        assertThat(reading).containsExactly(0f, 1f, 2f).inOrder()
    }

    /**
     * What the sky draws stays in the open band of the header: right of the clock and date, left of the header items, above
     * the cards. The band is worked out here from the laid-out nodes, and the pixels in the header's rows checked against it.
     */
    private fun skyStaysInTheBand(dark: Boolean) {
        show(fullHome(dark), SeasonalTheme.HALLOWEEN)
        val density = Density(compose.density.density)
        val gap = SeasonalDimens.skyGap.value * density.density
        val sky = bounds("halloween_sky")
        val clock = bounds("home_clock")
        val date = bounds("home_date")
        val items = bounds("home_header_items")
        val cards = listOf("card_a", "card_b").map { bounds(it) }
        val band = Rect(maxOf(clock.right, date.right) + gap, sky.top, items.left - gap, maxOf(date.bottom, items.bottom))

        listOf(clock, date, items).plus(cards).forEach { assertThat(band.overlaps(it)).isFalse() }
        assertThat(band.width).isGreaterThan(0f)

        val colours = HalloweenInks.run { (if (dark) HalloweenNight else HalloweenDay).let { listOf(moon(it), bat(it)) } }.map { it.toArgb() }
        val image = snapshot()
        // The weather draws its own divider in the same grey as the night bats.
        val texts = listOf(clock, date, items)
        var drawn = 0
        for (y in sky.top.toInt() until band.bottom.toInt()) {
            for (x in sky.left.toInt() until sky.right.toInt()) {
                val here = Offset(x.toFloat(), y.toFloat())
                if (image.getPixel(x, y) in colours && !texts.any { it.contains(here) }) {
                    drawn++
                    assertThat(band.inflate(1f).contains(here)).isTrue()
                }
            }
        }
        assertThat(drawn).isGreaterThan(0)

        val local = Rect(band.left - sky.left, 0f, band.right - sky.left, band.height)
        val moon = moonIn(local, density)
        assertThat(moon.centre.x + moon.radius).isAtMost(local.right)
        assertThat(moon.centre.y + moon.radius).isAtMost(local.bottom)
    }

    @Test
    fun theSkyStaysInTheOpenBandAtNight() = skyStaysInTheBand(dark = true)

    @Test
    fun theSkyStaysInTheOpenBandByDay() = skyStaysInTheBand(dark = false)

    @Test
    fun noSkyOffHome() {
        show(fullHome(dark = true, tab = "door"), SeasonalTheme.HALLOWEEN)
        compose.onAllNodesWithTag("halloween_sky").assertCountEquals(0)
    }

    /** Pin: no theme, no sky. */
    @Test
    fun noSkyWithoutATheme() {
        show(fullHome(dark = true), null)
        compose.onAllNodesWithTag("halloween_sky").assertCountEquals(0)
    }

    private fun snapshot(): Bitmap {
        val view = (compose.onRoot().fetchSemanticsNode().root as ViewRootForTest).view
        return Bitmap.createBitmap(view.width, view.height, Bitmap.Config.ARGB_8888).also { view.draw(Canvas(it)) }
    }
}
