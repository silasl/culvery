package uk.co.siland.culvery.capability.party.ui

import android.util.Log
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.paneTitle
import androidx.compose.ui.semantics.semantics
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import java.io.IOException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.launch
import uk.co.siland.culvery.capability.party.ModesWords
import uk.co.siland.culvery.capability.party.PadTaps
import uk.co.siland.culvery.capability.party.SoundPadsStore
import uk.co.siland.culvery.core.plugin.LocalOverlayHost
import uk.co.siland.culvery.core.plugin.Toaster
import uk.co.siland.culvery.core.plugin.saving
import uk.co.siland.culvery.core.sound.ON_THIS_TABLET
import uk.co.siland.culvery.core.sound.Sound
import uk.co.siland.culvery.core.sound.onTablet
import uk.co.siland.culvery.core.sound.soundFor
import uk.co.siland.culvery.core.sound.soundNameFromId
import uk.co.siland.culvery.core.ui.ButtonSurface
import uk.co.siland.culvery.core.ui.Culvery
import uk.co.siland.culvery.core.ui.HhButton
import uk.co.siland.culvery.core.ui.HhCloseButton
import uk.co.siland.culvery.core.ui.HhListRow
import uk.co.siland.culvery.core.ui.HhRadioGroup
import uk.co.siland.culvery.core.ui.HhRadioRow
import uk.co.siland.culvery.core.ui.HhSectionLabel
import uk.co.siland.culvery.core.ui.HhSheet
import uk.co.siland.culvery.core.ui.HhSoundRow
import uk.co.siland.culvery.core.ui.HhType
import uk.co.siland.culvery.core.ui.HouseModeDimens
import uk.co.siland.culvery.core.ui.Icons

private const val TAG = "SoundButtonsSection"

private val logPadSave: (IOException) -> Unit = { Log.w(TAG, "Couldn't save a sound button (${it::class.simpleName})") }

/** What the Sound buttons section reads and does: the pads' choices, the tray's room, the sounds on offer and their previews. */
class SoundButtons internal constructor(
    internal val store: SoundPadsStore,
    internal val room: Flow<Int>,
    internal val choices: Flow<List<Sound>>,
    /** Reads the tablet's sounds folder again. */
    internal val refresh: suspend () -> Unit,
    internal val taps: PadTaps,
)

/**
 * Settings › Modes › Sound buttons (18 §5): Pad 1 to Pad 3, each with its sound and a Play, opening a picker. A pad past the
 * tray's room says when it shows. The folder is read again once as the section opens.
 */
@Composable
internal fun SoundButtonsSection(sounds: SoundButtons, toaster: Toaster) {
    val stored by sounds.store.pads.collectAsStateWithLifecycle(null)
    val room by sounds.room.collectAsStateWithLifecycle(null)
    val choices by sounds.choices.collectAsStateWithLifecycle(null)
    val overlay = LocalOverlayHost.current
    val scope = rememberCoroutineScope()
    val change = scope.saving(toaster, logPadSave)
    LaunchedEffect(Unit) { sounds.refresh() }
    HhSectionLabel(ModesWords.SOUND_BUTTONS)
    val ids = stored ?: return
    val offered = choices ?: return
    ids.forEachIndexed { i, id ->
        val pad = i + 1
        val sound = offered.soundFor(id)
        val waiting = room?.let { pad > it } == true
        val value = when {
            id == null -> ModesWords.NONE
            sound != null -> sound.name
            else -> soundNameFromId(id)?.let(ModesWords::goneSound) ?: ModesWords.NONE
        }
        HhListRow(
            ModesWords.padName(pad), Modifier.testTag("sound_pad_$pad").semantics {
                contentDescription = "${ModesWords.padName(pad)}, $value" + if (waiting) ", ${ModesWords.PAD_WAITING}" else ""
            },
            subtitle = value,
            below = if (waiting) {
                {
                    Text(ModesWords.PAD_WAITING, style = HhType.secondary, color = Culvery.colors.mute, modifier = Modifier.testTag("sound_pad_${pad}_waiting"))
                }
            } else null,
            trailing = {
                if (sound != null) {
                    HhButton(
                        ModesWords.PLAY_SOUND, { scope.launch { sounds.taps.preview(sound) } }, Modifier.testTag("sound_pad_${pad}_play"),
                        icon = Icons.PLAY_ARROW, on = ButtonSurface.Surf, spokenName = ModesWords.playSpoken(pad),
                    )
                }
            },
            onClick = {
                overlay.show {
                    SoundSheet(
                        pad, offered, sound?.id ?: id?.takeIf { soundNameFromId(it) != null }, sounds,
                        onPick = { picked ->
                            change { sounds.store.setPad(pad, picked?.id) }
                            overlay.dismiss()
                        },
                        onClose = overlay::dismiss,
                    )
                }
            },
            clickLabel = ModesWords.chooseSound(pad),
        )
    }
    Line(ModesWords.BIRTHDAY_NOTE, "sound_pads_note")
}

/**
 * The sound for [pad]: None first, then each of [sounds]; [chosenId] is shown as chosen, null for None. Tapping a sound plays it
 * (again, to stop it); its radio chooses it. The preview stops with the sheet.
 */
@Composable
private fun SoundSheet(pad: Int, sounds: List<Sound>, chosenId: String?, buttons: SoundButtons, onPick: (Sound?) -> Unit, onClose: () -> Unit) {
    val title = ModesWords.padName(pad)
    val previewing by buttons.taps.previewing.collectAsStateWithLifecycle(null)
    val scope = rememberCoroutineScope()
    DisposableEffect(Unit) { onDispose { buttons.taps.stopPreview() } }
    HhSheet(
        padding = PaddingValues(horizontal = HouseModeDimens.sheetPaddingH, vertical = HouseModeDimens.sheetPaddingV),
        modifier = Modifier.testTag("sound_pad_sheet").semantics { paneTitle = title },
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(title, style = HhType.sectionTitle, color = Culvery.colors.ink, modifier = Modifier.weight(1f).semantics { heading() })
            HhCloseButton(onClose)
        }
        HhRadioGroup(Modifier.weight(1f)) {
            LazyColumn(verticalArrangement = Arrangement.spacedBy(PartyDimens.rowGap)) {
                item {
                    HhRadioRow(ModesWords.NONE, selected = chosenId == null, tag = "sound_choice_none", onClick = { onPick(null) })
                }
                items(sounds, key = { it.id }) { sound ->
                    HhSoundRow(
                        sound.name, ON_THIS_TABLET.takeIf { sound.onTablet }, previewing == sound.id, chosenId == sound.id, "sound_choice_${sound.id}",
                        onPlay = { if (previewing == sound.id) buttons.taps.stopPreview() else scope.launch { buttons.taps.preview(sound) } },
                        onChoose = { onPick(sound) },
                    )
                }
            }
        }
    }
}
