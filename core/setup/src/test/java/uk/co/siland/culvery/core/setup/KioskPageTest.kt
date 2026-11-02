package uk.co.siland.culvery.core.setup

import androidx.compose.foundation.layout.Column
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.isHeading
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.google.common.truth.Truth.assertThat
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import uk.co.siland.culvery.core.plugin.LocalShellNavigator
import uk.co.siland.culvery.core.plugin.test.RecordingNavigator
import uk.co.siland.culvery.core.setup.pages.TabletLockSection
import uk.co.siland.culvery.core.ui.ButtonTone
import uk.co.siland.culvery.core.ui.ButtonToneKey
import uk.co.siland.culvery.core.ui.CulveryTheme
import uk.co.siland.culvery.core.ui.test.assertClickablesHaveRoleAndLabel

@RunWith(AndroidJUnit4::class)
class KioskPageTest {
    @get:Rule val compose = createComposeRule()
    private val navigator = RecordingNavigator()
    private val home = FakeHomeApp(default = false)

    private fun show() = compose.setContent {
        CompositionLocalProvider(LocalShellNavigator provides navigator) {
            CulveryTheme(dark = true) { Column { TabletLockSection(home) } }
        }
    }

    /** 15b §6 W29: a status row, on while Culvery is the home app, with today's buttons. */
    @Test
    fun tabletLockSaysOnOrOff() {
        show()
        compose.onNodeWithText("Tablet lock is off").assertExists()
        compose.onNodeWithTag("choose_home_app").assertExists()
        compose.onNodeWithTag("settings_change_home_app").assertDoesNotExist()
        home.isDefault.value = true
        compose.onNodeWithText("Tablet lock is on").assertExists()
        compose.onNodeWithText("Culvery keeps the tablet on this app. Tap Use other apps to leave it. It locks again when Culvery next opens.").assertExists()
        compose.onNodeWithTag("settings_change_home_app").assertExists()
        compose.onNodeWithTag("settings_exit_kiosk").performClick()
        assertThat(navigator.kioskExits).isEqualTo(1)
        compose.onNodeWithTag("choose_home_app").assertDoesNotExist()
        compose.onNodeWithContentDescription("Tablet lock").assert(isHeading())
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

    private val howTo = "If that doesn’t work: tap Use other apps, then choose the home app in Android’s Settings › Apps › Choose default apps › Home app."

    @Test
    fun theHowToLineShowsWhetherOrNotCulveryIsTheHomeApp() {
        show()
        compose.onNodeWithText(howTo).assertExists()
        home.isDefault.value = true
        compose.onNodeWithText(howTo).assertExists()
    }

    @Test
    fun chooseHomeAppIsTheGreenButtonThere() {
        show()
        compose.onNodeWithTag("choose_home_app").assert(SemanticsMatcher.expectValue(ButtonToneKey, ButtonTone.Primary))
        compose.assertClickablesHaveRoleAndLabel()
    }
}
