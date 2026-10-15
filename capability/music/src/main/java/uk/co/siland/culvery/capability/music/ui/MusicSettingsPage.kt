package uk.co.siland.culvery.capability.music.ui

import android.util.Log
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.paneTitle
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import java.io.IOException
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch
import uk.co.siland.culvery.capability.music.ADDRESS_EXAMPLE
import uk.co.siland.culvery.capability.music.ADDRESS_HINT
import uk.co.siland.culvery.capability.music.CHECKING
import uk.co.siland.culvery.capability.music.CONNECTED
import uk.co.siland.culvery.capability.music.CONNECTED_TO_SONOS
import uk.co.siland.culvery.capability.music.CONNECT_TO_SPEAKER
import uk.co.siland.culvery.capability.music.ConnectOutcome
import uk.co.siland.culvery.capability.music.DOORBELL
import uk.co.siland.culvery.capability.music.ENTER_ADDRESS
import uk.co.siland.culvery.capability.music.FIND_SONOS
import uk.co.siland.culvery.capability.music.FOUND_SONOS
import uk.co.siland.culvery.capability.music.LOOKING_FOR_SONOS
import uk.co.siland.culvery.capability.music.LOWER_MUSIC_TO
import uk.co.siland.culvery.capability.music.MAX_LOWER_TO
import uk.co.siland.culvery.capability.music.MUSIC
import uk.co.siland.culvery.capability.music.MusicConnect
import uk.co.siland.culvery.capability.music.MusicSettings
import uk.co.siland.culvery.capability.music.MusicSystem
import uk.co.siland.culvery.capability.music.MusicView
import uk.co.siland.culvery.capability.music.NOT_CONNECTED
import uk.co.siland.culvery.capability.music.ROOMS_AFTER_FIND
import uk.co.siland.culvery.capability.music.SONOS
import uk.co.siland.culvery.capability.music.SPEAKER_ADDRESS
import uk.co.siland.culvery.capability.music.SpeakerRoom
import uk.co.siland.culvery.capability.music.Speakers
import uk.co.siland.culvery.capability.music.USUAL_ROOM_LABEL
import uk.co.siland.culvery.capability.music.lowerToWords
import uk.co.siland.culvery.capability.music.problemLine
import uk.co.siland.culvery.core.plugin.COULD_NOT_SAVE
import uk.co.siland.culvery.core.plugin.LocalOverlayHost
import uk.co.siland.culvery.core.plugin.SettingsPage
import uk.co.siland.culvery.core.plugin.Toaster
import uk.co.siland.culvery.core.ui.ButtonSurface
import uk.co.siland.culvery.core.ui.ButtonTone
import uk.co.siland.culvery.core.ui.Culvery
import uk.co.siland.culvery.core.ui.HhButton
import uk.co.siland.culvery.core.ui.HhChoiceChip
import uk.co.siland.culvery.core.ui.HhCloseButton
import uk.co.siland.culvery.core.ui.HhMessageLine
import uk.co.siland.culvery.core.ui.HhSectionLabel
import uk.co.siland.culvery.core.ui.HhSheet
import uk.co.siland.culvery.core.ui.HhTextField
import uk.co.siland.culvery.core.ui.HhType
import uk.co.siland.culvery.core.ui.Icons
import uk.co.siland.culvery.core.ui.ListRowTokens

private const val TAG = "MusicSettings"

const val MUSIC_PAGE_ID = "music"

/** Settings › Music (6 §5.4), admin-only as the rest of Settings; after Calendars (400), before Door (500). */
class MusicPage internal constructor(private val content: @Composable () -> Unit) : SettingsPage {
    override val id = MUSIC_PAGE_ID
    override val title = MUSIC
    override val order = 450

    @Composable
    override fun Content() = content()
}

data class MusicSettingsUi(
    val connected: Boolean,
    /** The §6 line, which takes the connection line's place. */
    val status: String?,
    /** Why the last Find Sonos found nothing. */
    val findLine: String?,
    val finding: Boolean,
    val rooms: List<SpeakerRoom>,
    val usualRoomId: String?,
    val lowerTo: Int,
)

