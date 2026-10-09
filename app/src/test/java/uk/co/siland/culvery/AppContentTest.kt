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
import uk.co.siland.culvery.core.plugin.test.FakeUserActivity
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

    private val activity = FakeUserActivity()

    private fun show() = compose.setContent {
        CulveryTheme(dark = true) {
            SessionTouches(activity, countsTouches(complete, settingsOpen), overlay.edited) { hold -> access.touch(hold) }
            ShellLayers(
                overlay = overlay,
                toast = null,
                onToastHidden = {},
                pinShowing = false,
                pinPad = {},
                activity = activity,
                onTouch = activity::touch,
                overlaysCloseWhenIdle = !countsTouches(complete, settingsOpen),
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
        val before = compose.runOnIdle { access.touches }
        compose.onNodeWithTag("sheet_text").performClick()
        compose.waitForIdle()
        assertThat(access.touches).isEqualTo(before + 1)
    }

    /** A38: whatever UserActivity counts (a TalkBack gesture on the device) reaches the session as a finger does. */
    @Test
    fun aUserActivityTouchWhileSettingsIsOpenReachesTheSession() {
        complete = true
        settingsOpen = true
        show()
        val before = compose.runOnIdle { access.touches }
        activity.touch()
        compose.waitForIdle()
        assertThat(access.touches).isEqualTo(before + 1)
    }

    @Test
    fun closingSettingsDismissesItsSheet() {
        complete = true
        settingsOpen = true
        show()
        compose.runOnIdle { overlay.show { Text("Sheet") } }
        compose.runOnIdle { overlay.markEdited(true) }
        compose.onNodeWithText("Sheet").assertExists()
        settingsOpen = false
        compose.onNodeWithText("Sheet").assertDoesNotExist()
        assertThat(overlay.isShowing).isFalse()
        assertThat(overlay.edited).isFalse()
    }

    /** Settings closed over an edited sheet leaves its session on two minutes, not the edit hold: reopening it needs the PIN sooner. */
    @Test
    fun closingSettingsOverAnEditedSheetDropsTheHold() {
        complete = true
        settingsOpen = true
        show()
        compose.runOnIdle { overlay.show { Text("Sheet") } }
        compose.runOnIdle { overlay.markEdited(true) }
        compose.waitForIdle()
        assertThat(access.holds.last()).isTrue()
        settingsOpen = false
        compose.waitForIdle()
        assertThat(access.holds.last()).isFalse()
        val after = access.touches
        activity.touch()
        compose.waitForIdle()
        assertThat(access.touches).isEqualTo(after)
    }

    /** Typing goes to the keyboard's window, which Culvery's touch detector never sees, so the sheet's first change holds the session. */
    @Test
    fun anOpenSheetsFirstChangeHoldsTheSettingsSession() {
        complete = true
        settingsOpen = true
        show()
        compose.runOnIdle { overlay.show { Text("Sheet") } }
        compose.runOnIdle { overlay.markEdited(true) }
        compose.waitForIdle()
        assertThat(access.holds.last()).isTrue()
    }

    /** Edits undone, or the sheet closed, put the session straight back on two minutes. */
    @Test
    fun closingAnEditedSheetPutsSettingsBackOnTwoMinutes() {
        complete = true
        settingsOpen = true
        show()
        compose.runOnIdle { overlay.show { Text("Sheet") } }
        compose.runOnIdle { overlay.markEdited(true) }
        compose.waitForIdle()
        compose.runOnIdle { overlay.dismiss() }
        compose.waitForIdle()
        assertThat(access.holds.takeLast(2)).containsExactly(true, false).inOrder()
    }

    @Test
    fun undoingTheEditsPutsSettingsBackOnTwoMinutes() {
        complete = true
        settingsOpen = true
        show()
        compose.runOnIdle { overlay.show { Text("Sheet") } }
        compose.runOnIdle { overlay.markEdited(true) }
        compose.waitForIdle()
        compose.runOnIdle { overlay.markEdited(false) }
        compose.waitForIdle()
        activity.touch()
        compose.waitForIdle()
        assertThat(access.holds.takeLast(3)).containsExactly(true, false, false).inOrder()
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
        compose.waitForIdle()
        activity.touch()
        compose.waitForIdle()
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
