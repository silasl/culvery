package uk.co.siland.culvery

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import uk.co.siland.culvery.core.access.AccessControl
import uk.co.siland.culvery.core.plugin.Capability
import uk.co.siland.culvery.core.plugin.OverlayHost
import uk.co.siland.culvery.core.plugin.SettingsPage
import uk.co.siland.culvery.core.plugin.SetupStep
import uk.co.siland.culvery.core.ui.Culvery

/** The wizard's steps: the core ones and each capability's, by order (4a design D7). */
internal fun wizardSteps(core: Set<SetupStep>, capabilities: Set<Capability>): List<SetupStep> =
    (core + capabilities.flatMap { it.setupSteps() }).sortedBy { it.order }

/** Settings' pages: the core ones and each capability's, by order (4a design §4.6). */
internal fun settingsPages(core: Set<SettingsPage>, capabilities: Set<Capability>): List<SettingsPage> =
    (core + capabilities.flatMap { it.settingsPages() }).sortedBy { it.order }

/** 4a design D10: on resume, the kiosk pins once setup is complete, and not while the household has exited it. */
internal fun shouldPin(setupComplete: Boolean, kioskExited: Boolean): Boolean = setupComplete && !kioskExited

/**
 * Ruling 16: `setupComplete` turning true (the first read included, which can land after onResume) pins at once if
 * the activity is in front, so Open Culvery locks the tablet straight away.
 */
internal fun pinOnSetupRead(previous: Boolean?, now: Boolean, resumed: Boolean, kioskExited: Boolean): Boolean =
    previous != true && resumed && shouldPin(now, kioskExited)

/** 4a design D5, D9: touches restart the session while Settings is open or the wizard shows (the setup session's ten minutes). */
internal fun touchTarget(complete: Boolean?, settingsOpen: Boolean, access: AccessControl): (() -> Unit)? =
    if (complete == false || (complete == true && settingsOpen)) access::touch else null

/**
 * What the app shows (4a design §3.3): nothing until SetupState is read, the wizard until setup is complete, then the
 * shell with Settings over it when open. Settings closing (its session ended, or Close), or setup completing, takes any
 * open sheet with it.
 */
@Composable
internal fun AppContent(
    complete: Boolean?,
    settingsOpen: Boolean,
    overlay: OverlayHost,
    wizard: @Composable () -> Unit,
    shell: @Composable () -> Unit,
    settings: @Composable () -> Unit,
) {
    LaunchedEffect(complete, settingsOpen) { if (!settingsOpen) overlay.dismiss() }
    when (complete) {
        null -> Box(Modifier.fillMaxSize().testTag("app_blank").background(Culvery.colors.bg))
        false -> wizard()
        true -> {
            shell()
            if (settingsOpen) settings()
        }
    }
}
