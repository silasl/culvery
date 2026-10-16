package uk.co.siland.culvery.capability.lights.ui

import android.util.Log
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import uk.co.siland.culvery.capability.lights.Automation
import uk.co.siland.culvery.capability.lights.BRIDGE_ADDRESS
import uk.co.siland.culvery.capability.lights.BRIDGE_ADDRESS_EXAMPLE
import uk.co.siland.culvery.capability.lights.BRIDGE_ADDRESS_HINT
import uk.co.siland.culvery.capability.lights.BridgeFound
import uk.co.siland.culvery.capability.lights.Bridges
import uk.co.siland.culvery.capability.lights.ConnectFailed
import uk.co.siland.culvery.capability.lights.ENTER_BRIDGE_ADDRESS
import uk.co.siland.culvery.capability.lights.FIND_HUE_BRIDGE
import uk.co.siland.culvery.capability.lights.FindOutcome
import uk.co.siland.culvery.capability.lights.HOLIDAY_JUST_LIGHTS
import uk.co.siland.culvery.capability.lights.HOLIDAY_SWITCHES_ON
import uk.co.siland.culvery.capability.lights.HUE_BRIDGE
import uk.co.siland.culvery.capability.lights.LEFT_OUT_LABEL
import uk.co.siland.culvery.capability.lights.LIGHTS
import uk.co.siland.culvery.capability.lights.LOOKING_FOR_BRIDGE
import uk.co.siland.culvery.capability.lights.LightRoom
import uk.co.siland.culvery.capability.lights.LightsConnect
import uk.co.siland.culvery.capability.lights.LightsSettings
import uk.co.siland.culvery.capability.lights.LightsSystem
import uk.co.siland.culvery.capability.lights.LightsView
import uk.co.siland.culvery.capability.lights.MAIN_ROOM
import uk.co.siland.culvery.capability.lights.NOT_PAIRED
import uk.co.siland.culvery.capability.lights.NO_AUTOMATIONS
import uk.co.siland.culvery.capability.lights.PAIRED
import uk.co.siland.culvery.capability.lights.PAIRED_WITH_BRIDGE
import uk.co.siland.culvery.capability.lights.PAIR_WITH_BRIDGE
import uk.co.siland.culvery.capability.lights.PairOutcome
import uk.co.siland.culvery.capability.lights.ROOMS_AFTER_PAIRING
import uk.co.siland.culvery.capability.lights.goneLeftOutLine
import uk.co.siland.culvery.capability.lights.mainRoom
import uk.co.siland.culvery.capability.lights.problemLine
import uk.co.siland.culvery.core.plugin.CHECKING
import uk.co.siland.culvery.core.plugin.LocalOverlayHost
import uk.co.siland.culvery.core.plugin.SettingsPage
import uk.co.siland.culvery.core.plugin.Toaster
import uk.co.siland.culvery.core.plugin.attempt
import uk.co.siland.culvery.core.plugin.save
import uk.co.siland.culvery.core.ui.AddressSheetWords
import uk.co.siland.culvery.core.ui.ButtonSurface
import uk.co.siland.culvery.core.ui.ButtonTone
import uk.co.siland.culvery.core.ui.Culvery
import uk.co.siland.culvery.core.ui.HhAddressSheet
import uk.co.siland.culvery.core.ui.HhButton
import uk.co.siland.culvery.core.ui.HhChoiceChip
import uk.co.siland.culvery.core.ui.HhMessageLine
import uk.co.siland.culvery.core.ui.HhSectionLabel
import uk.co.siland.culvery.core.ui.HhType
import uk.co.siland.culvery.core.ui.Icons

private const val TAG = "LightsSettings"

const val LIGHTS_PAGE_ID = "lights"

/** Settings › Lights (7 §5.3), admin-only as the rest of Settings; after Music (450), before Door (500). */
class LightsPage internal constructor(private val content: @Composable () -> Unit) : SettingsPage {
    override val id = LIGHTS_PAGE_ID
    override val title = LIGHTS
    override val order = 470

    @Composable
    override fun Content() = content()
}

data class LightsSettingsUi(
    val paired: Boolean,
    /** The §6 line, which takes the connection line's place. */
    val status: String?,
    /** Why the last Find Hue Bridge found nothing. */
    val findLine: String?,
    val finding: Boolean,
    val rooms: List<LightRoom>,
    val mainRoomId: String?,
    val leftOut: Set<String>,
    /** How many of [leftOut] the bridge no longer lists. */
    val goneLeftOut: Int,
    /** Null until the bridge has answered. */
    val automations: List<Automation>?,
    val holidayAutomations: Set<String>,
)