class MusicSettingsActions(
    val onFind: () -> Unit,
    val onEnterAddress: () -> Unit,
    val onUsualRoom: (String) -> Unit,
    val onLowerTo: (Int) -> Unit,
)

@Composable
fun MusicSettingsScreen(ui: MusicSettingsUi, actions: MusicSettingsActions) {
    val c = Culvery.colors
    Column(verticalArrangement = Arrangement.spacedBy(MusicDimens.settingsGap), modifier = Modifier.testTag("settings_music")) {
        Text(MUSIC, style = HhType.screenTitle, color = c.ink, modifier = Modifier.semantics { heading() })
        HhSectionLabel(SONOS)
        if (ui.status != null) {
            MusicStatusLine(ui.status)
        } else {
            Text(if (ui.connected) CONNECTED else NOT_CONNECTED, style = HhType.body, color = c.mute, modifier = Modifier.testTag("music_connection"))
        }
        Row(horizontalArrangement = Arrangement.spacedBy(MusicDimens.buttonGap)) {
            HhButton(
                if (ui.finding) LOOKING_FOR_SONOS else FIND_SONOS, actions.onFind, Modifier.testTag("music_find"),
                tone = if (ui.connected) ButtonTone.Quiet else ButtonTone.Primary, icon = Icons.SEARCH, enabled = !ui.finding, on = ButtonSurface.Bg,
            )
            HhButton(ENTER_ADDRESS, actions.onEnterAddress, Modifier.testTag("music_address"), on = ButtonSurface.Bg)
        }
        HhMessageLine(ui.findLine, HhType.body, c.danger, "music_find_line")
        HhSectionLabel(USUAL_ROOM_LABEL)
        if (ui.rooms.isEmpty()) {
            Text(ROOMS_AFTER_FIND, style = HhType.body, color = c.mute)
        } else {
            FlowRow(horizontalArrangement = Arrangement.spacedBy(MusicDimens.buttonGap), verticalArrangement = Arrangement.spacedBy(MusicDimens.buttonGap)) {
                ui.rooms.forEach { room ->
                    HhChoiceChip(room.name, selected = room.id == ui.usualRoomId, tag = "music_usual_${room.id}", onClick = { actions.onUsualRoom(room.id) })
                }
            }
        }
        HhSectionLabel(DOORBELL)
        LowerToRow(ui.lowerTo, actions.onLowerTo)
    }
}

/** 0–50 % in 5 % steps, saved when let go (6 §5.4). */
@Composable
private fun LowerToRow(lowerTo: Int, onLowerTo: (Int) -> Unit) {
    val c = Culvery.colors
    var shown by remember(lowerTo) { mutableIntStateOf(lowerTo) }
    Column(verticalArrangement = Arrangement.spacedBy(ListRowTokens.lineGap)) {
        Text(LOWER_MUSIC_TO, style = HhType.rowTitle, color = c.ink)
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(ListRowTokens.trailingGap)) {
            VolumeSlider(
                LOWER_MUSIC_TO, lowerTo, "music_lower_to", Modifier.weight(1f),
                range = 0..MAX_LOWER_TO, steps = MusicDimens.LOWER_STEPS, spokenName = LOWER_MUSIC_TO, spokenState = ::lowerToWords,
                onChange = { shown = it }, onVolume = onLowerTo,
            )
            Text(lowerToWords(shown), style = HhType.body, color = c.mute)
        }
    }
}

