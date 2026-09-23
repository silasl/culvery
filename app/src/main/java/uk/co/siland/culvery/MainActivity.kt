package uk.co.siland.culvery

import android.os.Bundle
import android.view.MotionEvent
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.addCallback
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.runtime.getValue
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject
import kotlinx.coroutines.launch
import uk.co.siland.culvery.core.access.PinPromptController
import uk.co.siland.culvery.core.access.ui.PinPadHost
import uk.co.siland.culvery.core.plugin.Capability
import uk.co.siland.culvery.core.ui.CulveryTheme
import uk.co.siland.culvery.shell.ShellViewModel
import uk.co.siland.culvery.shell.ui.CulveryShell
import uk.co.siland.culvery.shell.ui.SettingsPlaceholder

@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    private val shell: ShellViewModel by viewModels()

    @Inject lateinit var pinPrompt: PinPromptController
    @Inject lateinit var capabilities: Set<@JvmSuppressWildcards Capability>

    // Set by Settings › Exit kiosk; cleared when the app comes back to the foreground.
    private var kioskExited = false

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        onBackPressedDispatcher.addCallback(this) { }
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                shell.kioskExit.collect {
                    kioskExited = true
                    unpinFromScreen()
                    showSystemBars()
                    moveTaskToBack(true)
                }
            }
        }
        setContent {
            val state by shell.uiState.collectAsStateWithLifecycle()
            CulveryTheme(dark = state.dark) {
                CulveryShell(
                    state = state,
                    onSelectTab = shell::selectTab,
                    onOpenSettings = shell::openSettings,
                    onLockSession = shell::lockSession,
                    onToggleThemePreview = shell::toggleThemePreview,
                    tabContent = { id -> capabilities.firstOrNull { it.id == id }?.TabContent() },
                )
                if (state.settingsOpen) {
                    SettingsPlaceholder(onExitKiosk = shell::exitKiosk, onClose = shell::closeSettings)
                }
                PinPadHost(pinPrompt)
            }
        }
    }

    override fun onRestart() {
        super.onRestart()
        kioskExited = false
    }

    override fun onResume() {
        super.onResume()
        if (!kioskExited) {
            hideSystemBars()
            pinToScreen()
        }
    }

    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        if (hasFocus && !kioskExited) hideSystemBars()
    }

    // Every touch-down anywhere (shell, Settings, PIN pad) keeps the PIN session alive.
    override fun dispatchTouchEvent(ev: MotionEvent): Boolean {
        if (ev.actionMasked == MotionEvent.ACTION_DOWN) shell.onUserActivity()
        return super.dispatchTouchEvent(ev)
    }
}