@Composable
fun LightsSettingsPage(
    ui: LightsSettingsUi,
    onFind: () -> Unit,
    onEnterAddress: () -> Unit,
    onMainRoom: (String) -> Unit,
    onLeftOut: (Set<String>) -> Unit,
    onHolidayAutomations: (Set<String>) -> Unit,
) {
    val c = Culvery.colors
    Column(verticalArrangement = Arrangement.spacedBy(LightsDimens.settingsGap), modifier = Modifier.testTag("settings_lights")) {
        Text(LIGHTS, style = HhType.screenTitle, color = c.ink, modifier = Modifier.semantics { heading() })
        HhSectionLabel(HUE_BRIDGE)
        if (ui.status != null) {
            Text(ui.status, style = HhType.body, color = c.danger, modifier = Modifier.testTag("lights_status"))
        } else {
            Text(if (ui.paired) PAIRED else NOT_PAIRED, style = HhType.body, color = c.mute, modifier = Modifier.testTag("lights_connection"))
        }
        Row(horizontalArrangement = Arrangement.spacedBy(LightsDimens.settingsButtonGap)) {
            HhButton(
                if (ui.finding) LOOKING_FOR_BRIDGE else FIND_HUE_BRIDGE, onFind, Modifier.testTag("lights_find"),
                tone = if (ui.paired) ButtonTone.Quiet else ButtonTone.Primary, icon = Icons.SEARCH, enabled = !ui.finding, on = ButtonSurface.Bg,
            )
            HhButton(ENTER_BRIDGE_ADDRESS, onEnterAddress, Modifier.testTag("lights_address"), on = ButtonSurface.Bg)
        }
        HhMessageLine(ui.findLine, HhType.body, c.danger, "lights_find_line")
        MainRoomChoice(ui, onMainRoom)
        LeftOutChoice(ui, onLeftOut)
        HolidayChoice(ui, onHolidayAutomations)
    }
}

@Composable
private fun MainRoomChoice(ui: LightsSettingsUi, onMainRoom: (String) -> Unit) {
    HhSectionLabel(MAIN_ROOM)
    if (ui.rooms.isEmpty()) {
        RoomsAfterPairing()
        return
    }
    val chosen = mainRoom(ui.rooms, ui.mainRoomId)?.id
    ChipFlow {
        ui.rooms.forEach { room ->
            HhChoiceChip(room.name, selected = room.id == chosen, tag = "lights_main_${room.id}", onClick = { onMainRoom(room.id) })
        }
    }
}

@Composable
private fun LeftOutChoice(ui: LightsSettingsUi, onLeftOut: (Set<String>) -> Unit) {
    val c = Culvery.colors
    HhSectionLabel(LEFT_OUT_LABEL)
    if (ui.rooms.isEmpty()) {
        RoomsAfterPairing()
        return
    }
    // A tick saves only rooms the bridge still lists, so a gone room drops out of the saved set.
    val left = ui.leftOut intersect ui.rooms.map { it.id }.toSet()
    ChipFlow {
        ui.rooms.forEach { room ->
            HhChoiceChip(
                room.name, selected = room.id in left, tag = "lights_left_out_${room.id}", role = Role.Checkbox,
                onClick = { onLeftOut(if (room.id in left) left - room.id else left + room.id) },
            )
        }
    }
    if (ui.goneLeftOut > 0) Text(goneLeftOutLine(ui.goneLeftOut), style = HhType.body, color = c.mute, modifier = Modifier.testTag("lights_gone_line"))
}

@Composable
private fun HolidayChoice(ui: LightsSettingsUi, onHolidayAutomations: (Set<String>) -> Unit) {
    val c = Culvery.colors
    val automations = ui.automations ?: return
    HhSectionLabel(HOLIDAY_SWITCHES_ON)
    when {
        automations.isEmpty() -> Text(NO_AUTOMATIONS, style = HhType.body, color = c.mute, modifier = Modifier.testTag("lights_no_automations"))
        else -> {
            val ticked = ui.holidayAutomations intersect automations.map { it.id }.toSet()
            ChipFlow {
                automations.forEach { automation ->
                    HhChoiceChip(
                        automation.name, selected = automation.id in ticked, tag = "lights_automation_${automation.id}", role = Role.Checkbox,
                        onClick = { onHolidayAutomations(if (automation.id in ticked) ticked - automation.id else ticked + automation.id) },
                    )
                }
            }
            if (ticked.isEmpty()) Text(HOLIDAY_JUST_LIGHTS, style = HhType.body, color = c.mute, modifier = Modifier.testTag("lights_holiday_note"))
        }
    }
}

@Composable
private fun RoomsAfterPairing() = Text(ROOMS_AFTER_PAIRING, style = HhType.body, color = Culvery.colors.mute)

@Composable
private fun ChipFlow(content: @Composable () -> Unit) = FlowRow(
    horizontalArrangement = Arrangement.spacedBy(LightsDimens.settingsButtonGap),
    verticalArrangement = Arrangement.spacedBy(LightsDimens.settingsButtonGap),
) { content() }

/** Type the bridge's address (7 §4.1): [onConnect] answers null when the bridge answered, else the line to show. */
@Composable
internal fun AddressSheet(onConnect: suspend (String) -> String?, onEdited: (Boolean) -> Unit, onClose: () -> Unit) = HhAddressSheet(
    AddressSheetWords(ENTER_BRIDGE_ADDRESS, BRIDGE_ADDRESS_HINT, BRIDGE_ADDRESS, BRIDGE_ADDRESS_EXAMPLE, PAIR_WITH_BRIDGE, CHECKING),
    tag = "lights_address", buttonTag = "lights_address_pair",
    padding = PaddingValues(horizontal = LightsDimens.sheetPaddingH, vertical = LightsDimens.sheetPaddingV),
    onConnect = onConnect, onEdited = onEdited, onClose = onClose,
)

