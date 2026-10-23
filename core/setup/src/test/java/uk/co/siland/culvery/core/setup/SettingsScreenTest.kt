package uk.co.siland.culvery.core.setup

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Text
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.filterToOne
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.isHeading
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onParent
import androidx.compose.ui.test.performClick
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.height
import androidx.compose.ui.unit.width
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.google.common.truth.Truth.assertThat
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import uk.co.siland.culvery.core.plugin.LocalShellNavigator
import uk.co.siland.culvery.core.plugin.SettingsPage
import uk.co.siland.culvery.core.plugin.test.RecordingNavigator
import uk.co.siland.culvery.core.setup.pages.KioskPage
import uk.co.siland.culvery.core.ui.CulveryTheme
import uk.co.siland.culvery.core.ui.test.assertAccessible
import uk.co.siland.culvery.core.ui.test.assertClickablesHaveRoleAndLabel
import uk.co.siland.culvery.core.ui.test.isReadAsOne

@RunWith(AndroidJUnit4::class)
class SettingsScreenTest {
    @get:Rule val compose = createComposeRule()
    private var closes = 0
    private val navigator = RecordingNavigator()

    private fun page(id: String, title: String, order: Int) = StillPage(id, title, order) { Text("Page $id") }

    private fun show(vararg pages: SettingsPage) = compose.setContent {
        CulveryTheme(dark = true) {
            CompositionLocalProvider(LocalShellNavigator provides navigator) {
                SettingsScreen(pages.toList(), onClose = { closes++ })
            }
        }
    }

    @Test
    fun theFirstPageIsChosenOnOpen() {
        show(page("a", "Alpha", 0), page("b", "Beta", 1))
        compose.onNodeWithText("Settings").assertExists()
        compose.onNodeWithTag("settings_page_a").assertIsSelected()
        compose.onNodeWithText("Page a").assertExists()
    }

    @Test
    fun settingsOpensOnTheAskedPageOrTheFirstForAnUnknownOne() {
        compose.setContent {
            CulveryTheme(dark = true) {
                CompositionLocalProvider(LocalShellNavigator provides navigator) {
                    SettingsScreen(listOf(page("a", "Alpha", 0), page("location", "Home location", 1)), onClose = {}, initialPage = "location")
                }
            }
        }
        compose.onNodeWithTag("settings_page_location").assertIsSelected()
    }

    @Test
    fun anUnknownPageOpensTheFirst() {
        compose.setContent {
            CulveryTheme(dark = true) {
                CompositionLocalProvider(LocalShellNavigator provides navigator) {
                    SettingsScreen(listOf(page("a", "Alpha", 0), page("b", "Beta", 1)), onClose = {}, initialPage = "nowhere")
                }
            }
        }
        compose.onNodeWithTag("settings_page_a").assertIsSelected()
    }

    @Test
    fun choosingATitleShowsItsPage() {
        show(page("a", "Alpha", 0), page("b", "Beta", 1))
        compose.onNodeWithText("Beta").performClick()
        compose.onNodeWithText("Page b").assertExists()
        compose.onNodeWithText("Page a").assertDoesNotExist()
    }

    @Test
    fun closeCloses() {
        show(page("a", "Alpha", 0))
        compose.onNodeWithTag("settings_close").performClick()
        assertThat(closes).isEqualTo(1)
    }

    @Test
    fun theKioskPageExitsThroughTheShell() {
        show(KioskPage(FakeHomeApp()))
        compose.onNodeWithText("Culvery keeps the tablet on this app. Tap Use other apps to leave it. It locks again when Culvery next opens.").assertExists()
        compose.onNodeWithText("Use other apps").performClick()
        assertThat(navigator.kioskExits).isEqualTo(1)
    }

    @Test
    fun theChosenPageIsBoldAsWellAsTinted() {
        show(page("a", "Alpha", 0), page("b", "Beta", 1))
        assertThat(compose.onNodeWithText("Alpha", useUnmergedTree = true).textLayout().layoutInput.style.fontWeight).isEqualTo(FontWeight.W700)
        assertThat(compose.onNodeWithText("Beta", useUnmergedTree = true).textLayout().layoutInput.style.fontWeight).isEqualTo(FontWeight.W600)
    }

