package uk.co.siland.culvery.capability.plugs.ui

import android.util.Log
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
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
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import uk.co.siland.culvery.capability.plugs.ModeChoice
import uk.co.siland.culvery.capability.plugs.PlugActions
import uk.co.siland.culvery.capability.plugs.PlugPrefs
import uk.co.siland.culvery.capability.plugs.PlugSettings
import uk.co.siland.culvery.capability.plugs.PlugState
import uk.co.siland.culvery.capability.plugs.PlugSystem
import uk.co.siland.culvery.capability.plugs.PlugsWords
import uk.co.siland.culvery.core.house.HouseModeInfo
import uk.co.siland.culvery.core.ui.ButtonSurface
import uk.co.siland.culvery.core.ui.Culvery
import uk.co.siland.culvery.core.ui.HhButton
import uk.co.siland.culvery.core.ui.HhPageHeader
import uk.co.siland.culvery.core.ui.HhSectionLabel
import uk.co.siland.culvery.core.ui.HhType
import uk.co.siland.culvery.core.ui.Icons
import uk.co.siland.culvery.core.ui.rememberSingleAction

private const val TAG = "PlugsTab"

/** Edit ends this long after the last change (12 §2.14). */
internal const val EDIT_IDLE_MS = 120_000L

/**
 * The Plugs tab (12 §4.3): the title with how many are on and Edit on the right, each mode's name over its column, then a
 * row per plug in store order. The mode cells are read-only until [editing]. [problem] is the link's line, under the header.
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
    onEdit: () -> Unit,
    onDone: () -> Unit,
    onChoice: (modeId: String, entityId: String, ModeChoice) -> Unit,
    modifier: Modifier = Modifier,
) {
    val c = Culvery.colors
    val ordered = remember(modes) { modes.sortedBy { it.order } }
    Column(modifier.fillMaxSize().testTag("plugs_tab")) {
        HhPageHeader(
            PlugsWords.PLUGS, if (plugs.isEmpty()) null else PlugsWords.plugsOn(plugs),
            trailing = {
                if (ordered.isNotEmpty()) {
                    HhButton(
                        if (editing) PlugsWords.DONE else PlugsWords.EDIT, if (editing) onDone else onEdit, Modifier.testTag("plugs_edit"),
                        icon = if (editing) null else Icons.LOCK, on = ButtonSurface.Bg,
                    )
                }
            },
        )
        if (problem != null) {
            Text(problem, style = HhType.secondary, color = c.danger, modifier = Modifier.padding(bottom = PlugsDimens.lineGap).testTag("plugs_line"))
        }
        if (ordered.isNotEmpty()) ModeHeadings(ordered)
        LazyColumn(Modifier.weight(1f).fillMaxWidth().testTag("plugs_list"), verticalArrangement = Arrangement.spacedBy(PlugsDimens.rowGap)) {
            items(plugs, key = { it.entityId }) { plug ->
                PlugRow(
                    plug, plug.entityId in needsPin, plug.entityId in switching, ordered,
                    { prefs.choice(it, plug.entityId) }, editing,
                    onSwitch = { onSwitch(plug) }, onChoose = { mode, choice -> onChoice(mode, plug.entityId, choice) },
                )
            }
        }
    }
}

/** Each mode's name over its column, once; the left part keeps the row's own widths so the columns line up. */
@Composable
private fun ModeHeadings(modes: List<HouseModeInfo>) {
    Row(
        Modifier.fillMaxWidth().padding(horizontal = PlugsDimens.rowPaddingH, vertical = PlugsDimens.headingGap),
        horizontalArrangement = Arrangement.spacedBy(PlugsDimens.columnGap),
    ) {
        Spacer(Modifier.weight(1f))
        Spacer(Modifier.width(PlugsDimens.switchSlot))
        modes.forEach { mode ->
            Box(Modifier.width(PlugsDimens.modeCell).testTag("mode_heading_${mode.id}"), contentAlignment = Alignment.Center) { HhSectionLabel(mode.name) }
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

    val scope = rememberCoroutineScope()
    val ask = rememberSingleAction(system) { e -> Log.w(TAG, "Asking for the plugs' PIN failed (${e::class.simpleName})") }
    PlugsTab(
        plugs, prefs.needsPin, switching, problem, shown, prefs, editing,
        onSwitch = actions::switch,
        onEdit = { ask.run { if (actions.mayManage()) editing = true } },
        onDone = { editing = false },
        onChoice = { mode, id, choice ->
            changes++
            scope.launch { actions.setChoice(mode, id, choice) }
        },
    )
}
