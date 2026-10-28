package uk.co.siland.culvery.capability.plugs.ui

import android.util.Log
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import uk.co.siland.culvery.capability.plugs.ModeChoice
import uk.co.siland.culvery.capability.plugs.PlugActions
import uk.co.siland.culvery.capability.plugs.PlugCandidates
import uk.co.siland.culvery.capability.plugs.PlugPrefs
import uk.co.siland.culvery.capability.plugs.PlugSettings
import uk.co.siland.culvery.capability.plugs.PlugState
import uk.co.siland.culvery.capability.plugs.PlugSystem
import uk.co.siland.culvery.capability.plugs.PlugsWords
import uk.co.siland.culvery.core.house.HouseModeInfo
import uk.co.siland.culvery.core.plugin.LocalOverlayHost
import uk.co.siland.culvery.core.ui.ButtonSurface
import uk.co.siland.culvery.core.ui.Culvery
import uk.co.siland.culvery.core.ui.HhButton
import uk.co.siland.culvery.core.ui.HhCard
import uk.co.siland.culvery.core.ui.HhPageHeader
import uk.co.siland.culvery.core.ui.HhType
import uk.co.siland.culvery.core.ui.Icons
import uk.co.siland.culvery.core.ui.gridColumns
import uk.co.siland.culvery.core.ui.rememberSingleAction

private const val TAG = "PlugsTab"

/** Edit ends this long after the last change (12 §2.14). */
internal const val EDIT_IDLE_MS = 120_000L

/**
 * The Plugs tab (12 §4.3): the title with how many are on and Add a plug, a card per plug that switches it, and on the
 * right each mode's list with Leave, On or Off per plug, read-only until [editing]. With no plug there is only the line
 * saying how to add one. [problem] is the link's line, under the header.
 */
@Composable
internal fun PlugsTab(
    plugs: List<PlugState>,
    needsPin: Set<String>,
    switching: Set<String>,
    problem: String?,
    modes: List<HouseModeInfo>,
    prefs: PlugPrefs,
    editing: Boolean,
    onSwitch: (PlugState) -> Unit,
    onSettings: (PlugState) -> Unit,
    onAdd: () -> Unit,
    onEdit: () -> Unit,
    onDone: () -> Unit,
    onChoice: (modeId: String, entityId: String, ModeChoice) -> Unit,
    modifier: Modifier = Modifier,
) {
    val c = Culvery.colors
    Column(modifier.fillMaxSize().testTag("plugs_tab")) {
        HhPageHeader(
            PlugsWords.PLUGS, if (plugs.isEmpty()) null else PlugsWords.plugsOn(plugs),
            trailing = { HhButton(PlugsWords.ADD_A_PLUG, onAdd, Modifier.testTag("plugs_add"), icon = Icons.ADD, on = ButtonSurface.Bg) },
        )
        if (problem != null) {
            Text(problem, style = HhType.secondary, color = c.danger, modifier = Modifier.padding(bottom = PlugsDimens.lineGap).testTag("plugs_line"))
        }
        if (plugs.isEmpty()) {
            Text(PlugsWords.NO_PLUGS, style = HhType.body, color = c.mute, modifier = Modifier.testTag("plugs_empty"))
            return@Column
        }
        Row(Modifier.weight(1f).fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(PlugsDimens.gap)) {
            LazyVerticalGrid(
                columns = GridCells.Fixed(gridColumns(plugs.size)),
                horizontalArrangement = Arrangement.spacedBy(PlugsDimens.gap),
                verticalArrangement = Arrangement.spacedBy(PlugsDimens.gap),
                modifier = Modifier.weight(1f).fillMaxHeight().testTag("plugs_grid"),
            ) {
                itemsIndexed(plugs, key = { _, it -> it.entityId }) { i, plug ->
                    PlugCard(plug, i, plug.entityId in needsPin, plug.entityId in switching, { onSwitch(plug) }, { onSettings(plug) })
                }
            }
            if (modes.isNotEmpty()) ModeColumn(modes, plugs, prefs, editing, onEdit, onDone, onChoice, Modifier.width(PlugsDimens.modeColumn).fillMaxHeight())
        }
    }
}

