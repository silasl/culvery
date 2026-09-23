package uk.co.siland.househub.core.plugin

import androidx.compose.runtime.Composable
import kotlinx.coroutines.flow.Flow

interface Capability : HomeCardContributor {
    val id: String
    val label: String
    /** Material Symbols ligature name for the nav rail. */
    val icon: String
    /** Rail position: Calendar 10, Lights 20, Music 30, Climate 40, Security 50. */
    val order: Int
    /** True when at least one connection provides this capability and it has a tab. */
    val hasTab: Flow<Boolean>

    @Composable
    fun TabContent()
}
