package uk.co.siland.culvery.shell.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.layout.FirstBaseline
import androidx.compose.ui.layout.LastBaseline
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertHeightIsAtLeast
import androidx.compose.ui.test.assertHeightIsEqualTo
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.click
import androidx.compose.ui.test.getAlignmentLinePosition
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onParent
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.height
import androidx.compose.ui.unit.sp
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
import uk.co.siland.culvery.core.plugin.test.FakeUserActivity
import uk.co.siland.culvery.core.plugin.test.RecordingNavigator
import uk.co.siland.culvery.core.ui.CulveryTheme
import uk.co.siland.culvery.core.ui.Icons
import uk.co.siland.culvery.core.ui.WithFontScale
import uk.co.siland.culvery.core.ui.test.assertAccessible
import uk.co.siland.culvery.core.ui.test.assertClickablesHaveRoleAndLabel
import uk.co.siland.culvery.core.ui.test.isReadAsOne
import uk.co.siland.culvery.shell.HOME_TAB_ID
import uk.co.siland.culvery.shell.OverlayState
import uk.co.siland.culvery.shell.SessionUi
import uk.co.siland.culvery.shell.ShellUiState
import uk.co.siland.culvery.shell.TabItem

/** Needs real text metrics: legacy Robolectric graphics fakes glyph widths and font ascents. */
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class ShellLayoutTest {
    @get:Rule val compose = createComposeRule()
    private val at = LocalDateTime.of(2026, 9, 23, 11, 54)

    @Test
    fun dateBaselineSits42dpBelowClockBaseline() {
        compose.setContent {
            CulveryTheme(dark = true) { HomeScreen({ at }, emptyList(), emptyList()) }
        }
        val clock = compose.onNodeWithTag("home_clock")
        val date = compose.onNodeWithTag("home_date")
        val clockBaseline = clock.getUnclippedBoundsInRoot().top + clock.getAlignmentLinePosition(LastBaseline)
        val dateBaseline = date.getUnclippedBoundsInRoot().top + date.getAlignmentLinePosition(FirstBaseline)
        assertThat((dateBaseline - clockBaseline).value).isWithin(1f).of(42f)
    }

    /** Hand-off: the clock's line box is 0.9 × its size, 88 sp since 5 §A.2. The text alone is taller; `lineBox` makes the box the line height. */
    @Test
    fun theClockBoxIsItsLineHeight() {
        compose.setContent { CulveryTheme(dark = true) { HomeScreen({ at }, emptyList(), emptyList()) } }
        val clock = compose.onNodeWithTag("home_clock").getUnclippedBoundsInRoot()
        assertThat(clock.height.value).isWithin(1f).of(79f)
    }

    /** 5 §A.2: the clock went from 104 to 88 sp; the date is unchanged. */
    @Test
    fun theClockIs88spAndTheDate21() {
        compose.setContent { CulveryTheme(dark = true) { HomeScreen({ at }, emptyList(), emptyList()) } }
        assertThat(compose.onNodeWithTag("home_clock").textLayout().layoutInput.style.fontSize).isEqualTo(88.sp)
        assertThat(compose.onNodeWithTag("home_date").textLayout().layoutInput.style.fontSize).isEqualTo(21.sp)
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

    private fun statusBar(session: SessionUi?, watching: Boolean = false, onSignOut: () -> Unit = {}) = compose.setContent {
        CulveryTheme(dark = true) {
            StatusBar(dark = true, previewing = false, session = session, watching = watching, onSignOut = onSignOut, onToggleThemePreview = {})
        }
    }

    private fun shell() = compose.setContent {
        CulveryTheme(dark = true) {
            CulveryShell(
                ShellUiState(tabs = listOf(TabItem("calendar", "Calendar", Icons.CALENDAR_MONTH)), session = SessionUi("Alex", "Admin")), { at },
                onSelectTab = {}, onOpenSettings = {}, onSignOut = {}, onToggleThemePreview = {},
            ) {}
        }
    }

    /** The mark is the app's logo, a picture TalkBack skips, with as much room above it as beside it in the rail (9 §4.1). */
    @Test
    fun theMarkHasEqualSpaceAboveAndBeside() {
        shell()
        val mark = compose.onNodeWithTag("culvery_mark", useUnmergedTree = true)
        mark.assert(SemanticsMatcher.keyIsDefined(SemanticsProperties.HideFromAccessibility))
        mark.assert(SemanticsMatcher.keyNotDefined(SemanticsProperties.Role))
        val bounds = mark.getUnclippedBoundsInRoot()
        val inset = ((RAIL_WIDTH - MARK_WIDTH) / 2).value
        assertThat(bounds.width.value).isWithin(0.5f).of(MARK_WIDTH.value)
        assertThat(bounds.left.value).isWithin(0.5f).of(inset)
        assertThat(bounds.top.value).isWithin(0.5f).of(inset)
        assertThat(bounds.top.value).isWithin(0.5f).of(bounds.left.value)
        compose.assertClickablesHaveRoleAndLabel()
    }

    /** The bar is in the main area, beside the full-height rail; the page starts under it and keeps 28 dp from the right edge. */
    @Test
    fun theTopBarSitsInTheMainArea() {
        compose.setContent {
            CulveryTheme(dark = true) {
                CulveryShell(
                    ShellUiState(tabs = listOf(TabItem("calendar", "Calendar", Icons.CALENDAR_MONTH)), selectedTabId = "calendar", session = SessionUi("Alex", "Admin")),
                    { at }, onSelectTab = {}, onOpenSettings = {}, onSignOut = {}, onToggleThemePreview = {},
                ) { Box(Modifier.fillMaxSize().testTag("page")) }
            }
        }
        val root = compose.onRoot().getUnclippedBoundsInRoot()
        val bar = compose.onNodeWithTag("status_bar").getUnclippedBoundsInRoot()
        val page = compose.onNodeWithTag("page").getUnclippedBoundsInRoot()
        val rail = compose.onNodeWithTag("rail_home").getUnclippedBoundsInRoot()
        assertThat(bar.left.value).isAtLeast(RAIL_WIDTH.value)
        assertThat(rail.right.value).isAtMost(bar.left.value)
        assertThat(bar.top.value).isWithin(0.5f).of(0f)
        assertThat(bar.height.value).isWithin(0.5f).of(48f)
        assertThat(page.top.value).isWithin(0.5f).of(bar.bottom.value)
        assertThat((root.right - page.right).value).isWithin(0.5f).of(28f)
        assertThat(bar.right.value).isWithin(0.5f).of(page.right.value)
        compose.assertClickablesHaveRoleAndLabel()
    }

    @Test
    fun theStatusBarShowsNoTime() {
        statusBar(SessionUi("Alex", "Admin"))
        val clock = Regex("""\d{1,2}:\d\d""")
        val clockLike = SemanticsMatcher("shows a time") { node -> SemanticsProperties.Text in node.config && node.config[SemanticsProperties.Text].any { text -> clock.matches(text.text) } }
        compose.onAllNodes(clockLike, useUnmergedTree = true).assertCountEquals(0)
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
        statusBar(SessionUi("Alex", "Admin"), onSignOut = { signedOut = true })
        compose.onNodeWithTag("status_sign_out").performClick()
        assertThat(signedOut).isTrue()
    }

    /**
     * 4e session 2b: on the SM-T510 Compose didn't widen the controls' small targets, so a touch just outside Sign out or
     * the theme button missed. The bar is 48 dp and each control takes its full height.
     */
    @Test
    fun theStatusBarIs48DpTallAndItsControlsTakeItsHeight() {
        statusBar(SessionUi("Alex", "Admin"))
        compose.onNodeWithTag("status_bar").assertHeightIsEqualTo(48.dp)
        listOf("status_sign_out", "status_theme").forEach { compose.onNodeWithTag(it).assertHeightIsAtLeast(48.dp) }
    }

    /** A grown text neither overflows its height nor ends in an ellipsis. */
    @Test
    fun atFontScale15TheStatusBarGrowsToHoldItsText() {
        compose.setContent {
            CulveryTheme(dark = true) {
                WithFontScale(1.5f) {
                    StatusBar(dark = true, previewing = false, session = SessionUi("Alex", "Admin"), watching = true, onSignOut = {}, onToggleThemePreview = {})
                }
            }
        }
        listOf("Sign out", "Night · Auto", "Alex · Admin", WATCHING).forEach {
            val text = compose.onNodeWithText(it, useUnmergedTree = true).textLayout()
            assertWithMessage(it).that(text.didOverflowHeight || text.isLineEllipsized(text.lineCount - 1)).isFalse()
        }
    }


    /**
     * 4e session 2b: on the SM-T510 a touch just below the words "Sign out" or the theme's label missed: Compose didn't
     * widen their small targets there. Each now takes the bar's full 48 dp.
     */
    @Test
    fun inTheShellSignOutAndTheThemeTakeATouchJustBelowThem() {
        var signedOut = false
        var toggled = 0
        compose.setContent {
            CulveryTheme(dark = true) {
                // As in the app: the layers that count touches and hold the sheets wrap the shell.
                ShellLayers(
                    OverlayState(), toast = null, onToastHidden = {}, pinShowing = false, pinPad = {},
                    activity = FakeUserActivity(), onTouch = {},
                ) {
                    CulveryShell(
                        ShellUiState(session = SessionUi("Alex", "Admin")), { at },
                        onSelectTab = {}, onOpenSettings = {}, onSignOut = { signedOut = true }, onToggleThemePreview = { toggled++ },
                    ) {}
                }
            }
        }
        // The words are about 18 dp tall: 13 dp below a control's centre is just under them.
        compose.onNodeWithTag("status_theme").performTouchInput { click(Offset(centerX, centerY + 13.dp.toPx())) }
        assertThat(toggled).isEqualTo(1)
        compose.onNodeWithTag("status_sign_out").performTouchInput { click(Offset(centerX, centerY + 13.dp.toPx())) }
        assertThat(signedOut).isTrue()
    }

    @Test
    fun signOutIsNamedOnce() {
        statusBar(SessionUi("Alex", "Admin"))
        val onClick = compose.onNodeWithTag("status_sign_out").fetchSemanticsNode().config[SemanticsActions.OnClick]
        assertThat(onClick.label).isNull()
        compose.assertAccessible()
    }

    private fun themeToggle(dark: Boolean, previewing: Boolean): Pair<String, String?> {
        compose.setContent {
            CulveryTheme(dark = dark) { StatusBar(dark = dark, previewing = previewing, session = null, onSignOut = {}, onToggleThemePreview = {}) }
        }
        val node = compose.onNodeWithTag("status_theme").assert(SemanticsMatcher.expectValue(SemanticsProperties.Role, Role.Button))
        val config = node.fetchSemanticsNode().config
        return config[SemanticsProperties.ContentDescription].single() to config[SemanticsActions.OnClick].label
    }

    @Test
    fun theThemeToggleOnAutoByDayOffersTheNightTheme() {
        assertThat(themeToggle(dark = false, previewing = false)).isEqualTo("Theme: Day · Auto" to "preview the night theme")
    }

    @Test
    fun theThemeToggleOnAutoByNightOffersTheDayTheme() {
        assertThat(themeToggle(dark = true, previewing = false)).isEqualTo("Theme: Night · Auto" to "preview the day theme")
    }

    @Test
    fun theThemeTogglePreviewingOffersTheWayBack() {
        assertThat(themeToggle(dark = true, previewing = true)).isEqualTo("Theme: Night · Preview" to "go back to the automatic theme")
    }

    /** The control says which theme is on screen and whether it follows the clock (9 §4.1). */
    @Test
    fun theThemeControlSaysWhatItIs() {
        assertThat(themeLabel(dark = true, previewing = false)).isEqualTo("Night · Auto")
        assertThat(themeLabel(dark = false, previewing = false)).isEqualTo("Day · Auto")
        assertThat(themeLabel(dark = true, previewing = true)).isEqualTo("Night · Preview")
        assertThat(themeLabel(dark = false, previewing = true)).isEqualTo("Day · Preview")
        compose.setContent {
            CulveryTheme(dark = false) { StatusBar(dark = false, previewing = true, session = null, onSignOut = {}, onToggleThemePreview = {}) }
        }
        compose.onNodeWithTag("status_theme").assert(hasContentDescription("Theme: Day · Preview"))
        compose.onNodeWithText("Day · Preview", useUnmergedTree = true).assertExists()
        val onClick = compose.onNodeWithTag("status_theme").fetchSemanticsNode().config[SemanticsActions.OnClick]
        assertThat(onClick.label).isEqualTo("go back to the automatic theme")
        compose.assertClickablesHaveRoleAndLabel()
    }

    /** The pill shows only while the camera runs, is named for what it tells, and leaves the theme control where it was (9 §6.7). */
    @Test
    fun theWatchingPillShowsOnlyWhileTheCameraRuns() {
        var watching by mutableStateOf(true)
        compose.setContent {
            CulveryTheme(dark = true) {
                StatusBar(dark = true, previewing = false, session = SessionUi("Alex", "Admin"), watching = watching, onSignOut = {}, onToggleThemePreview = {})
            }
        }
        compose.onNodeWithText(WATCHING, useUnmergedTree = true).assertExists()
        compose.onNodeWithContentDescription("The camera is on").assertExists()
        val theme = compose.onNodeWithTag("status_theme").getUnclippedBoundsInRoot()
        val signOut = compose.onNodeWithTag("status_sign_out").getUnclippedBoundsInRoot()
        compose.assertClickablesHaveRoleAndLabel()
        watching = false
        compose.waitForIdle()
        // The hidden pill keeps its place but leaves TalkBack's tree.
        compose.onAllNodesWithText(WATCHING).assertCountEquals(0)
        compose.onAllNodesWithContentDescription("The camera is on").assertCountEquals(0)
        assertThat(compose.onNodeWithTag("status_theme").getUnclippedBoundsInRoot()).isEqualTo(theme)
        assertThat(compose.onNodeWithTag("status_sign_out").getUnclippedBoundsInRoot()).isEqualTo(signOut)
        compose.assertClickablesHaveRoleAndLabel()
    }

    /** Sign out is drawn as a 29 dp pill, but a touch anywhere in the bar's 48 dp height locks (9 §4.1). */
    @Test
    fun signOutIsAQuietPill() {
        var signedOut = 0
        statusBar(SessionUi("Alex", "Admin"), onSignOut = { signedOut++ })
        compose.onNodeWithTag("status_sign_out_pill", useUnmergedTree = true).assertHeightIsEqualTo(29.dp)
        compose.onNodeWithTag("status_sign_out").assertHeightIsEqualTo(48.dp)
        compose.onNodeWithTag("status_sign_out").performTouchInput { click(Offset(centerX, 1.dp.toPx())) }
        compose.onNodeWithTag("status_sign_out").performTouchInput { click(Offset(centerX, height - 1.dp.toPx())) }
        assertThat(signedOut).isEqualTo(2)
        compose.assertClickablesHaveRoleAndLabel()
    }

    /** TalkBack's double-tap is the semantics action, not a touch: it must reach the same toggle. */
    @Test
    fun theThemeToggleWorksFromTalkBack() {
        var toggles = 0
        compose.setContent {
            CulveryTheme(dark = false) { StatusBar(dark = false, previewing = false, session = null, onSignOut = {}, onToggleThemePreview = { toggles++ }) }
        }
        compose.onNodeWithTag("status_theme").performSemanticsAction(SemanticsActions.OnClick)
        assertThat(toggles).isEqualTo(1)
        compose.onNodeWithTag("status_theme").performClick()
        assertThat(toggles).isEqualTo(2)
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

    /** The items sit level with the clock, so by position TalkBack read them between the clock and the date. */
    @Test
    fun theHeaderIsReadClockDateThenItems() {
        home(item("weather", 10))
        compose.onNodeWithTag("home_clock").onParent().assert(isReadAsOne)
        compose.onNodeWithTag("home_header_items").assert(isReadAsOne)
        listOf("home_clock" to 0f, "home_date" to 1f, "home_header_items" to 2f).forEach { (tag, index) ->
            compose.onNodeWithTag(tag).assert(SemanticsMatcher.expectValue(SemanticsProperties.TraversalIndex, index))
        }
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
            CompositionLocalProvider(LocalShellNavigator provides RecordingNavigator()) {
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