    @Test
    fun thePagesAreTabsInOneGroupUnderAHeading() {
        show(page("a", "Alpha", 0), KioskPage(FakeHomeApp()))
        compose.onNodeWithTag("settings_page_a").assert(SemanticsMatcher.expectValue(SemanticsProperties.Role, Role.Tab)).assertIsSelected()
        compose.onNodeWithTag("settings_page_a").onParent().assert(SemanticsMatcher.keyIsDefined(SemanticsProperties.SelectableGroup))
        compose.onNodeWithText("Settings").assert(isHeading())
        compose.onNodeWithTag("settings_page_kiosk").performClick()
        compose.onAllNodesWithText("Tablet lock").filterToOne(isHeading()).assertExists()
        compose.assertAccessible()
    }

    /** TalkBack reads the title, the page list and Close before the page, not the page between the title and the list. */
    @Test
    fun thePageListIsReadThroughBeforeThePage() {
        show(page("a", "Alpha", 0))
        compose.onNodeWithTag("settings_close").onParent().assert(isReadAsOne)
    }

    /** 4e session 1: with the keyboard up for the town search, Close must stay reachable. */
    @Test
    fun closeStaysAboveTheKeyboard() {
        compose.setContent {
            CulveryTheme(dark = true) {
                CompositionLocalProvider(LocalShellNavigator provides navigator) {
                    Box(Modifier.size(1280.dp, 800.dp)) {
                        SettingsScreen(listOf(page("a", "Alpha", 0)), onClose = {}, keyboard = WindowInsets(bottom = 320.dp))
                    }
                }
            }
        }
        assertThat(compose.onNodeWithTag("settings_close").getUnclippedBoundsInRoot().bottom).isAtMost(480.dp)
    }

    @Test
    fun settingsIsAPane() {
        show(page("a", "Alpha", 0))
        compose.onNodeWithTag("settings").assert(SemanticsMatcher.expectValue(SemanticsProperties.PaneTitle, "Settings"))
    }

    /** Pin: Settings' pane keeps its 720 dp when the wizard's column narrows to 640. */
    @Test
    fun thePaneStaysSevenTwentyWide() {
        compose.setContent {
            CulveryTheme(dark = true) {
                CompositionLocalProvider(LocalShellNavigator provides navigator) {
                    Box(Modifier.size(1280.dp, 800.dp)) {
                        SettingsScreen(listOf(StillPage("a", "Alpha", 0) { Box(Modifier.fillMaxWidth().height(20.dp)) }), onClose = {})
                    }
                }
            }
        }
        val width = compose.onNodeWithTag("settings_pane").getUnclippedBoundsInRoot().width.value
        assertThat(width).isAtMost(720f)
        assertThat(width).isGreaterThan(700f)
        compose.assertClickablesHaveRoleAndLabel()
    }

    private fun showOnCanvas(vararg pages: SettingsPage) = compose.setContent {
        CulveryTheme(dark = true) {
            CompositionLocalProvider(LocalShellNavigator provides navigator) {
                Box(Modifier.size(1280.dp, 800.dp)) { SettingsScreen(pages.toList(), onClose = {}) }
            }
        }
    }

    @Test
    fun theListIsTheHandoffsPanel() {
        showOnCanvas(page("a", "Alpha", 0), page("b", "Beta", 1), page("c", "Gamma", 2))
        val a = compose.onNodeWithTag("settings_page_a").getUnclippedBoundsInRoot()
        val b = compose.onNodeWithTag("settings_page_b").getUnclippedBoundsInRoot()
        val c = compose.onNodeWithTag("settings_page_c").getUnclippedBoundsInRoot()
        assertThat(a.height.value).isWithin(0.5f).of(52f)
        assertThat(b.top.value - a.bottom.value).isWithin(0.5f).of(7f)
        assertThat(c.top.value - b.bottom.value).isWithin(0.5f).of(7f)
        assertThat(a.left.value).isWithin(0.5f).of(28f)
        assertThat(a.width.value).isWithin(0.5f).of(320f - 2 * 28f)
        val close = compose.onNodeWithTag("settings_close").getUnclippedBoundsInRoot()
        assertThat(close.height.value).isWithin(0.5f).of(48f)
        assertThat(close.bottom.value).isWithin(0.5f).of(800f - 28f)
        compose.assertClickablesHaveRoleAndLabel()
    }

    @Test
    fun thePageIsPaddedAsDrawn() {
        showOnCanvas(page("a", "Alpha", 0))
        val pane = compose.onNodeWithTag("settings_pane").getUnclippedBoundsInRoot()
        assertThat(pane.left.value).isWithin(0.5f).of(320f + 48f)
        assertThat(pane.top.value).isWithin(0.5f).of(43f)
        compose.assertClickablesHaveRoleAndLabel()
    }
}
