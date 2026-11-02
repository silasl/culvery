package uk.co.siland.culvery.capability.party.ui

import android.util.Log
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import uk.co.siland.culvery.capability.party.ModesWords
import uk.co.siland.culvery.capability.party.PartyRun
import uk.co.siland.culvery.capability.party.StoredMode
import uk.co.siland.culvery.capability.party.StoredModeActions
import uk.co.siland.culvery.capability.party.StoredModesHouse
import uk.co.siland.culvery.core.ui.HouseModeOnPill
import uk.co.siland.culvery.core.ui.HouseModeTile
import uk.co.siland.culvery.core.ui.HouseModeWords
import uk.co.siland.culvery.core.ui.rememberSingleAction

private const val TAG = "StoredModeTile"

/**
 * A stored mode's tray tile (13 §2.6, 15a §2.1), tagged `mode_tile_{id}`: the icon and [name] spoken as one item with
 * "on" or "off", the "ON" pill while it is on and no state line while it is off, then what didn't happen as [line]
 * under it. A tap does nothing while [running] says a change is out.
 */
@Composable
internal fun StoredModeTile(id: String, name: String, icon: String, on: Boolean, line: String?, running: PartyRun?, onTap: () -> Unit) {
    val state = when (running) {
        PartyRun.TURNING_ON -> HouseModeWords.TURNING_ON
        PartyRun.TURNING_OFF -> HouseModeWords.TURNING_OFF
        null -> null
    }
    HouseModeTile(
        tag = "mode_tile_$id",
        icon = icon,
        label = name,
        on = on,
        second = state,
        secondTag = "mode_tile_${id}_state",
        line = line,
        spoken = if (running != null) ModesWords.runningSpoken(name, running) else ModesWords.tileSpoken(name, on),
        enabled = running == null,
        onTap = onTap,
    ) {
        if (on) HouseModeOnPill("mode_tile_${id}_on")
    }
}

/**
 * [mode]'s tile on Home (15b §4.3): a tap asks for a PIN only where the mode needs one, then turns it on or off with no
 * sheet. While another mode is on it asks nothing and says to turn that off first.
 */
@Composable
fun StoredModeTileHost(mode: StoredMode, actions: StoredModeActions, house: StoredModesHouse) {
    val onMode by house.onMode.collectAsState()
    val running by house.running.collectAsState()
    val houseLine by remember(house, mode.id) { house.line(mode.id) }.collectAsState(initial = null)
    val refusal by remember(actions, mode.id) { actions.refusal(mode.id) }.collectAsState(initial = null)
    val on = onMode == mode.id
    val action = rememberSingleAction(house) { e -> Log.w(TAG, "A stored mode's tile action failed: ${e::class.simpleName}") }
    StoredModeTile(mode.id, mode.name, mode.icon, on, refusal ?: houseLine, running?.takeIf { it.modeId == mode.id }?.change) {
        action.run { actions.tap(mode.id, sawOn = on) }
    }
}
