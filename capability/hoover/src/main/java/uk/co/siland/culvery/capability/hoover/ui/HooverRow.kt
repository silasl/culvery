package uk.co.siland.culvery.capability.hoover.ui

import android.util.Log
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import kotlinx.coroutines.delay
import uk.co.siland.culvery.capability.hoover.CLEANING_WORD
import uk.co.siland.culvery.capability.hoover.HOOVER
import uk.co.siland.culvery.capability.hoover.HooverActions
import uk.co.siland.culvery.capability.hoover.HooverActivity
import uk.co.siland.culvery.capability.hoover.HooverState
import uk.co.siland.culvery.capability.hoover.HooverSystem
import uk.co.siland.culvery.capability.hoover.NOT_STARTED
import uk.co.siland.culvery.capability.hoover.START
import uk.co.siland.culvery.capability.hoover.START_SPOKEN
import uk.co.siland.culvery.capability.hoover.STARTABLE
import uk.co.siland.culvery.capability.hoover.STARTING
import uk.co.siland.culvery.capability.hoover.hooverSpoken
import uk.co.siland.culvery.capability.hoover.hooverStatus
import uk.co.siland.culvery.capability.hoover.resultLine
import uk.co.siland.culvery.core.ui.ButtonSurface
import uk.co.siland.culvery.core.ui.ButtonTone
import uk.co.siland.culvery.core.ui.Culvery
import uk.co.siland.culvery.core.ui.HhButton
import uk.co.siland.culvery.core.ui.HhIcon
import uk.co.siland.culvery.core.ui.HhType
import uk.co.siland.culvery.core.ui.Icons
import uk.co.siland.culvery.core.ui.rememberSingleAction
import uk.co.siland.culvery.core.ui.shownNotSpoken

private const val TAG = "HooverRow"

/** How long a failed start's line stays on the row when the hoover does not move (11 §4.4). */
const val START_LINE_MS = 300_000L

/**
 * The tray row: the icon and words spoken as one group, then Start while the hoover is [STARTABLE], or the quiet
 * "Cleaning" pill while it works. [line] replaces the status; [starting] holds the button while a start is out.
 */
@Composable
internal fun HooverRow(state: HooverState, line: String?, starting: Boolean, onStart: () -> Unit) {
    val colors = Culvery.colors
    val activity = state.activity
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(HooverDimens.gap),
        modifier = Modifier
            .fillMaxWidth()
            .testTag("hoover_row")
            .background(colors.surf2, RoundedCornerShape(HooverDimens.radius))
            .padding(horizontal = HooverDimens.paddingH, vertical = HooverDimens.paddingV),
    ) {
        val spoken = if (line != null) "$HOOVER. $line" else hooverSpoken(state)
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(HooverDimens.gap),
            modifier = Modifier.weight(1f).semantics(mergeDescendants = true) { contentDescription = spoken },
        ) {
            HhIcon(Icons.CLEANING_SERVICES, size = HooverDimens.icon, filled = true, tint = colors.accent)
            Column(Modifier.weight(1f).shownNotSpoken()) {
                Text(HOOVER, style = HhType.buttonLabel, color = colors.ink, maxLines = 1)
                Text(
                    line ?: hooverStatus(state),
                    style = HooverDimens.statusStyle,
                    color = colors.mute,
                    maxLines = HooverDimens.STATUS_LINES,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.testTag("hoover_status"),
                )
            }
        }
        when {
            state.problem != null -> Unit
            starting || activity in STARTABLE -> HhButton(
                if (starting) STARTING else START,
                onClick = onStart,
                tone = ButtonTone.Primary,
                icon = Icons.PLAY_ARROW,
                enabled = !starting,
                on = ButtonSurface.Surf,
                spokenName = if (starting) null else START_SPOKEN,
                modifier = Modifier.testTag("hoover_start"),
            )
            activity == HooverActivity.CLEANING || activity == HooverActivity.RETURNING -> InactivePill()
        }
    }
}

@Composable
private fun InactivePill() {
    val colors = Culvery.colors
    Box(
        contentAlignment = Alignment.Center,
        modifier = Modifier
            .testTag("hoover_inactive")
            .shownNotSpoken()
            .height(HooverDimens.pillHeight)
            .border(HooverDimens.pillEdge, colors.surf3, RoundedCornerShape(percent = 50))
            .padding(horizontal = HooverDimens.pillPaddingH),
    ) {
        Text(CLEANING_WORD, style = HhType.buttonLabel, color = colors.mute, maxLines = 1)
    }
}

/** The row on Home: the hoover's state, a Start that asks for a PIN, and the line a failed start leaves. */
@Composable
fun HooverRowHost(system: HooverSystem, actions: HooverActions) {
    val state by system.state.collectAsState(initial = null)
    DisposableEffect(system) {
        val release = system.watch()
        onDispose(release)
    }
    var shown by remember { mutableStateOf<StartLine?>(null) }
    val action = rememberSingleAction(system) { e ->
        Log.w(TAG, "Hoover start failed: ${e::class.simpleName}")
        shown = StartLine(NOT_STARTED)
    }
    val current = state ?: return
    LaunchedEffect(current.activity) { shown = null }
    LaunchedEffect(shown) {
        if (shown != null) {
            delay(START_LINE_MS)
            shown = null
        }
    }
    HooverRow(current, shown?.text, action.busy) {
        action.run {
            shown = null
            actions.start()?.let { result -> resultLine(result)?.let { shown = StartLine(it) } }
        }
    }
}

/** A failed start's line; each failure is its own instance, so a repeat gets its own full time. */
private class StartLine(val text: String)
