package uk.co.siland.culvery.capability.party.ui

import android.util.Log
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.paneTitle
import androidx.compose.ui.semantics.semantics
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.withContext
import uk.co.siland.culvery.capability.party.PartyActions
import uk.co.siland.culvery.capability.party.PartyHouse
import uk.co.siland.culvery.capability.party.PartyWords
import uk.co.siland.culvery.core.access.Authorised
import uk.co.siland.culvery.core.plugin.attempt
import uk.co.siland.culvery.core.ui.ButtonSize
import uk.co.siland.culvery.core.ui.ButtonSurface
import uk.co.siland.culvery.core.ui.ButtonTone
import uk.co.siland.culvery.core.ui.Culvery
import uk.co.siland.culvery.core.ui.HhButton
import uk.co.siland.culvery.core.ui.HhFooterButton
import uk.co.siland.culvery.core.ui.HhSheet
import uk.co.siland.culvery.core.ui.HhType
import uk.co.siland.culvery.core.ui.ShellTokens
import uk.co.siland.culvery.core.ui.rememberSingleAction

private const val TAG = "PartySheet"

/** The sheet a tap opens (13 §2.6): what Party mode will do when [turningOn], else what ends with it, as [lines]. */
@Composable
internal fun PartySheet(turningOn: Boolean, lines: List<String>, onConfirm: () -> Unit, onCancel: () -> Unit) {
    val c = Culvery.colors
    val title = if (turningOn) PartyWords.WILL else PartyWords.WHEN_ENDS
    HhSheet(
        padding = PaddingValues(horizontal = PartyDimens.sheetPaddingH, vertical = PartyDimens.sheetPaddingV),
        modifier = Modifier.testTag("party_sheet").semantics { paneTitle = title },
    ) {
        Column(
            verticalArrangement = Arrangement.spacedBy(ShellTokens.sheetGap),
            modifier = Modifier.weight(1f).verticalScroll(rememberScrollState()),
        ) {
            Text(title, style = HhType.label, color = c.mute)
            Column(verticalArrangement = Arrangement.spacedBy(PartyDimens.stepGap), modifier = Modifier.testTag("party_sheet_lines")) {
                lines.forEach { Text(it, style = HhType.body, color = c.ink) }
            }
        }
        Column(verticalArrangement = Arrangement.spacedBy(PartyDimens.buttonGap)) {
            HhFooterButton(
                if (turningOn) PartyWords.TURN_ON_BUTTON else PartyWords.TURN_OFF_BUTTON,
                onConfirm, "party_sheet_confirm",
            )
            HhButton(
                PartyWords.CANCEL, onCancel, Modifier.fillMaxWidth().testTag("party_sheet_cancel"),
                tone = ButtonTone.Quiet, size = ButtonSize.Large, on = ButtonSurface.Bg,
            )
        }
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
