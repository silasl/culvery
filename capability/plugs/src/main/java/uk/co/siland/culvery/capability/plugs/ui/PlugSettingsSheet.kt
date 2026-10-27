package uk.co.siland.culvery.capability.plugs.ui

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
import uk.co.siland.culvery.capability.plugs.PlugState
import uk.co.siland.culvery.capability.plugs.PlugsWords
import uk.co.siland.culvery.core.ui.ButtonSize
import uk.co.siland.culvery.core.ui.ButtonSurface
import uk.co.siland.culvery.core.ui.ButtonTone
import uk.co.siland.culvery.core.ui.Culvery
import uk.co.siland.culvery.core.ui.HhButton
import uk.co.siland.culvery.core.ui.HhCloseButton
import uk.co.siland.culvery.core.ui.HhSheet
import uk.co.siland.culvery.core.ui.HhSwitchRow
import uk.co.siland.culvery.core.ui.HhType

/** One plug's settings (12 §4.3): whether switching it needs a PIN, and Remove, which takes effect at once and closes the sheet. */
@Composable
internal fun PlugSettingsSheet(plug: PlugState, needsPin: Boolean, onNeedsPin: (Boolean) -> Unit, onRemove: () -> Unit, onClose: () -> Unit) {
    val c = Culvery.colors
    HhSheet(
        padding = PaddingValues(horizontal = PlugsDimens.sheetPaddingH, vertical = PlugsDimens.sheetPaddingV),
        modifier = Modifier.testTag("plug_settings_sheet").semantics { paneTitle = plug.name },
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(plug.name, style = HhType.sectionTitle, color = c.ink, modifier = Modifier.semantics { heading() })
            }
            HhCloseButton(onClose)
        }
        HhSwitchRow(PlugsWords.NEEDS_A_PIN, null, needsPin, "plug_settings_pin", onNeedsPin)
        HhButton(
            PlugsWords.REMOVE, { onRemove(); onClose() }, Modifier.testTag("plug_settings_remove"),
            tone = ButtonTone.Danger, size = ButtonSize.Large, on = ButtonSurface.Bg,
        )
    }
}
