package uk.co.siland.culvery

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.clearAndSetSemantics
import kotlinx.coroutines.flow.drop
import uk.co.siland.culvery.core.plugin.Capability
import uk.co.siland.culvery.core.plugin.OverlayHost
import uk.co.siland.culvery.core.plugin.SettingsPage
import uk.co.siland.culvery.core.plugin.SetupStep
import uk.co.siland.culvery.core.plugin.UserActivity
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

/** The splash's longest stay (4c design §4.1). */
internal const val SPLASH_MAX_MS = 2_000L

/** 4c §4.1: the splash stays until setup's state is known and, when Home shows, its first cards are in; at most [SPLASH_MAX_MS]. */
internal fun holdSplash(complete: Boolean?, cardsLoaded: Boolean, shownMillis: Long): Boolean =
    shownMillis < SPLASH_MAX_MS && (complete == null || (complete && !cardsLoaded))

/** 4a design D5, D9: touches restart the session while Settings is open or the wizard shows (the setup session's ten minutes). */
internal fun countsTouches(complete: Boolean?, settingsOpen: Boolean): Boolean = complete == false || (complete == true && settingsOpen)

/** While [counting], every touch [activity] reports, a TalkBack gesture's included, runs [onTouch] (4d-2 §6.1). */
@Composable
internal fun SessionTouches(activity: UserActivity, counting: Boolean, onTouch: () -> Unit) {
    val touch by rememberUpdatedState(onTouch)
    LaunchedEffect(activity, counting) {
        if (counting) activity.touches.drop(1).collect { touch() }
    }
}

/**
 * What the app shows (4a design §3.3): nothing until SetupState is read, the wizard until setup is complete, then the
 * shell with Settings over it when open. Settings closing (its session ended, or Close), or setup completing, takes any
 * open sheet with it. The shell's first frame is reported once to [onHomeDrawn].
 */
@Composable
internal fun AppContent(
    complete: Boolean?,
    settingsOpen: Boolean,
    overlay: OverlayHost,
    wizard: @Composable () -> Unit,
    shell: @Composable () -> Unit,
    settings: @Composable () -> Unit,
    onHomeDrawn: () -> Unit,
) {
    LaunchedEffect(complete, settingsOpen) { if (!settingsOpen) overlay.dismiss() }
    when (complete) {
        null -> Box(Modifier.fillMaxSize().testTag("app_blank").background(Culvery.colors.bg))
        false -> wizard()
        true -> {
            Box(Modifier.fillMaxSize().then(if (settingsOpen) Modifier.clearAndSetSemantics {} else Modifier)) { shell() }
            if (settingsOpen) settings()
            // Once: the first background passes wait for it (4c §4.1).
            LaunchedEffect(Unit) {
                withFrameNanos { }
                onHomeDrawn()
            }
        }
    }
}
