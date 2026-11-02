package uk.co.siland.culvery.capability.party.ui

import android.util.Log
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import java.io.IOException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import uk.co.siland.culvery.capability.party.ModeIcons
import uk.co.siland.culvery.capability.party.ModesPrefs
import uk.co.siland.culvery.capability.party.ModesStore
import uk.co.siland.culvery.capability.party.ModesWords
import uk.co.siland.culvery.capability.party.StoredMode
import uk.co.siland.culvery.capability.party.newModeId
import uk.co.siland.culvery.core.house.HouseModes
import uk.co.siland.culvery.core.house.HousePlugs
import uk.co.siland.culvery.core.plugin.BuiltInModePage
import uk.co.siland.culvery.core.plugin.LocalOverlayHost
import uk.co.siland.culvery.core.plugin.OverlayHost
import uk.co.siland.culvery.core.plugin.SettingsPage
import uk.co.siland.culvery.core.plugin.Toaster
import uk.co.siland.culvery.core.plugin.saving
import uk.co.siland.culvery.core.ui.HhAddRow
import uk.co.siland.culvery.core.ui.HhBackLink
import uk.co.siland.culvery.core.ui.HhIcon
import uk.co.siland.culvery.core.ui.HhListRow
import uk.co.siland.culvery.core.ui.HhPageTitle
import uk.co.siland.culvery.core.ui.Icons
import uk.co.siland.culvery.core.ui.SettingsTokens

private const val TAG = "ModesPage"

/** How every Modes page logs a change that didn't save. */
internal val logSave: (IOException) -> Unit = { Log.w(TAG, "Couldn't save a mode (${it::class.simpleName})") }

/** Settings › Modes (15b §4.1): the list of modes, and the page of the one opened. Between Lights (470) and Door (500). */
class ModesPage internal constructor(
    private val store: ModesStore,
    private val controls: ModeControls,
    private val holidayOn: StateFlow<Boolean?>,
    private val room: Flow<Int>,
    private val plugs: HousePlugs?,
    private val toaster: Toaster,
    private val builtIn: Set<BuiltInModePage> = emptySet(),
) : SettingsPage {
    override val id = "modes"
    override val title = ModesWords.PAGE_TITLE
    override val order = 475

    @Composable
    override fun Content() {
        // The mode whose page shows; null shows the list.
        var open by rememberSaveable { mutableStateOf<String?>(null) }
        ModesHost(store, controls, holidayOn, room, plugs, toaster, builtIn, open) { open = it }
    }
}

/** A mode's row: [id] is [HouseModes.HOLIDAY] for Holiday mode. [toString] leaves out all of it. */
data class ModeRowUi(val id: String, val icon: String, val name: String, val summary: String) {
    override fun toString() = "ModeRowUi"
}

