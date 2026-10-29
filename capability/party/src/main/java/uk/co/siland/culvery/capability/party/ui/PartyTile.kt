package uk.co.siland.culvery.capability.party.ui

import android.util.Log
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import uk.co.siland.culvery.capability.party.PartyActions
import uk.co.siland.culvery.capability.party.PartyHouse
import uk.co.siland.culvery.capability.party.PartyRun
import uk.co.siland.culvery.capability.party.PartyWords
import uk.co.siland.culvery.core.plugin.LocalOverlayHost
import uk.co.siland.culvery.core.ui.HouseModeTile
import uk.co.siland.culvery.core.ui.HouseModeWords
import uk.co.siland.culvery.core.ui.Icons
import uk.co.siland.culvery.core.ui.rememberSingleAction

private const val TAG = "PartyTile"

/**
 * The tray tile (13 §2.6): the icon, the label and "Off" or "On" spoken as one item, then what didn't happen as [line]
 * under it. A tap does nothing while [running] says a change is out.
 */
@Composable
internal fun PartyTile(on: Boolean, line: String?, running: PartyRun?, onTap: () -> Unit) {
    val state = when (running) {
        PartyRun.TURNING_ON -> HouseModeWords.TURNING_ON
        PartyRun.TURNING_OFF -> HouseModeWords.TURNING_OFF
        null -> if (on) PartyWords.ON else PartyWords.OFF
    }
    HouseModeTile(
        tag = "party_tile",
        icon = Icons.CELEBRATION,
        label = PartyWords.LABEL,
        on = on,
        second = state,
        secondTag = "party_tile_state",
        line = line,
        spoken = if (running != null) PartyWords.runningSpoken(running) else PartyWords.tileSpoken(on),
        enabled = running == null,
        onTap = onTap,
    )
}

/**
 * The tile on Home: a tap asks for a PIN, then opens the sheet that turns Party mode on or off. While Holiday mode is
 * on it asks nothing and says to turn that off first.
 */
@Composable
fun PartyTileHost(actions: PartyActions, house: PartyHouse) {
    val on by house.isOn.collectAsState()
    val houseLine by house.line.collectAsState()
    val refusal by actions.refusal.line.collectAsState(initial = actions.refusal.current())
    val running by house.running.collectAsState()
    val overlay = LocalOverlayHost.current
    val action = rememberSingleAction(house) { e -> Log.w(TAG, "Party mode tile action failed: ${e::class.simpleName}") }
    PartyTile(on == true, refusal ?: houseLine, running) {
        action.run { actions.toggle { by, turningOn -> overlay.show { PartySheetHost(by, turningOn, actions, house, overlay::dismiss) } } }
    }
}
