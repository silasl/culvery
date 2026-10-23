package uk.co.siland.culvery

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipeRight
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.google.common.truth.Truth.assertThat
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import uk.co.siland.culvery.core.plugin.test.FakeUserActivity
import uk.co.siland.culvery.core.ui.CulveryTheme
import uk.co.siland.culvery.core.ui.HhButton
import uk.co.siland.culvery.core.ui.ButtonSurface
import uk.co.siland.culvery.core.ui.HhPercentSlider
import uk.co.siland.culvery.core.ui.HhRadioRow
import uk.co.siland.culvery.core.ui.HhSheet
import uk.co.siland.culvery.core.ui.HhSwitchRow
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

    private fun show(settingsPage: @Composable () -> Unit = { Box(Modifier.fillMaxSize().testTag("settings_stand_in")) }) = compose.setContent {
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
                    settings = settingsPage,
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

    /** Pin: nothing in Settings consumes a touch before the shell's counting box sees it. */
    @Test
    fun aTapOnASettingsSwitchCountsAsATouch() {
        complete = true
        settingsOpen = true
        var on by mutableStateOf(false)
        show { HhSwitchRow("Night mode", null, checked = on, tag = "stand_in_switch", onChange = { on = it }) }
        val before = compose.runOnIdle { access.touches }
        compose.onNodeWithTag("stand_in_switch").performClick()
        compose.waitForIdle()
        assertThat(on).isTrue()
        assertThat(access.touches).isEqualTo(before + 1)
    }

    /** Pin: Settings › People opens a person's sheet over Settings; its role rows are the shared radio rows. */
    @Test
    fun aTapOnASettingsRadioRowCountsAsATouch() {
        complete = true
        settingsOpen = true
        var role by mutableStateOf("ADULT")
        show {
            HhButton("Edit Sam", {
                overlay.show {
                    HhSheet(PaddingValues()) {
                        listOf("ADMIN", "ADULT", "CHILD").forEach { r -> HhRadioRow(r, null, selected = role == r, tag = "role_$r") { role = r } }
                    }
                }
            }, Modifier.testTag("stand_in_open"), on = ButtonSurface.Bg)
        }
        compose.onNodeWithTag("stand_in_open").performClick()
        val before = compose.runOnIdle { access.touches }
        compose.onNodeWithTag("role_CHILD").performClick()
        compose.waitForIdle()
        assertThat(role).isEqualTo("CHILD")
        assertThat(access.touches).isEqualTo(before + 1)
    }

    @Test
    fun aDragOnASettingsSliderCountsAsATouch() {
        complete = true
        settingsOpen = true
        val letGo = mutableListOf<Int>()
        show { HhPercentSlider("Brightness", 10, "stand_in_slider", onLetGo = { letGo += it }) }
        val before = compose.runOnIdle { access.touches }
        compose.onNodeWithTag("stand_in_slider").performTouchInput { swipeRight() }
        compose.waitForIdle()
        assertThat(letGo).isNotEmpty()
        assertThat(access.touches).isGreaterThan(before)
    }

    @Test
    fun aTapInsideAPersonSheetOverSettingsCountsAsATouch() {
        complete = true
        settingsOpen = true
        var saves = 0
        show()
        compose.runOnIdle {
            overlay.show { HhSheet(PaddingValues()) { HhButton("Save person", { saves++ }, Modifier.testTag("sheet_save"), on = ButtonSurface.Surf) } }
        }
        val before = compose.runOnIdle { access.touches }
        compose.onNodeWithTag("sheet_save").performClick()
        compose.waitForIdle()
        assertThat(saves).isEqualTo(1)
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
