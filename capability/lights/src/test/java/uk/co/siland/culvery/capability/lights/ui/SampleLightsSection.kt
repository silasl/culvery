package uk.co.siland.culvery.capability.lights.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import uk.co.siland.culvery.core.plugin.LightsTabSection
import uk.co.siland.culvery.core.ui.Culvery
import uk.co.siland.culvery.core.ui.HhCard
import uk.co.siland.culvery.core.ui.HhSectionLabel
import uk.co.siland.culvery.core.ui.HhType

/** For screenshots: the plugs' place under the rooms, four small tiles to a row. The real tiles are drawn by Plugs. */
internal class SampleLightsSection(private val plugs: List<Pair<String, Boolean>>) : LightsTabSection {
    override val shown: Flow<Boolean> = flowOf(true)
    override val summary: Flow<String?> = flowOf("${plugs.count { it.second }} of ${plugs.size} plugs on")
    override fun watch(): () -> Unit = {}

    @Composable
    override fun Content(modifier: Modifier) {
        val c = Culvery.colors
        Column(modifier, verticalArrangement = Arrangement.spacedBy(10.dp)) {
            HhSectionLabel("Plugs")
            plugs.chunked(4).forEach { row ->
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    row.forEach { (name, on) ->
                        HhCard(Modifier.weight(1f), radius = 20.dp, padding = PaddingValues(14.dp)) {
                            Text(name, style = HhType.body, color = c.ink, maxLines = 1)
                            Text(if (on) "On" else "Off", style = HhType.secondary, color = if (on) c.accent else c.mute, modifier = Modifier.padding(top = 2.dp))
                        }
                    }
                    repeat(4 - row.size) { Spacer(Modifier.weight(1f)) }
                }
            }
        }
    }
}
