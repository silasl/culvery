package uk.co.siland.culvery.shell.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.google.common.truth.Truth.assertThat
import java.time.LocalDateTime
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import uk.co.siland.culvery.AppContent
import uk.co.siland.culvery.core.plugin.LocalShellNavigator
import uk.co.siland.culvery.core.plugin.test.FakeUserActivity
import uk.co.siland.culvery.core.plugin.test.RecordingNavigator
import uk.co.siland.culvery.core.ui.CulveryTheme
import uk.co.siland.culvery.core.ui.test.assertClickablesHaveRoleAndLabel
import uk.co.siland.culvery.display.Screen
import uk.co.siland.culvery.display.ui.SleepLayer
import uk.co.siland.culvery.display.ui.THE_CAMERA_IS_ON
import uk.co.siland.culvery.overlayCameraMark
import uk.co.siland.culvery.shell.OverlayState
import uk.co.siland.culvery.shell.SessionUi
import uk.co.siland.culvery.shell.ShellUiState
import uk.co.siland.culvery.watchingPill

/**
 * While the camera runs exactly one mark is in TalkBack's tree, wherever the household is: the top bar's pill on Home, the
 * overlay mark wherever the bar is covered or absent (9 §6.7). The layers are wired as MainActivity wires them, and the
 * nodes are counted in the tree TalkBack reads, where what a layer covers is gone.
 */
@RunWith(AndroidJUnit4::class)
class CameraMarksTest {
    @get:Rule val compose = createComposeRule()
    private val at = LocalDateTime.of(2026, 10, 19, 11, 54)
    private val overlay = OverlayState()

    private var running by mutableStateOf(false)
    private var complete by mutableStateOf<Boolean?>(true)
    private var settingsOpen by mutableStateOf(false)
    private var pinShowing by mutableStateOf(false)
    private var alert by mutableStateOf<(@Composable () -> Unit)?>(null)
    private var screen by mutableStateOf(Screen.AWAKE)

    private fun show() = compose.setContent {
        CompositionLocalProvider(LocalShellNavigator provides RecordingNavigator()) {
            CulveryTheme(dark = true) {
                val spot = remember { CameraMarkSpot() }
                ShellLayers(
                    overlay = overlay,
                    toast = null,
                    onToastHidden = {},
                    pinShowing = pinShowing,
                    pinPad = { if (pinShowing) Box(Modifier.fillMaxSize()) },
                    activity = FakeUserActivity(),
                    onTouch = {},
                    alert = alert,
                    sleeping = screen != Screen.AWAKE,
                    sleep = { SleepLayer(screen, { at }) {} },
                    cameraMark = overlayCameraMark(running, screen != Screen.AWAKE, complete, settingsOpen, overlay.isShowing, pinShowing, alert != null),
                ) {
                    AppContent(
                        complete = complete,
                        settingsOpen = settingsOpen,
                        overlay = overlay,
                        wizard = { Box(Modifier.fillMaxSize()) { Text("Welcome") } },
                        shell = {
                            CulveryShell(
                                state = ShellUiState(session = SessionUi("Alex", "Admin")),
                                now = { at },
                                onSelectTab = {},
                                onOpenSettings = {},
                                onSignOut = {},
                                onToggleThemePreview = {},
                                watching = watchingPill(running, pinShowing, alert != null),
                                pillSpot = spot,
                                tabContent = {},
                            )
                        },
                        settings = { Box(Modifier.fillMaxSize()) { Text("Settings") } },
                        onHomeDrawn = {},
                    )
                }
            }
        }
    }

    private fun marks(): Int {
        compose.waitForIdle()
        return compose.onAllNodesWithContentDescription(THE_CAMERA_IS_ON).fetchSemanticsNodes().size
    }

    /** One mark with the camera running and none without, in the state [set] puts the app in. */
    private fun oneMarkWhileTheCameraRuns(name: String, set: () -> Unit) {
        show()
        compose.runOnIdle { set() }
        running = false
        assertThat(marks()).isEqualTo(0)
        running = true
        assertWithName(name, marks())
        compose.assertClickablesHaveRoleAndLabel()
    }

    private fun assertWithName(name: String, count: Int) = assertThat("$name: $count").isEqualTo("$name: 1")

    @Test
    fun homeShowsOnePill() = oneMarkWhileTheCameraRuns("Home") {}

    @Test
    fun settingsShowsOneOverlayMark() = oneMarkWhileTheCameraRuns("Settings") { settingsOpen = true }

    @Test
    fun aSheetShowsOneMark() = oneMarkWhileTheCameraRuns("a sheet") { overlay.show { Text("Sheet") } }

    @Test
    fun thePinPadShowsOneMark() = oneMarkWhileTheCameraRuns("the PIN pad") { pinShowing = true }

    @Test
    fun theDoorCardShowsOneMark() = oneMarkWhileTheCameraRuns("the door card") { alert = { Text("Door") } }

    @Test
    fun theWizardShowsOneMark() = oneMarkWhileTheCameraRuns("the wizard") { complete = false }

    @Test
    fun theDarkScreenShowsOneMark() = oneMarkWhileTheCameraRuns("asleep") { screen = Screen.ASLEEP }

    /** Fails if the pill and the overlay mark can both be seen over an uncovered bar, or neither: the rule is one or the other. */
    @Test
    fun theRuleGivesThePillOrTheMarkNeverBothAndNeverNeither() {
        for (settings in listOf(false, true)) for (sheet in listOf(false, true)) for (pin in listOf(false, true)) for (alert in listOf(false, true)) {
            // Over a sheet or Settings the bar is covered, so its pill doesn't count as seen.
            val pillSeen = watchingPill(true, pin, alert) && !settings && !sheet
            val markSeen = overlayCameraMark(true, false, true, settings, sheet, pin, alert)
            assertThat(pillSeen != markSeen).isTrue()
            assertThat(overlayCameraMark(false, false, true, settings, sheet, pin, alert)).isFalse()
            assertThat(watchingPill(false, pin, alert)).isFalse()
        }
        assertThat(overlayCameraMark(true, true, true, false, false, false, false)).isTrue()
        assertThat(overlayCameraMark(true, false, false, false, false, false, false)).isTrue()
    }
}
