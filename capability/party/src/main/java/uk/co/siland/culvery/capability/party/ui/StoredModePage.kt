package uk.co.siland.culvery.capability.party.ui

import android.util.Log
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import uk.co.siland.culvery.capability.party.ModesPrefs
import uk.co.siland.culvery.capability.party.ModesStore
import uk.co.siland.culvery.capability.party.ModesWords
import uk.co.siland.culvery.capability.party.StoredMode
import uk.co.siland.culvery.capability.party.StoredModeActions
import uk.co.siland.culvery.capability.party.StoredModesHouse
import uk.co.siland.culvery.core.house.HouseLights
import uk.co.siland.culvery.core.house.HouseLines
import uk.co.siland.culvery.core.house.HouseMusic
import uk.co.siland.culvery.core.house.HousePlugs
import uk.co.siland.culvery.core.plugin.KEEP
import uk.co.siland.culvery.core.plugin.Toaster
import uk.co.siland.culvery.core.plugin.save
import uk.co.siland.culvery.core.plugin.saving
import uk.co.siland.culvery.core.plugin.turnNowLabel
import uk.co.siland.culvery.core.ui.ButtonSurface
import uk.co.siland.culvery.core.ui.ButtonTone
import uk.co.siland.culvery.core.ui.HhButton
import uk.co.siland.culvery.core.ui.HhConfirmCard
import uk.co.siland.culvery.core.ui.HhStatusLine
import uk.co.siland.culvery.core.ui.HhSwitchRow
import uk.co.siland.culvery.core.ui.HouseModeTurnButton
import uk.co.siland.culvery.core.ui.rememberSingleAction

private const val TAG = "StoredModePage"

/** What a stored mode's page acts on besides the store: turning it on and off, its place on Home, and the music and lights systems. */
internal class ModeControls(
    val actions: StoredModeActions,
    val house: StoredModesHouse,
    val place: (String) -> Flow<Int?>,
    val music: HouseMusic?,
    val lights: HouseLights?,
)

/**
 * A stored mode's page below its header (15b §4.2): Turn on now, Show on Home and Needs a PIN, then music, lights and
 * plugs, then Delete mode. [room] is how many modes Home has room for, null until it is read. Each change is written at once.
 */
@Composable
internal fun StoredModePage(
    mode: StoredMode,
    prefs: ModesPrefs,
    room: Int?,
    controls: ModeControls,
    plugs: HousePlugs?,
    store: ModesStore,
    toaster: Toaster,
) {
    val change = rememberCoroutineScope().saving(toaster, logSave)
    val house = controls.house
    val onId by house.onMode.collectAsStateWithLifecycle()
    val running by house.running.collectAsStateWithLifecycle()
    val read by house.isOn.collectAsStateWithLifecycle()
    val on = onId == mode.id
    val place by remember(mode.id) { controls.place(mode.id) }.collectAsStateWithLifecycle(null)
    val refusal by remember(mode.id) { controls.actions.refusal(mode.id) }.collectAsStateWithLifecycle(null)
    val added by remember(plugs, mode.id) { plugs?.addedOrNone(mode.id, TAG) ?: flowOf(emptyList()) }.collectAsStateWithLifecycle(emptyList())
    val locked = added.any { it.needsPin }
    val turn = rememberSingleAction(house) { e -> Log.w(TAG, "A stored mode's page action failed: ${e::class.simpleName}") }

    fun save(transform: (StoredMode) -> StoredMode) = change { store.update { it.withMode(mode.id, transform) } }

    HouseModeTurnButton(
        turnNowLabel(on), { turn.run { controls.actions.tap(mode.id, sawOn = on) } },
        read = read != null, running = running != null, line = refusal,
    )
    if (on) Line(ModesWords.appliesNext(mode.name), "mode_applies_line")

    ModeSwitches(
        home = { ShowOnHomeSwitch(mode.showOnHome, place, room, prefs) { save { m -> m.copy(showOnHome = it) } } },
        pin = {
            HhSwitchRow(
                ModesWords.NEEDS_A_PIN, line = ModesWords.PLUG_NEEDS_PIN.takeIf { locked },
                checked = mode.needsPin || locked, tag = "mode_pin", enabled = !locked,
                onChange = { save { m -> m.copy(needsPin = it) } },
            )
        },
    )

    ModeMusicSection(mode, controls.music, ::save)
    ModeLightsSection(mode, controls.lights, ::save)
    if (plugs != null) ModePlugsSection(mode.id, plugs, added, onLockedAdded = { save { it.copy(needsPin = true) } }, toaster = toaster)

    DeleteMode(mode, blocked = on || running != null || read == null) {
        toaster.save({
            withContext(NonCancellable) {
                // The plugs the mode switched go back first, so a failure leaves the mode to try again.
                plugs?.forget(mode.id)
                store.update { now -> now.copy(modes = now.modes.filterNot { it.id == mode.id }) }
            }
        }) { Log.w(TAG, "Couldn't delete a mode (${it::class.simpleName})") }
    }
}

/** Delete mode, then an inline card to confirm; refused with a line while the mode is on, running or unread (15b §4.2). */
@Composable
private fun DeleteMode(mode: StoredMode, blocked: Boolean, delete: suspend () -> Unit) {
    val scope = rememberCoroutineScope()
    var confirming by remember { mutableStateOf(false) }
    var refused by remember { mutableStateOf(false) }
    var deleting by remember { mutableStateOf(false) }
    LaunchedEffect(blocked) { if (!blocked) refused = false }
    if (refused && blocked) HhStatusLine(HouseLines.turnOffFirst(mode.name), "mode_delete_line")
    if (!confirming) {
        HhButton(
            ModesWords.DELETE_MODE, { if (blocked) refused = true else confirming = true }, Modifier.testTag("mode_delete"),
            tone = ButtonTone.Danger, on = ButtonSurface.Bg,
        )
        return
    }
    HhConfirmCard(
        tag = "mode_delete_confirm", title = ModesWords.deleteQuestion(mode.name),
        keep = KEEP, keepTag = "mode_delete_keep",
        confirm = ModesWords.DELETE_MODE, confirmTag = "mode_delete_go",
        busy = deleting, onKeep = { confirming = false },
        onConfirm = {
            if (blocked) {
                confirming = false
                refused = true
            } else {
                deleting = true
                scope.launch {
                    delete()
                    deleting = false
                }
            }
        },
    )
}
