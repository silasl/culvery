package uk.co.siland.culvery.capability.party.ui

import android.util.Log
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.paneTitle
import androidx.compose.ui.semantics.semantics
import uk.co.siland.culvery.capability.party.ModesWords
import uk.co.siland.culvery.capability.party.StoredMode
import uk.co.siland.culvery.core.house.HouseLights
import uk.co.siland.culvery.core.house.HouseLines
import uk.co.siland.culvery.core.house.LightChoice
import uk.co.siland.culvery.core.house.LightOptions
import uk.co.siland.culvery.core.house.LightRoomOption
import uk.co.siland.culvery.core.house.RoomChoice
import uk.co.siland.culvery.core.plugin.LocalOverlayHost
import uk.co.siland.culvery.core.plugin.attempt
import uk.co.siland.culvery.core.ui.ButtonSurface
import uk.co.siland.culvery.core.ui.Culvery
import uk.co.siland.culvery.core.ui.HhAddRow
import uk.co.siland.culvery.core.ui.HhChoiceChip
import uk.co.siland.culvery.core.ui.HhCloseButton
import uk.co.siland.culvery.core.ui.HhListRow
import uk.co.siland.culvery.core.ui.HhRoundButton
import uk.co.siland.culvery.core.ui.HhSectionLabel
import uk.co.siland.culvery.core.ui.HhSheet
import uk.co.siland.culvery.core.ui.HhType
import uk.co.siland.culvery.core.ui.HouseModeDimens
import uk.co.siland.culvery.core.ui.Icons

private const val TAG = "ModeLightsSection"

/**
 * The rooms a mode sets (15b §4.2): each added room with its choice, a scene, On at a percent or Off, then "Add a light"
 * for the rooms not added. Every room not listed is left alone. Reads what Hue offers once as the section opens; [save]
 * changes the mode and writes it at once.
 */
@Composable
internal fun ModeLightsSection(mode: StoredMode, lights: HouseLights?, save: ((StoredMode) -> StoredMode) -> Unit) {
    val options = rememberHouseOptions(lights, { it.ready }) { l ->
        attempt({ l.lightOptions() }) { e ->
            Log.w(TAG, "Couldn't read what the lights offer (${e::class.simpleName})")
            LightOptions(emptyList(), HouseLines.HUE_REFUSED)
        }
    }
    HhSectionLabel(ModesWords.LIGHTS_SECTION)
    val offered = options.offered
    if (offered == null) {
        if (options.notSetUp) Line(ModesWords.SET_UP_HUE, "mode_lights_line")
        return
    }
    offered.line?.let { Line(it, "mode_lights_line", problem = true) }
    val overlay = LocalOverlayHost.current
    // A room the bridge no longer has can still be set to On or Off, or taken out.
    fun roomOf(choice: RoomChoice) = offered.rooms.firstOrNull { it.id == choice.roomId } ?: LightRoomOption(choice.roomId, choice.roomName, emptyList())

    fun saveChoice(room: LightRoomOption, choice: LightChoice) {
        save { it.withLight(RoomChoice(room.id, room.name, choice)) }
        overlay.dismiss()
    }
    mode.lights.forEachIndexed { i, saved ->
        val room = roomOf(saved)
        HhListRow(
            saved.roomName, Modifier.testTag("light_row_$i"),
            trailing = {
                HhChoiceChip(
                    lightChip(saved.choice, room.dimmable), selected = false, tag = "light_chip_$i",
                    onClick = { overlay.show { LightChoiceSheet(room, saved.choice, { saveChoice(room, it) }, overlay::dismiss) } },
                    role = Role.Button, clickLabel = ModesWords.changeLight(saved.roomName),
                    spokenName = "${saved.roomName}, ${lightChip(saved.choice, room.dimmable)}",
                )
                HhRoundButton(
                    Icons.CLOSE, ModesWords.removeLight(saved.roomName), { save { it.withoutLight(saved.roomId) } },
                    Modifier.testTag("light_remove_$i"), on = ButtonSurface.Surf,
                )
            },
        )
    }
    val unused = offered.rooms.filter { room -> mode.lights.none { it.roomId == room.id } }
    if (unused.isNotEmpty()) {
        HhAddRow(ModesWords.ADD_LIGHT, "light_add", {
            overlay.show {
                PickerSheet(
                    ModesWords.ADD_LIGHT_TITLE, "light_add_sheet", "light_room", unused.map { it.name },
                    onPick = { i -> overlay.show { LightChoiceSheet(unused[i], null, { saveChoice(unused[i], it) }, overlay::dismiss) } },
                    onClose = overlay::dismiss,
                )
            }
        })
    }
}

/** [names] as rows tagged `{rowTag}_{i}` in a sheet; a tap sends the row's index. */
@Composable
internal fun PickerSheet(title: String, tag: String, rowTag: String, names: List<String>, onPick: (Int) -> Unit, onClose: () -> Unit) {
    HhSheet(
        padding = PaddingValues(horizontal = HouseModeDimens.sheetPaddingH, vertical = HouseModeDimens.sheetPaddingV),
        modifier = Modifier.testTag(tag).semantics { paneTitle = title },
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(title, style = HhType.sectionTitle, color = Culvery.colors.ink, modifier = Modifier.weight(1f).semantics { heading() })
            HhCloseButton(onClose)
        }
        LazyColumn(verticalArrangement = Arrangement.spacedBy(PartyDimens.rowGap), modifier = Modifier.weight(1f)) {
            itemsIndexed(names) { i, name -> HhListRow(name, Modifier.testTag("${rowTag}_$i"), onClick = { onPick(i) }) }
        }
    }
}
