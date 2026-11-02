package uk.co.siland.culvery.capability.party.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.paneTitle
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import uk.co.siland.culvery.capability.party.ModeIcon
import uk.co.siland.culvery.capability.party.ModeIcons
import uk.co.siland.culvery.capability.party.ModesWords
import uk.co.siland.culvery.core.plugin.LocalOverlayHost
import uk.co.siland.culvery.core.plugin.NEXT_LABEL
import uk.co.siland.culvery.core.ui.ButtonSurface
import uk.co.siland.culvery.core.ui.ButtonTone
import uk.co.siland.culvery.core.ui.Culvery
import uk.co.siland.culvery.core.ui.HhButton
import uk.co.siland.culvery.core.ui.HhCloseButton
import uk.co.siland.culvery.core.ui.HhIcon
import uk.co.siland.culvery.core.ui.HhSheet
import uk.co.siland.culvery.core.ui.HhTextField
import uk.co.siland.culvery.core.ui.HhType
import uk.co.siland.culvery.core.ui.HouseModeDimens
import uk.co.siland.culvery.core.ui.RadioRowTokens
import uk.co.siland.culvery.core.ui.hhClickable

private val ICON_TILE = 72.dp
private val ICON_SIZE = 32.dp
private val ICON_GAP = 12.dp
private const val ICONS_PER_ROW = 4

/** "Add a mode" in two steps, its name then its icon. [taken] is the other modes' names; [onAdd] gets the trimmed name and the icon's ligature. */
@Composable
fun AddModeSheet(taken: List<String>, onAdd: (name: String, icon: String) -> Unit, onClose: () -> Unit) {
    var name by remember { mutableStateOf("") }
    var icon by remember { mutableStateOf(ModeIcons.GRID.first().name) }
    var naming by remember { mutableStateOf(true) }
    ModeSheet(if (naming) ModesWords.NAME_SHEET_TITLE else ModesWords.ICON_SHEET_TITLE, "mode_add_sheet", onClose) {
        if (naming) {
            NameStep(name, { name = it }, taken, NEXT_LABEL, "mode_name_next") { naming = false }
        } else {
            IconStep(icon) { icon = it }
            Row(horizontalArrangement = Arrangement.spacedBy(PartyDimens.rowGap)) {
                HhButton(ModesWords.BACK, { naming = true }, Modifier.testTag("mode_icon_back"), on = ButtonSurface.Bg)
                HhButton(ModesWords.ADD_MODE, { onAdd(name.trim(), icon) }, Modifier.testTag("mode_add_save"), tone = ButtonTone.Primary, on = ButtonSurface.Bg)
            }
        }
    }
}

/** Renaming a mode; [taken] is the other modes' names. */
@Composable
fun RenameModeSheet(current: String, taken: List<String>, onSave: (String) -> Unit, onClose: () -> Unit) {
    var name by remember { mutableStateOf(current) }
    ModeSheet(ModesWords.RENAME_SHEET_TITLE, "mode_rename_sheet", onClose) {
        NameStep(name, { name = it }, taken, ModesWords.SAVE_NAME, "mode_rename_save") { onSave(name.trim()) }
    }
}

/** Changing a mode's icon; [current] is shown as chosen. */
@Composable
fun ModeIconSheet(current: String, onSave: (String) -> Unit, onClose: () -> Unit) {
    val overlay = LocalOverlayHost.current
    var icon by remember { mutableStateOf(current) }
    ModeSheet(ModesWords.ICON_SHEET_TITLE, "mode_icon_sheet", onClose) {
        IconStep(icon) {
            icon = it
            overlay.markEdited(it != current)
        }
        HhButton(ModesWords.SAVE_ICON, { onSave(icon) }, Modifier.testTag("mode_icon_save"), tone = ButtonTone.Primary, on = ButtonSurface.Bg)
    }
}

@Composable
private fun ModeSheet(title: String, tag: String, onClose: () -> Unit, content: @Composable () -> Unit) {
    HhSheet(
        padding = PaddingValues(horizontal = HouseModeDimens.sheetPaddingH, vertical = HouseModeDimens.sheetPaddingV),
        modifier = Modifier.testTag(tag).semantics { paneTitle = title },
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(title, style = HhType.sectionTitle, color = Culvery.colors.ink, modifier = Modifier.weight(1f).semantics { heading() })
            HhCloseButton(onClose)
        }
        content()
    }
}

/** The name field, capped at [ModesWords.NAME_LIMIT], and the button [action] that goes on; Done on the keyboard does the same. */
@Composable
private fun NameStep(name: String, onName: (String) -> Unit, taken: List<String>, action: String, actionTag: String, onGo: () -> Unit) {
    val overlay = LocalOverlayHost.current
    val problem = ModesWords.nameProblem(name, taken)
    HhTextField(
        value = name,
        onValueChange = {
            onName(it.take(ModesWords.NAME_LIMIT))
            overlay.markEdited(it.isNotBlank())
        },
        placeholder = ModesWords.NAME_EXAMPLE, label = ModesWords.NAME_LABEL, tag = "mode_name",
        error = problem.takeIf { name.isNotEmpty() },
        onDone = { if (problem == null) onGo() },
    )
    HhButton(action, onGo, Modifier.testTag(actionTag), tone = ButtonTone.Primary, enabled = problem == null, on = ButtonSurface.Bg)
}

/** The icons as a grid of radios, four to a row, with [chosen] selected. */
@Composable
private fun IconStep(chosen: String, onChoose: (String) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(ICON_GAP), modifier = Modifier.selectableGroup().testTag("mode_icons")) {
        ModeIcons.GRID.chunked(ICONS_PER_ROW).forEach { row ->
            Row(horizontalArrangement = Arrangement.SpaceBetween, modifier = Modifier.fillMaxWidth()) {
                row.forEach { IconTile(it, it.name == chosen) { onChoose(it.name) } }
            }
        }
    }
}

@Composable
private fun IconTile(icon: ModeIcon, selected: Boolean, onClick: () -> Unit) {
    val c = Culvery.colors
    val shape = RoundedCornerShape(RadioRowTokens.radius)
    Box(
        contentAlignment = Alignment.Center,
        modifier = Modifier
            .testTag("mode_icon_${icon.name}")
            .semantics { this.selected = selected }
            .hhClickable(onClick, role = Role.RadioButton, spoken = icon.spoken)
            .size(ICON_TILE)
            .clip(shape)
            .background(if (selected) c.accentSoft else c.surf2)
            .then(if (selected) Modifier.border(RadioRowTokens.ring, c.accent, shape) else Modifier),
    ) {
        HhIcon(icon.name, size = ICON_SIZE, tint = if (selected) c.accent else c.ink)
    }
}
