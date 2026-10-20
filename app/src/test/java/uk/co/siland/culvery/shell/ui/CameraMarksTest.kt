package uk.co.siland.culvery.shell.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
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
import androidx.compose.ui.unit.dp
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
import uk.co.siland.culvery.core.ui.WithFontScale
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
 * overlay mark wherever the bar is covered or absent (9 §6.7). The layers are wired as MainActivity wires them. A hidden
 * pill has no description, so the unmerged count is what is painted.
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
    private var fontScale by mutableStateOf(1f)
    private var previewing by mutableStateOf(false)
    private var name by mutableStateOf("Alex")
    private var spot: CameraMarkSpot? = null

    private fun show() = compose.setContent {
        CompositionLocalProvider(LocalShellNavigator provides RecordingNavigator()) {
            CulveryTheme(dark = true) {
                WithFontScale(fontScale) {
                    val spot = remember { CameraMarkSpot() }.also { this@CameraMarksTest.spot = it }
                    // The system bars' padding that MainActivity puts around the layers.
                    Box(Modifier.padding(top = 24.dp, start = 10.dp)) {
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
                            sleep = { SleepLayer(screen, { at }, {}, cameraRunning = running, glyphAt = spot.centre) },
                            cameraMark = overlayCameraMark(running, screen != Screen.AWAKE, complete, settingsOpen, overlay.isShowing, pinShowing, alert != null),
                        ) {
                            AppContent(
                                complete = complete,
                                settingsOpen = settingsOpen,
                                overlay = overlay,
                                wizard = { Box(Modifier.fillMaxSize()) { Text("Welcome") } },
                                shell = {
                                    CulveryShell(
                                        state = ShellUiState(previewing = previewing, session = SessionUi(name, "Admin")),
                                        now = { at },
                                        onSelectTab = {},
                                        onOpenSettings = {},
                                        onSignOut = {},
                                        onToggleThemePreview = {},
                                        watching = watchingPill(running, screen != Screen.AWAKE, complete, settingsOpen, overlay.isShowing, pinShowing, alert != null),
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
        }
    }

    private fun marks(): Int {
        compose.waitForIdle()
        return compose.onAllNodesWithContentDescription(THE_CAMERA_IS_ON, useUnmergedTree = true).fetchSemanticsNodes().size
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

    @Test
    fun theNightClockShowsOneMark() = oneMarkWhileTheCameraRuns("the night clock") { screen = Screen.NIGHT_CLOCK }

    /** Fails if the glyph over either waking layer is not centred on the Watching pill's icon, as Home drew it (within 1 dp). */
    @Test
    fun theGlyphSitsWhereTheWatchingPillsIconSat() {
        running = true
        show()
        assertThat(marks()).isEqualTo(1)
        val iconCentre = spot!!.centre!!
        val tolerance = with(compose.density) { 1.dp.toPx() }
        for (asleep in listOf(Screen.ASLEEP, Screen.NIGHT_CLOCK)) {
            screen = asleep
            assertThat(marks()).isEqualTo(1)
            val glyph = compose.onAllNodesWithContentDescription(THE_CAMERA_IS_ON, useUnmergedTree = true).fetchSemanticsNodes().single()
            assertThat(glyph.boundsInRoot.center.x).isWithin(tolerance).of(iconCentre.x)
            assertThat(glyph.boundsInRoot.center.y).isWithin(tolerance).of(iconCentre.y)
        }
    }

    /** The pill pushed under a sheet's scrim by a large font, a long name and "Night · Preview" must not paint beside the overlay mark. */
    @Test
    fun aSheetAtFontScale15StillShowsOneMark() = oneMarkWhileTheCameraRuns("a sheet at 1.5") {
        fontScale = 1.5f
        previewing = true
        name = "Alex".repeat(5)
        overlay.show { Text("Sheet") }
    }

    /** Fails if any combination shows two marks, or none while awake and the camera runs; asleep, only the sleep layer's glyph marks it. */
    @Test
    fun theRuleGivesThePillOrTheMarkNeverBothAndNeverNeither() {
        val flags = listOf(false, true)
        for (running in flags) for (sleeping in flags) for (complete in listOf(true, false, null))
            for (settings in flags) for (sheet in flags) for (pin in flags) for (alert in flags) {
                val pill = watchingPill(running, sleeping, complete, settings, sheet, pin, alert)
                val mark = overlayCameraMark(running, sleeping, complete, settings, sheet, pin, alert)
                val expected = if (running && !sleeping) 1 else 0
                assertThat(listOf(pill, mark).count { it }).isEqualTo(expected)
            }
    }
}