/** Settings › Lights with its data; reads the rooms and the automations once as it opens, for the choices. */
@Composable
internal fun LightsSettingsHost(
    settings: LightsSettings,
    bridges: Bridges,
    view: LightsView,
    connect: LightsConnect,
    toaster: Toaster,
) {
    val prefs by settings.prefs.collectAsStateWithLifecycle(null)
    val rooms by view.rooms.collectAsStateWithLifecycle(emptyList())
    val problem by view.problem.collectAsStateWithLifecycle(null)
    val system by bridges.system.collectAsStateWithLifecycle()
    var automations by remember { mutableStateOf<List<Automation>?>(null) }
    var finding by remember { mutableStateOf(false) }
    var findLine by remember { mutableStateOf<String?>(null) }
    val pairing = remember { PairingRun() }
    val overlay = LocalOverlayHost.current
    val scope = rememberCoroutineScope()
    LaunchedEffect(system) {
        val s = system
        automations = null
        if (s != null) {
            findLine = null
            automations = readBridge(s)
        }
    }
    val p = prefs ?: return
    fun change(write: suspend () -> Unit) {
        scope.launch { toaster.save(write) { Log.w(TAG, "Couldn't save a lights setting (${it::class.simpleName})") } }
    }
    fun closePairing() {
        pairing.job?.cancel()
        pairing.sheet = null
        overlay.dismiss()
    }
    fun showPairing(state: PairingState, idle: Boolean, onTryAgain: () -> Unit) {
        val sheet = Any()
        pairing.sheet = sheet
        overlay.show(closesWhenIdle = idle) {
            // The host closing this sheet by itself ends the run; a sheet replaced by ours is no longer current.
            DisposableEffect(sheet) {
                onDispose {
                    if (pairing.sheet === sheet) {
                        pairing.sheet = null
                        pairing.job?.cancel()
                    }
                }
            }
            PairingSheet(state, onTryAgain, ::closePairing)
        }
    }
    fun dismissPairing() {
        if (pairing.sheet == null) return
        pairing.sheet = null
        overlay.dismiss()
    }
    fun pairWith(bridge: BridgeFound) {
        pairing.job?.cancel()
        pairing.job = scope.launch {
            showPairing(PairingState.WAITING, idle = false, onTryAgain = {})
            when (val outcome = connect.pair(bridge)) {
                PairOutcome.Paired -> {
                    findLine = null
                    toaster.show(PAIRED_WITH_BRIDGE)
                    dismissPairing()
                }
                PairOutcome.NotInTime -> showPairing(PairingState.NOT_IN_TIME, idle = true) { pairWith(bridge) }
                is ConnectFailed -> {
                    findLine = outcome.line
                    dismissPairing()
                }
            }
        }
    }
    val listed = rooms.map { it.id }.toSet()
    LightsSettingsPage(
        LightsSettingsUi(
            paired = system != null,
            status = problem?.let(::problemLine),
            findLine = findLine,
            finding = finding,
            rooms = rooms,
            mainRoomId = p.mainRoomId,
            leftOut = p.leftOut,
            goneLeftOut = if (rooms.isEmpty()) 0 else (p.leftOut - listed).size,
            automations = automations,
            holidayAutomations = p.holidayAutomations,
        ),
        onFind = {
            scope.launch {
                finding = true
                findLine = null
                when (val outcome = connect.find()) {
                    is FindOutcome.Found -> pairWith(outcome.bridge)
                    is ConnectFailed -> findLine = outcome.line
                }
                finding = false
            }
        },
        onEnterAddress = {
            scope.launch {
                overlay.show {
                    AddressSheet(
                        onConnect = { typed ->
                            when (val outcome = connect.enterAddress(typed)) {
                                is FindOutcome.Found -> {
                                    pairWith(outcome.bridge)
                                    null
                                }
                                is ConnectFailed -> outcome.line
                            }
                        },
                        onEdited = overlay::markEdited,
                        onClose = overlay::dismiss,
                    )
                }
            }
        },
        onMainRoom = { id -> change { settings.setMainRoom(id) } },
        onLeftOut = { ids -> change { settings.setLeftOut(ids) } },
        onHolidayAutomations = { ids -> change { settings.setHolidayAutomations(ids) } },
    )
}

/** No check runs while only Settings shows, and the choices need the rooms and the automations. */
private suspend fun readBridge(system: LightsSystem): List<Automation>? = attempt({
    system.readAll()
    system.automations()
}) { e ->
    Log.w(TAG, "Couldn't read the bridge for Settings (${e::class.simpleName})")
    null
}

/** The pairing in progress: its job, and the sheet that is its own (null once closed or replaced). */
private class PairingRun {
    var job: Job? = null
    var sheet: Any? = null
}
