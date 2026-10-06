package uk.co.siland.culvery.shell.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.layout.FirstBaseline
import androidx.compose.ui.layout.LastBaseline
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertHeightIsEqualTo
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.click
import androidx.compose.ui.test.getAlignmentLinePosition
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.height
import androidx.compose.ui.unit.width
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.google.common.truth.Truth.assertThat
import com.google.common.truth.Truth.assertWithMessage
import java.time.LocalDateTime
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.GraphicsMode
import uk.co.siland.culvery.capability.weather.Condition
import uk.co.siland.culvery.capability.weather.HeaderWeather
import uk.co.siland.culvery.capability.weather.ui.WeatherHeaderItem
import uk.co.siland.culvery.core.plugin.HeaderItem
import uk.co.siland.culvery.core.plugin.LocalShellNavigator
import uk.co.siland.culvery.core.ui.CulveryTheme
import uk.co.siland.culvery.core.ui.WithFontScale
import uk.co.siland.culvery.shell.HOME_TAB_ID
import uk.co.siland.culvery.shell.SessionUi
import uk.co.siland.culvery.shell.ShellUiState

/** Needs real text metrics: legacy Robolectric graphics fakes glyph widths and font ascents. */
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class ShellLayoutTest {
    @get:Rule val compose = createComposeRule()
    private val at = LocalDateTime.of(2026, 9, 23, 11, 54)

    @Test
    fun dateBaselineSits44dpBelowClockBaseline() {
        compose.setContent {
            CulveryTheme(dark = true) { HomeScreen({ at }, emptyList(), emptyList()) }
        }
        val clock = compose.onNodeWithTag("home_clock")
        val date = compose.onNodeWithTag("home_date")
        val clockBaseline = clock.getUnclippedBoundsInRoot().top + clock.getAlignmentLinePosition(LastBaseline)
        val dateBaseline = date.getUnclippedBoundsInRoot().top + date.getAlignmentLinePosition(FirstBaseline)
        assertThat((dateBaseline - clockBaseline).value).isWithin(1f).of(44f)
    }

    /** Hand-off: the clock's line box is 0.9 × 104 sp; Trim.Both had made it the font's whole 135 dp. */
    @Test
    fun theClockBoxIsItsLineHeight() {
        compose.setContent { CulveryTheme(dark = true) { HomeScreen({ at }, emptyList(), emptyList()) } }
        val clock = compose.onNodeWithTag("home_clock").getUnclippedBoundsInRoot()
        assertThat(clock.height.value).isWithin(1f).of(94f)
    }

    /** The weather, then a wide item after it (a future Climate): together they'd run onto the date without a budget. */
    @Test
    fun atFontScale13HeaderItemsKeepClearOfTheClockAndDate() {
        val weather = HeaderItem("weather", 10) {
            WeatherHeaderItem(HeaderWeather(Condition.PARTLY_CLOUDY, night = false, temperature = 17.0, high = 19.0, low = 11.0))
        }
        val wide = HeaderItem("climate", 20) { Box(Modifier.size(800.dp, 60.dp)) }
        compose.setContent { CulveryTheme(dark = true) { WithFontScale(1.3f) { HomeScreen({ at }, emptyList(), listOf(weather, wide)) } } }
        val clock = compose.onNodeWithTag("home_clock").getUnclippedBoundsInRoot()
        val date = compose.onNodeWithTag("home_date").getUnclippedBoundsInRoot()
        val items = compose.onNodeWithTag("home_header_items").getUnclippedBoundsInRoot()
        assertThat(items.left.value).isAtLeast(maxOf(clock.right, date.right).value + 28f - 1f)
        val shown = compose.onNodeWithTag("home_header_weather").getUnclippedBoundsInRoot()
        assertThat(shown.width.value).isGreaterThan(0f)
        val degrees = compose.onNodeWithText("17°", useUnmergedTree = true).textLayout()
        assertThat(degrees.lineCount).isEqualTo(1)
        assertThat(degrees.isLineEllipsized(0)).isFalse()
        val highLow = compose.onNodeWithText("High 19° · Low 11°", useUnmergedTree = true).textLayout()
        assertThat(highLow.lineCount).isEqualTo(1)
        assertThat(highLow.isLineEllipsized(0)).isFalse()
    }

    private fun statusBar(session: SessionUi?, onSignOut: () -> Unit = {}) = compose.setContent {
        CulveryTheme(dark = true) { StatusBar({ at }, dark = true, previewing = false, session = session, onSignOut = onSignOut, onToggleThemePreview = {}) }
    }

    @Test
    fun statusBarShowsWhoIsSignedInAndTheirRole() {
        statusBar(SessionUi("Alex", "Admin"))
        compose.onNodeWithText("Alex · Admin").assertExists()
        compose.onNodeWithText("Sign out").assertExists()
    }

    @Test
    fun signOutLocks() {
        var signedOut = false
        statusBar(SessionUi("Alex", "Admin")) { signedOut = true }
        compose.onNodeWithTag("status_sign_out").performClick()
        assertThat(signedOut).isTrue()
    }

    /** A 48 dp touch target mustn't grow the bar: that would push Home down. */
    @Test
    fun theStatusBarIs30DpTallAtFontScale1() {
        statusBar(SessionUi("Alex", "Admin"))
        compose.onNodeWithTag("status_bar").assertHeightIsEqualTo(30.dp)
    }

    /** Not `hasVisualOverflow`: it is true for any text narrower than the width it was offered. */
    @Test
    fun atFontScale15TheStatusBarGrowsToHoldItsText() {
        compose.setContent {
            CulveryTheme(dark = true) {
                WithFontScale(1.5f) {
                    StatusBar({ at }, dark = true, previewing = false, session = SessionUi("Alex", "Admin"), onSignOut = {}, onToggleThemePreview = {})
                }
            }
        }
        listOf("Sign out", "Auto", "Alex · Admin").forEach {
            val text = compose.onNodeWithText(it, useUnmergedTree = true).textLayout()
            assertWithMessage(it).that(text.didOverflowHeight || text.isLineEllipsized(text.lineCount - 1)).isFalse()
        }
    }

    /** Compose takes a touch up to 48 dp around a smaller clickable; Sign out's text is about 28 dp tall. */
    @Test
    fun signOutTakesATouchJustBelowItsText() {
        var signedOut = false
        statusBar(SessionUi("Alex", "Admin")) { signedOut = true }
        compose.onNodeWithTag("status_sign_out").performTouchInput { click(Offset(centerX, height + 8.dp.toPx())) }
        assertThat(signedOut).isTrue()
    }

    @Test
    fun signOutAnnouncesItsAction() {
        statusBar(SessionUi("Alex", "Admin"))
        val onClick = compose.onNodeWithTag("status_sign_out").fetchSemanticsNode().config[SemanticsActions.OnClick]
        assertThat(onClick.label).isEqualTo("Sign out")
    }

    @Test
    fun nothingAboutSigningInShowsWhenLocked() {
        statusBar(null)
        compose.onNodeWithTag("status_session").assertDoesNotExist()
        compose.onNodeWithText("Sign out").assertDoesNotExist()
    }

    private fun item(id: String, order: Int) = HeaderItem(id, order) { Box(Modifier.size(200.dp, 60.dp)) }

    private fun home(vararg items: HeaderItem) = compose.setContent {
        CulveryTheme(dark = true) { HomeScreen({ at }, emptyList(), items.toList()) }
    }

    @Test
    fun headerItemsSitAtTheRightFourAboveTheDatesBottom() {
        home(item("weather", 10))
        val root = compose.onRoot().getUnclippedBoundsInRoot()
        val date = compose.onNodeWithTag("home_date").getUnclippedBoundsInRoot()
        val items = compose.onNodeWithTag("home_header_items").getUnclippedBoundsInRoot()
        assertThat(items.right.value).isWithin(1f).of(root.right.value)
        assertThat((date.bottom - items.bottom).value).isWithin(1f).of(4f)
    }

    @Test
    fun oneItemHasNoDivider() {
        home(item("weather", 10))
        compose.onAllNodesWithTag("home_header_divider").assertCountEquals(0)
    }

    @Test
    fun twoItemsShowInOrderWithOneDividerBetween() {
        home(item("weather", 10), item("climate", 20))
        compose.onAllNodesWithTag("home_header_divider").assertCountEquals(1)
        val weather = compose.onNodeWithTag("home_header_weather").getUnclippedBoundsInRoot()
        val divider = compose.onNodeWithTag("home_header_divider").getUnclippedBoundsInRoot()
        val climate = compose.onNodeWithTag("home_header_climate").getUnclippedBoundsInRoot()
        assertThat(weather.right.value).isLessThan(divider.left.value)
        assertThat(divider.right.value).isLessThan(climate.left.value)
    }

    @Test
    fun withoutItemsTheHeaderIsAsBefore() {
        home()
        compose.onAllNodesWithTag("home_header_divider").assertCountEquals(0)
        compose.onNodeWithTag("home_clock").assertExists()
    }

    /** Through the whole shell, as MainActivity draws it, a minute's tick updates the Home clock. */
    @Test
    fun aMinuteTickUpdatesTheClock() {
        var now by mutableStateOf(at)
        val state = ShellUiState()
        compose.setContent {
            CompositionLocalProvider(LocalShellNavigator provides NoNavigation) {
                CulveryTheme(dark = true) {
                    CulveryShell(
                        state = state,
                        now = { now },
                        onSelectTab = {},
                        onOpenSettings = {},
                        onSignOut = {},
                        onToggleThemePreview = {},
                        tabContent = {},
                    )
                }
            }
        }
        compose.waitForIdle()
        now = at.plusMinutes(1)
        compose.waitForIdle()
        compose.onNodeWithTag("home_clock").assertTextEquals("11:55")
    }
}
