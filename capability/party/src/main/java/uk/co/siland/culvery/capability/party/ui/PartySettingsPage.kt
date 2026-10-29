package uk.co.siland.culvery.capability.party.ui

import android.util.Log
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.paneTitle
import androidx.compose.ui.semantics.semantics
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.launch
import uk.co.siland.culvery.capability.party.MusicChoice
import uk.co.siland.culvery.capability.party.PartyPrefs
import uk.co.siland.culvery.capability.party.PartySettings
import uk.co.siland.culvery.capability.party.PartyWords
import uk.co.siland.culvery.core.house.HouseLights
import uk.co.siland.culvery.core.house.HouseMusic
import uk.co.siland.culvery.core.house.HouseRoom
import uk.co.siland.culvery.core.house.HouseScene
import uk.co.siland.culvery.core.house.LightOptions
import uk.co.siland.culvery.core.house.LightRoomOption
import uk.co.siland.culvery.core.house.MusicOptions
import uk.co.siland.culvery.core.house.MusicPick
import uk.co.siland.culvery.core.house.SceneChoice
import uk.co.siland.culvery.core.plugin.LocalOverlayHost
import uk.co.siland.culvery.core.plugin.SettingsPage
import uk.co.siland.culvery.core.plugin.attempt
import uk.co.siland.culvery.core.ui.Culvery
import uk.co.siland.culvery.core.ui.HhCloseButton
import uk.co.siland.culvery.core.ui.HhListRow
import uk.co.siland.culvery.core.ui.HhPageTitle
import uk.co.siland.culvery.core.ui.HhRadioRow
import uk.co.siland.culvery.core.ui.HhSectionLabel
import uk.co.siland.culvery.core.ui.HhSheet
import uk.co.siland.culvery.core.ui.HhSwitchRow
import uk.co.siland.culvery.core.ui.HhType

private const val TAG = "PartySettingsPage"

/** Settings › Party mode (13 §3), between Lights (470) and Other integrations (480); always listed. */
class PartyPage internal constructor(private val content: @Composable () -> Unit) : SettingsPage {
    override val id = "party"
    override val title = PartyWords.SETTINGS_TITLE
    override val order = 475

    @Composable
    override fun Content() = content()
}

/** What the page shows: null [music] or [lights] means that system isn't set up. [toString] leaves out the choices. */
data class PartySettingsUi(val prefs: PartyPrefs, val music: MusicOptions?, val lights: LightOptions?) {
    override fun toString() = "PartySettingsUi"
}

@Composable
fun PartySettingsScreen(
    ui: PartySettingsUi,
    onMusic: (MusicPick?) -> Unit,
    onSpeaker: (HouseRoom, Boolean) -> Unit,
    onScene: (LightRoomOption, HouseScene?) -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(PartyDimens.settingsGap), modifier = Modifier.testTag("settings_party")) {
        HhPageTitle(PartyWords.SETTINGS_TITLE)
        MusicSection(ui, onMusic, onSpeaker)
        LightsSection(ui, onScene)
        HhSectionLabel(PartyWords.PLUGS_SECTION)
        Line(PartyWords.PLUGS_LINE, "party_plugs_line")
    }
}

