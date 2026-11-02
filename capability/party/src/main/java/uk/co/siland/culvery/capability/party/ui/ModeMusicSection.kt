package uk.co.siland.culvery.capability.party.ui

import android.util.Log
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.paneTitle
import androidx.compose.ui.semantics.semantics
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import uk.co.siland.culvery.capability.party.ModesWords
import uk.co.siland.culvery.capability.party.MusicChoice
import uk.co.siland.culvery.capability.party.StoredMode
import uk.co.siland.culvery.core.house.HouseLines
import uk.co.siland.culvery.core.house.HouseMusic
import uk.co.siland.culvery.core.house.HouseRoom
import uk.co.siland.culvery.core.house.MusicOptions
import uk.co.siland.culvery.core.house.MusicPick
import uk.co.siland.culvery.core.plugin.LocalOverlayHost
import uk.co.siland.culvery.core.plugin.attempt
import uk.co.siland.culvery.core.ui.Culvery
import uk.co.siland.culvery.core.ui.HhCloseButton
import uk.co.siland.culvery.core.ui.HhListRow
import uk.co.siland.culvery.core.ui.HhRadioRow
import uk.co.siland.culvery.core.ui.HhSectionLabel
import uk.co.siland.culvery.core.ui.HhSheet
import uk.co.siland.culvery.core.ui.HhSwitchRow
import uk.co.siland.culvery.core.ui.HhType
import uk.co.siland.culvery.core.ui.HouseModeDimens

private const val TAG = "ModeMusicSection"

/**
 * A mode's music and the speakers it plays on (15b §4.2). Reads what Sonos offers once as the section opens. With no
 * [music] seam, or one that isn't ready, it says to set Sonos up; while the first read runs it shows nothing. [save]
 * changes the mode and writes it at once.
 */
@Composable
internal fun ModeMusicSection(mode: StoredMode, music: HouseMusic?, save: ((StoredMode) -> StoredMode) -> Unit) {
    val options = rememberHouseOptions(music, { it.ready }) { m ->
        attempt({ m.options() }) { e ->
            Log.w(TAG, "Couldn't read what the speakers offer (${e::class.simpleName})")
            MusicOptions(emptyList(), emptyList(), HouseLines.CANT_REACH_SONOS)
        }
    }
    HhSectionLabel(ModesWords.MUSIC_SECTION)
    val offered = options.offered
    if (offered == null) {
        if (options.notSetUp) Line(ModesWords.SET_UP_SONOS, "mode_music_line")
        return
    }
    offered.line?.let { Line(it, "mode_music_line", problem = true) }
    val overlay = LocalOverlayHost.current
    val chosen = mode.music
    ChoiceRow(ModesWords.MUSIC_SECTION, chosen?.pickName ?: ModesWords.NONE, ModesWords.chooseMusic(mode.name), "mode_music") {
        overlay.show {
            MusicSheet(
                mode.name, offered.picks, chosen?.pickId,
                onPick = { pick ->
                    save { it.copy(music = pick?.let { p -> MusicChoice(p.id, p.name) }) }
                    overlay.dismiss()
                },
                onClose = overlay::dismiss,
            )
        }
    }
    if (offered.rooms.isEmpty()) return
    val on = offered.rooms.map { mode.speakers == null || mode.speakers.any { saved -> saved.id == it.id } }
    Text(ModesWords.SPEAKERS, style = HhType.label, color = Culvery.colors.mute, modifier = Modifier.semantics { heading() })
    offered.rooms.forEachIndexed { i, room ->
        HhSwitchRow(
            room.name, line = null, checked = on[i], tag = "mode_speaker_$i",
            // The last speaker stays on: a mode that plays on none would fail its music step.
            enabled = !on[i] || on.count { it } > 1,
            onChange = { save { m -> m.withSpeaker(offered.rooms, room, it) } },
        )
    }
}

/** What a house system offers a mode's section, [offered] once read; [notSetUp] with no system or one that isn't set up. */
internal class HouseOptions<T>(val offered: T?, val notSetUp: Boolean)

/** Reads what [seam] offers with [load] once [ready] says it is set up; nothing while that read runs. */
@Composable
internal fun <S : Any, T : Any> rememberHouseOptions(seam: S?, ready: (S) -> Flow<Boolean>, load: suspend (S) -> T): HouseOptions<T> {
    val isReady by remember(seam) { seam?.let(ready) ?: flowOf(false) }.collectAsStateWithLifecycle<Boolean?>(null)
    val offered by produceState<T?>(null, seam, isReady) {
        value = if (seam == null || isReady != true) null else load(seam)
    }
    return HouseOptions(offered, notSetUp = seam == null || isReady == false)
}

/** A row that says what it holds and opens a sheet to change it; spoken "[title], [value]". */
@Composable
internal fun ChoiceRow(title: String, value: String, clickLabel: String, tag: String, onClick: () -> Unit) = HhListRow(
    title, Modifier.testTag(tag).semantics { contentDescription = "$title, $value" },
    subtitle = value, onClick = onClick, clickLabel = clickLabel,
)

@Composable
internal fun Line(text: String, tag: String, problem: Boolean = false) = Text(
    text, style = HhType.body, color = if (problem) Culvery.colors.danger else Culvery.colors.mute, modifier = Modifier.testTag(tag),
)

/** The music to play, "None" first; [chosenId] is the pick shown as chosen, null for "None". */
@Composable
internal fun MusicSheet(modeName: String, picks: List<MusicPick>, chosenId: String?, onPick: (MusicPick?) -> Unit, onClose: () -> Unit) {
    val title = ModesWords.musicSheetTitle(modeName)
    val chosen = if (chosenId == null) 0 else picks.indexOfFirst { it.id == chosenId }.let { if (it < 0) -1 else it + 1 }
    HhSheet(
        padding = PaddingValues(horizontal = HouseModeDimens.sheetPaddingH, vertical = HouseModeDimens.sheetPaddingV),
        modifier = Modifier.testTag("mode_music_sheet").semantics { paneTitle = title },
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(title, style = HhType.sectionTitle, color = Culvery.colors.ink, modifier = Modifier.weight(1f).semantics { heading() })
            HhCloseButton(onClose)
        }
        LazyColumn(verticalArrangement = Arrangement.spacedBy(PartyDimens.rowGap), modifier = Modifier.weight(1f)) {
            itemsIndexed(listOf(ModesWords.NONE) + picks.map { it.name }) { i, name ->
                HhRadioRow(name, selected = i == chosen, tag = "mode_music_$i", onClick = { onPick(picks.getOrNull(i - 1)) })
            }
        }
    }
}

/**
 * Every speaker saves as "all" (null). Switching the last one off changes nothing, so a saved list is never empty
 * (an empty one would fail the music step and read "on 0 speakers").
 */
internal fun StoredMode.withSpeaker(all: List<HouseRoom>, room: HouseRoom, on: Boolean): StoredMode {
    val chosen = speakers?.map { it.id }?.toSet() ?: all.map { it.id }.toSet()
    val next = all.filter { if (it.id == room.id) on else it.id in chosen }
    return when {
        next.isEmpty() -> this
        next.size == all.size -> copy(speakers = null)
        else -> copy(speakers = next)
    }
}
