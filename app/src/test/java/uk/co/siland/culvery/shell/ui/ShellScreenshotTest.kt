package uk.co.siland.culvery.shell.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onRoot
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.github.takahirom.roborazzi.captureRoboImage
import java.time.LocalDateTime
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.GraphicsMode
import uk.co.siland.culvery.core.access.ui.PinPadSheet
import uk.co.siland.culvery.core.ui.CulveryTheme
import uk.co.siland.culvery.shell.SessionChip
import uk.co.siland.culvery.shell.ShellUiState

@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class ShellScreenshotTest {
    @get:Rule val compose = createComposeRule()

    private val at = LocalDateTime.of(2026, 9, 23, 11, 54)

    private fun snap(
        name: String,
        dark: Boolean,
        state: ShellUiState = ShellUiState(now = at, dark = dark),
        overlay: @Composable () -> Unit = {},
    ) {
        compose.setContent {
            CulveryTheme(dark = dark) {
                Box(Modifier.fillMaxSize()) {
                    CulveryShell(
                        state = state,
                        onSelectTab = {},
                        onOpenSettings = {},
                        onLockSession = {},
                        onToggleThemePreview = {},
                        tabContent = {},
                    )
                    overlay()
                }
            }
        }
        compose.onRoot().captureRoboImage("src/test/screenshots/$name.png")
    }

    @Test
    fun homeEmptyDark() = snap("home_empty_dark", dark = true)

    @Test
    fun homeEmptyLight() = snap("home_empty_light", dark = false)

    @Test
    fun homeWithSessionDark() = snap(
        "home_session_dark",
        dark = true,
        state = ShellUiState(now = at, dark = true, session = SessionChip("Admin", 0xFF4CB387)),
    )

    @Test
    fun settingsDark() = snap("settings_dark", dark = true) {
        SettingsPlaceholder(onExitKiosk = {}, onClose = {})
    }

    @Test
    fun pinPadDark() = snap("pin_pad_dark", dark = true) {
        PinPadSheet("Change settings", error = null, lockedUntilMillis = null, onSubmit = {}, onCancel = {})
    }

    @Test
    fun pinPadLight() = snap("pin_pad_light", dark = false) {
        PinPadSheet("Change settings", error = null, lockedUntilMillis = null, onSubmit = {}, onCancel = {})
    }
}