@Composable
private fun MusicSection(ui: PartySettingsUi, onMusic: (MusicPick?) -> Unit, onSpeaker: (HouseRoom, Boolean) -> Unit) {
    HhSectionLabel(PartyWords.MUSIC_SECTION)
    val music = ui.music
    if (music == null) {
        Line(PartyWords.SET_UP_SONOS, "party_music_line")
        return
    }
    music.line?.let { Line(it, "party_music_line", problem = true) }
    val overlay = LocalOverlayHost.current
    val chosen = ui.prefs.music
    ChoiceRow(PartyWords.MUSIC_SECTION, chosen?.pickName ?: PartyWords.NONE, PartyWords.CHOOSE_MUSIC, "party_music") {
        overlay.show {
            PartyMusicSheet(
                music.picks, chosen?.pickId,
                onPick = { pick ->
                    onMusic(pick)
                    overlay.dismiss()
                },
                onClose = overlay::dismiss,
            )
        }
    }
    if (music.rooms.isEmpty()) return
    val on = music.rooms.map { ui.prefs.rooms == null || ui.prefs.rooms.any { saved -> saved.id == it.id } }
    Text(PartyWords.SPEAKERS, style = HhType.label, color = Culvery.colors.mute, modifier = Modifier.semantics { heading() })
    music.rooms.forEachIndexed { i, room ->
        HhSwitchRow(
            room.name, line = null, checked = on[i], tag = "party_speaker_$i",
            enabled = !on[i] || on.count { it } > 1,
            onChange = { onSpeaker(room, it) },
        )
    }
}

@Composable
private fun LightsSection(ui: PartySettingsUi, onScene: (LightRoomOption, HouseScene?) -> Unit) {
    HhSectionLabel(PartyWords.LIGHTS_SECTION)
    val lights = ui.lights
    if (lights == null) {
        Line(PartyWords.SET_UP_HUE, "party_lights_line")
        return
    }
    lights.line?.let { Line(it, "party_lights_line", problem = true) }
    val overlay = LocalOverlayHost.current
    lights.rooms.forEachIndexed { i, room ->
        val chosen = ui.prefs.scenes.firstOrNull { it.roomId == room.id }
        ChoiceRow(room.name, chosen?.sceneName ?: PartyWords.LEAVE_ALONE, PartyWords.chooseScene(room.name), "party_light_$i") {
            overlay.show {
                PartySceneSheet(
                    room, chosen?.sceneId,
                    onPick = { scene ->
                        onScene(room, scene)
                        overlay.dismiss()
                    },
                    onClose = overlay::dismiss,
                )
            }
        }
    }
}

/** A row that says what it holds and opens a sheet to change it; spoken "[title], [value]". */
@Composable
private fun ChoiceRow(title: String, value: String, clickLabel: String, tag: String, onClick: () -> Unit) = HhListRow(
    title, Modifier.testTag(tag).semantics { contentDescription = "$title, $value" },
    subtitle = value, onClick = onClick, clickLabel = clickLabel,
)

@Composable
private fun Line(text: String, tag: String, problem: Boolean = false) = Text(
    text, style = HhType.body, color = if (problem) Culvery.colors.danger else Culvery.colors.mute, modifier = Modifier.testTag(tag),
)

/** The music to play, "None" first; [chosenId] is the pick shown as chosen, null for "None". */
@Composable
internal fun PartyMusicSheet(picks: List<MusicPick>, chosenId: String?, onPick: (MusicPick?) -> Unit, onClose: () -> Unit) {
    val chosen = if (chosenId == null) 0 else picks.indexOfFirst { it.id == chosenId }.let { if (it < 0) -1 else it + 1 }
    ChoiceSheet(
        PartyWords.MUSIC_SHEET_TITLE, "party_music_sheet", "party_music", listOf(PartyWords.NONE) + picks.map { it.name }, chosen,
        onPick = { onPick(picks.getOrNull(it - 1)) }, onClose = onClose,
    )
}

/** A light room's scenes, "Leave alone" first; [chosenId] is the scene shown as chosen, null for "Leave alone". */
@Composable
internal fun PartySceneSheet(room: LightRoomOption, chosenId: String?, onPick: (HouseScene?) -> Unit, onClose: () -> Unit) {
    val chosen = if (chosenId == null) 0 else room.scenes.indexOfFirst { it.id == chosenId }.let { if (it < 0) -1 else it + 1 }
    ChoiceSheet(
        room.name, "party_scene_sheet", "party_scene", listOf(PartyWords.LEAVE_ALONE) + room.scenes.map { it.name }, chosen,
        onPick = { onPick(room.scenes.getOrNull(it - 1)) }, onClose = onClose,
    )
}

