package uk.co.siland.culvery.capability.plugs.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import javax.inject.Inject
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import uk.co.siland.culvery.capability.plugs.PlugActions
import uk.co.siland.culvery.capability.plugs.PlugPrefs
import uk.co.siland.culvery.capability.plugs.PlugSettings
import uk.co.siland.culvery.capability.plugs.PlugState
import uk.co.siland.culvery.capability.plugs.PlugSystem
import uk.co.siland.culvery.capability.plugs.PlugsWords
import uk.co.siland.culvery.core.plugin.LightsTabSection
import uk.co.siland.culvery.core.ui.Culvery
import uk.co.siland.culvery.core.ui.HhIcon
import uk.co.siland.culvery.core.ui.HhSectionLabel
import uk.co.siland.culvery.core.ui.HhType
import uk.co.siland.culvery.core.ui.Icons

/** The plugs under the Lights tab's rooms (15a §4.5): shown once Home Assistant is linked and at least one plug is shown. */
class PlugsSection @Inject constructor(
    systems: Set<@JvmSuppressWildcards PlugSystem>,
    private val actions: PlugActions,
    private val settings: PlugSettings,
) : LightsTabSection {
    private val system = systems.firstOrNull()

    override val shown: Flow<Boolean> =
        system?.plugs?.map { !it.isNullOrEmpty() }?.distinctUntilChanged() ?: flowOf(false)

    override val summary: Flow<String?> =
        system?.plugs?.map { PlugsWords.plugsOnSummary(it.orEmpty()) }?.distinctUntilChanged() ?: flowOf(null)

    override fun watch(): () -> Unit = system?.watch() ?: {}

    @Composable
    override fun Content(modifier: Modifier) {
        val system = system ?: return
        val plugs by remember(system) { system.plugs.map { it.orEmpty() } }.collectAsStateWithLifecycle(emptyList())
        val problem by system.problem.collectAsStateWithLifecycle(null)
        val prefs by settings.prefs.collectAsStateWithLifecycle(PlugPrefs())
        val switching by actions.switching.collectAsStateWithLifecycle()
        PlugsSectionBody(plugs, prefs.needsPin, switching, problem, actions::switch, modifier)
    }
}

/** The label, the link's [problem] line and the tiles, four to a row in store order. */
@Composable
internal fun PlugsSectionBody(
    plugs: List<PlugState>,
    needsPin: Set<String>,
    switching: Set<String>,
    problem: String?,
    onSwitch: (PlugState) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier.testTag("plugs_section"), verticalArrangement = Arrangement.spacedBy(PlugsDimens.tileGap)) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(PlugsDimens.labelIconGap)) {
            HhIcon(Icons.POWER, size = PlugsDimens.labelIcon, tint = Culvery.colors.mute)
            HhSectionLabel(PlugsWords.SECTION)
        }
        if (problem != null) {
            Text(problem, style = HhType.secondary, color = Culvery.colors.danger, modifier = Modifier.padding(bottom = PlugsDimens.lineGap).testTag("plugs_line"))
        }
        plugs.chunked(PlugsDimens.tilesPerRow).forEach { row ->
            Row(Modifier.height(IntrinsicSize.Max), horizontalArrangement = Arrangement.spacedBy(PlugsDimens.tileGap)) {
                row.forEach { plug ->
                    PlugTile(plug, plug.entityId in needsPin, plug.entityId in switching, { onSwitch(plug) }, Modifier.weight(1f).fillMaxHeight())
                }
                // A short last row keeps the tiles' width.
                repeat(PlugsDimens.tilesPerRow - row.size) { Spacer(Modifier.weight(1f)) }
            }
        }
    }
}
