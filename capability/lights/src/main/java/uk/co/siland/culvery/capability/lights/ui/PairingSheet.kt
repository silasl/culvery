package uk.co.siland.culvery.capability.lights.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.paneTitle
import androidx.compose.ui.semantics.semantics
import uk.co.siland.culvery.capability.lights.NOT_PRESSED_IN_TIME
import uk.co.siland.culvery.capability.lights.PRESS_THE_BUTTON
import uk.co.siland.culvery.capability.lights.TRY_AGAIN
import uk.co.siland.culvery.capability.lights.WAITING_FOR_BUTTON
import uk.co.siland.culvery.core.ui.ButtonSurface
import uk.co.siland.culvery.core.ui.ButtonTone
import uk.co.siland.culvery.core.ui.Culvery
import uk.co.siland.culvery.core.ui.HhButton
import uk.co.siland.culvery.core.ui.HhCloseButton
import uk.co.siland.culvery.core.ui.HhMessageLine
import uk.co.siland.culvery.core.ui.HhSheet
import uk.co.siland.culvery.core.ui.HhType

enum class PairingState { WAITING, NOT_IN_TIME }

/** Pairing with the bridge found (7 §4.1): waits for its button, then offers another go once the time is up. */
@Composable
internal fun PairingSheet(state: PairingState, onTryAgain: () -> Unit, onClose: () -> Unit) {
    val c = Culvery.colors
    HhSheet(
        padding = PaddingValues(horizontal = LightsDimens.sheetPaddingH, vertical = LightsDimens.sheetPaddingV),
        modifier = Modifier.testTag("lights_pairing_sheet").semantics { paneTitle = PRESS_THE_BUTTON },
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(PRESS_THE_BUTTON, style = HhType.sectionTitle, color = c.ink, modifier = Modifier.semantics { heading() })
            }
            HhCloseButton(onClose)
        }
        when (state) {
            PairingState.WAITING -> HhMessageLine(WAITING_FOR_BUTTON, HhType.body, c.mute, "lights_pairing_line")
            PairingState.NOT_IN_TIME -> {
                HhMessageLine(NOT_PRESSED_IN_TIME, HhType.body, c.danger, "lights_pairing_line")
                HhButton(TRY_AGAIN, onTryAgain, Modifier.testTag("lights_pairing_again"), tone = ButtonTone.Primary, on = ButtonSurface.Bg)
            }
        }
    }
}
