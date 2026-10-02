package uk.co.siland.culvery.core.setup

import androidx.compose.foundation.layout.Column
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.google.common.truth.Truth.assertThat
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import uk.co.siland.culvery.core.plugin.LocalShellNavigator
import uk.co.siland.culvery.core.setup.pages.KioskPage
import uk.co.siland.culvery.core.ui.CulveryTheme

@RunWith(AndroidJUnit4::class)
class KioskPageTest {
    @get:Rule val compose = createComposeRule()
    private val navigator = RecordingNavigator()
    private val home = FakeHomeApp(default = false)

    private fun show() = compose.setContent {
        CompositionLocalProvider(LocalShellNavigator provides navigator) {
            CulveryTheme(dark = true) { Column { KioskPage(home).Content() } }
        }
    }

    @Test
    fun untilCulveryIsTheHomeAppItOffersToChooseIt() {
        show()
        compose.onNodeWithText("Make Culvery the home app so it comes back after a restart.").assertExists()
        compose.onNodeWithText("Change home app").assertDoesNotExist()
        compose.onNodeWithText("Choose home app").performClick()
        assertThat(navigator.homeAppChoices).isEqualTo(1)
    }

    @Test
    fun asTheHomeAppItOffersToChangeIt() {
        home.isDefault.value = true
        show()
        compose.onNodeWithText("Make Culvery the home app so it comes back after a restart.").assertDoesNotExist()
        compose.onNodeWithText("Change home app").performClick()
        assertThat(navigator.homeAppChanges).isEqualTo(1)
    }

    @Test
    fun choosingItElsewhereShowsAtOnce() {
        show()
        home.isDefault.value = true
        compose.onNodeWithText("Change home app").assertExists()
    }
}
