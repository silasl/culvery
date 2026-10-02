package uk.co.siland.culvery

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Text
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.google.common.truth.Truth.assertThat
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import uk.co.siland.culvery.core.ui.CulveryTheme
import uk.co.siland.culvery.core.ui.HhSheet
import uk.co.siland.culvery.shell.FakeAccessControl
import uk.co.siland.culvery.shell.OverlayState
import uk.co.siland.culvery.shell.ui.ShellLayers

@RunWith(AndroidJUnit4::class)
class AppContentTest {
    @get:Rule val compose = createComposeRule()
    private val overlay = OverlayState()
    private val access = FakeAccessControl()
    private var complete by mutableStateOf<Boolean?>(null)
    private var settingsOpen by mutableStateOf(false)
    private var homeDrawn = 0

    private fun show() = compose.setContent {
        CulveryTheme(dark = true) {
            ShellLayers(
                overlay = overlay,
                toast = null,
                onToastHidden = {},
                pinPad = {},
                onTouch = touchTarget(complete, settingsOpen, access),
            ) {
                AppContent(
                    complete = complete,
                    settingsOpen = settingsOpen,
                    overlay = overlay,
                    wizard = { Text("Wizard") },
                    shell = { Text("Shell") },
                    settings = { Box(Modifier.fillMaxSize().testTag("settings_stand_in")) },
                    onHomeDrawn = { homeDrawn++ },
                )
            }
        }
    }

    @Test
    fun untilSetupIsKnownNothingShows() {
        show()
        compose.onNodeWithTag("app_blank").assertExists()
        compose.onNodeWithText("Wizard").assertDoesNotExist()
        compose.onNodeWithText("Shell").assertDoesNotExist()
    }

    @Test
    fun anIncompleteSetupShowsTheWizard() {
        complete = false
        show()
        compose.onNodeWithText("Wizard").assertExists()
        compose.onNodeWithText("Shell").assertDoesNotExist()
    }

    @Test
    fun aCompleteSetupShowsTheShellAndSettings() {
        complete = true
        show()
        compose.onNodeWithText("Shell").assertExists()
        compose.onNodeWithTag("settings_stand_in").assertDoesNotExist()
        settingsOpen = true
        compose.onNodeWithTag("settings_stand_in").assertExists()
    }

    @Test
    fun aTapInsideAnOpenSheetCountsAsATouch() {
        complete = true
        settingsOpen = true
        show()
        compose.runOnIdle {
            overlay.show { HhSheet(PaddingValues()) { Text("Sheet", Modifier.testTag("sheet_text")) } }
        }
        compose.onNodeWithTag("sheet_text").performClick()
        assertThat(access.touches).isEqualTo(1)
    }

    @Test
    fun closingSettingsDismissesItsSheet() {
        complete = true
        settingsOpen = true
        show()
        compose.runOnIdle { overlay.show { Text("Sheet") } }
        compose.onNodeWithText("Sheet").assertExists()
        settingsOpen = false
        compose.onNodeWithText("Sheet").assertDoesNotExist()
        assertThat(overlay.isShowing).isFalse()
    }

    @Test
    fun finishingSetupClosesAnOpenSheet() {
        complete = false
        show()
        compose.runOnIdle { overlay.show { Text("Sheet") } }
        compose.onNodeWithText("Sheet").assertExists()
        complete = true
        compose.onNodeWithText("Sheet").assertDoesNotExist()
        assertThat(overlay.isShowing).isFalse()
    }

    @Test
    fun withSettingsClosedTouchesDoNothing() {
        complete = true
        show()
        compose.onNodeWithText("Shell").performClick()
        assertThat(access.touches).isEqualTo(0)
    }

    @Test
    fun theWizardIsNotHome() {
        complete = false
        show()
        compose.waitForIdle()
        assertThat(homeDrawn).isEqualTo(0)
    }

    @Test
    fun homeReportsItsFirstFrameOnce() {
        complete = true
        show()
        compose.waitUntil(5_000) { homeDrawn == 1 }
        settingsOpen = true
        compose.waitForIdle()
        assertThat(homeDrawn).isEqualTo(1)
    }
}
