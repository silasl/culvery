package uk.co.siland.culvery.capability.plugs.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.coroutines.launch
import uk.co.siland.culvery.capability.plugs.PlugActions
import uk.co.siland.culvery.capability.plugs.PlugCandidates
import uk.co.siland.culvery.capability.plugs.PlugPrefs
import uk.co.siland.culvery.capability.plugs.PlugSettings
import uk.co.siland.culvery.capability.plugs.PlugSystem
import uk.co.siland.culvery.capability.plugs.PlugsWords
import uk.co.siland.culvery.core.ui.Culvery
import uk.co.siland.culvery.core.ui.HhSectionLabel
import uk.co.siland.culvery.core.ui.HhSwitchRow
import uk.co.siland.culvery.core.ui.HhType

/** One plug's row. [line] is set for a shown plug Home Assistant no longer has. */
data class PlugRowUi(val entityId: String, val name: String, val shown: Boolean, val needsPin: Boolean, val line: String?) {
    override fun toString() = "PlugRowUi(shown=$shown, needsPin=$needsPin)"
}

/** [rows] are empty until Home Assistant has answered; [line] says why there are none, or that the list can't be read. */
data class PlugsSettingsUi(val rows: List<PlugRowUi>, val line: String?)

@Composable
fun PlugsSettings(ui: PlugsSettingsUi, onShow: (String, Boolean) -> Unit, onNeedsPin: (String, Boolean) -> Unit) {
    val c = Culvery.colors
    Column(verticalArrangement = Arrangement.spacedBy(PlugsDimens.settingsGap), modifier = Modifier.testTag("plugs_settings")) {
        HhSectionLabel(PlugsWords.PLUGS)
        Text(PlugsWords.PLUGS_HELP, style = HhType.body, color = c.mute, modifier = Modifier.testTag("plugs_help"))
        ui.line?.let { Text(it, style = HhType.body, color = c.mute, modifier = Modifier.testTag("plugs_line")) }
        ui.rows.forEachIndexed { index, row ->
            Column(verticalArrangement = Arrangement.spacedBy(PlugsDimens.settingsRowGap)) {
                HhSectionLabel(row.name, Modifier.testTag("plugs_name_$index"))
                row.line?.let { Text(it, style = HhType.body, color = c.mute, modifier = Modifier.testTag("plugs_gone_$index")) }
                HhSwitchRow(
                    PlugsWords.SHOW_ON_TAB, null, row.shown, "plugs_show_$index", { onShow(row.entityId, it) },
                    spokenName = PlugsWords.showSpoken(row.name),
                )
                HhSwitchRow(
                    PlugsWords.NEEDS_A_PIN, null, row.needsPin, "plugs_pin_$index", { onNeedsPin(row.entityId, it) },
                    enabled = row.shown, spokenName = PlugsWords.needsPinSpoken(row.name),
                )
            }
        }
    }
}

/** Settings' Plugs block over [system]; reads Home Assistant's plugs as the section appears and after each Show change; the page lists them against the stored choices. */
@Composable
fun PlugsSettingsHost(system: PlugSystem, actions: PlugActions, settings: PlugSettings) {
    val prefs by remember(settings) { settings.prefs }.collectAsStateWithLifecycle(PlugPrefs())
    var found by remember { mutableStateOf<PlugCandidates?>(null) }
    var reads by remember { mutableIntStateOf(0) }
    val scope = rememberCoroutineScope()
    LaunchedEffect(system, reads) { found = system.available() }

    PlugsSettings(
        found?.let { plugsSettingsUi(it, prefs) } ?: PlugsSettingsUi(emptyList(), null),
        onShow = { id, on -> scope.launch { actions.show(id, on); reads++ } },
        onNeedsPin = { id, on -> scope.launch { actions.setNeedsPin(id, on) } },
    )
}

/** The system's order is kept. With the registry unreadable no plug can be called gone, so the shown ones are listed unnamed and without the gone line. */
internal fun plugsSettingsUi(found: PlugCandidates, prefs: PlugPrefs): PlugsSettingsUi {
    if (found is PlugCandidates.Failed) {
        return PlugsSettingsUi(prefs.plugs.map { PlugRowUi(it, PlugsWords.A_PLUG, true, it in prefs.needsPin, null) }, found.line)
    }
    val available = (found as PlugCandidates.Found).all
    val known = available.mapTo(HashSet()) { it.entityId }
    val rows = available.map { PlugRowUi(it.entityId, it.name, it.entityId in prefs.plugs, it.entityId in prefs.needsPin, null) } +
        prefs.plugs.filter { it !in known }.map { PlugRowUi(it, PlugsWords.A_PLUG, true, it in prefs.needsPin, PlugsWords.GONE_SHOWN) }
    return PlugsSettingsUi(rows, PlugsWords.NO_TP_LINK_PLUGS.takeIf { rows.isEmpty() })
}