@Composable
private fun ModesHost(
    store: ModesStore,
    controls: ModeControls,
    holidayOn: StateFlow<Boolean?>,
    room: Flow<Int>,
    plugs: HousePlugs?,
    toaster: Toaster,
    builtIn: Set<BuiltInModePage>,
    open: String?,
    setOpen: (String?) -> Unit,
) {
    val prefs by store.prefs.collectAsStateWithLifecycle(null)
    val on by controls.house.onMode.collectAsStateWithLifecycle()
    val homeRoom by room.collectAsStateWithLifecycle(null)
    val holiday by holidayOn.collectAsStateWithLifecycle()
    val overlay = LocalOverlayHost.current
    val scope = rememberCoroutineScope()
    val change = scope.saving(toaster, logSave)
    val p = prefs ?: return
    val holidayOpen = open == HouseModes.HOLIDAY
    val opened = if (holidayOpen) null else open?.let(p::mode)
    // A mode deleted while its page is open leaves the list.
    val gone = open != null && !holidayOpen && opened == null
    LaunchedEffect(open, gone) { if (gone) setOpen(null) }

    fun add(name: String, icon: String) = change {
        val spots = room.first()
        var id = ""
        store.update { now ->
            id = newModeId(now.modes.map { it.id }.toSet())
            now.copy(modes = now.modes + StoredMode(id, name, icon, showOnHome = now.onHome() < spots, needsPin = true))
        }
        overlay.dismiss()
        setOpen(id)
    }

    fun save(id: String, transform: (StoredMode) -> StoredMode) = change {
        store.update { it.withMode(id, transform) }
        overlay.dismiss()
    }

    Column(verticalArrangement = Arrangement.spacedBy(SettingsTokens.rowGap), modifier = Modifier.testTag("settings_modes")) {
        if (!holidayOpen && opened == null) {
            val rows = modeRows(p, holiday == true, on, plugCounts(plugs, p.modes.map { it.id }))
            ModesList(rows, onOpen = setOpen, onAdd = { overlay.showAddSheet(p, ::add) })
        } else {
            HhBackLink(ModesWords.BACK_TO_MODES, "modes_back") { setOpen(null) }
            if (opened == null) {
                ModeHeader(Icons.BEACH_ACCESS, ModesWords.HOLIDAY_NAME, onIcon = null, onName = null)
                HolidayPage(p, homeRoom, controls, builtIn.firstOrNull { it.modeId == HouseModes.HOLIDAY }, plugs, store, toaster)
            } else {
                val others = p.modes.filter { it.id != opened.id }.map { it.name }
                ModeHeader(
                    ModeIcons.drawn(opened.icon), opened.name,
                    onIcon = {
                        overlay.show {
                            ModeIconSheet(ModeIcons.drawn(opened.icon), onSave = { icon -> save(opened.id) { it.copy(icon = icon) } }, onClose = overlay::dismiss)
                        }
                    },
                    onName = {
                        overlay.show {
                            RenameModeSheet(opened.name, others, onSave = { name -> save(opened.id) { it.copy(name = name) } }, onClose = overlay::dismiss)
                        }
                    },
                )
                StoredModePage(opened, p, homeRoom, controls, plugs, store, toaster)
            }
        }
    }
}

/** Holiday mode's row, then each stored mode's; an icon off the grid is drawn as Party mode's. [on] is the stored mode that is on, if any. */
internal fun modeRows(prefs: ModesPrefs, holidayOn: Boolean, on: String?, plugCounts: Map<String, Int>): List<ModeRowUi> =
    listOf(ModeRowUi(HouseModes.HOLIDAY, Icons.BEACH_ACCESS, ModesWords.HOLIDAY_NAME, ModesWords.holidaySummary(holidayOn, prefs.holidayOnHome))) +
        prefs.modes.map { ModeRowUi(it.id, ModeIcons.drawn(it.icon), it.name, ModesWords.summary(it, on == it.id, plugCounts[it.id] ?: 0)) }

internal fun ModesPrefs.onHome() = (if (holidayOnHome) 1 else 0) + modes.count { it.showOnHome }

private fun OverlayHost.showAddSheet(prefs: ModesPrefs, onAdd: (String, String) -> Unit) = show {
    AddModeSheet(prefs.modes.map { it.name }, onAdd, onClose = ::dismiss)
}

/** The page's title, the rows, then the dashed "Add a mode", the Settings row gap apart. */
@Composable
fun ModesList(rows: List<ModeRowUi>, onOpen: (String) -> Unit, onAdd: () -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(SettingsTokens.rowGap)) {
        HhPageTitle(ModesWords.PAGE_TITLE, ModesWords.PAGE_LINE)
        rows.forEach { row ->
            HhListRow(
                row.name, Modifier.testTag("mode_row_${row.id}"),
                leading = { HhIcon(row.icon) },
                subtitle = row.summary,
                trailing = { HhIcon(Icons.CHEVRON_RIGHT) },
                onClick = { onOpen(row.id) }, clickLabel = ModesWords.openMode(row.name),
            )
        }
        HhAddRow(ModesWords.ADD_MODE, "modes_add", onAdd)
    }
}

/** How many plugs each of [ids] switches; 0 while unread, with no plug system, or when a read fails. */
@Composable
private fun plugCounts(plugs: HousePlugs?, ids: List<String>): Map<String, Int> =
    produceState(ids.associateWith { 0 }, plugs, ids) {
        if (plugs == null || ids.isEmpty()) {
            value = ids.associateWith { 0 }
        } else {
            combine(
                ids.map { id ->
                    plugs.addedOrNone(id, TAG).map { it.size }
                },
            ) { counts -> ids.zip(counts.toList()).toMap() }.collect { value = it }
        }
    }.value
