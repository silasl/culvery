package uk.co.siland.culvery.capability.plugs.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.key
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import uk.co.siland.culvery.capability.plugs.ModeChoice
import uk.co.siland.culvery.capability.plugs.PlugPower
import uk.co.siland.culvery.capability.plugs.PlugState
import uk.co.siland.culvery.capability.plugs.PlugsWords
import uk.co.siland.culvery.capability.plugs.isOn
import uk.co.siland.culvery.core.house.HouseModeInfo
import uk.co.siland.culvery.core.ui.ButtonSize
import uk.co.siland.culvery.core.ui.ButtonSurface
import uk.co.siland.culvery.core.ui.ButtonTone
import uk.co.siland.culvery.core.ui.Culvery
import uk.co.siland.culvery.core.ui.HhButton
import uk.co.siland.culvery.core.ui.HhCard
import uk.co.siland.culvery.core.ui.HhChoiceChip
import uk.co.siland.culvery.core.ui.HhIcon
import uk.co.siland.culvery.core.ui.HhType
import uk.co.siland.culvery.core.ui.Icons
import uk.co.siland.culvery.core.ui.shownNotSpoken

private fun word(choice: ModeChoice) = when (choice) {
    ModeChoice.IGNORE -> PlugsWords.IGNORE
    ModeChoice.ON -> PlugsWords.ON
    ModeChoice.OFF -> PlugsWords.OFF
}

/**
 * One plug (12 §4.3): its name over its state, the lock when it needs a PIN and the button that switches it (only for
 * a plug that is On or Off; it reads "Switching…" while a switch is out, when taps are ignored), then a cell per mode in
 * [modes] holding that mode's word from [choices] (Ignore when missing), which Edit turns into Ignore, On and Off chips.
 * An on plug's row has the accent fill.
 */
@Composable
internal fun PlugRow(
    state: PlugState,
    needsPin: Boolean,
    switching: Boolean,
    modes: List<HouseModeInfo>,
    choices: Map<String, ModeChoice>,
    editing: Boolean,
    onSwitch: () -> Unit,
    onChoose: (modeId: String, ModeChoice) -> Unit,
    modifier: Modifier = Modifier,
) {
    val c = Culvery.colors
    val tag = "plug_${state.entityId}"
    val on = state.isOn == true
    val trouble = state.power == PlugPower.UNREACHABLE || state.power == PlugPower.GONE
    HhCard(
        modifier = modifier
            .fillMaxWidth()
            .heightIn(min = PlugsDimens.rowMinHeight)
            .testTag(tag)
            .semantics { contentDescription = PlugsWords.plugSpoken(state) },
        radius = PlugsDimens.rowRadius,
        color = if (on) c.accentSoft else c.surf,
        padding = PaddingValues(horizontal = PlugsDimens.rowPaddingH, vertical = PlugsDimens.rowPaddingV),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(PlugsDimens.columnGap)) {
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
            Row(
                Modifier.width(PlugsDimens.switchSlot), verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(PlugsDimens.footGap, Alignment.End),
            ) {
                if (state.isOn != null) {
                    if (needsPin) HhIcon(Icons.LOCK, size = PlugsDimens.lock, tint = c.mute, modifier = Modifier.testTag("${tag}_lock"))
                    val label = if (switching) PlugsWords.SWITCHING else if (on) PlugsWords.SWITCH_OFF else PlugsWords.SWITCH_ON
                    HhButton(
                        label, { if (!switching) onSwitch() }, Modifier.testTag("${tag}_switch"),
                        tone = if (on) ButtonTone.Primary else ButtonTone.Quiet, size = ButtonSize.Large, on = ButtonSurface.Surf,
                        spokenName = if (switching) label else PlugsWords.switchSpoken(state, needsPin),
                    )
                }
            }
            modes.forEach { mode ->
                key(mode.id) { ModeCell(mode, state, choices[mode.id] ?: ModeChoice.IGNORE, editing, "mode_${mode.id}_${state.entityId}", onChoose) }
            }
        }
    }
}

/** At rest the word, read as "Holiday mode, ignore"; in Edit three radio chips, each read as "Holiday mode, Lamp, on". */
@Composable
private fun ModeCell(
    mode: HouseModeInfo,
    state: PlugState,
    chosen: ModeChoice,
    editing: Boolean,
    tag: String,
    onChoose: (modeId: String, ModeChoice) -> Unit,
) {
    val c = Culvery.colors
    Box(Modifier.width(PlugsDimens.modeCell), contentAlignment = Alignment.Center) {
        if (editing) {
            Row(horizontalArrangement = Arrangement.spacedBy(PlugsDimens.chipGap), modifier = Modifier.selectableGroup()) {
                ModeChoice.entries.forEach { choice ->
                    HhChoiceChip(
                        word(choice), selected = choice == chosen, tag = "${tag}_${choice.name.lowercase()}",
                        onClick = { onChoose(mode.id, choice) }, role = Role.RadioButton,
                        spokenName = PlugsWords.modeRowSpoken(mode.name, state.name, choice), unselectedFill = c.surf2,
                    )
                }
            }
        } else {
            Text(
                word(chosen), style = HhType.body, color = c.mute, maxLines = 1, textAlign = TextAlign.Center,
                modifier = Modifier.testTag(tag).semantics { contentDescription = PlugsWords.modeCellSpoken(mode.name, chosen) },
            )
        }
    }
}
