package uk.co.siland.culvery.capability.party.ui

import android.util.Log
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import uk.co.siland.culvery.capability.party.PartyActions
import uk.co.siland.culvery.capability.party.PartyHouse
import uk.co.siland.culvery.capability.party.PartyRun
import uk.co.siland.culvery.capability.party.PartyWords
import uk.co.siland.culvery.core.plugin.LocalOverlayHost
import uk.co.siland.culvery.core.ui.Culvery
import uk.co.siland.culvery.core.ui.HhIcon
import uk.co.siland.culvery.core.ui.HhType
import uk.co.siland.culvery.core.ui.Icons
import uk.co.siland.culvery.core.ui.hhClickable
import uk.co.siland.culvery.core.ui.rememberSingleAction
import uk.co.siland.culvery.core.ui.shownNotSpoken

private const val TAG = "PartyTile"

/**
 * The tray tile (13 §2.6): the icon, the label and "Off" or "On" spoken as one item, then what didn't happen as [line]
 * under it. A tap does nothing while [running] says a change is out.
 */
@Composable
internal fun PartyTile(on: Boolean, line: String?, running: PartyRun?, onTap: () -> Unit) {
    val colors = Culvery.colors
    val ink = if (on) colors.accentInk else colors.ink
    val state = when (running) {
        PartyRun.TURNING_ON -> PartyWords.TURNING_ON
        PartyRun.TURNING_OFF -> PartyWords.TURNING_OFF
        null -> if (on) PartyWords.ON else PartyWords.OFF
    }
    Column(Modifier.fillMaxWidth()) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(PartyDimens.gap),
            modifier = Modifier
                .fillMaxWidth()
                .testTag("party_tile")
                .clip(RoundedCornerShape(PartyDimens.radius))
                .background(if (on) colors.accent else colors.surf2)
                .hhClickable(
                    onTap,
                    label = if (on) PartyWords.CLICK_OFF else PartyWords.CLICK_ON,
                    enabled = running == null,
                    spoken = if (running != null) PartyWords.runningSpoken(running) else PartyWords.tileSpoken(on),
                )
                .padding(horizontal = PartyDimens.paddingH, vertical = PartyDimens.paddingV),
        ) {
            HhIcon(Icons.CELEBRATION, size = PartyDimens.icon, filled = true, tint = if (on) ink else colors.accent)
            Column(Modifier.weight(1f).shownNotSpoken()) {
                Text(PartyWords.LABEL, style = HhType.buttonLabel, color = ink, maxLines = 1)
                Text(
                    state,
                    style = PartyDimens.secondStyle,
                    color = if (on) ink else colors.mute,
                    maxLines = 1,
                    modifier = Modifier.testTag("party_tile_state"),
                )
            }
        }
        if (line != null) {
            Text(
                line,
                style = PartyDimens.secondStyle,
                color = colors.danger,
                modifier = Modifier
                    .padding(start = PartyDimens.paddingH, end = PartyDimens.paddingH, top = PartyDimens.lineGap)
                    .testTag("party_tile_line"),
            )
        }
    }
}

/**
 * The tile on Home: a tap asks for a PIN, then opens the sheet that turns Party mode on or off. While Holiday mode is
 * on it asks nothing and says to turn that off first.
 */
@Composable
fun PartyTileHost(actions: PartyActions, house: PartyHouse) {
    val on by house.isOn.collectAsState()
    val houseLine by house.line.collectAsState()
    val refusal by actions.refusal.collectAsState()
    val running by house.running.collectAsState()
    val overlay = LocalOverlayHost.current
    val action = rememberSingleAction(house) { e -> Log.w(TAG, "Party mode tile action failed: ${e::class.simpleName}") }
    PartyTile(on == true, refusal ?: houseLine, running) {
        action.run { actions.toggle { by, turningOn -> overlay.show { PartySheetHost(by, turningOn, actions, house, overlay::dismiss) } } }
    }
}
