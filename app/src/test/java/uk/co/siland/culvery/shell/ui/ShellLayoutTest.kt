package uk.co.siland.culvery.shell.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.FirstBaseline
import androidx.compose.ui.layout.LastBaseline
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.getAlignmentLinePosition
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.google.common.truth.Truth.assertThat
import java.time.LocalDateTime
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.GraphicsMode
import uk.co.siland.culvery.core.plugin.HeaderItem
import uk.co.siland.culvery.core.plugin.LocalShellNavigator
import uk.co.siland.culvery.core.ui.CulveryTheme
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
