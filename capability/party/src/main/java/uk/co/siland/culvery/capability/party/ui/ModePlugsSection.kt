package uk.co.siland.culvery.capability.party.ui

import android.util.Log
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import uk.co.siland.culvery.capability.party.ModesWords
import uk.co.siland.culvery.core.house.AddedPlug
import uk.co.siland.culvery.core.house.HousePlugs
import uk.co.siland.culvery.core.house.PlugOption
import uk.co.siland.culvery.core.plugin.LocalOverlayHost
import uk.co.siland.culvery.core.plugin.Toaster
import uk.co.siland.culvery.core.plugin.attempt
import uk.co.siland.culvery.core.plugin.saving
import uk.co.siland.culvery.core.ui.ButtonSurface
import uk.co.siland.culvery.core.ui.Culvery
import uk.co.siland.culvery.core.ui.HhAddRow
import uk.co.siland.culvery.core.ui.HhChoiceChip
import uk.co.siland.culvery.core.ui.HhIcon
import uk.co.siland.culvery.core.ui.HhListRow
import uk.co.siland.culvery.core.ui.HhRoundButton
import uk.co.siland.culvery.core.ui.HhSectionLabel
import uk.co.siland.culvery.core.ui.Icons

private const val TAG = "ModePlugsSection"

/** The plugs [modeId] switches, logged under [tag] and read as none when the read fails. */
internal fun HousePlugs.addedOrNone(modeId: String, tag: String): Flow<List<AddedPlug>> = added(modeId).catch { e ->
    Log.w(tag, "Couldn't read a mode's plugs (${e::class.simpleName})")
    emit(emptyList())
}

/**
 * The plugs [modeId] switches (15b §4.2), [added] as read: each added plug with an On or Off chip and a padlock when it needs a PIN, then
 * "Add a plug" for the Lights tab's plugs not added. Every plug not listed is left alone. A change is written through
 * [plugs] at once, and a write that fails says so through [toaster]; [onLockedAdded] runs after a plug that needs a PIN
 * was added, so the mode can hold the PIN too.
 */
@Composable
internal fun ModePlugsSection(modeId: String, plugs: HousePlugs, added: List<AddedPlug>, onLockedAdded: () -> Unit, toaster: Toaster) {
    val overlay = LocalOverlayHost.current
    val change = rememberCoroutineScope().saving(toaster, logSave)
    val options by produceState(emptyList<PlugOption>(), plugs) {
        value = attempt({ plugs.options() }) { e ->
            Log.w(TAG, "Couldn't read the plugs (${e::class.simpleName})")
            emptyList()
        }
    }

    fun choose(plugId: String, on: Boolean?, afterwards: () -> Unit = {}) = change {
        plugs.choose(modeId, plugId, on)
        afterwards()
    }
    HhSectionLabel(ModesWords.PLUGS_SECTION)
    added.forEachIndexed { i, plug ->
        val name = options.firstOrNull { it.id == plug.id }?.name ?: ModesWords.MISSING_PLUG
        HhListRow(
            name, Modifier.testTag("plug_row_$i"),
            leading = if (plug.needsPin) {
                { HhIcon(Icons.LOCK, tint = Culvery.colors.mute, modifier = Modifier.testTag("plug_lock_$i")) }
            } else {
                null
            },
            trailing = {
                HhChoiceChip(
                    if (plug.on) ModesWords.LIGHT_ON else ModesWords.LIGHT_OFF, selected = false, tag = "plug_chip_$i",
                    onClick = { choose(plug.id, !plug.on) },
                    role = Role.Button, clickLabel = ModesWords.switchPlug(name, plug.on),
                    spokenName = ModesWords.plugSpoken(name, plug.on),
                )
                HhRoundButton(
                    Icons.CLOSE, ModesWords.removePlug(name), { choose(plug.id, null) },
                    Modifier.testTag("plug_remove_$i"), on = ButtonSurface.Surf,
                )
            },
        )
    }
    val unused = options.filter { option -> added.none { it.id == option.id } }
    if (unused.isNotEmpty()) {
        HhAddRow(ModesWords.ADD_PLUG, "plug_add", {
            overlay.show {
                PickerSheet(
                    ModesWords.ADD_PLUG_TITLE, "plug_add_sheet", "plug_option", unused.map { it.name },
                    onPick = { i ->
                        choose(unused[i].id, true) { if (unused[i].needsPin) onLockedAdded() }
                        overlay.dismiss()
                    },
                    onClose = overlay::dismiss,
                )
            }
        })
    }
}
