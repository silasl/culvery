package uk.co.siland.culvery

import android.app.role.RoleManager
import android.content.ActivityNotFoundException
import android.content.Intent
import android.os.Bundle
import android.os.SystemClock
import android.provider.Settings
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.addCallback
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
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
import kotlinx.coroutines.flow.first
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
import uk.co.siland.culvery.core.plugin.ShellNavigator
import uk.co.siland.culvery.core.setup.SettingsScreen
import uk.co.siland.culvery.core.setup.SetupSessionGate
import uk.co.siland.culvery.core.setup.SetupState
import uk.co.siland.culvery.core.setup.SetupWizard
import uk.co.siland.culvery.core.ui.Culvery
import uk.co.siland.culvery.core.ui.CulveryTheme
import uk.co.siland.culvery.shell.HomeAppRequest
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
    @Inject lateinit var homeApp: AndroidHomeApp
    // Only read when the wizard or Settings shows.
    @Inject lateinit var coreSteps: Provider<Set<@JvmSuppressWildcards SetupStep>>
    @Inject lateinit var corePages: Provider<Set<@JvmSuppressWildcards SettingsPage>>

    // Whether Culvery is device owner, so it is in true lock-task and Google's chooser needs Play services allowed in it.
    private var isDeviceOwner = false

    /** The shell's navigator, with the kiosk's side of Google's screens done at once: the chooser opens right after. */
    private val navigator: ShellNavigator by lazy {
        object : ShellNavigator by shell {
            override fun leavePinning() = kiosk.leaveForGoogle()

            override fun returnToPinning() = kiosk.returnToPinning()
        }
    }

    // SetupState read once for the activity, so the pin and the screen never disagree (4a design D10); null until read.
    private lateinit var setupComplete: StateFlow<Boolean?>

    private val kiosk by lazy {
        KioskLifecycle(
            ActivityKioskWindow(this),
            setupComplete = { setupComplete.value == true },
            isHomeApp = { homeApp.isDefault.value },
            inHomeTask = { isHomeTask(intent) },
            isDeviceOwner = { isDeviceOwner },
            kioskExited = { shell.kioskExited },
            // Read when used, so an instance that hands over in onCreate never builds the shell's view model.
            returnedToFront = { shell.returnedToFront() },
            front = { shell.front },
            changingConfigurations = { isChangingConfigurations },
            say = toasts::show,
        )
    }

    // Android's yes/no "make Culvery the home app?" dialog (4c §5.1); a yes hands over to the home task's Culvery.
    private val askHomeRole = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        homeApp.refresh()
        kiosk.roleAnswered(cancelled = result.resultCode == RESULT_CANCELED)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        val splash = installSplashScreen()
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        // Started outside the home task while Culvery is home (the launcher, Settings' Open): the home task's shows instead.
        homeApp.refresh()
        if (kiosk.handOverToHome()) return
        val shownAt = SystemClock.uptimeMillis()
        setupComplete = setupState.setupComplete.stateIn(lifecycleScope, SharingStarted.Eagerly, null)
        isDeviceOwner = allowLockTaskIfOwner(this)
        lifecycle.addObserver(kiosk)
        splash.setKeepOnScreenCondition {
            holdSplash(setupComplete.value, shell.uiState.value.cardsLoaded, SystemClock.uptimeMillis() - shownAt)
        }
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        lifecycleScope.launch {
            // Home with its cards is what the splash waits for; the wizard path is already shown at first draw.
            if (setupComplete.filterNotNull().first()) {
                shell.uiState.first { it.cardsLoaded }
                reportFullyDrawn()
            }
        }
        onBackPressedDispatcher.addCallback(this) { }
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                launch { shell.kioskExit.collect { kiosk.exitKiosk() } }
                launch { shell.homeAppRequests.collect(::openHomeAppScreen) }
            }
        }
        lifecycleScope.launch {
            var previous: Boolean? = null
            setupComplete.filterNotNull().collect { complete ->
                val resumed = lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED)
                if (!kiosk.handedOver && pinOnSetupRead(previous, complete, resumed, shell.kioskExited)) pinToScreen()
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
                LocalShellNavigator provides navigator,
                LocalOverlayHost provides overlay,
            ) {
                CulveryTheme(dark = state.dark) {
                    // Zero while the bars are hidden (the pinned kiosk); after Exit kiosk the content clears them (K1).
                    Box(Modifier.fillMaxSize().background(Culvery.colors.bg).windowInsetsPadding(WindowInsets.systemBars)) {
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
                                wizard = { SetupWizard(remember { wizardSteps(coreSteps.get(), capabilities) }, gate, setupState) },
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
    }

    override fun onResume() {
        super.onResume()
        homeApp.refresh()
    }

    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        if (hasFocus && !kiosk.handedOver && !shell.kioskExited) hideSystemBars()
    }

    private fun openHomeAppScreen(request: HomeAppRequest) = kiosk.openHomeAppScreen {
        when (request) {
            HomeAppRequest.CHOOSE -> {
                val roles = getSystemService(RoleManager::class.java)
                if (roles == null || !roles.isRoleAvailable(RoleManager.ROLE_HOME)) throw ActivityNotFoundException("No home role")
                askHomeRole.launch(roles.createRequestRoleIntent(RoleManager.ROLE_HOME))
            }
            HomeAppRequest.CHANGE -> startActivity(Intent(Settings.ACTION_HOME_SETTINGS))
        }
    }
}
