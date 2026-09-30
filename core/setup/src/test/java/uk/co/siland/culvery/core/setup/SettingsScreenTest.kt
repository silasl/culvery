package uk.co.siland.culvery.core.setup

import androidx.compose.material3.Text
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.google.common.truth.Truth.assertThat
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import uk.co.siland.culvery.core.plugin.LocalShellNavigator
import uk.co.siland.culvery.core.plugin.SettingsPage
import uk.co.siland.culvery.core.setup.pages.KioskPage
import uk.co.siland.culvery.core.ui.CulveryTheme

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
        show(KioskPage())
        compose.onNodeWithText("Culvery keeps the tablet on this app. Exit to use other apps; it locks again next time Culvery opens.").assertExists()
        compose.onNodeWithText("Exit kiosk").performClick()
        assertThat(navigator.kioskExits).isEqualTo(1)
    }
}
