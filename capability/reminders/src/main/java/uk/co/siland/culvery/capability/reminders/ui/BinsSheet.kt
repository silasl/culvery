package uk.co.siland.culvery.capability.reminders.ui

import android.util.Log
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.paneTitle
import androidx.compose.ui.semantics.semantics
import java.time.LocalTime
import uk.co.siland.culvery.capability.reminders.BinsRowUi
import uk.co.siland.culvery.capability.reminders.BinsSave
import uk.co.siland.culvery.capability.reminders.COULD_NOT_SAVE
import uk.co.siland.culvery.capability.reminders.PICK_A_BINS_TIME
import uk.co.siland.culvery.capability.reminders.RemindersActions
import uk.co.siland.culvery.core.plugin.OverlayHost
import uk.co.siland.culvery.core.ui.ButtonSize
import uk.co.siland.culvery.core.ui.ButtonSurface
import uk.co.siland.culvery.core.ui.ButtonTone
import uk.co.siland.culvery.core.ui.Culvery
import uk.co.siland.culvery.core.ui.HhButton
import uk.co.siland.culvery.core.ui.HhCloseButton
import uk.co.siland.culvery.core.ui.HhSheet
import uk.co.siland.culvery.core.ui.HhSwitchRow
import uk.co.siland.culvery.core.ui.HhTimeStepperRow
import uk.co.siland.culvery.core.ui.HhType
import uk.co.siland.culvery.core.ui.rememberSingleAction

private const val TAG = "Reminders"
private const val TITLE = "Bins reminder"
private const val SWITCH_TITLE = "The evening before a collection"
private const val TIME_LABEL = "Remind at"
private const val TIME_SUBJECT = "bins reminder time"
private const val SAVE_CHANGES = "Save changes"

/** Shows the bins reminder's sheet with no PIN pad; Save changes asks for an adult's (22 §4.6). */
internal fun OverlayHost.openBins(row: BinsRowUi, actions: RemindersActions) = show {
    BinsSheetHost(on = row.on, time = row.time, save = actions::saveBins, onEdited = ::markEdited, onClose = ::dismiss)
}

/** Runs Save changes once at a time. A bad time and a failed save show their line and keep the sheet open, as a cancelled PIN pad does. */
@Composable
internal fun BinsSheetHost(
    on: Boolean,
    time: LocalTime,
    save: suspend (Boolean, LocalTime) -> BinsSave,
    onEdited: (Boolean) -> Unit,
    onClose: () -> Unit,
) {
    var switch by remember { mutableStateOf(on) }
    var at by remember { mutableStateOf(time) }
    var line by remember { mutableStateOf<String?>(null) }
    val action = rememberSingleAction(Unit) { e ->
        Log.w(TAG, "Couldn't save (${e::class.simpleName})")
        line = COULD_NOT_SAVE
    }
    // A save running counts as edited, so the idle close never cuts it off half done.
    val edited by rememberUpdatedState(onEdited)
    LaunchedEffect(Unit) { snapshotFlow { switch != on || at != time || action.busy }.collect { edited(it) } }
    BinsSheet(
        on = switch,
        time = at,
        line = line,
        busy = action.busy,
        onSwitch = { switch = it },
        onTime = { at = it },
        onSave = {
            line = null
            action.run {
                when (save(switch, at)) {
                    BinsSave.Saved -> onClose()
                    BinsSave.BadTime -> line = PICK_A_BINS_TIME
                    BinsSave.Cancelled -> Unit
                }
            }
        },
        onClose = onClose,
    )
}

@Composable
internal fun BinsSheet(
    on: Boolean,
    time: LocalTime,
    line: String?,
    busy: Boolean,
    onSwitch: (Boolean) -> Unit,
    onTime: (LocalTime) -> Unit,
    onSave: () -> Unit,
    onClose: () -> Unit,
) {
    val c = Culvery.colors
    HhSheet(
        padding = PaddingValues(horizontal = RemindersDimens.sheetPaddingH, vertical = RemindersDimens.sheetPaddingV),
        modifier = Modifier.testTag("bins_sheet").semantics { paneTitle = TITLE },
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(TITLE, style = HhType.sectionTitle, color = c.ink, modifier = Modifier.weight(1f).semantics { heading() })
            HhCloseButton(onClose)
        }
        Column(verticalArrangement = Arrangement.spacedBy(RemindersDimens.sheetGap), modifier = Modifier.weight(1f)) {
            HhSwitchRow(SWITCH_TITLE, null, on, "bins_on", { if (!busy) onSwitch(it) })
            HhTimeStepperRow(TIME_LABEL, TIME_SUBJECT, time, "bins_time") { if (!busy) onTime(it) }
        }
        if (line != null) {
            Text(
                line, style = HhType.body, color = c.danger,
                modifier = Modifier.testTag("bins_line").semantics { liveRegion = LiveRegionMode.Polite },
            )
        }
        HhButton(
            SAVE_CHANGES, onSave, Modifier.testTag("bins_save"),
            tone = ButtonTone.Primary, size = ButtonSize.Large, enabled = !busy, on = ButtonSurface.Bg, busy = busy,
        )
    }
}
