package uk.co.siland.culvery.capability.plugs.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import uk.co.siland.culvery.capability.plugs.PlugPower
import uk.co.siland.culvery.capability.plugs.PlugState
import uk.co.siland.culvery.capability.plugs.isOn
import uk.co.siland.culvery.capability.plugs.PlugsWords
import uk.co.siland.culvery.core.ui.ButtonSize
import uk.co.siland.culvery.core.ui.ButtonSurface
import uk.co.siland.culvery.core.ui.ButtonTone
import uk.co.siland.culvery.core.ui.Culvery
import uk.co.siland.culvery.core.ui.HhButton
import uk.co.siland.culvery.core.ui.HhCard
import uk.co.siland.culvery.core.ui.HhIcon
import uk.co.siland.culvery.core.ui.HhRoundButton
import uk.co.siland.culvery.core.ui.HhType
import uk.co.siland.culvery.core.ui.Icons
import uk.co.siland.culvery.core.ui.shownNotSpoken

/**
 * One plug (12 §4.3): its name and state, the › to its settings, the lock when it needs a PIN, and the button that
 * switches it. The button is inactive for a plug that can't be reached, is gone or hasn't been read, and reads
 * "Switching…" while a switch is out, when taps are ignored. An on card has the accent fill.
 */
@Composable
internal fun PlugCard(
    plug: PlugState,
    index: Int,
    needsPin: Boolean,
    switching: Boolean,
    onSwitch: () -> Unit,
    onSettings: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val c = Culvery.colors
    val tag = "plug_card_$index"
    val on = plug.isOn == true
    val switchable = plug.isOn != null
    val trouble = plug.power == PlugPower.UNREACHABLE || plug.power == PlugPower.GONE
    HhCard(
        modifier = modifier
            .fillMaxWidth()
            .height(PlugsDimens.cardHeight)
            .testTag(tag)
            .semantics { contentDescription = PlugsWords.plugSpoken(plug) },
        radius = PlugsDimens.cardRadius,
        color = if (on) c.accentSoft else c.surf,
        padding = PaddingValues(horizontal = PlugsDimens.cardPaddingH, vertical = PlugsDimens.cardPaddingV),
    ) {
        Column(Modifier.weight(1f).fillMaxWidth().shownNotSpoken(), verticalArrangement = Arrangement.spacedBy(PlugsDimens.textGap)) {
            Text(
                plug.name, style = HhType.cardTitle.copy(fontSize = PlugsDimens.nameSize), color = c.ink,
                maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.testTag("${tag}_name"),
            )
            Text(
                PlugsWords.plugStatus(plug), style = HhType.body.copy(fontSize = PlugsDimens.statusSize),
                color = if (trouble) c.danger else c.mute, maxLines = 3, overflow = TextOverflow.Ellipsis,
                modifier = Modifier.testTag("${tag}_status"),
            )
        }
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(PlugsDimens.footGap)) {
            HhRoundButton(
                Icons.CHEVRON_RIGHT, PlugsWords.settingsFor(plug.name), onSettings, Modifier.testTag("${tag}_settings"),
                on = ButtonSurface.Surf, size = PlugsDimens.chevron,
            )
            Spacer(Modifier.weight(1f))
            if (needsPin) HhIcon(Icons.LOCK, size = PlugsDimens.lock, tint = c.mute, modifier = Modifier.testTag("${tag}_lock"))
            val label = if (switching) PlugsWords.SWITCHING else if (on) PlugsWords.SWITCH_OFF else PlugsWords.SWITCH_ON
            HhButton(
                label, { if (!switching) onSwitch() }, Modifier.testTag("${tag}_switch"),
                tone = if (on) ButtonTone.Primary else ButtonTone.Quiet, size = ButtonSize.Large, enabled = switchable, on = ButtonSurface.Surf,
                spokenName = if (switching) label else PlugsWords.switchSpoken(plug, needsPin),
            )
        }
    }
}
