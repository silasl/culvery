package uk.co.siland.culvery.capability.plugs.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.paneTitle
import androidx.compose.ui.semantics.semantics
import uk.co.siland.culvery.capability.plugs.PlugCandidate
import uk.co.siland.culvery.capability.plugs.PlugCandidates
import uk.co.siland.culvery.capability.plugs.PlugsWords
import uk.co.siland.culvery.core.plugin.CHECKING
import uk.co.siland.culvery.core.ui.Culvery
import uk.co.siland.culvery.core.ui.HhCloseButton
import uk.co.siland.culvery.core.ui.HhListRow
import uk.co.siland.culvery.core.ui.HhSheet
import uk.co.siland.culvery.core.ui.HhType

/**
 * Add a plug (12 §4.3): the switches Home Assistant has that aren't [added] yet, by name. [candidates] is null while
 * they are being read. Picking one adds it and closes the sheet.
 */
@Composable
internal fun AddPlugSheet(candidates: PlugCandidates?, added: List<String>, onPick: (PlugCandidate) -> Unit, onClose: () -> Unit) {
    val c = Culvery.colors
    HhSheet(
        padding = PaddingValues(horizontal = PlugsDimens.sheetPaddingH, vertical = PlugsDimens.sheetPaddingV),
        modifier = Modifier.testTag("add_plug_sheet").semantics { paneTitle = PlugsWords.ADD_A_PLUG },
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(PlugsWords.ADD_A_PLUG, style = HhType.sectionTitle, color = c.ink, modifier = Modifier.semantics { heading() })
            }
            HhCloseButton(onClose)
        }
        when (candidates) {
            null -> Text(CHECKING, style = HhType.body, color = c.mute)
            is PlugCandidates.Failed -> Text(candidates.line, style = HhType.body, color = c.danger, modifier = Modifier.testTag("add_plug_line"))
            is PlugCandidates.Found -> {
                val free = candidates.all.filter { it.entityId !in added }
                if (free.isEmpty()) {
                    Text(PlugsWords.NOTHING_TO_ADD, style = HhType.body, color = c.mute, modifier = Modifier.testTag("add_plug_none"))
                } else {
                    Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(PlugsDimens.sheetRowGap)) {
                        free.forEachIndexed { i, candidate ->
                            HhListRow(candidate.name, Modifier.testTag("add_plug_$i"), onClick = { onPick(candidate); onClose() })
                        }
                    }
                }
            }
        }
    }
}
