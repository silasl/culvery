package uk.co.siland.culvery

import android.os.Bundle
import android.os.SystemClock
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.addCallback
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject
import javax.inject.Provider
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import uk.co.siland.culvery.core.access.AccessControl
import uk.co.siland.culvery.core.access.PinPromptController
import uk.co.siland.culvery.core.access.ui.PinPadHost
import uk.co.siland.culvery.core.plugin.Capability
import uk.co.siland.culvery.core.plugin.FirstDraw
import uk.co.siland.culvery.core.plugin.LocalOverlayHost
import uk.co.siland.culvery.core.plugin.LocalShellNavigator
import uk.co.siland.culvery.core.plugin.SettingsPage
import uk.co.siland.culvery.core.plugin.SetupStep
import uk.co.siland.culvery.core.setup.SettingsScreen
import uk.co.siland.culvery.core.setup.SetupSessionGate
import uk.co.siland.culvery.core.setup.SetupState
import uk.co.siland.culvery.core.setup.SetupWizard
import uk.co.siland.culvery.core.ui.CulveryTheme
import uk.co.siland.culvery.shell.OverlayState
import uk.co.siland.culvery.shell.ShellToasts
import uk.co.siland.culvery.shell.ShellViewModel
import uk.co.siland.culvery.shell.ui.CulveryShell
import uk.co.siland.culvery.shell.ui.ShellLayers

@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    private val shell: ShellViewModel by viewModels()

    @Inject lateinit var pinPrompt: PinPromptController
    @Inject lateinit var capabilities: Set<@JvmSuppressWildcards Capability>
    @Inject lateinit var toasts: ShellToasts
    @Inject lateinit var access: AccessControl
    @Inject lateinit var setupState: SetupState
    @Inject lateinit var gate: SetupSessionGate
    @Inject lateinit var firstDraw: FirstDraw
    // Only read when the wizard or Settings shows.
    @Inject lateinit var coreSteps: Provider<Set<@JvmSuppressWildcards SetupStep>>
    @Inject lateinit var corePages: Provider<Set<@JvmSuppressWildcards SettingsPage>>

    // Set by Settings › Exit kiosk; cleared when the app comes back to the foreground.
    private var kioskExited = false

    // SetupState read once for the activity, so the pin and the screen never disagree (4a design D10); null until read.
    private lateinit var setupComplete: StateFlow<Boolean?>

    override fun onCreate(savedInstanceState: Bundle?) {
        val splash = installSplashScreen()
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        val shownAt = SystemClock.uptimeMillis()
        setupComplete = setupState.setupComplete.stateIn(lifecycleScope, SharingStarted.Eagerly, null)
        splash.setKeepOnScreenCondition {
            holdSplash(setupComplete.value, shell.uiState.value.cardsLoaded, SystemClock.uptimeMillis() - shownAt)
        }
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
        lifecycleScope.launch {
            var previous: Boolean? = null
            setupComplete.filterNotNull().collect { complete ->
                val resumed = lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED)
                if (pinOnSetupRead(previous, complete, resumed, kioskExited)) pinToScreen()
                previous = complete
            }
        }
        setContent {
            val state by shell.uiState.collectAsStateWithLifecycle()
            // Read inside the clocks only: a minute's tick recomposes them, not the shell (4c §4.2).
            val now = shell.now.collectAsStateWithLifecycle()
            val toast by toasts.current.collectAsStateWithLifecycle()
            val complete by setupComplete.collectAsStateWithLifecycle()
            val overlay = remember { OverlayState() }
            CompositionLocalProvider(
                LocalShellNavigator provides shell,
                LocalOverlayHost provides overlay,
            ) {
                CulveryTheme(dark = state.dark) {
                    ShellLayers(
                        overlay = overlay,
                        toast = toast,
                        onToastHidden = toasts::hide,
                        pinPad = { PinPadHost(pinPrompt, overSheet = overlay.isShowing) },
                        onTouch = touchTarget(complete, state.settingsOpen, access),
                    ) {
                        AppContent(
                            complete = complete,
                            settingsOpen = state.settingsOpen,
                            overlay = overlay,
                            wizard = { SetupWizard(remember { wizardSteps(coreSteps.get(), capabilities) }, gate) },
                            shell = {
                                CulveryShell(
                                    state = state,
                                    now = { now.value },
                                    onSelectTab = shell::selectTab,
                                    onOpenSettings = shell::openSettings,
                                    onSignOut = shell::signOut,
                                    onToggleThemePreview = shell::toggleThemePreview,
                                    tabContent = { id -> capabilities.firstOrNull { it.id == id }?.TabContent() },
                                )
                            },
                            settings = { SettingsScreen(remember { settingsPages(corePages.get(), capabilities) }, onClose = shell::closeSettings) },
                            onHomeDrawn = firstDraw::markDrawn,
                        )
                    }
                }
            }
        }
    }

    override fun onRestart() {
        super.onRestart()
        kioskExited = false
    }

    override fun onResume() {
        super.onResume()
        if (!kioskExited) hideSystemBars()
        if (shouldPin(setupComplete.value == true, kioskExited)) pinToScreen()
    }

    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        if (hasFocus && !kioskExited) hideSystemBars()
    }
}
