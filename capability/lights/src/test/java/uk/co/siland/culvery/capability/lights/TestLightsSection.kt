package uk.co.siland.culvery.capability.lights

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.Box
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import uk.co.siland.culvery.core.plugin.LightsTabSection

/** A plugs section stand-in: [tiles] boxes [tileHeight] tall, tagged `test_plug_<n>`; counts how many watches are open. */
internal class TestLightsSection(
    shown: Boolean = true,
    summary: String? = "1 of 2 plugs on",
    private val tiles: Int = 2,
    private val tileHeight: Dp = 100.dp,
) : LightsTabSection {
    val shownFlow = MutableStateFlow(shown)
    val summaryFlow = MutableStateFlow(summary)
    override val shown: Flow<Boolean> = shownFlow
    override val summary: Flow<String?> = summaryFlow

    /** The watches started and not yet stopped. */
    var watching = 0
        private set

    override fun watch(): () -> Unit {
        watching++
        var open = true
        return { if (open) { open = false; watching-- } }
    }

    @Composable
    override fun Content(modifier: Modifier) {
        Column(modifier.fillMaxWidth()) {
            repeat(tiles) { Box(Modifier.fillMaxWidth().height(tileHeight).testTag("test_plug_$it")) }
        }
    }
}