/** [names] as radio rows tagged `{rowTag}_{i}`, the one at [chosen] selected (-1: none); [onPick] gets the row's index. */
@Composable
private fun ChoiceSheet(title: String, tag: String, rowTag: String, names: List<String>, chosen: Int, onPick: (Int) -> Unit, onClose: () -> Unit) {
    HhSheet(
        padding = PaddingValues(horizontal = PartyDimens.sheetPaddingH, vertical = PartyDimens.sheetPaddingV),
        modifier = Modifier.testTag(tag).semantics { paneTitle = title },
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(title, style = HhType.sectionTitle, color = Culvery.colors.ink, modifier = Modifier.weight(1f).semantics { heading() })
            HhCloseButton(onClose)
        }
        LazyColumn(verticalArrangement = Arrangement.spacedBy(PartyDimens.rowGap), modifier = Modifier.weight(1f)) {
            itemsIndexed(names) { i, name -> HhRadioRow(name, selected = i == chosen, tag = "${rowTag}_$i", onClick = { onPick(i) }) }
        }
    }
}

/** Settings › Party mode with its data; reads what Sonos and Hue offer once as it opens, and saves each choice as it is made. */
@Composable
fun PartySettingsHost(settings: PartySettings, music: HouseMusic?, lights: HouseLights?) {
    val prefs by settings.prefs.collectAsStateWithLifecycle(null)
    val musicReady by remember(music) { music?.ready ?: flowOf(false) }.collectAsStateWithLifecycle(false)
    val lightsReady by remember(lights) { lights?.ready ?: flowOf(false) }.collectAsStateWithLifecycle(false)
    var musicOptions by remember { mutableStateOf<MusicOptions?>(null) }
    var lightOptions by remember { mutableStateOf<LightOptions?>(null) }
    val scope = rememberCoroutineScope()
    LaunchedEffect(music, musicReady) {
        musicOptions = if (music == null || !musicReady) {
            null
        } else {
            attempt({ music.options() }) { e ->
                Log.w(TAG, "Couldn't read what the speakers offer (${e::class.simpleName})")
                MusicOptions(emptyList(), emptyList(), null)
            }
        }
    }
    LaunchedEffect(lights, lightsReady) {
        lightOptions = if (lights == null || !lightsReady) {
            null
        } else {
            attempt({ lights.partyOptions() }) { e ->
                Log.w(TAG, "Couldn't read what the lights offer (${e::class.simpleName})")
                LightOptions(emptyList(), null)
            }
        }
    }
    val p = prefs ?: return
    fun save(change: (PartyPrefs) -> PartyPrefs) {
        scope.launch {
            attempt({ settings.update(change) }) { e -> Log.w(TAG, "Couldn't save a Party mode setting (${e::class.simpleName})") }
        }
    }
    PartySettingsScreen(
        PartySettingsUi(p, musicOptions, lightOptions),
        onMusic = { pick -> save { it.withMusic(pick?.let { chosen -> MusicChoice(chosen.id, chosen.name) }) } },
        onSpeaker = { room, on -> save { it.withSpeaker(musicOptions?.rooms.orEmpty(), room, on) } },
        onScene = { room, scene ->
            save { if (scene == null) it.withoutScene(room.id) else it.withScene(SceneChoice(room.id, room.name, scene.id, scene.name)) }
        },
    )
}

/** Every speaker saves as "all" (null); turning the last one off changes nothing, as an empty list would fail the music step. */
internal fun PartyPrefs.withSpeaker(all: List<HouseRoom>, room: HouseRoom, on: Boolean): PartyPrefs {
    val chosen = rooms?.map { it.id }?.toSet() ?: all.map { it.id }.toSet()
    val next = all.filter { (it.id in chosen || (on && it.id == room.id)) && (on || it.id != room.id) }
    return when {
        next.isEmpty() -> this
        next.size == all.size -> withRooms(null)
        else -> withRooms(next)
    }
}
