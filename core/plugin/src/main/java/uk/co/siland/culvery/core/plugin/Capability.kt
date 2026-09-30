package uk.co.siland.culvery.core.plugin

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

    /** This capability's block in Settings, if it has one (3a design §4.1: the calendar's connections). */
    @Composable
    fun SettingsSection() {
    }

    /** This capability's wizard steps (4a design D7), placed by [SetupStep.order] among the core ones. */
    fun setupSteps(): List<SetupStep> = emptyList()

    /** This capability's Settings pages (4a design D7), placed by [SettingsPage.order]. */
    fun settingsPages(): List<SettingsPage> = emptyList()
}