/** Enter a speaker's address (6 §4.1): [onConnect] answers null when connected, else the line to show. */
@Composable
internal fun AddressSheet(onConnect: suspend (String) -> String?, onEdited: (Boolean) -> Unit, onClose: () -> Unit) {
    val c = Culvery.colors
    var typed by remember { mutableStateOf("") }
    var line by remember { mutableStateOf<String?>(null) }
    var checking by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    fun connect() {
        if (checking || typed.isBlank()) return
        scope.launch {
            checking = true
            line = onConnect(typed)
            checking = false
        }
    }
    HhSheet(
        padding = PaddingValues(horizontal = MusicDimens.sheetPaddingH, vertical = MusicDimens.sheetPaddingV),
        modifier = Modifier.testTag("music_address_sheet").semantics { paneTitle = ENTER_ADDRESS },
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(ENTER_ADDRESS, style = HhType.sectionTitle, color = c.ink, modifier = Modifier.semantics { heading() })
                Text(ADDRESS_HINT, style = HhType.body, color = c.mute)
            }
            HhCloseButton(onClose)
        }
        HhTextField(
            typed,
            {
                typed = it
                line = null
                onEdited(it.isNotEmpty())
            },
            placeholder = ADDRESS_EXAMPLE, label = SPEAKER_ADDRESS, tag = "music_address_field",
            capitalization = KeyboardCapitalization.None, error = line, onDone = ::connect,
        )
        HhMessageLine(line, HhType.body, c.danger, "music_address_line", fieldError = line)
        HhButton(
            if (checking) CHECKING else CONNECT_TO_SPEAKER, ::connect, Modifier.testTag("music_address_connect"),
            tone = ButtonTone.Primary, enabled = !checking && typed.isNotBlank(), on = ButtonSurface.Bg,
        )
    }
}

/** Settings › Music with its data; reads the layout once as it opens, for the room picker. */
@Composable
internal fun MusicSettingsHost(settings: MusicSettings, speakers: Speakers, view: MusicView, connect: MusicConnect, toaster: Toaster) {
    val prefs by settings.prefs.collectAsStateWithLifecycle(null)
    val groups by view.groups.collectAsStateWithLifecycle(emptyList())
    val problem by view.problem.collectAsStateWithLifecycle(null)
    val system by speakers.system.collectAsStateWithLifecycle()
    var finding by remember { mutableStateOf(false) }
    var findLine by remember { mutableStateOf<String?>(null) }
    val overlay = LocalOverlayHost.current
    val scope = rememberCoroutineScope()
    LaunchedEffect(system) {
        val s = system ?: return@LaunchedEffect
        findLine = null
        readRooms(s)
    }
    val p = prefs ?: return
    fun change(write: suspend () -> Unit) {
        scope.launch { save(toaster, write) }
    }
    fun said(outcome: ConnectOutcome, done: String): String? = when (outcome) {
        ConnectOutcome.Connected -> {
            findLine = null
            toaster.show(done)
            null
        }
        is ConnectOutcome.Failed -> outcome.line
    }
    MusicSettingsScreen(
        MusicSettingsUi(
            connected = system != null,
            status = problem?.let(::problemLine),
            findLine = findLine,
            finding = finding,
            rooms = groups.flatMap { it.rooms },
            usualRoomId = p.usualRoomId,
            lowerTo = p.lowerTo,
        ),
        MusicSettingsActions(
            onFind = {
                scope.launch {
                    finding = true
                    findLine = null
                    findLine = said(connect.find(), FOUND_SONOS)
                    finding = false
                }
            },
            onEnterAddress = {
                overlay.show {
                    AddressSheet(
                        onConnect = { typed -> said(connect.enterAddress(typed), CONNECTED_TO_SONOS).also { if (it == null) overlay.dismiss() } },
                        onEdited = overlay::markEdited,
                        onClose = overlay::dismiss,
                    )
                }
            },
            onUsualRoom = { id -> change { settings.setUsualRoom(id) } },
            onLowerTo = { percent -> change { settings.setLowerTo(percent) } },
        ),
    )
}

/** No check runs while only Settings shows, and the picker needs the rooms. */
private suspend fun readRooms(system: MusicSystem) {
    try {
        system.checkLayout()
    } catch (e: CancellationException) {
        throw e
    } catch (e: Exception) {
        Log.w(TAG, "Couldn't read the rooms for Settings (${e::class.simpleName})")
    }
}

/** A setting that didn't save says so; nothing changed. */
private suspend fun save(toaster: Toaster, write: suspend () -> Unit) {
    try {
        write()
    } catch (e: IOException) {
        Log.w(TAG, "Couldn't save a music setting (${e::class.simpleName})")
        toaster.show(COULD_NOT_SAVE, urgent = true)
    }
}