@Composable
private fun ModeColumn(
    modes: List<HouseModeInfo>,
    plugs: List<PlugState>,
    prefs: PlugPrefs,
    editing: Boolean,
    onEdit: () -> Unit,
    onDone: () -> Unit,
    onChoice: (modeId: String, entityId: String, ModeChoice) -> Unit,
    modifier: Modifier,
) {
    HhCard(modifier, padding = PaddingValues(horizontal = PlugsDimens.modePaddingH, vertical = PlugsDimens.modePaddingV)) {
        Column(Modifier.verticalScroll(rememberScrollState()).fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(PlugsDimens.sectionGap)) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                HhButton(
                    if (editing) PlugsWords.DONE else PlugsWords.EDIT, if (editing) onDone else onEdit, Modifier.testTag("plugs_edit"),
                    icon = if (editing) null else Icons.LOCK, on = ButtonSurface.Surf,
                )
            }
            ModeSections(modes, plugs, prefs, editing, onChoice)
        }
    }
}

/** The tab over the system and the store; while it shows, the plugs are watched (12 §4.1). Edit ends on Done, on leaving the tab and [EDIT_IDLE_MS] after the last change. */
@Composable
internal fun PlugsTabHost(system: PlugSystem, actions: PlugActions, settings: PlugSettings, modes: Set<HouseModeInfo>) {
    DisposableEffect(system) {
        val stopWatching = system.watch()
        onDispose { stopWatching() }
    }
    val plugs by remember(system) { system.plugs.map { it.orEmpty() } }.collectAsStateWithLifecycle(emptyList())
    val problem by system.problem.collectAsStateWithLifecycle(null)
    val prefs by settings.prefs.collectAsStateWithLifecycle(PlugPrefs())
    val switching by actions.switching.collectAsStateWithLifecycle()
    val shown = remember(modes) { modes.toList() }

    var editing by remember { mutableStateOf(false) }
    var changes by remember { mutableIntStateOf(0) }
    LaunchedEffect(editing, changes) {
        if (editing) {
            delay(EDIT_IDLE_MS)
            editing = false
        }
    }

    val overlay = LocalOverlayHost.current
    val scope = rememberCoroutineScope()
    val ask = rememberSingleAction(system) { e -> Log.w(TAG, "Asking for the plugs' PIN failed (${e::class.simpleName})") }
    PlugsTab(
        plugs, prefs.needsPin, switching, problem, shown, prefs, editing,
        onSwitch = actions::switch,
        onSettings = { plug ->
            ask.run { if (actions.mayManage()) overlay.show { PlugSettingsSheetHost(plug, actions, settings, scope, overlay::dismiss) } }
        },
        onAdd = { ask.run { if (actions.mayManage()) overlay.show { AddPlugSheetHost(system, actions, settings, scope, overlay::dismiss) } } },
        onEdit = { ask.run { if (actions.mayManage()) editing = true } },
        onDone = { editing = false },
        onChoice = { mode, id, choice ->
            changes++
            scope.launch { actions.setChoice(mode, id, choice) }
        },
    )
}

@Composable
private fun AddPlugSheetHost(system: PlugSystem, actions: PlugActions, settings: PlugSettings, scope: CoroutineScope, onClose: () -> Unit) {
    val candidates by produceState<PlugCandidates?>(null, system) { value = system.available() }
    val prefs by settings.prefs.collectAsStateWithLifecycle(PlugPrefs())
    AddPlugSheet(candidates, prefs.plugs, onPick = { scope.launch { actions.add(it.entityId) } }, onClose = onClose)
}

@Composable
private fun PlugSettingsSheetHost(plug: PlugState, actions: PlugActions, settings: PlugSettings, scope: CoroutineScope, onClose: () -> Unit) {
    val prefs by settings.prefs.collectAsStateWithLifecycle(PlugPrefs())
    PlugSettingsSheet(
        plug, plug.entityId in prefs.needsPin,
        onNeedsPin = { scope.launch { actions.setNeedsPin(plug.entityId, it) } },
        onRemove = { scope.launch { actions.remove(plug.entityId) } },
        onClose = onClose,
    )
}
