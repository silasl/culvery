package uk.co.siland.culvery.core.plugin

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import kotlinx.coroutines.flow.Flow

/** The plugs' part of the Lights tab (15a §4.5), bound `@IntoSet` by Plugs: drawn under the rooms, watched while the tab shows. */
interface LightsTabSection {
    val shown: Flow<Boolean>

    /** "1 of 2 plugs on"; null when no plug's state is known. */
    val summary: Flow<String?>

    /** Starts watching; call the result when the tab goes. */
    fun watch(): () -> Unit

    @Composable
    fun Content(modifier: Modifier)
}
