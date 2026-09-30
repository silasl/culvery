package uk.co.siland.culvery.core.setup

import android.util.Log
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import kotlinx.coroutines.flow.combine
import uk.co.siland.culvery.core.access.CONTINUE_SETUP
import uk.co.siland.culvery.core.plugin.SetupStep
import uk.co.siland.culvery.core.ui.Culvery
import uk.co.siland.culvery.core.ui.HhPillButton
import uk.co.siland.culvery.core.ui.rememberSingleAction

private const val TAG = "SetupWizard"

/**
 * The first-run wizard (4a design §3.3, §4.1): the shown [steps], already in order, starting where
 * [WizardRules.resumeAt] says, with progress dots, Back, and Next or Skip for now. Whenever [gate] needs a PIN (an Admin
 * exists and nobody is signed in: a start after a kill, the setup session's idle limit, a lock), it asks before any step
 * shows, then the wizard carries on at the same step.
 */
@Composable
fun SetupWizard(steps: List<SetupStep>, gate: SetupSessionGate) {
    val statusFlow = remember(steps) {
        combine(steps.map { step -> combine(step.shown, step.done, step.canGoOn, ::StepStatus) }) { it.toList() }
    }
    val statuses = statusFlow.collectAsState(initial = null).value
    // Not saved: a wizard restored after its process died has lost the setup session and must ask again.
    val needsPin = remember(gate) { gate.needsPin }.collectAsState<Boolean, Boolean?>(initial = null).value
    // Saved, and kept here rather than in Steps, so the gate coming and going doesn't lose the step.
    var current by rememberSaveable { mutableStateOf(-1) }
    Box(Modifier.fillMaxSize().background(Culvery.colors.bg).testTag("wizard")) {
        when {
            statuses == null || needsPin == null -> Unit
            needsPin -> PinGate(gate)
            else -> {
                // Where it resumes is read once, from the first statuses; after that only Next, Back and Skip move it.
                val at = current.takeIf { it >= 0 } ?: WizardRules.resumeAt(statuses)
                SideEffect { if (current < 0) current = at }
                Steps(steps, statuses, at) { current = it }
            }
        }
    }
}

@Composable
private fun Steps(steps: List<SetupStep>, statuses: List<StepStatus>, current: Int, onGoTo: (Int) -> Unit) {
    // A step hidden while it shows (Review, after Disconnect) gives way to the one before it.
    val index = if (statuses.getOrNull(current)?.shown == true) {
        current
    } else {
        WizardRules.previousShown(current, statuses) ?: WizardRules.nextShown(current, statuses) ?: 0
    }
    val step = steps[index]
    val latest by rememberUpdatedState(statuses)
    val goTo by rememberUpdatedState(onGoTo)
    val action = rememberSingleAction(step.id) { e -> Log.w(TAG, "${step.id}: Next failed (${e::class.simpleName})") }
    val goOn: () -> Unit = {
        action.run {
            if (step.onNext()) WizardRules.nextShown(index, latest)?.let { goTo(it) }
        }
    }
    WizardFrame(
        dotCount = WizardRules.dotCount(statuses),
        dotIndex = WizardRules.dotIndex(index, statuses),
        back = WizardRules.previousShown(index, statuses)?.let { previous -> { goTo(previous) } },
        forward = WizardRules.forward(step.skippable, step.nextLabel, statuses[index]),
        busy = action.busy,
        onForward = goOn,
        onSkip = { WizardRules.nextShown(index, latest)?.let { goTo(it) } },
    ) {
        step.Content(onNext = goOn)
    }
}

/** 4a design §4.1: dots at the top centre, the step in a 720 dp column, Back left and the forward button right. */
@Composable
internal fun WizardFrame(
    dotCount: Int,
    dotIndex: Int,
    back: (() -> Unit)?,
    forward: Forward,
    busy: Boolean,
    onForward: () -> Unit,
    onSkip: () -> Unit,
    content: @Composable () -> Unit,
) {
    Column(
        Modifier
            .fillMaxSize()
            .padding(
                top = SetupDimens.wizardPaddingTop,
                bottom = SetupDimens.wizardPaddingBottom,
                start = SetupDimens.wizardPaddingH,
                end = SetupDimens.wizardPaddingH,
            ),
    ) {
        Dots(dotCount, dotIndex)
        Spacer(Modifier.height(SetupDimens.dotsBottom))
        Box(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.TopCenter) {
            Column(
                verticalArrangement = Arrangement.spacedBy(SetupDimens.blockGap),
                modifier = Modifier
                    .testTag("wizard_step")
                    .width(SetupDimens.wizardColumn)
                    .fillMaxHeight()
                    .verticalScroll(rememberScrollState()),
            ) {
                content()
            }
        }
        Spacer(Modifier.height(SetupDimens.buttonsTop))
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            if (back != null) HhPillButton(BACK, back, Modifier.testTag("wizard_back"), enabled = !busy)
            Spacer(Modifier.weight(1f))
            when (forward) {
                Forward.Skip -> HhPillButton(SKIP_FOR_NOW, onSkip, Modifier.testTag("wizard_skip"), enabled = !busy)
                is Forward.Next -> HhPillButton(
                    forward.label,
                    onForward,
                    Modifier.testTag("wizard_next"),
                    primary = true,
                    enabled = forward.enabled && !busy,
                )
            }
        }
    }
}

@Composable
private fun Dots(count: Int, index: Int) {
    val c = Culvery.colors
    Row(
        horizontalArrangement = Arrangement.spacedBy(SetupDimens.dotGap, Alignment.CenterHorizontally),
        modifier = Modifier.fillMaxWidth().testTag("wizard_dots"),
    ) {
        repeat(count) { i ->
            Box(
                Modifier
                    .testTag("wizard_dot")
                    .semantics { selected = i == index }
                    .size(SetupDimens.dot)
                    .clip(CircleShape)
                    .background(if (i == index) c.accent else c.surf3),
            )
        }
    }
}

/**
 * Opens the PIN pad at once; if it is cancelled, [CONTINUE_SETUP] and an Enter PIN pill to try again (ruling 11). The gate
 * goes when the setup session begins again.
 */
@Composable
private fun PinGate(gate: SetupSessionGate) {
    val action = rememberSingleAction(gate) { e -> Log.w(TAG, "Couldn't carry on setting up (${e::class.simpleName})") }
    val ask = { action.run { gate.carryOn() } }
    LaunchedEffect(gate) { ask() }
    PinGateContent(busy = action.busy, onEnterPin = ask)
}

@Composable
internal fun PinGateContent(busy: Boolean, onEnterPin: () -> Unit) {
    Column(
        verticalArrangement = Arrangement.spacedBy(SetupDimens.blockGap, Alignment.CenterVertically),
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier.fillMaxSize().testTag("wizard_gate"),
    ) {
        Text(CONTINUE_SETUP, style = SetupType.line, color = Culvery.colors.mute)
        HhPillButton(ENTER_PIN, onEnterPin, Modifier.testTag("wizard_enter_pin"), primary = true, enabled = !busy)
    }
}
