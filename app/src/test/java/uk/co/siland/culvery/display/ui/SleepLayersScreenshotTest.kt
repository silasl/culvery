package uk.co.siland.culvery.display.ui

import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onRoot
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.github.takahirom.roborazzi.captureRoboImage
import java.time.LocalDateTime
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.GraphicsMode
import uk.co.siland.culvery.core.plugin.LocalShellNavigator
import uk.co.siland.culvery.core.plugin.test.FakeUserActivity
import uk.co.siland.culvery.core.plugin.test.RecordingNavigator
import uk.co.siland.culvery.core.ui.CulveryTheme
import uk.co.siland.culvery.core.ui.WithFontScale
import uk.co.siland.culvery.display.Screen
import uk.co.siland.culvery.overlayCameraMark
import uk.co.siland.culvery.shell.OverlayState
import uk.co.siland.culvery.shell.ShellUiState
import uk.co.siland.culvery.shell.ui.CulveryShell
import uk.co.siland.culvery.shell.ui.ShellLayers
import uk.co.siland.culvery.watchingPill

@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class SleepLayersScreenshotTest {
    @get:Rule val compose = createComposeRule()

    private val at = LocalDateTime.of(2026, 10, 19, 23, 41)

    /** Home in the shell's layers, with [screen] over it; the camera running ([mark]) shows as the top bar's pill on Home and as the overlay mark over the dark screen. */
    private fun snap(name: String, dark: Boolean, screen: Screen, mark: Boolean = false, fontScale: Float = 1f) {
        compose.setContent {
            CompositionLocalProvider(LocalShellNavigator provides RecordingNavigator()) {
                CulveryTheme(dark = dark) {
                    WithFontScale(fontScale) {
                        ShellLayers(
                            overlay = OverlayState(),
                            toast = null,
                            onToastHidden = {},
                            pinShowing = false,
                            pinPad = {},
                            activity = FakeUserActivity(),
                            onTouch = {},
                            sleeping = screen != Screen.AWAKE,
                            sleep = { SleepLayer(screen, { at }) {} },
                            cameraMark = overlayCameraMark(
                                mark, screen != Screen.AWAKE, complete = true, settingsOpen = false,
                                sheetShowing = false, pinShowing = false, alertShowing = false,
                            ),
                        ) {
                            CulveryShell(
                                state = ShellUiState(dark = dark),
                                now = { at },
                                onSelectTab = {},
                                onOpenSettings = {},
                                onSignOut = {},
                                onToggleThemePreview = {},
                                watching = watchingPill(mark, pinShowing = false, alertShowing = false),
                                tabContent = {},
                            )
                        }
                    }
                }
            }
        }
        compose.onRoot().captureRoboImage("src/test/screenshots/$name.png")
    }

    @Test
    fun nightClock() = snap("night_clock", dark = true, screen = Screen.NIGHT_CLOCK)

    @Test
    fun nightClockAtTheLargestFontScale() = snap("night_clock_fs15", dark = true, screen = Screen.NIGHT_CLOCK, fontScale = 1.5f)

    @Test
    fun cameraMarkDark() = snap("camera_mark_dark", dark = true, screen = Screen.AWAKE, mark = true)

    @Test
    fun cameraMarkLight() = snap("camera_mark_light", dark = false, screen = Screen.AWAKE, mark = true)

    /** By day the theme is light, so the mark over the dark screen is light `mute` on black. */
    @Test
    fun cameraMarkAsleep() = snap("camera_mark_asleep", dark = false, screen = Screen.ASLEEP, mark = true)
}
