package uk.co.siland.culvery.capability.plugs.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
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
import uk.co.siland.culvery.capability.plugs.PlugsWords
import uk.co.siland.culvery.capability.plugs.isOn
import uk.co.siland.culvery.core.ui.ButtonSurface
import uk.co.siland.culvery.core.ui.ButtonTone
import uk.co.siland.culvery.core.ui.Culvery
import uk.co.siland.culvery.core.ui.HhCard
import uk.co.siland.culvery.core.ui.HhIcon
import uk.co.siland.culvery.core.ui.HhRoundButton
import uk.co.siland.culvery.core.ui.HhType
import uk.co.siland.culvery.core.ui.Icons
import uk.co.siland.culvery.core.ui.shownNotSpoken

/**
 * One plug as a small tile (15a §4.5): its name over its state, the lock when it needs a PIN and a round button that
 * switches it (only for a plug that is On or Off; it reads "Switching…" while a switch is out, when taps are ignored).
 * An on plug's tile has the accent fill.
 */
@Composable
internal fun PlugTile(
    state: PlugState,
    needsPin: Boolean,
    switching: Boolean,
    onSwitch: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val c = Culvery.colors
    val tag = "plug_${state.entityId}"
    val on = state.isOn == true
    val trouble = state.power == PlugPower.UNREACHABLE || state.power == PlugPower.GONE
    HhCard(
        modifier = modifier
            .heightIn(min = PlugsDimens.tileMinHeight)
            .testTag(tag)
            .semantics { contentDescription = PlugsWords.plugSpoken(state) },
        radius = PlugsDimens.tileRadius,
        color = if (on) c.accentSoft else c.surf,
        padding = PaddingValues(horizontal = PlugsDimens.tilePaddingH, vertical = PlugsDimens.tilePaddingV),
    ) {
        Row(Modifier.height(IntrinsicSize.Min), horizontalArrangement = Arrangement.spacedBy(PlugsDimens.textGap)) {
            Column(Modifier.weight(1f).shownNotSpoken(), verticalArrangement = Arrangement.spacedBy(PlugsDimens.textGap)) {
                Text(
                    state.name, style = HhType.cardTitle.copy(fontSize = PlugsDimens.nameSize), color = c.ink,
                    maxLines = 2, overflow = TextOverflow.Ellipsis, modifier = Modifier.testTag("${tag}_name"),
                )
                Text(
                    PlugsWords.plugStatus(state), style = HhType.body.copy(fontSize = PlugsDimens.statusSize),
                    color = if (trouble) c.danger else c.mute, maxLines = 3, overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.testTag("${tag}_status"),
                )
            }
            if (state.isOn != null) {
                Column(
                    Modifier.fillMaxHeight(), horizontalAlignment = Alignment.End,
                    verticalArrangement = Arrangement.Bottom,
                ) {
                    if (needsPin) HhIcon(Icons.LOCK, size = PlugsDimens.lock, tint = c.mute, modifier = Modifier.testTag("${tag}_lock"))
                    HhRoundButton(
                        if (switching) Icons.SCHEDULE else Icons.POWER_SETTINGS_NEW,
                        if (switching) PlugsWords.SWITCHING else PlugsWords.switchSpoken(state, needsPin),
                        { if (!switching) onSwitch() }, Modifier.testTag("${tag}_switch"),
                        tone = if (on) ButtonTone.Primary else ButtonTone.Quiet, on = ButtonSurface.Surf,
                    )
                }
            }
        }
    }
}
