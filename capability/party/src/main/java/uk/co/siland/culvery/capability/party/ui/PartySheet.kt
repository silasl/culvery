package uk.co.siland.culvery.capability.party.ui

import android.util.Log
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.withContext
import uk.co.siland.culvery.capability.party.PartyActions
import uk.co.siland.culvery.capability.party.PartyHouse
import uk.co.siland.culvery.capability.party.PartyWords
import uk.co.siland.culvery.core.access.Authorised
import uk.co.siland.culvery.core.plugin.attempt
import uk.co.siland.culvery.core.ui.HouseModeLines
import uk.co.siland.culvery.core.ui.HouseModeSheet
import uk.co.siland.culvery.core.ui.rememberSingleAction

private const val TAG = "PartySheet"

/** The sheet a tap opens (13 §2.6): what Party mode will do when [turningOn], else what ends with it, as [lines]. */
@Composable
internal fun PartySheet(turningOn: Boolean, lines: List<String>, onConfirm: () -> Unit, onCancel: () -> Unit) {
    val title = if (turningOn) PartyWords.WILL else PartyWords.WHEN_ENDS
    HouseModeSheet(
        tag = "party_sheet",
        title = title,
        confirm = if (turningOn) PartyWords.TURN_ON_BUTTON else PartyWords.TURN_OFF_BUTTON,
        confirmTag = "party_sheet_confirm",
        cancelTag = "party_sheet_cancel",
        onConfirm = onConfirm,
        onCancel = onCancel,
    ) {
        HouseModeLines(title, lines, "party_sheet_lines")
    }
}

/**
 * Reads what Party mode will do, or put back, as it opens (13 §2.6). Confirming turns it on or off, which carries on
 * if the sheet closes meanwhile, then closes the sheet; with Holiday mode on by then nothing turns on and the tile
 * says why.
 */
@Composable
internal fun PartySheetHost(by: Authorised, turningOn: Boolean, actions: PartyActions, house: PartyHouse, onClose: () -> Unit) {
    var lines by remember { mutableStateOf<List<String>?>(null) }
    val action = rememberSingleAction(house) { e -> Log.w(TAG, "Turning Party mode on or off failed: ${e::class.simpleName}") }
    LaunchedEffect(turningOn) {
        lines = attempt({ if (turningOn) PartyWords.willLines(house.plan()) else house.backLines() }) { e ->
            Log.w(TAG, "Couldn't read what Party mode will change (${e::class.simpleName})")
            emptyList()
        }
    }
    val shown = lines ?: return
    PartySheet(
        turningOn, shown,
        onConfirm = {
            action.run {
                withContext(NonCancellable) { if (turningOn) actions.turnOn(by) else actions.turnOff(by) }
                onClose()
            }
        },
        onCancel = onClose,
    )
}
