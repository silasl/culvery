package uk.co.siland.culvery.capability.plugs.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.key
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextOverflow
import uk.co.siland.culvery.capability.plugs.ModeChoice
import uk.co.siland.culvery.capability.plugs.PlugPrefs
import uk.co.siland.culvery.capability.plugs.PlugState
import uk.co.siland.culvery.capability.plugs.PlugsWords
import uk.co.siland.culvery.core.house.HouseModeInfo
import uk.co.siland.culvery.core.ui.Culvery
import uk.co.siland.culvery.core.ui.HhChoiceChip
import uk.co.siland.culvery.core.ui.HhSectionLabel
import uk.co.siland.culvery.core.ui.HhType

private fun word(choice: ModeChoice) = when (choice) {
    ModeChoice.LEAVE -> PlugsWords.LEAVE
    ModeChoice.ON -> PlugsWords.ON
    ModeChoice.OFF -> PlugsWords.OFF
}

private fun tagWord(choice: ModeChoice) = choice.name.lowercase()

/** Each mode's section, in the order of [HouseModeInfo.order]. */
@Composable
internal fun ModeSections(
    modes: List<HouseModeInfo>,
    plugs: List<PlugState>,
    prefs: PlugPrefs,
    editing: Boolean,
    onChoice: (modeId: String, entityId: String, ModeChoice) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier, verticalArrangement = Arrangement.spacedBy(PlugsDimens.sectionGap)) {
        modes.sortedBy { it.order }.forEach { mode -> key(mode.id) { ModeSection(mode, plugs, prefs, editing, onChoice) } }
    }
}

/**
 * One mode's list (12 §2.4): each plug with the word it has for this mode, which Edit turns into Leave, On and Off
 * chips. The chips are spoken as the row ("Holiday mode, Lamp, on"), the chosen one by its radio state.
 */
@Composable
internal fun ModeSection(
    mode: HouseModeInfo,
    plugs: List<PlugState>,
    prefs: PlugPrefs,
    editing: Boolean,
    onChoice: (modeId: String, entityId: String, ModeChoice) -> Unit,
) {
    val c = Culvery.colors
    Column(Modifier.fillMaxWidth().testTag("mode_${mode.id}")) {
        HhSectionLabel(mode.name, Modifier.padding(bottom = PlugsDimens.labelGap))
        plugs.forEachIndexed { i, plug ->
            key(plug.entityId) {
                val chosen = prefs.choice(mode.id, plug.entityId)
                val tag = "mode_${mode.id}_$i"
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(PlugsDimens.chipGap),
                    modifier = Modifier.fillMaxWidth().heightIn(min = PlugsDimens.rowHeight).testTag(tag),
                ) {
                    Text(plug.name, style = HhType.body, color = c.ink, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
                    if (editing) {
                        Row(horizontalArrangement = Arrangement.spacedBy(PlugsDimens.chipGap), modifier = Modifier.selectableGroup()) {
                            ModeChoice.entries.forEach { choice ->
                                HhChoiceChip(
                                    word(choice), selected = choice == chosen, tag = "${tag}_${tagWord(choice)}",
                                    onClick = { onChoice(mode.id, plug.entityId, choice) }, role = Role.RadioButton,
                                    spokenName = PlugsWords.modeRowSpoken(mode.name, plug.name, choice), unselectedFill = c.surf2,
                                )
                            }
                        }
                    } else {
                        Text(word(chosen), style = HhType.body, color = c.mute, maxLines = 1)
                    }
                }
            }
        }
    }
}
