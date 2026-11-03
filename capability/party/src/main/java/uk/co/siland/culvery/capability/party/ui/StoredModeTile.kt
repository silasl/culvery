package uk.co.siland.culvery.capability.party.ui

import android.util.Log
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import uk.co.siland.culvery.capability.party.ModeRun
import uk.co.siland.culvery.capability.party.ModesWords
import uk.co.siland.culvery.capability.party.StoredModeActions
import uk.co.siland.culvery.capability.party.StoredModesHouse
import uk.co.siland.culvery.core.ui.HouseModeOnPill
import uk.co.siland.culvery.core.ui.HouseModeTile
import uk.co.siland.culvery.core.ui.HouseModeWords
import uk.co.siland.culvery.core.ui.rememberSingleAction

private const val TAG = "StoredModeTile"

/**
 * A stored mode's tray tile (13 §2.6, 15a §2.1), tagged `mode_tile_{id}`: the icon and [name] spoken as one item with
 * "on" or "off", and the "ON" pill while it is on and no state line while it is off. A tap does nothing while [running]
 * says a change is out.
 */
@Composable
internal fun StoredModeTile(id: String, name: String, icon: String, on: Boolean, running: ModeRun?, onTap: () -> Unit) {
    val state = when (running) {
        ModeRun.TURNING_ON -> HouseModeWords.TURNING_ON
        ModeRun.TURNING_OFF -> HouseModeWords.TURNING_OFF
        null -> null
    }
    HouseModeTile(
        tag = "mode_tile_$id",
        icon = icon,
        label = name,
        on = on,
        second = state,
        secondTag = "mode_tile_${id}_state",
        spoken = if (running != null) ModesWords.runningSpoken(name, running) else ModesWords.tileSpoken(name, on),
        enabled = running == null,
        onTap = onTap,
    ) {
        if (on) HouseModeOnPill("mode_tile_${id}_on")
    }
}

/**
 * The tile on Home of the stored mode [id], drawn with [name] and [icon] (15b §4.3): a tap asks for a PIN only where the mode needs one, then turns it on or off with no
 * sheet. While another mode is on it asks nothing and says to turn that off first.
 */
@Composable
fun StoredModeTileHost(id: String, name: String, icon: String, actions: StoredModeActions, house: StoredModesHouse) {
    val onMode by house.onMode.collectAsState()
    val running by house.running.collectAsState()
    val on = onMode == id
    val action = rememberSingleAction(house) { e -> Log.w(TAG, "A stored mode's tile action failed: ${e::class.simpleName}") }
    StoredModeTile(id, name, icon, on, running?.takeIf { it.modeId == id }?.change) {
        action.run { actions.tap(id, sawOn = on) }
    }
}

/** The tile's line under its tray row: why a tap was refused, else what didn't happen. */
fun storedModeTileLine(id: String, actions: StoredModeActions, house: StoredModesHouse): Flow<String?> =
    combine(actions.refusal(id), house.line(id)) { refusal, houseLine -> refusal ?: houseLine }

/** The tag the tile of the stored mode [id] has on its line under its tray row. */
fun storedModeTileLineTag(id: String) = "mode_tile_${id}_line"
