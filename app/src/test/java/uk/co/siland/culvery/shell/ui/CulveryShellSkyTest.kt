package uk.co.siland.culvery.shell.ui

import androidx.compose.runtime.Composable
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
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
import uk.co.siland.culvery.core.ui.CulveryTheme
import uk.co.siland.culvery.core.ui.SeasonalDimens
import uk.co.siland.culvery.core.ui.SeasonalTheme
import uk.co.siland.culvery.core.ui.batPositions
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
        headerItems = listOf(
            HeaderItem("weather", 10) {
                WeatherHeaderItem(HeaderWeather.Now(Condition.PARTLY_CLOUDY, night = false, temperature = 17.0, high = 19.0, low = 11.0, days = days))
            },
        ),
    )

    private fun show(state: ShellUiState, seasonal: SeasonalTheme?, drifting: Boolean = false) = compose.setContent {
        CulveryTheme(dark = state.dark, seasonal = seasonal) {
            CulveryShell(state, { at }, {}, {}, {}, {}, skyDrifts = drifting, tabContent = { Tab() })
        }
    }

    @Composable
    private fun Tab() = Unit

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

    /** Each bat lane's strip keeps clear of the weather and the date. */
    private fun batsKeepClear(dark: Boolean) {
        show(fullHome(dark), SeasonalTheme.HALLOWEEN)
        val sky = bounds("halloween_sky")
        val density = Density(compose.density.density)
        val items = bounds("home_header_items")
        val date = bounds("home_date")
        val headerBottom = date.bottom.coerceAtLeast(items.bottom) - sky.top
        val span = SeasonalDimens.batSpan.value * density.density
        batPositions(0, Size(sky.width, sky.height), headerBottom, density).forEach { bat ->
            val lane = Rect(sky.left, sky.top + bat.y, sky.right, sky.top + bat.y + span / 2)
            assertThat(lane.overlaps(items)).isFalse()
            assertThat(lane.overlaps(date)).isFalse()
        }
    }

    @Test
    fun theBatsKeepClearOfTheHeaderItemsAtNight() = batsKeepClear(dark = true)

    @Test
    fun theBatsKeepClearOfTheHeaderItemsByDay() = batsKeepClear(dark = false)

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
}
